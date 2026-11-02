# The production environment of ADR 0013: one resource group with the
# virtual network and subnet, the network security group, a static public IP,
# the Ubuntu virtual machine and its managed data disk. The VM's first boot
# (cloud-init) prepares Docker, the Doppler CLI and a single-node Swarm.

locals {
  subnet_prefix = cidrsubnet(var.address_space, 8, 1)
  # A fixed private address: the Swarm advertises it.
  private_ip = cidrhost(local.subnet_prefix, 4)
}

resource "azurerm_resource_group" "main" {
  name     = "${var.name}-production"
  location = var.location
}

resource "azurerm_virtual_network" "main" {
  name                = "${var.name}-vnet"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  address_space       = [var.address_space]
}

resource "azurerm_subnet" "main" {
  name                 = "${var.name}-subnet"
  resource_group_name  = azurerm_resource_group.main.name
  virtual_network_name = azurerm_virtual_network.main.name
  address_prefixes     = [local.subnet_prefix]
}

# Rules are separate resources, not inline blocks, so the temporary SSH rule
# the deploy job adds for its own address is not removed by an apply that runs
# at the same time.
resource "azurerm_network_security_group" "main" {
  name                = "${var.name}-nsg"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
}

resource "azurerm_network_security_rule" "router" {
  count                       = length(var.router_ports)
  name                        = "allow-router-${var.router_ports[count.index]}"
  priority                    = 100 + count.index
  direction                   = "Inbound"
  access                      = "Allow"
  protocol                    = "Tcp"
  source_port_range           = "*"
  destination_port_range      = tostring(var.router_ports[count.index])
  source_address_prefix       = "Internet"
  destination_address_prefix  = local.private_ip
  resource_group_name         = azurerm_resource_group.main.name
  network_security_group_name = azurerm_network_security_group.main.name
}

resource "azurerm_network_security_rule" "ssh" {
  count                       = length(var.ssh_allowed_cidrs) > 0 ? 1 : 0
  name                        = "allow-ssh-team"
  priority                    = 200
  direction                   = "Inbound"
  access                      = "Allow"
  protocol                    = "Tcp"
  source_port_range           = "*"
  destination_port_range      = "22"
  source_address_prefixes     = var.ssh_allowed_cidrs
  destination_address_prefix  = local.private_ip
  resource_group_name         = azurerm_resource_group.main.name
  network_security_group_name = azurerm_network_security_group.main.name
}

resource "azurerm_subnet_network_security_group_association" "main" {
  subnet_id                 = azurerm_subnet.main.id
  network_security_group_id = azurerm_network_security_group.main.id
}

resource "azurerm_public_ip" "main" {
  name                = "${var.name}-ip"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name
  allocation_method   = "Static"
  sku                 = "Standard"
  domain_name_label   = var.dns_label
}

resource "azurerm_network_interface" "main" {
  name                = "${var.name}-nic"
  location            = azurerm_resource_group.main.location
  resource_group_name = azurerm_resource_group.main.name

  ip_configuration {
    name                          = "primary"
    subnet_id                     = azurerm_subnet.main.id
    private_ip_address_allocation = "Static"
    private_ip_address            = local.private_ip
    public_ip_address_id          = azurerm_public_ip.main.id
  }
}

resource "azurerm_linux_virtual_machine" "main" {
  name                            = "${var.name}-vm"
  location                        = azurerm_resource_group.main.location
  resource_group_name             = azurerm_resource_group.main.name
  size                            = var.vm_size
  admin_username                  = var.admin_username
  disable_password_authentication = true
  network_interface_ids           = [azurerm_network_interface.main.id]
  secure_boot_enabled             = true
  vtpm_enabled                    = true

  dynamic "admin_ssh_key" {
    for_each = toset(var.admin_ssh_public_keys)
    content {
      username   = var.admin_username
      public_key = admin_ssh_key.value
    }
  }

  os_disk {
    name                 = "${var.name}-os-disk"
    caching              = "ReadWrite"
    storage_account_type = "StandardSSD_LRS"
    disk_size_gb         = var.os_disk_size_gb
  }

  source_image_reference {
    publisher = "Canonical"
    offer     = "ubuntu-24_04-lts"
    sku       = "server"
    version   = "latest"
  }

  custom_data = base64encode(templatefile("${path.module}/cloud-init.yaml", {
    admin_username = var.admin_username
    first_boot     = base64gzip(file("${path.module}/first-boot.sh"))
    remote_deploy  = base64gzip(file("${path.module}/../deployment/remote-deploy.sh"))
    common         = base64gzip(file("${path.module}/../deployment/lib/common.sh"))
  }))

  # Serial console and boot screenshots, in a storage account Azure manages.
  boot_diagnostics {}

  lifecycle {
    # The first boot runs once: a change to the scripts it copies must not
    # replace the VM. Later deployments copy deployment/ themselves.
    ignore_changes = [custom_data]
  }
}

# Docker's images and the volumes of PostgreSQL, RabbitMQ, Redis, Prometheus
# and Loki. Outlives the VM: replacing the VM attaches the same disk again.
resource "azurerm_managed_disk" "data" {
  name                 = "${var.name}-data-disk"
  location             = azurerm_resource_group.main.location
  resource_group_name  = azurerm_resource_group.main.name
  storage_account_type = "StandardSSD_LRS"
  create_option        = "Empty"
  disk_size_gb         = var.data_disk_size_gb

  lifecycle {
    prevent_destroy = true
  }
}

resource "azurerm_virtual_machine_data_disk_attachment" "data" {
  managed_disk_id    = azurerm_managed_disk.data.id
  virtual_machine_id = azurerm_linux_virtual_machine.main.id
  lun                = 0
  caching            = "ReadOnly"
}
