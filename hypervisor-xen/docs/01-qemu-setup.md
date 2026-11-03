# Mốc 1 — Chạy Xen trên QEMU (image mẫu, có dom0)

> Ghi chép ngày 2026-11-03. Nhãn: [đã kiểm chứng] / [suy luận] / [đoán].

## Vấn đề / mục tiêu

Chứng minh môi trường chạy được: QEMU `virt` có EL2 → Xen → Linux.
Dùng nguyên image mẫu `xen-image-minimal` của meta-virtualization, chưa tự cấu hình gì.

Lưu ý: image này **chỉ có một Linux (dom0)**, theo mô hình Xen thường. Đây chưa phải dual-OS,
chưa phải dom0less. Nó chỉ là bước đệm.

Kết quả: boot tới `qemuarm64 login:` [đã kiểm chứng].

## Cấu hình Yocto

- poky scarthgap 5.0.19, `MACHINE = "qemuarm64"`, `DISTRO = "poky"`.
- Layer: meta-oe, meta-python, meta-networking, meta-filesystems (theo `LAYERDEPENDS` của
  meta-virtualization), meta-virtualization, meta-dualos-xen (hiện chưa có recipe).
- `local.conf`:
  - `DISTRO_FEATURES:append = " virtualization xen"`
  - `DISTRO_FEATURES:remove = "x11 wayland vulkan opengl pulseaudio bluetooth ptest 3g nfc"`
  - `INHERIT += "rm_work"`
  - `DL_DIR` / `SSTATE_DIR` dùng chung tại `~/yocto/`.
- Xen 4.18 (`PREFERRED_VERSION_xen ?= "4.18+stable%"` trong meta-virtualization).
- Chạy: `hypervisor-xen/scripts/run-qemu.sh`, gọi thẳng `qemu-system-aarch64` của host.

## Quá trình, gồm cả hướng sai

1. **Build lần 1 hết đĩa**, dừng ở task 6203/6244, `/home` còn 68 MB.
   - Nguyên nhân: `DISTRO_FEATURES` mặc định của poky rất nặng (x11, wayland, vulkan, ptest...)
     và không bật `rm_work`. `tmp/` lên tới 56 GB.
   - Xử lý: xoá `tmp/` (tạo lại được từ sstate), thêm `rm_work` và bỏ bớt feature,
     chuyển cache dùng chung sang `~/yocto/`.
   - Bài học: đổi `DISTRO_FEATURES` làm đổi hash của hầu hết recipe target, nên chốt distro sớm.
2. **Dừng build giữa chừng**: kill shell bên ngoài không dừng được `bitbake-server`, nó vẫn build tiếp.
   Dừng đúng cách: `Ctrl+C`, hoặc `bitbake -m` trong build dir.
3. **Build lần 2 thành công**: 4204 task (giảm khoảng 1/3), 3187 task lấy lại từ cache.
4. **Bỏ `runqemu`**: ban đầu viết script gọi `runqemu`, phải source môi trường Yocto.
   Nhận ra `runqemu` chỉ là lớp gói: nó gọi `bitbake -e` để ghép lệnh QEMU.
   Mọi tham số đã có trong `*.qemuboot.conf` cạnh image, nên chuyển sang gọi thẳng
   `qemu-system-aarch64`. Cách này thấy rõ cách Xen được nạp, và hướng U-Boot/dom0less cũng
   bắt buộc phải làm vậy.

## Kiến thức cần có

### QEMU đóng vai bootloader

| Tham số | Vai trò |
|---|---|
| `-machine virt,gic-version=3 -machine virtualization=true` | bật **EL2**. Thiếu nó thì Xen không chạy được |
| `-kernel xen-qemuarm64` | nạp **Xen**, CPU nhảy vào Xen ở EL2 |
| `-dtb ...qemuboot.dtb` | DTB của Xen, địa chỉ truyền qua `x0` |
| `-device loader,file=Image,addr=0x45000000` | đặt kernel dom0 vào RAM ở địa chỉ cố định |
| `-drive ...rootfs.ext4 ... -device virtio-blk-pci` | rootfs của dom0, thấy là `/dev/vda` |

### Node `/chosen` mà Xen đọc [đã kiểm chứng: dump bằng dtc]

```dts
chosen {
    xen,xen-bootargs  = "console=dtuart dtuart=/pl011@9000000 dom0_mem=192M";
    xen,dom0-bootargs = "console=hvc0 earlyprintk clk_ignore_unused root=/dev/vda";
    #address-cells = <0x01>;
    #size-cells = <0x01>;
    module@0x45000000 {
        reg = <0x45000000 0x16f9a00>;   /* địa chỉ, kích thước kernel */
        compatible = "multiboot,module", "multiboot,kernel";
    };
};
```

- `xen,xen-bootargs`: cmdline của Xen (console, RAM cho dom0).
- `xen,dom0-bootargs`: cmdline của Linux dom0.
- `module@...`: `reg` phải **khớp** với `addr=` của `-device loader`. Lệch là Xen đọc vùng RAM rỗng.
- Cấu trúc này do `qemuboot-xen-dtb.bbclass` (meta-virtualization) sinh ra lúc build.

### Đọc log boot

| Log | Ý nghĩa |
|---|---|
| `Exception Levels: EL3:No EL2:64+32` | có EL2, không có EL3 (không chạy TF-A) |
| `P2M: 44-bit IPA ... VMID` | Xen bật **stage-2 MMU**: dịch IPA của guest sang PA thật |
| `Loading d0 kernel from boot module @ 45000000` | Xen tìm thấy module theo `/chosen` |
| `BANK[0] 0x4c000000-0x58000000 (192MB)` | RAM riêng của dom0 |
| `Loading d0 DTB to 0x54000000` | Xen **tự sinh DTB riêng cho guest** |
| `CPU: All CPU(s) started at EL1` | Linux chạy ở EL1, dưới Xen |

Cảnh báo vô hại trong môi trường học: thiếu `ARM_SMCCC_ARCH_WORKAROUND_1` (không có TF-A),
`vGICD: unhandled word write` (Xen bỏ qua thanh ghi GIC không mô phỏng),
`Failed to add - passthrough` (chưa bật IOMMU).

## Bước tiếp theo

Chuyển sang **dom0less, 2 domU Linux**:
- `/chosen` không còn `xen,dom0-bootargs`. Thay vào đó là các node `domU1`, `domU2` với
  `compatible = "xen,domain"`, mỗi node có `memory`, `cpus` và các module kernel/ramdisk riêng.
  Tham chiếu: `docs/misc/arm/device-tree/booting.txt` trong source Xen.
- Rootfs của domU: không có dom0 làm backend nên dùng initramfs (`multiboot,ramdisk`) trước. [suy luận]
- Nạp các file bằng U-Boot + ImageBuilder thay cho `-device loader`.
