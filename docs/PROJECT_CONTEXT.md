# Embedded Dual-OS — Tổng quan project

> Cập nhật lần cuối: 2026-10-30

## Bối cảnh

Nghiên cứu cách chạy **2 hệ điều hành độc lập cùng lúc trên một SoC nhúng**, theo hai hướng:

- **Hypervisor**: một lớp phần mềm đứng giữa phần cứng và các OS, chia tài nguyên cho từng OS.
- **AMP**: mỗi OS chạy trực tiếp trên một nhóm core riêng, không có lớp nào ở giữa.

Chưa có phần cứng phù hợp nên làm trên **QEMU + Yocto**.

## Mục tiêu

- Học sâu, nghiên cứu là chính, không chỉ dừng ở mức "chạy được".
- Làm cả hai hướng, mỗi hướng là một project con, để sau còn so sánh.
- Bám theo những gì vendor support ngoài thực tế.

**"Độc lập"** nghĩa là hai OS tách biệt hoàn toàn, từ phần cứng tới tài nguyên. Hai OS được giao tiếp với nhau nếu có cơ chế hỗ trợ.

## Định hướng

| | `hypervisor-xen/` | `amp/` |
|---|---|---|
| Thứ tự | Làm trước | Làm sau |
| Cặp OS | Linux + Linux | Linux + RTOS |
| Giải pháp | Xen (dom0less) | OpenAMP |
| Phần cứng giả lập | QEMU `virt` ARM64 | AMD/Xilinx ZynqMP (Cortex-A53 + R5F) |

Mỗi project con dùng phần cứng giả lập hợp với nó nhất, và có meta layer riêng bên trong.

## Môi trường

- Yocto: poky **scarthgap 5.0.19**, ở `/home/haidoan2098/Workspace_XBStation/yocto-project/poky`.
- Layer cần thêm:
  - `hypervisor-xen/`: `meta-virtualization`.
  - `amp/`: `meta-xilinx`, `meta-openamp`.

## Cấu trúc repo

```
embedded-dual-os/
├── docs/              # tài liệu chung
├── hypervisor-xen/    # project hypervisor (meta layer, config, script, ghi chép)
└── amp/               # project AMP
```
