SUMMARY = "Rootfs initramfs của domU2 (Xen dom0less)"

# Build trong multiconfig domu2 để đi cùng kernel 6.6-rt:
#   bitbake mc:domu2:dualos-domu2-image

require dualos-domu-image.inc

DOMU_NAME = "domu2"

# rt-tests (cyclictest) để đo độ trễ trên kernel 6.6-rt
DOMU_EXTRA_INSTALL = "rt-tests"
