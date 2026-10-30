# Embedded Dual-OS

Nghiên cứu chạy 2 hệ điều hành độc lập trên một SoC nhúng (QEMU + Yocto), theo hai hướng:

- [`hypervisor-xen/`](hypervisor-xen/): Linux + Linux trên Xen (dom0less), QEMU `virt` ARM64.
- [`amp/`](amp/): Linux + RTOS với OpenAMP, ZynqMP (làm sau).

Tổng quan project: [docs/PROJECT_CONTEXT.md](docs/PROJECT_CONTEXT.md)
