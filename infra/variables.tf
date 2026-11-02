variable "state_passphrase" {
  description = "Passphrase that encrypts the state and saved plans (at least 16 characters). Kept in the TOFU_STATE_PASSPHRASE secret."
  type        = string
  sensitive   = true

  validation {
    condition     = length(var.state_passphrase) >= 16
    error_message = "The state passphrase must have at least 16 characters."
  }
}

variable "name" {
  description = "Prefix of every resource name."
  type        = string
  default     = "crowded"
}

variable "location" {
  description = "Azure region. Azure for Students allows only some regions."
  type        = string
  default     = "spaincentral"
}

variable "vm_size" {
  description = "Size of the virtual machine: 2 vCPU, 4 GB, burstable (ADR 0014)."
  type        = string
  default     = "Standard_B2ls_v2"
}

variable "os_disk_size_gb" {
  description = "Size of the OS disk, which holds Ubuntu and the releases in /opt/crowded."
  type        = number
  default     = 32
}

variable "data_disk_size_gb" {
  description = "Size of the managed data disk mounted at /var/lib/docker: images and the volumes of the data stores."
  type        = number
  default     = 64
}

variable "admin_username" {
  description = "Linux user that the team and the deploy job log in as over SSH."
  type        = string
  default     = "crowded"
}

variable "admin_ssh_public_keys" {
  description = "Public SSH keys allowed to log in as the admin user: the deploy job's key and the team's."
  type        = list(string)

  validation {
    condition     = length(var.admin_ssh_public_keys) > 0
    error_message = "At least one public SSH key is needed: the deploy job's."
  }
}

variable "ssh_allowed_cidrs" {
  description = "Addresses (CIDR) allowed to reach SSH. The deploy job opens SSH to its own address only while it runs."
  type        = list(string)
  default     = []
}

variable "router_ports" {
  description = "Ports the router publishes, open to everyone."
  type        = list(number)
  default     = [80]
}

variable "dns_label" {
  description = "DNS label of the public IP: <label>.<location>.cloudapp.azure.com."
  type        = string
  default     = "crowded-labdsof"
}

variable "address_space" {
  description = "Address space of the virtual network; the subnet takes its first /24."
  type        = string
  default     = "10.10.0.0/16"
}
