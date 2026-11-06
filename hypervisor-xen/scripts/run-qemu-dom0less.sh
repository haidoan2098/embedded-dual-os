#!/usr/bin/env bash
# Chạy Xen dom0less (domU1 + domU2, không có dom0) trên QEMU virt (ARM64).
# Không cần môi trường Yocto. Build trước: bitbake dualos-dom0less-dtb
#
# Thoát QEMU: Ctrl+A rồi X.
# Console: Xen gom console của các domain vào một UART; nhấn Ctrl+A ba lần để
# chuyển input giữa Xen và từng domU (Xen in hướng dẫn này lúc boot).
#
# Tham số QEMU và địa chỉ nạp đọc từ dualos-dom0less.env, file này sinh cùng
# lúc với DTB nên địa chỉ trong /chosen và trong lệnh -device loader luôn khớp.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD="${BUILD:-$SCRIPT_DIR/../build-yocto-hypervisor}"
QEMU="${QEMU:-qemu-system-aarch64}"

# Mỗi config một deploy dir (xem meta-dualos-xen/conf/multiconfig/)
DEPLOY="$BUILD/tmp/deploy/images/qemuarm64"                 # Xen + DTB
DEPLOY_DOMU1="$BUILD/tmp-mc-domu1/deploy/images/qemuarm64"  # kernel 6.6
DEPLOY_DOMU2="$BUILD/tmp-mc-domu2/deploy/images/qemuarm64"  # kernel 6.6-rt

ENV="$DEPLOY/dualos-dom0less.env"
XEN="$DEPLOY/xen-qemuarm64"
DTB="$DEPLOY/dualos-dom0less.dtb"
DOMU1_KERNEL="$DEPLOY_DOMU1/Image"
DOMU1_RAMDISK="$DEPLOY_DOMU1/dualos-domu1-image-qemuarm64.rootfs.cpio.gz"
DOMU2_KERNEL="$DEPLOY_DOMU2/Image"
DOMU2_RAMDISK="$DEPLOY_DOMU2/dualos-domu2-image-qemuarm64.rootfs.cpio.gz"

for f in "$ENV" "$XEN" "$DTB" "$DOMU1_KERNEL" "$DOMU1_RAMDISK" "$DOMU2_KERNEL" "$DOMU2_RAMDISK"; do
    [ -f "$f" ] || { echo "Thiếu file: $f (đã chạy bitbake dualos-dom0less-dtb chưa?)" >&2; exit 1; }
done

# shellcheck source=/dev/null
. "$ENV"

# -machine ...virtualization=true : bật EL2 cho Xen
# -kernel $XEN                    : QEMU nạp thẳng Xen, không qua bootloader
# -dtb $DTB                       : DTB của máy + /chosen khai báo 2 domU
# -device loader ... addr=...     : đặt kernel/ramdisk của từng domU vào RAM,
#                                   địa chỉ khớp reg của module@ trong /chosen
# Không có -drive: domU chạy rootfs từ ramdisk (xem docs/kernel-per-domain.md)
exec "$QEMU" \
    -machine "$QEMU_MACHINE" \
    -cpu "$QEMU_CPU" -smp "$QEMU_SMP" -m "$QEMU_MEM" \
    -nographic -serial mon:stdio \
    -kernel "$XEN" \
    -dtb "$DTB" \
    -device loader,file="$DOMU1_KERNEL",addr="$DOMU1_KERNEL_ADDR" \
    -device loader,file="$DOMU1_RAMDISK",addr="$DOMU1_RAMDISK_ADDR" \
    -device loader,file="$DOMU2_KERNEL",addr="$DOMU2_KERNEL_ADDR" \
    -device loader,file="$DOMU2_RAMDISK",addr="$DOMU2_RAMDISK_ADDR" \
    "$@"
