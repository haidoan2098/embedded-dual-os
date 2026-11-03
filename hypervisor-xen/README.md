# hypervisor-xen

Xen trên QEMU `virt` ARM64 (EL2). Mốc hiện tại: image mẫu `xen-image-minimal`
(Xen + một Linux dom0). Mục tiêu tiếp theo: dom0less với 2 domU Linux.

## Chuẩn bị

Layer cần có: xem [layers.txt](../layers.txt). `meta-virtualization` clone vào `../layers/`;
`poky` và `meta-openembedded` đặt cạnh nhau ở ngoài repo.

## Build

```bash
cd <poky>
TEMPLATECONF=<repo>/hypervisor-xen/meta-dualos-xen/conf/templates/default \
    source oe-init-build-env <repo>/hypervisor-xen/build-yocto-hypervisor
bitbake xen-image-minimal
```

`TEMPLATECONF` chỉ có tác dụng khi build dir chưa có `conf/`.
Build dir phải nằm trong `hypervisor-xen/`, vì `bblayers.conf.sample` dùng đường dẫn tương đối
theo `${TOPDIR}`.

## Chạy

```bash
scripts/run-qemu.sh      # thoát QEMU: Ctrl+A rồi X
```

## Tài liệu

- [docs/why-xen-dom0less.md](docs/why-xen-dom0less.md): vì sao chọn Xen dom0less.
- [docs/01-qemu-setup.md](docs/01-qemu-setup.md): ghi chép mốc 1.
