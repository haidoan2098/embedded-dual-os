# Phần chung của các image domU (Xen dom0less).
#
# Dom0less không có backend ổ đĩa, nên rootfs là initramfs: một file cpio.gz
# được bootloader nạp vào RAM, Xen đưa cho guest qua module "multiboot,ramdisk"
# trong /chosen. Kernel là module riêng nên không nằm trong rootfs.
#
# Mẫu tham khảo: poky meta/recipes-core/images/core-image-minimal-initramfs.bb
# Khác mẫu:
#   - Không dùng INITRAMFS_SCRIPTS (initramfs-framework): mẫu dùng initramfs để
#     tìm rootfs thật rồi chuyển sang; ở đây initramfs CHÍNH LÀ rootfs.
#     Kernel mặc định chạy /init; rootfs này chỉ có /sbin/init, nhưng
#     IMAGE_CMD:cpio trong poky (classes-recipe/image_types.bbclass) tự thêm
#     symlink /init -> /sbin/init khi tạo cpio, nên không cần rdinit=.
#     Kiểm sau khi build: zcat <file>.cpio.gz | cpio -t | grep -E '^(init|sbin/init)$'
#   - Giữ IMAGE_FEATURES từ local.conf (debug-tweaks: root không mật khẩu) để
#     đăng nhập được trên console khi thử nghiệm. Mẫu đặt IMAGE_FEATURES = "".

LICENSE = "MIT"

inherit core-image

IMAGE_FSTYPES = "cpio.gz"
IMAGE_LINGUAS = ""

IMAGE_INSTALL = "packagegroup-core-boot ${DOMU_EXTRA_INSTALL}"
DOMU_EXTRA_INSTALL ?= ""

# Chỉ lấy package khai báo tường minh, không kéo recommends (giữ rootfs nhỏ,
# tránh kéo kernel-modules)
NO_RECOMMENDATIONS = "1"

# Theo mẫu: chặn kernel lọt vào initramfs một cách tường minh
PACKAGE_EXCLUDE = "kernel-image-*"

# Mỗi domain = kernel + rootfs: build image thì deploy luôn kernel của config
# hiện tại. Cùng cách xen-image-minimal làm với Xen (do_build[depends]).
do_build[depends] += "virtual/kernel:do_deploy"

# Mỗi domain đặt tên riêng để nhìn console biết đang ở OS nào
DOMU_NAME ?= "domu"

set_domu_identity() {
    echo "${DOMU_NAME}" > ${IMAGE_ROOTFS}${sysconfdir}/hostname
    echo "dualos ${DOMU_NAME}" > ${IMAGE_ROOTFS}${sysconfdir}/issue
}
ROOTFS_POSTPROCESS_COMMAND += "set_domu_identity;"
