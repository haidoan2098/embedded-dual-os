SUMMARY = "Rootfs initramfs của domU1 (Xen dom0less)"

# Build trong multiconfig domu1 để đi cùng kernel 6.6:
#   bitbake mc:domu1:dualos-domu1-image

require dualos-domu-image.inc

DOMU_NAME = "domu1"
