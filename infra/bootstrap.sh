#!/usr/bin/env bash
# Prepares an Azure subscription for the OpenTofu configuration in this folder
# (ADR 0014). Run once, by hand, logged in with `az login` as an owner of the
# subscription. Every step can be run again: what exists is kept.
#
#   1. Registers the resource providers the configuration uses.
#   2. Creates the state's storage account (Azure AD access only, no shared
#      keys, versioning and soft delete) and its blob container.
#   3. Creates two user-assigned managed identities that GitHub Actions signs
#      in as through OIDC, with no secret:
#        crowded-github-deploy  main's Release workflow (environment
#                               production): Contributor on the subscription,
#                               read and write on the state;
#        crowded-github-plan    pull requests: Reader on the subscription,
#                               read on the state.
#   4. Lets the signed-in user read and write the state, to run tofu locally.
#   5. Writes backend.hcl and prints the values for the GitHub settings.
#
# Usage: infra/bootstrap.sh
#
# Environment (optional):
#   AZURE_SUBSCRIPTION_ID  default: the az CLI's current subscription
#   LOCATION               default: spaincentral
#   GITHUB_REPOSITORY      default: andrereis26/crowded-labdsof
#   STATE_RESOURCE_GROUP   default: crowded-tfstate
#   STATE_STORAGE_ACCOUNT  default: crowdedtf + the first 10 characters of the subscription ID

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

log() { printf '[%s] %s\n' "$(date -u +%H:%M:%S)" "$*" >&2; }
die() { printf 'Error: %s\n' "$*" >&2; exit 1; }

command -v az >/dev/null 2>&1 || die "the Azure CLI (az) is missing"
az account show >/dev/null 2>&1 || die "not logged in; run az login first"

SUBSCRIPTION_ID="${AZURE_SUBSCRIPTION_ID:-$(az account show --query id --output tsv)}"
TENANT_ID="$(az account show --subscription "${SUBSCRIPTION_ID}" --query tenantId --output tsv)"
LOCATION="${LOCATION:-spaincentral}"
REPOSITORY="${GITHUB_REPOSITORY:-andrereis26/crowded-labdsof}"
STATE_RESOURCE_GROUP="${STATE_RESOURCE_GROUP:-crowded-tfstate}"
subscription_hex="$(printf '%s' "${SUBSCRIPTION_ID}" | tr -d '-' | tr '[:upper:]' '[:lower:]')"
STATE_STORAGE_ACCOUNT="${STATE_STORAGE_ACCOUNT:-crowdedtf${subscription_hex:0:10}}"
STATE_CONTAINER=tfstate
STATE_KEY=production.tfstate
DEPLOY_IDENTITY=crowded-github-deploy
PLAN_IDENTITY=crowded-github-plan
OIDC_ISSUER=https://token.actions.githubusercontent.com

az account set --subscription "${SUBSCRIPTION_ID}"
SUBSCRIPTION_SCOPE="/subscriptions/${SUBSCRIPTION_ID}"
STATE_ACCOUNT_SCOPE="${SUBSCRIPTION_SCOPE}/resourceGroups/${STATE_RESOURCE_GROUP}/providers/Microsoft.Storage/storageAccounts/${STATE_STORAGE_ACCOUNT}"
STATE_CONTAINER_SCOPE="${STATE_ACCOUNT_SCOPE}/blobServices/default/containers/${STATE_CONTAINER}"

# --- 1. Resource providers ------------------------------------------------------

for namespace in Microsoft.Compute Microsoft.Network Microsoft.Storage Microsoft.ManagedIdentity; do
  if [[ "$(az provider show --namespace "${namespace}" --query registrationState --output tsv)" != Registered ]]; then
    log "Registering ${namespace}"
    az provider register --namespace "${namespace}" --wait --output none
  fi
done

# --- 2. State storage -------------------------------------------------------------

log "Resource group ${STATE_RESOURCE_GROUP} in ${LOCATION}"
az group create --name "${STATE_RESOURCE_GROUP}" --location "${LOCATION}" --output none

if ! az storage account show --name "${STATE_STORAGE_ACCOUNT}" --resource-group "${STATE_RESOURCE_GROUP}" --output none 2>/dev/null; then
  log "Creating the storage account ${STATE_STORAGE_ACCOUNT}"
  az storage account create --name "${STATE_STORAGE_ACCOUNT}" --resource-group "${STATE_RESOURCE_GROUP}" \
    --location "${LOCATION}" --sku Standard_LRS --kind StorageV2 \
    --min-tls-version TLS1_2 --https-only true \
    --allow-blob-public-access false --allow-shared-key-access false --output none
fi
# Earlier versions of the state can be restored for 30 days.
az storage account blob-service-properties update --account-name "${STATE_STORAGE_ACCOUNT}" \
  --resource-group "${STATE_RESOURCE_GROUP}" --enable-versioning true \
  --enable-delete-retention true --delete-retention-days 30 \
  --enable-container-delete-retention true --container-delete-retention-days 30 --output none
# Through Azure Resource Manager: shared keys are off, and the data-plane role
# of step 4 can take minutes to apply.
az storage container-rm create --storage-account "${STATE_STORAGE_ACCOUNT}" \
  --resource-group "${STATE_RESOURCE_GROUP}" --name "${STATE_CONTAINER}" \
  --public-access off --output none

# --- 3. Identities for GitHub Actions ------------------------------------------------

# assign_role <principal id> <principal type> <role> <scope>
assign_role() {
  local existing
  existing="$(az role assignment list --assignee "$1" --role "$3" --scope "$4" --query 'length(@)' --output tsv)"
  if [[ "${existing}" == 0 ]]; then
    log "Granting ${3} on ${4##*/} to ${1}"
    az role assignment create --assignee-object-id "$1" --assignee-principal-type "$2" \
      --role "$3" --scope "$4" --output none
  fi
}

# identity <name> <federated credential name> <OIDC subject>; prints the
# client ID and the principal ID on one line.
identity() {
  local name="$1" credential="$2" subject="$3"
  if ! az identity show --name "${name}" --resource-group "${STATE_RESOURCE_GROUP}" --output none 2>/dev/null; then
    log "Creating the managed identity ${name}"
    az identity create --name "${name}" --resource-group "${STATE_RESOURCE_GROUP}" \
      --location "${LOCATION}" --output none
  fi
  if ! az identity federated-credential show --name "${credential}" --identity-name "${name}" \
    --resource-group "${STATE_RESOURCE_GROUP}" --output none 2>/dev/null; then
    log "Trusting GitHub Actions tokens with subject ${subject} for ${name}"
    az identity federated-credential create --name "${credential}" --identity-name "${name}" \
      --resource-group "${STATE_RESOURCE_GROUP}" --issuer "${OIDC_ISSUER}" \
      --subject "${subject}" --audiences api://AzureADTokenExchange --output none
  fi
  az identity show --name "${name}" --resource-group "${STATE_RESOURCE_GROUP}" \
    --query "join(' ', [clientId, principalId])" --output tsv
}

read -r DEPLOY_CLIENT_ID DEPLOY_PRINCIPAL_ID < <(identity "${DEPLOY_IDENTITY}" github-production \
  "repo:${REPOSITORY}:environment:production")
read -r PLAN_CLIENT_ID PLAN_PRINCIPAL_ID < <(identity "${PLAN_IDENTITY}" github-pull-request \
  "repo:${REPOSITORY}:pull_request")

assign_role "${DEPLOY_PRINCIPAL_ID}" ServicePrincipal Contributor "${SUBSCRIPTION_SCOPE}"
assign_role "${DEPLOY_PRINCIPAL_ID}" ServicePrincipal "Storage Blob Data Contributor" "${STATE_CONTAINER_SCOPE}"
assign_role "${PLAN_PRINCIPAL_ID}" ServicePrincipal Reader "${SUBSCRIPTION_SCOPE}"
assign_role "${PLAN_PRINCIPAL_ID}" ServicePrincipal "Storage Blob Data Reader" "${STATE_CONTAINER_SCOPE}"

# --- 4. The signed-in user ------------------------------------------------------------

USER_OBJECT_ID="$(az ad signed-in-user show --query id --output tsv)"
assign_role "${USER_OBJECT_ID}" User "Storage Blob Data Contributor" "${STATE_CONTAINER_SCOPE}"

# --- 5. Backend and GitHub settings -------------------------------------------------------

cat >"${SCRIPT_DIR}/backend.hcl" <<EOF
# Written by bootstrap.sh: where the state lives (ADR 0014).
resource_group_name  = "${STATE_RESOURCE_GROUP}"
storage_account_name = "${STATE_STORAGE_ACCOUNT}"
container_name       = "${STATE_CONTAINER}"
key                  = "${STATE_KEY}"
use_azuread_auth     = true
EOF
log "Wrote ${SCRIPT_DIR}/backend.hcl"

cat <<EOF

Done. Add these GitHub Actions secrets (infra/README.md):
  AZURE_TENANT_ID        ${TENANT_ID}
  AZURE_SUBSCRIPTION_ID  ${SUBSCRIPTION_ID}
  AZURE_CLIENT_ID        ${DEPLOY_CLIENT_ID}
  AZURE_PLAN_CLIENT_ID   ${PLAN_CLIENT_ID}
Role assignments can take a few minutes to apply.
EOF
