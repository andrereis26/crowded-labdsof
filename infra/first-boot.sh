#!/usr/bin/env bash
# Runs once, from cloud-init, on the VM's first boot (cloud-init.yaml):
#   1. mounts the managed data disk (LUN 0) at /var/lib/docker, formatting it
#      only when it has no file system yet, so a new VM keeps the old data;
#   2. prepares the host with deployment/remote-deploy.sh --bootstrap-only;
#   3. lets the admin user run Docker and own /opt/crowded, where the deploy
#      job copies deployment/ and the releases are kept.
#
# Usage: first-boot.sh <admin user>

set -euo pipefail

admin_user="${1:?usage: first-boot.sh <admin user>}"
mount_point=/var/lib/docker

# The disk is attached after the VM is created, so it can appear a little
# after the first boot started. Newer Azure images name it by-lun, older ones
# scsi1/lun0.
device=""
for _ in $(seq 1 120); do
  for candidate in /dev/disk/azure/data/by-lun/0 /dev/disk/azure/scsi1/lun0; do
    if [[ -e "${candidate}" ]]; then
      device="$(readlink -f "${candidate}")"
      break 2
    fi
  done
  sleep 5
done
[[ -n "${device}" ]] || { echo "The data disk (LUN 0) did not appear in 10 minutes." >&2; exit 1; }

if [[ -z "$(blkid -s TYPE -o value "${device}" || true)" ]]; then
  echo "Formatting the data disk ${device}"
  mkfs.ext4 -q -L crowded-data "${device}"
fi
uuid="$(blkid -s UUID -o value "${device}")"
mkdir -p "${mount_point}"
grep -q "^UUID=${uuid} " /etc/fstab \
  || echo "UUID=${uuid} ${mount_point} ext4 defaults,nofail,discard 0 2" >>/etc/fstab
mountpoint -q "${mount_point}" || mount "${mount_point}"
echo "Data disk ${device} mounted at ${mount_point}"

bash /opt/crowded/deployment/remote-deploy.sh --bootstrap-only

usermod -aG docker "${admin_user}"
chown -R "${admin_user}:${admin_user}" /opt/crowded
