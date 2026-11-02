# Written by bootstrap.sh: where the state lives (ADR 0014).
resource_group_name  = "crowded-tfstate"
storage_account_name = "crowdedtf3fd932584b"
container_name       = "tfstate"
key                  = "production.tfstate"
use_azuread_auth     = true
