# Phần chung của các image domU (Xen dom0less).
#
# Image này là rootfs của một domain, đóng gói thành cpio.gz để bootloader nạp vào
# RAM. Xen đưa nó cho guest như một ramdisk qua module "multiboot,ramdisk" trong
# /chosen. Kernel là module riêng trong /chosen nên không nằm trong rootfs.
#
# Thiết kế:
#
# - Rootfs là initramfs vì dom0less không có dom0 làm backend ổ đĩa, domU không
#   có ổ nào để mount. Hệ quả: dữ liệu trong domain mất khi domain tắt.
#
# - Initramfs ở đây là hệ thống chạy luôn, không chuyển sang rootfs khác. Vì vậy
#   không dùng INITRAMFS_SCRIPTS (initramfs-framework) như core-image-minimal-initramfs
#   của poky, vốn dùng initramfs để đi tìm rootfs thật rồi switch_root.
#
# - Kernel chạy /init khi có initramfs. Rootfs này chỉ có /sbin/init, nhưng
#   IMAGE_CMD:cpio của poky (classes-recipe/image_types.bbclass) tự thêm symlink
#   /init -> /sbin/init khi tạo cpio, nên không cần tham số rdinit=.
#   Kiểm: zcat <file>.cpio.gz | cpio -t | grep -E '^(init|sbin/init)$'
#
# - IMAGE_FEATURES lấy từ local.conf (debug-tweaks: root không mật khẩu), để đăng
#   nhập được trên console khi thử nghiệm. Không dùng cho sản phẩm.

LICENSE = "MIT"

inherit core-image

IMAGE_FSTYPES = "cpio.gz"
IMAGE_LINGUAS = ""

# Bộ package tối thiểu để boot (busybox, init, base-files...) cộng phần riêng của
# từng domain. Mỗi image đặt DOMU_EXTRA_INSTALL cho phần của nó.
IMAGE_INSTALL = "packagegroup-core-boot ${DOMU_EXTRA_INSTALL}"
DOMU_EXTRA_INSTALL ?= ""

# Không kéo package khuyến nghị: giữ rootfs nhỏ và tránh kéo kernel-modules,
# vốn phải khớp đúng kernel mà domain chạy.
NO_RECOMMENDATIONS = "1"

# Chặn kernel lọt vào rootfs một cách tường minh (kernel đã là module riêng).
PACKAGE_EXCLUDE = "kernel-image-*"

# Một domain gồm kernel và rootfs: build image thì build và deploy luôn kernel của
# config hiện tại (virtual/kernel). Cách này giống xen-image-minimal của
# meta-virtualization, vốn gắn do_build với xen:do_deploy.
do_build[depends] += "virtual/kernel:do_deploy"

# Tên domain. Ghi vào /etc/hostname và /etc/issue để nhìn console biết đang ở
# domain nào (các domain chia chung một UART của Xen). Mỗi image đặt giá trị riêng.
DOMU_NAME ?= "domu"

set_domu_identity() {
    echo "${DOMU_NAME}" > ${IMAGE_ROOTFS}${sysconfdir}/hostname
    echo "dualos ${DOMU_NAME}" > ${IMAGE_ROOTFS}${sysconfdir}/issue
}
ROOTFS_POSTPROCESS_COMMAND += "set_domu_identity;"
