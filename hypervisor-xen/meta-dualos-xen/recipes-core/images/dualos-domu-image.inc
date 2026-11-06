# Rootfs chung của các domU (Xen dom0less): initramfs cpio.gz, Xen đưa cho guest qua
# module "multiboot,ramdisk" trong /chosen. Kernel là module riêng, không nằm trong rootfs.
#
# Dom0less không có backend ổ đĩa nên domU không có ổ nào: initramfs chính là rootfs
# chạy luôn (không switch_root), dữ liệu mất khi domain tắt.
# /init: IMAGE_CMD:cpio của poky tự thêm symlink /init -> /sbin/init.
# debug-tweaks (root không mật khẩu) lấy từ local.conf, chỉ để thử nghiệm.

LICENSE = "MIT"

inherit core-image

IMAGE_FSTYPES = "cpio.gz"
IMAGE_LINGUAS = ""

# Mỗi image đặt DOMU_EXTRA_INSTALL cho phần riêng của nó
IMAGE_INSTALL = "packagegroup-core-boot ${DOMU_EXTRA_INSTALL}"
DOMU_EXTRA_INSTALL ?= ""

# Không kéo recommends: rootfs nhỏ, tránh kéo kernel-modules (phải khớp kernel của domain)
NO_RECOMMENDATIONS = "1"
PACKAGE_EXCLUDE = "kernel-image-*"

# Build image thì deploy luôn kernel của config hiện tại
do_build[depends] += "virtual/kernel:do_deploy"

# Ghi vào /etc/hostname và /etc/issue để phân biệt domain trên console chung
DOMU_NAME ?= "domu"

set_domu_identity() {
    echo "${DOMU_NAME}" > ${IMAGE_ROOTFS}${sysconfdir}/hostname
    echo "dualos ${DOMU_NAME}" > ${IMAGE_ROOTFS}${sysconfdir}/issue
}
ROOTFS_POSTPROCESS_COMMAND += "set_domu_identity;"
