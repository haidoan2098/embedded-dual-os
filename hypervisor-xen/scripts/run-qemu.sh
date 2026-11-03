#!/usr/bin/env bash
# Chạy Xen + Linux dom0 trên QEMU virt (ARM64), không cần môi trường Yocto.
# Thoát QEMU: Ctrl+A rồi X
#
# Các tham số lấy từ <deploy>/xen-image-minimal-qemuarm64.rootfs.qemuboot.conf
# (tên trường qb_* ghi ở cuối mỗi dòng).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY="${DEPLOY:-$SCRIPT_DIR/../build-yocto-hypervisor/tmp/deploy/images/qemuarm64}"
QEMU="${QEMU:-qemu-system-aarch64}"

XEN="$DEPLOY/xen-qemuarm64"                                      # qb_default_kernel
DTB="$DEPLOY/xen-image-minimal-qemuarm64.rootfs.qemuboot.dtb"    # qb_dtb: có /chosen mô tả dom0
DOM0_KERNEL="$DEPLOY/Image"                                      # qb_xen_domain_modules
ROOTFS="$DEPLOY/xen-image-minimal-qemuarm64.rootfs.ext4"         # qb_rootfs_opt

for f in "$XEN" "$DTB" "$DOM0_KERNEL" "$ROOTFS"; do
    [ -f "$f" ] || { echo "Thiếu file: $f" >&2; exit 1; }
done

# -machine ...virtualization=true : bật EL2 (bắt buộc cho hypervisor)   [qb_machine]
# -kernel $XEN                    : QEMU nạp thẳng Xen, không qua bootloader
# -device loader ... addr=...     : đặt kernel dom0 vào RAM, địa chỉ khớp /chosen trong DTB
# snapshot=on                     : ghi vào rootfs chỉ nằm trong RAM, file build không bị sửa
exec "$QEMU" \
    -machine virt,gic-version=3 -machine virtualization=true \
    -cpu cortex-a57 -smp 4 -m 512 \
    -nographic -serial mon:stdio \
    -kernel "$XEN" \
    -dtb "$DTB" \
    -device loader,file="$DOM0_KERNEL",addr=0x45000000 \
    -drive id=disk0,file="$ROOTFS",if=none,format=raw,snapshot=on \
    -device virtio-blk-pci,drive=disk0 \
    "$@"
