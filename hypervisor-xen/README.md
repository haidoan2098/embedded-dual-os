# hypervisor-xen

Hai Linux độc lập trên một SoC: **Xen dom0less** trên QEMU `virt` ARM64 (EL2).
Mục đích của phần này là hiểu và dựng được kiến trúc, không phải sản phẩm hoàn chỉnh.

## Kiến trúc

```
 bootloader (QEMU -device loader)       chỉ đặt file vào RAM rồi nhảy vào Xen
        │
   Xen  (EL2, chạy suốt runtime)        đọc /chosen, dựng từng domain, không có dom0
        │
        ├── domU1  (EL1)  Linux 6.6      kernel + ramdisk riêng, 256 MiB, 1 vCPU
        └── domU2  (EL1)  Linux 6.6-rt   kernel + ramdisk riêng, 256 MiB, 1 vCPU
```

- **Bootloader chung, một cái**; sau khi Xen chạy thì Xen đóng vai bootloader cho từng domain
  (copy kernel vào RAM của domain, sinh DTB riêng cho guest, nhảy vào kernel ở EL1).
- **Riêng mỗi domain**: kernel, rootfs (ramdisk `cpio.gz`), RAM, vCPU, console ảo `vpl011`.
- **Chung**: bootloader, Xen, phần cứng.
- Cách ly RAM do stage-2 (IPA → PA). Hai domain thấy cùng dải IPA `0x40000000` nhưng
  trỏ tới RAM vật lý khác nhau.
- Mọi thứ do `/chosen` trong device tree của máy quyết định: mỗi node `xen,domain` khai báo RAM,
  số vCPU, địa chỉ kernel và ramdisk trong RAM.

## Cách tổ chức

```
hypervisor-xen/
├── meta-dualos-xen/                 layer Yocto, chỉ chứa phần khác upstream
│   ├── conf/templates/default/      mẫu local.conf, bblayers.conf cho build dir
│   ├── conf/multiconfig/            domu1.conf (kernel 6.6), domu2.conf (kernel 6.6-rt)
│   ├── recipes-core/images/         dualos-domu{1,2}-image: rootfs initramfs của từng domain
│   └── recipes-bsp/dom0less-dtb/    sinh DTB (có /chosen) và file .env cho script chạy
├── scripts/run-qemu-dom0less.sh     nạp Xen, DTB, kernel, ramdisk vào đúng địa chỉ và chạy QEMU
└── docs/
```

Mỗi domain là một multiconfig riêng (cùng `MACHINE`, khác kernel và `TMPDIR`), nên hai
`Image` không đè nhau. Xen và DTB build ở config mặc định.

## Build và chạy

Layer cần có: xem [layers.txt](../layers.txt). `meta-virtualization` clone vào `../layers/`;
`poky` và `meta-openembedded` đặt cạnh nhau ở ngoài repo.

```bash
cd <poky>
TEMPLATECONF=<repo>/hypervisor-xen/meta-dualos-xen/conf/templates/default \
    source oe-init-build-env <repo>/hypervisor-xen/build-yocto-hypervisor
bitbake dualos-dom0less-dtb       # build Xen, hai kernel, hai rootfs và DTB
```

`TEMPLATECONF` chỉ có tác dụng khi build dir chưa có `conf/`. Build dir phải nằm trong
`hypervisor-xen/`, vì `bblayers.conf.sample` dùng đường dẫn tương đối theo `${TOPDIR}`.

```bash
scripts/run-qemu-dom0less.sh      # thoát QEMU: Ctrl+A rồi X
```

Console của hai domain đi chung một UART của Xen. Nhấn `Ctrl+A` ba lần để chuyển input giữa
Xen, domU1 và domU2. Đăng nhập `root`, không mật khẩu.

## Đã kiểm chứng và giới hạn

Đã chạy thật:
- Cả hai domain boot từ `/chosen`, không dom0, mỗi domain đúng kernel, hostname, RAM, 1 vCPU.
- Một domain hết RAM (OOM) thì Xen vẫn chạy.

Chưa kiểm chứng: một domain kernel panic thì domain kia có chạy tiếp không.

Khác thực tế, do QEMU `virt`:
- rootfs chạy từ ramdisk, **dữ liệu mất mỗi lần boot**. Ổ đĩa riêng cho từng domain cần IOMMU
  (QEMU không có cho virtio) cộng tính năng `STATIC_MEMORY` đang đánh dấu UNSUPPORTED trong Xen 4.18.
- Không có firmware EL3 nên Xen cảnh báo thiếu workaround Spectre.
- Chưa có core riêng: scheduler credit2 cho vCPU của cả hai domain chạy trên bất kỳ pCPU nào.
- Chưa có giao tiếp giữa các domain (static shared memory, static event channel).
- Kích thước module trong `/chosen` lấy từ file thật khi build; đổi kernel hoặc rootfs thì build lại DTB.

## Tài liệu

- [docs/why-xen-dom0less.md](docs/why-xen-dom0less.md): vì sao chọn Xen dom0less.
- [docs/kernel-per-domain.md](docs/kernel-per-domain.md): mỗi domain một kernel, multiconfig.
