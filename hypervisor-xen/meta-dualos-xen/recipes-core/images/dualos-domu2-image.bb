SUMMARY = "Rootfs initramfs của domU2 (Xen dom0less)"

# Build: bitbake mc:domu2:dualos-domu2-image
require dualos-domu-image.inc

DOMU_NAME = "domu2"

# cyclictest để đo độ trễ trên kernel PREEMPT_RT
DOMU_EXTRA_INSTALL = "rt-tests"
