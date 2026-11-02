# OpenTofu, the azurerm provider, the state backend and the state encryption
# (ADR 0014). The backend's settings are in backend.hcl: tofu init
# -backend-config=backend.hcl.

terraform {
  required_version = "~> 1.13"

  required_providers {
    azurerm = {
      source  = "hashicorp/azurerm"
      version = "~> 5.11"
    }
  }

  # An Azure Storage blob: its lease locks the state while a run holds it.
  backend "azurerm" {}

  # The state and saved plans are encrypted before they leave the machine.
  # Unencrypted state or plans are refused.
  encryption {
    key_provider "pbkdf2" "passphrase" {
      passphrase = var.state_passphrase
    }

    method "aes_gcm" "passphrase" {
      keys = key_provider.pbkdf2.passphrase
    }

    state {
      method   = method.aes_gcm.passphrase
      enforced = true
    }

    plan {
      method   = method.aes_gcm.passphrase
      enforced = true
    }
  }
}

provider "azurerm" {
  features {}

  # The subscription and the credentials come from the ARM_* environment
  # variables (infra/README.md), or from the az CLI's login.
  # The bootstrap script registers the few resource providers the
  # configuration uses, so the read-only identity of pull requests can plan.
  resource_provider_registrations = "none"
}
