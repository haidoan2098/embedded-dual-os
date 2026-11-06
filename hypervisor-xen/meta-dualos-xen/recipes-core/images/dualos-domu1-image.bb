SUMMARY = "Rootfs initramfs của domU1 (Xen dom0less)"
DESCRIPTION = "Rootfs tối giản của domU1, chạy với kernel linux-yocto 6.6. \
Phần chung nằm trong dualos-domu-image.inc."

# Build trong multiconfig domu1 để đi cùng kernel 6.6 (xem conf/multiconfig/domu1.conf):
#   bitbake mc:domu1:dualos-domu1-image

require dualos-domu-image.inc

DOMU_NAME = "domu1"
