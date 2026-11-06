# Mỗi domain một kernel: 6.6 và 6.6-rt trong cùng một Yocto

> Ghi chú ngày 2026-11-04. Nhãn: [đã kiểm chứng] / [suy luận] / [đoán].
> Trạng thái: đã build và boot được hai domain (xem README). Kernel panic của một domain
> chưa được kiểm chứng.

## Mục tiêu và kết quả

Hai domU dom0less, hai kernel khác nhau, cùng release Yocto scarthgap:

| Domain | Kernel | Cách build |
|---|---|---|
| domU1 | `linux-yocto` 6.6.144 | `bitbake mc:domu1:dualos-domu1-image` |
| domU2 | `linux-yocto-rt` 6.6.144 (`PREEMPT_RT`) | `bitbake mc:domu2:dualos-domu2-image` |

Xen và phần chung vẫn build ở config mặc định (`local.conf`).

## Điểm tựa: Xen không quan tâm kernel build từ đâu

Giao diện giữa guest và Xen chỉ gồm một `Image` arm64, một ramdisk, và DTB do Xen tự
sinh cho guest. Mỗi node domU có `module@` kernel riêng nên hai domain trỏ tới hai file
khác nhau là hợp lệ [suy luận từ cơ chế `/chosen`]. Dùng chung hay khác file kernel đều
không ảnh hưởng độc lập lúc chạy: Xen copy kernel vào RAM riêng của từng domain, mỗi
domain một page table stage-2 [suy luận].

## Cách tổ chức trong meta-dualos-xen

- `conf/multiconfig/domu1.conf`, `domu2.conf`: mỗi domU một config, cùng `MACHINE`
  `qemuarm64`, khác provider kernel và `TMPDIR` (`tmp-mc-domu1`, `tmp-mc-domu2`).
- `recipes-core/images/dualos-domu-image.inc` + `dualos-domu{1,2}-image.bb`: rootfs
  initramfs (`cpio.gz`), phần chung ở `.inc`.
- `BBMULTICONFIG = "domu1 domu2"` trong template `local.conf.sample`.

Vì sao multiconfig: chỉ một `virtual/kernel` cho mỗi config, và tên file kernel trong
deploy dir không chứa tên recipe (xem output deploy mốc 1:
`Image--<PV>-<PR>-<MACHINE>-<time>.bin`), nên hai kernel cùng `MACHINE` dễ đè symlink
`Image`. Tách `TMPDIR` theo config giải quyết việc này [đã kiểm chứng: hai deploy dir
tách nhau].

## Kết quả kiểm sau build

- `/init` có trong cả hai cpio: `IMAGE_CMD:cpio` trong poky
  (`classes-recipe/image_types.bbclass`) tự thêm symlink `/init -> /sbin/init` nên không
  cần `rdinit=` [đã kiểm chứng].
- Không có kernel/modules lọt vào rootfs (`PACKAGE_EXCLUDE = "kernel-image-*"`)
  [đã kiểm chứng: 0 mục].
- `.config` của kernel rt: `CONFIG_PREEMPT_RT=y`, `CONFIG_XEN=y`,
  `CONFIG_SERIAL_AMBA_PL011=y`, `CONFIG_BLK_DEV_INITRD=y`. Fragment Xen của
  meta-virtualization đi cùng được với `PREEMPT_RT` ở các tùy chọn đã kiểm
  [đã kiểm chứng]; các tùy chọn Xen khác chưa kiểm.
- Kernel domU1 giống hệt `Image` của mốc 1 (lấy từ sstate) [đã kiểm chứng bằng `cmp`].
- Kích thước file (cần cho `reg` của `module@` trong `/chosen`, nên để dư):

  | File | Bytes | ≈ MiB |
  |---|---|---|
  | `Image` domU1 | 24 091 136 | 23,0 |
  | `Image` domU2 | 21 113 344 | 20,1 |
  | `cpio.gz` domU1 | 5 521 209 | 5,3 |
  | `cpio.gz` domU2 | 6 515 459 | 6,2 |

## Bài học về cách làm

Lần đầu mình viết layer từ trí nhớ, chỉ kiểm bằng parse. Đối chiếu với mẫu thật sau đó
tìm ra ba chỗ lệch: thiếu `PACKAGE_EXCLUDE` (so với `core-image-minimal-initramfs.bb`),
dependency khác upstream (`do_build[depends]` trong `xen-image-minimal.bb`), và
`MACHINE`/`DISTRO` chưa tường minh trong multiconfig (so với selftest
`oeqa/selftest/cases/multiconfig.py`). Quy trình: tìm mẫu **cùng bản chất** → đánh dấu từng
dòng giữ/bỏ/đổi → tra biến → parse/dry-run → build rồi kiểm đầu ra.
Trường hợp `/init`: phân tích từ kernel cho ra "vấn đề", nhưng Yocto đã xử lý sẵn ở tầng
định dạng image; phải đọc `image_types.bbclass` mới biết.

## Giới hạn của "RT trong guest"

Kernel `-rt` trong domU chỉ giảm độ trễ ở tầng Linux. Độ trễ thật còn phụ thuộc scheduler
của Xen và việc vCPU có được ghim lên pCPU riêng hay không. Hai vCPU tranh một pCPU thì RT
trong guest không cứu được [suy luận]. Muốn RT có ý nghĩa phải giải quyết phần CPU
(cpupool/pinning); mức hỗ trợ của Xen 4.18 cho dom0less ở điểm này chưa kiểm.

## Chưa làm / chưa kiểm

- Kernel panic của một domain có làm domain kia dừng không.
- Chi phí multiconfig: mỗi `TMPDIR` có sysroot native riêng (tốn thêm đĩa), và BitBake in nhiều
  cảnh báo `Runqueue deadlocked on deferred tasks` khi ba config cùng chờ recipe native giống nhau;
  build vẫn thành công [đã kiểm chứng].
- Có thể tinh gọn kernel domU (fragment `docker`, `lxc`... của meta-virtualization được áp
  cho cả hai kernel, có thể thừa với guest) [đoán].

## Phụ: nếu sau này cần kernel cách xa nhau về phiên bản (ví dụ 6.12)

Mỗi release poky chỉ có sẵn vài phiên bản `linux-yocto` (scarthgap chọn 6.6). Vấn đề:
không có recipe, không có file `.inc` Xen cho phiên bản đó, toolchain GCC 13.x của
scarthgap có thể quá cũ hoặc quá mới so với kernel [suy luận].

| # | Cách | Nhận xét |
|---|---|---|
| A | **Build OS đó ở một release Yocto khác có sẵn kernel cần dùng** | Hướng ưu tiên. Kernel và toolchain đi cùng nhau, đã được Yocto kiểm thử. |
| B | Recipe kernel riêng (`inherit kernel`, source kernel.org, defconfig) trong cùng build | Khả thi; phải tự giữ cấu hình và toolchain. |
| C | Kéo recipe `linux-yocto` từ release mới sang scarthgap | Không chọn: gắn với `kernel-yocto.bbclass` của release đó [suy luận]. |

Chi phí của hướng A: hai build dir, hai bộ layer, hai poky; `sstate-cache` nên tách theo
release; ghi thêm layer vào `layers.txt`; khớp nối thủ công (copy `Image` và `cpio.gz`,
cập nhật `reg` trong `/chosen`); layer riêng cho OS đó.

## Tài liệu để tự kiểm chứng

- `poky/meta/classes-recipe/image_types.bbclass` (`IMAGE_CMD:cpio`).
- `poky/meta/lib/oeqa/selftest/cases/multiconfig.py`,
  `meta-virtualization/conf/multiconfig/container.conf`.
- `poky/meta/recipes-core/images/core-image-minimal-initramfs.bb`,
  `meta-virtualization/recipes-extended/images/xen-image-minimal.bb`.
- `Documentation/process/changes.rst` (kernel): toolchain tối thiểu.
