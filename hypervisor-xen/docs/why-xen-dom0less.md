# Vì sao chọn Xen dom0less

> Ghi chép ngày 2026-10-31. Nhãn: [đã kiểm chứng] / [suy luận] / [đoán].

## Tiêu chí lựa chọn

1. **Độc lập hoàn toàn**: boot độc lập, lỗi độc lập, tài nguyên (core, RAM, thiết bị) tách riêng.
2. **Chạy được trên QEMU + Yocto**: có layer chính thức, ghép được với poky scarthgap.
3. **Có thật ngoài thực tế**: vendor dùng hoặc support, học xong mang đi làm được.

## So sánh các ứng viên

| Ứng viên | (1) Độc lập | (2) QEMU + Yocto | (3) Thực tế | Kết luận |
|---|---|---|---|---|
| KVM | ✗ guest là process trên Linux host, host chết thì guest chết theo | ✓ | chủ yếu server/cloud | loại vì (1) |
| ACRN | ✓ một phần | ✗ chỉ x86 | Intel IoT, automotive x86 | loại vì không có ARM |
| Jailhouse | ✗ phải boot Linux root cell trước rồi mới tách cell | ✓ meta-jailhouse | Siemens, NXP Real-Time Edge | loại vì vướng boot độc lập |
| Bao | ✓ static partitioning | ✗ không có layer Yocto chính thức | nghiên cứu, một số dự án automotive | loại vì (2) |
| seL4 / CAmkES | ✓ rất mạnh | ✗ hệ sinh thái riêng, học rất nặng | quốc phòng, hàng không | quá nặng so với mục tiêu |
| QNX, PikeOS, COQOS... | ✓ | ✗ thương mại, đóng | ✓ automotive | không tiếp cận được |
| Xen thường (dom0 + domU) | ✗ dom0 tạo và quản lý domU | ✓ meta-virtualization | ✓ | loại vì (1) |
| **Xen dom0less** | ✓ Xen boot thẳng các domain từ device tree | ✓ meta-virtualization | ✓ AMD/Xilinx, automotive | **đáp ứng cả ba** |

[đã kiểm chứng ở mức tổng quát: KVM chạy trong Linux, ACRN chỉ có x86, Jailhouse cần root cell]
[suy luận: mức độ dùng thực tế của Bao và Xen trong automotive]

## Nhược điểm của Xen dom0less, cần biết trước

- **Tài liệu ít hơn Xen thường**: đa số tutorial dùng mô hình có dom0. Tài liệu dom0less chủ yếu nằm trong source Xen (`docs/features/dom0less.pandoc`, `docs/misc/arm/device-tree/booting.txt`) và mailing list.
- **Không có dom0 thì mất công cụ quản lý runtime** (`xl`, xenstore, PV driver bị hạn chế). [suy luận, cần kiểm tra theo bản Xen] Bản mới có cách khởi tạo xenstore muộn, hoặc kết hợp dom0 với domU dạng dom0less.
- **Codebase lớn**, đọc source để hiểu cơ chế khó hơn Jailhouse hoặc Bao.

## Bức tranh phổ biến ngoài thực tế

- **Thương mại** (chiếm phần lớn sản phẩm thật vì cần chứng nhận an toàn): QNX Hypervisor, Green Hills INTEGRITY Multivisor, PikeOS, Wind River Helix, COQOS.
- **Do hãng SoC/nền tảng đẩy**: Gunyah (Qualcomm), pKVM / Android Virtualization Framework (Google, cách ly bảo mật trên điện thoại).
- **Mã nguồn mở**: KVM (rất phổ biến nhưng ở server), **Xen (mạnh nhất cho embedded/automotive)**, Jailhouse (ngách công nghiệp), ACRN (x86), Bao (nghiên cứu).

[suy luận] Không có số liệu thị phần công khai đáng tin, phần xếp hạng ở trên chỉ là nhận định.

## Kết luận

- Xen dom0less là ứng viên duy nhất qua được cả ba tiêu chí.
- Kiến thức nền (EL2, stage-2 MMU, vGIC, chia tài nguyên tĩnh, virtio) chuyển được sang các hypervisor thương mại.
- Hướng mở rộng sau này: làm Jailhouse như một project phụ để so sánh mô hình "Linux boot trước rồi tách".
