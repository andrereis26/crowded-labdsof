output "public_ip" {
  description = "Static public IP of the VM: the router and SSH."
  value       = azurerm_public_ip.main.ip_address
}

output "fqdn" {
  description = "DNS name of the public IP."
  value       = azurerm_public_ip.main.fqdn
}

output "admin_username" {
  description = "User to log in as over SSH."
  value       = var.admin_username
}

output "resource_group_name" {
  description = "Resource group of the environment."
  value       = azurerm_resource_group.main.name
}

output "network_security_group_name" {
  description = "Network security group, where the deploy job opens SSH for its own address."
  value       = azurerm_network_security_group.main.name
}

output "vm_name" {
  description = "Name of the virtual machine."
  value       = azurerm_linux_virtual_machine.main.name
}
