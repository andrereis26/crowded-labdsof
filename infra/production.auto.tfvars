# Values of the production environment (infra/README.md). Not secret.

# Who may log in to the VM: the deploy job's key (its private key is the
# SSH_PRIVATE_KEY secret), then the team members who need SSH.
admin_ssh_public_keys = [
  "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIHkOTw/oKiHdNGAZIjXHJsOj6TUHdiJWTr0EADdh+Bgy crowded-deploy",
]
