# Infrastructure

The production environment on Azure, as code ([ADR 0013](../docs/deliverables/adr/0013-single-azure-vm-docker-swarm-with-terraform.md), [ADR 0014](../docs/deliverables/adr/0014-opentofu-state-access-and-deployment-from-main.md)): one resource group with the virtual network and subnet, the network security group, which opens ports 80 and 443 to the router (HTTPS, and Let's Encrypt's challenge, [`deployment/`](../deployment/README.md#tls)), a static public IP with a DNS name, the Ubuntu 24.04 virtual machine (`Standard_B2ls_v2`, 2 vCPU, 4 GB) and its managed data disk, in `spaincentral`, provisioned with **OpenTofu**. Pull requests that change it show the plan; every merge to `main` applies it and deploys the release ([`deployment/`](../deployment/README.md)).

| Path | What it is |
|---|---|
| `versions.tf` | OpenTofu and provider versions, the state backend and the state encryption |
| `variables.tf` | Settings, with their defaults: region, VM size, disk sizes, SSH keys and addresses |
| `main.tf` | The resources |
| `outputs.tf` | Public IP, DNS name and names the deploy job needs |
| `production.auto.tfvars` | This environment's values: the public SSH keys allowed on the VM |
| `backend.hcl` | Where the state lives, written by `bootstrap.sh` |
| `cloud-init.yaml`, `first-boot.sh` | The VM's first boot: mounts the data disk at `/var/lib/docker` and runs `deployment/remote-deploy.sh --bootstrap-only` (Docker Engine, Doppler CLI, single-node Swarm) |
| `bootstrap.sh` | Prepares a subscription once: state storage and the identities GitHub Actions signs in as |
| `.terraform.lock.hcl` | Provider versions and hashes |

## How It Runs

| When | Workflow | What |
|---|---|---|
| A pull request changes `infra/` (or the deployment scripts the first boot copies) | `infrastructure.yml` | `tofu fmt -check`, `tofu validate`, Trivy's misconfiguration scan, then `tofu plan`, posted as a comment in the pull request. `Infrastructure result` must pass to merge |
| Every push to `main` | `release.yml` | The app workflows (checks and images), `tofu apply`, then the deployment: SSH opened for the runner, `deployment/` copied to the host, `remote-deploy.sh`, a check from outside, `remote-rollback.sh` if that check fails |
| By hand | `release.yml` (*Run workflow*) | The same, and it always deploys, even when no image changed |

The plan in pull requests signs in as the read-only identity, so it cannot change anything; only jobs in the `production` environment, which only `main` can use, can apply.

## Setting It Up

Done once per subscription, by an owner of it. Recreating the environment elsewhere, for example when the student credit runs out, is the same steps.

You need the [Azure CLI](https://learn.microsoft.com/cli/azure/install-azure-cli), [OpenTofu 1.13](https://opentofu.org/docs/intro/install/), the [GitHub CLI](https://cli.github.com/) logged in with admin rights on the repository, `ssh-keygen` and `openssl`. Commands run from the repository root.

### 1. Sign in to Azure

```bash
az login
az account set --subscription "<Azure for Students subscription ID>"
```

Azure for Students allows only some regions, listed by `az policy assignment list --query "[].parameters"`; this subscription allows `spaincentral`, the default. In another subscription, pick an allowed region where the size is offered (`az vm list-skus --location <region> --size Standard_B2`) and set `location` (and `vm_size` if needed) in `production.auto.tfvars`, and `LOCATION` for the next step.

### 2. State storage and identities

```bash
bash infra/bootstrap.sh
```

It registers the resource providers, creates the resource group `crowded-tfstate` with the state's storage account and container, creates the managed identities `crowded-github-deploy` and `crowded-github-plan` with their GitHub federated credentials and roles, lets you read and write the state, and writes `infra/backend.hcl`. Commit `backend.hcl`. It prints the four values for step 4. Running it again changes nothing.

### 3. Passphrase and deploy key

```bash
# The state passphrase: keep a copy in the team's password manager too.
# Without it the state cannot be read.
openssl rand -base64 32 | tr -d '\n' | gh secret set TOFU_STATE_PASSPHRASE

# The deploy job's SSH key.
ssh-keygen -t ed25519 -N '' -C crowded-deploy -f crowded-deploy
gh secret set SSH_PRIVATE_KEY < crowded-deploy
cat crowded-deploy.pub    # goes into production.auto.tfvars
rm crowded-deploy crowded-deploy.pub
```

Put the public key in `admin_ssh_public_keys` in `infra/production.auto.tfvars`, with the public keys of the team members who need SSH, and commit it. Public keys are not secrets.

### 4. GitHub settings

```bash
gh secret set AZURE_TENANT_ID --body "<printed by bootstrap.sh>"
gh secret set AZURE_SUBSCRIPTION_ID --body "<printed by bootstrap.sh>"
gh secret set AZURE_CLIENT_ID --body "<printed by bootstrap.sh>"
gh secret set AZURE_PLAN_CLIENT_ID --body "<printed by bootstrap.sh>"

# The production environment, usable only from main, with no required reviewers.
gh api --method PUT repos/{owner}/{repo}/environments/production \
  --input - <<<'{"deployment_branch_policy": {"protected_branches": false, "custom_branch_policies": true}}'
gh api --method POST repos/{owner}/{repo}/environments/production/deployment-branch-policies \
  -f name=main -f type=branch
```

Optional: SSH for the team from fixed addresses, as a JSON list (otherwise SSH opens only for the deploy job):

```bash
gh variable set SSH_ALLOWED_CIDRS --body '["203.0.113.10/32"]'
```

Every secret and variable is listed in [`deployment/README.md`](../deployment/README.md#github-settings).

### 5. First apply

From your machine, signed in with the Azure CLI:

```bash
cd infra
export ARM_SUBSCRIPTION_ID="<subscription ID>"
export TF_VAR_state_passphrase="<the TOFU_STATE_PASSPHRASE value>"
export TF_VAR_ssh_allowed_cidrs='<the SSH_ALLOWED_CIDRS value, or []>'
tofu init -backend-config=backend.hcl
tofu plan
tofu apply
```

The VM's first boot takes a few minutes after `apply` returns (packages, Docker, Doppler, Swarm). Its log: `az vm boot-diagnostics get-boot-log --resource-group crowded-production --name crowded-vm`.

### 6. Pin the host key

Read the VM's SSH host key through Azure, not over the network, and store it:

```bash
key="$(az vm run-command invoke --resource-group crowded-production --name crowded-vm \
  --command-id RunShellScript --scripts 'cat /etc/ssh/ssh_host_ed25519_key.pub' \
  --query 'value[0].message' --output tsv | grep '^ssh-ed25519' | cut -d' ' -f1,2)"
printf '%s %s\n' "$(cd infra && tofu output -raw public_ip)" "$key" | gh secret set SSH_KNOWN_HOSTS
```

### 7. Required check and first deployment

Add `Infrastructure result` to the required status checks of the `main` ruleset (*Settings → Rules → Rulesets*), once the workflow has run in a pull request. Then deploy the current `main`:

```bash
gh workflow run release.yml --ref main
```

The site is at `http://<public IP>/` and `http://crowded-labdsof.spaincentral.cloudapp.azure.com/` (`tofu output public_ip` and `tofu output fqdn`).

## Everyday Tasks

**Change the infrastructure:** edit `infra/`, open a pull request, read the plan in its comment, merge. `main` applies it. Run `tofu fmt -recursive` before committing.

**Save credit:** a stopped (deallocated) VM costs only its disks and IP ([operating costs](../docs/deliverables/operating-costs.md)). Start it again before a deployment or a demo; the IP does not change.

```bash
az vm deallocate --resource-group crowded-production --name crowded-vm
az vm start --resource-group crowded-production --name crowded-vm
```

**Log in to the VM:** `ssh crowded@<public IP>`, from an address in `SSH_ALLOWED_CIDRS` and with a key in `production.auto.tfvars`. Without one of those: `az ssh` is not set up; use `az vm run-command invoke` as in step 6, or add your address for a while:

```bash
az network nsg rule create --resource-group crowded-production --nsg-name crowded-nsg \
  --name ssh-by-hand --priority 310 --access Allow --protocol Tcp --direction Inbound \
  --source-address-prefixes "$(curl -s https://api.ipify.org)/32" --destination-port-ranges 22
# ... and afterwards:
az network nsg rule delete --resource-group crowded-production --nsg-name crowded-nsg --name ssh-by-hand
```

**Roll back by hand:** on the VM, `bash /opt/crowded/deployment/remote-rollback.sh` ([`deployment/README.md`](../deployment/README.md#rolling-back)).

## A New VM

A change to the VM that Azure cannot apply in place (for example its image or the admin user) replaces it; the plan says `must be replaced`. The data disk survives (it cannot be destroyed by `apply`), and the new VM's first boot mounts it again without formatting it. The new VM has a new SSH host key, so after the apply:

1. repeat step 6 to pin the new host key;
2. run the Release workflow by hand (`gh workflow run release.yml --ref main`), which deploys every service.

The releases kept for rollback were on the old VM's OS disk, so the first deployment on a new VM has nothing to roll back to.

## Destroying It

`tofu destroy` stops at the data disk on purpose. To remove everything, first remove the `prevent_destroy` line from `azurerm_managed_disk.data` (locally, not on `main`), then `tofu destroy`. The state storage and identities are removed with `az group delete --name crowded-tfstate`.
