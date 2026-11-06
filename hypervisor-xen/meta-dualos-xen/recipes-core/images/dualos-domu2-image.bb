SUMMARY = "Rootfs initramfs của domU2 (Xen dom0less)"
DESCRIPTION = "Rootfs tối giản của domU2, chạy với kernel linux-yocto-rt 6.6. \
Phần chung nằm trong dualos-domu-image.inc."

# Build trong multiconfig domu2 để đi cùng kernel 6.6-rt (xem conf/multiconfig/domu2.conf):
#   bitbake mc:domu2:dualos-domu2-image

require dualos-domu-image.inc

DOMU_NAME = "domu2"

# rt-tests (cyclictest) để đo độ trễ lập lịch, phù hợp với kernel PREEMPT_RT của
# domain này. domU1 không có gói này nên hai rootfs khác nhau về nội dung.
DOMU_EXTRA_INSTALL = "rt-tests"
