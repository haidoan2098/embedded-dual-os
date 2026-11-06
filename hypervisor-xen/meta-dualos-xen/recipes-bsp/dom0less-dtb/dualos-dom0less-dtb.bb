SUMMARY = "Device tree cho Xen dom0less trên QEMU virt: /chosen với domU1 và domU2"
DESCRIPTION = "Dump DTB của máy QEMU virt rồi thêm /chosen khai báo hai domU \
(kernel + ramdisk). Kích thước module lấy từ file thật trong deploy dir của \
từng multiconfig. Deploy kèm file .env để script chạy dùng đúng tham số QEMU \
và địa chỉ nạp."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# Recipe này tạo ra file mà Xen đọc để biết phải dựng những domain nào.
#
# Cách hoạt động:
#   1. QEMU dump device tree của máy virt (đúng tham số sẽ dùng lúc chạy).
#   2. Thêm node /chosen: mỗi domU là một node "xen,domain" khai báo RAM, số vCPU,
#      console ảo vpl011, và hai module: kernel và ramdisk (rootfs cpio.gz).
#   3. Biên dịch thành DTB, deploy cùng file .env chứa tham số QEMU và địa chỉ nạp
#      để scripts/run-qemu-dom0less.sh dùng chung nguồn, không lệch với DTB.
#
# Quan hệ giữa DTB và lệnh chạy QEMU: reg của mỗi module@ trong /chosen là địa chỉ
# trong RAM mà bootloader (QEMU -device loader) đã đặt file vào. Hai bên phải khớp.
# Đây là chỗ để tạm; Xen copy kernel/ramdisk vào RAM của guest, ở địa chỉ guest do
# Xen quyết định, nên các địa chỉ này không phải địa chỉ mà guest nhìn thấy.
#
# Tham khảo:
#   - Xen docs/misc/arm/device-tree/booting.txt, mục "Creating Multiple Domains
#     directly from Xen": ý nghĩa các property của node xen,domain.
#   - meta-virtualization classes/qemuboot-xen-dtb.bbclass: cách dump DTB và lấy
#     kích thước module từ file thật. Lớp đó chỉ thêm module cho dom0 (dùng lopper);
#     ở đây cần node xen,domain cho từng domU nên ghép /chosen bằng dtc.

inherit deploy nopackages

INHIBIT_DEFAULT_DEPS = "1"
DEPENDS = "dtc-native qemu-system-native"
COMPATIBLE_MACHINE = "^qemuarm64$"

S = "${WORKDIR}/sources"
B = "${WORKDIR}/build"

# --- Máy QEMU ---
# Phải khớp với tham số khi chạy thật. Các giá trị này được ghi vào file .env và
# script chạy đọc lại từ đó. Đổi DOM0LESS_QEMU_CPU thì tune của kernel/rootfs cũng
# phải tương thích với CPU đó.
DOM0LESS_QEMU_MACHINE ?= "virt,gic-version=3,virtualization=true"
DOM0LESS_QEMU_CPU ?= "cortex-a57"
DOM0LESS_QEMU_SMP ?= "4"
DOM0LESS_QEMU_MEM ?= "2G"

# Tham số dòng lệnh của Xen. Xen dùng chính UART của máy (pl011@9000000) làm
# console. Không có dom0 nên không có dom0_mem.
DOM0LESS_XEN_BOOTARGS ?= "console=dtuart dtuart=/pl011@9000000"

# --- Địa chỉ nạp module ---
# Với tham số QEMU ở trên, QEMU đặt DTB ở 0x48000000 (RAM base + 128 MiB), nên vùng nạp
# kernel/ramdisk phải nằm ngoài chỗ đó. Các địa chỉ bên dưới bắt đầu từ 0x50000000.
# Quy tắc chung: không đè lên Xen, DTB, và nhau.
#
# DOM0LESS_KERNEL_MAX / DOM0LESS_RAMDISK_MAX là chỗ chừa cho mỗi module, tức khoảng
# cách giữa hai địa chỉ nạp liền nhau. File lớn hơn chỗ chừa thì build dừng (xem
# hàm dom0less_module_size bên dưới).
DOM0LESS_KERNEL_MAX ?= "0x2000000"
DOM0LESS_RAMDISK_MAX ?= "0x1000000"

# --- domU1: kernel 6.6 (multiconfig domu1) ---
# Đường dẫn phụ thuộc TMPDIR đặt trong conf/multiconfig/domu1.conf.
DOMU1_DEPLOY ?= "${TOPDIR}/tmp-mc-domu1/deploy/images/${MACHINE}"
DOMU1_KERNEL ?= "${DOMU1_DEPLOY}/Image"
DOMU1_RAMDISK ?= "${DOMU1_DEPLOY}/dualos-domu1-image-${MACHINE}.rootfs.cpio.gz"
DOMU1_KERNEL_ADDR ?= "0x50000000"
DOMU1_RAMDISK_ADDR ?= "0x52000000"
# RAM của domain, đơn vị KiB (theo property "memory" của booting.txt): 262144 = 256 MiB.
DOMU1_MEM_KB ?= "262144"
DOMU1_CPUS ?= "1"
# console=ttyAMA0: console ảo vpl011 hiện ra trong guest như một UART PL011.
DOMU1_BOOTARGS ?= "console=ttyAMA0"

# --- domU2: kernel 6.6-rt (multiconfig domu2) ---
DOMU2_DEPLOY ?= "${TOPDIR}/tmp-mc-domu2/deploy/images/${MACHINE}"
DOMU2_KERNEL ?= "${DOMU2_DEPLOY}/Image"
DOMU2_RAMDISK ?= "${DOMU2_DEPLOY}/dualos-domu2-image-${MACHINE}.rootfs.cpio.gz"
DOMU2_KERNEL_ADDR ?= "0x54000000"
DOMU2_RAMDISK_ADDR ?= "0x56000000"
DOMU2_MEM_KB ?= "262144"
DOMU2_CPUS ?= "1"
DOMU2_BOOTARGS ?= "console=ttyAMA0"

# Cần cả hai image domU (cùng kernel của chúng) đã build trước khi sinh DTB, vì kích
# thước module lấy từ file thật. mcdepends là cách khai báo phụ thuộc sang một
# multiconfig khác, cú pháp mc:<config hiện tại>:<config đích>:<recipe>:<task>.
# Để trống config hiện tại nghĩa là config mặc định (nơi recipe này được build).
do_compile[mcdepends] = "mc::domu1:dualos-domu1-image:do_build mc::domu2:dualos-domu2-image:do_build"
# Xen không cần cho việc sinh DTB, nhưng kéo vào để một lệnh bitbake ra đủ mọi file
# cần để chạy (Xen, DTB, hai kernel, hai rootfs).
do_compile[depends] += "xen:do_deploy"

# Kích thước module đổi mỗi khi domU build lại, nhưng BitBake không thấy nội dung
# file ở multiconfig khác nên không biết task đã cũ. Cho task chạy lại mỗi lần;
# task rất nhẹ.
do_compile[nostamp] = "1"
do_deploy[nostamp] = "1"
do_compile[vardepsexclude] += "TOPDIR"

# In ra kích thước (hex) của file module $1. Dừng build nếu file không tồn tại hoặc
# lớn hơn chỗ chừa $2 (khi đó module sẽ đè lên module kế tiếp trong RAM).
# Chuyển hex sang thập phân bằng printf '%d': parser shell của BitBake không hiểu
# phép tính số học dạng dấu đô-la cùng hai ngoặc tròn.
dom0less_module_size() {
    f="$(readlink -f "$1")"
    [ -f "$f" ] || bbfatal "Thiếu module: $1"
    s=$(stat -c %s "$f")
    [ "$s" -le "$(printf '%d' "$2")" ] || bbfatal "$1 ($s B) lớn hơn chỗ chừa $2"
    printf '0x%x' "$s"
}

# In ra một node domU hoàn chỉnh của /chosen.
# Tham số: $1 tên node, $2 file kernel, $3 địa chỉ kernel, $4 file ramdisk,
# $5 địa chỉ ramdisk, $6 RAM (KiB), $7 số vCPU, $8 bootargs của kernel guest.
#
# #address-cells = 2 và #size-cells = 1 cho node domU, nên reg gồm ba ô: địa chỉ
# (hai ô, 64 bit) rồi kích thước (một ô), theo ví dụ trong booting.txt.
# "vpl011" bật UART ảo cho guest.
dom0less_domu_node() {
    ksize=$(dom0less_module_size "$2" ${DOM0LESS_KERNEL_MAX})
    rsize=$(dom0less_module_size "$4" ${DOM0LESS_RAMDISK_MAX})
    cat <<EOF
        $1 {
            compatible = "xen,domain";
            #address-cells = <2>;
            #size-cells = <1>;
            memory = <0 $6>;
            cpus = <$7>;
            vpl011;

            module@${3#0x} {
                compatible = "multiboot,kernel", "multiboot,module";
                reg = <0x0 $3 $ksize>;
                bootargs = "$8";
            };

            module@${5#0x} {
                compatible = "multiboot,ramdisk", "multiboot,module";
                reg = <0x0 $5 $rsize>;
            };
        };
EOF
}

do_compile() {
    # Bước 1: DTB gốc của máy, với đúng tham số sẽ dùng khi chạy.
    qemu-system-aarch64 \
        -machine ${DOM0LESS_QEMU_MACHINE} \
        -cpu ${DOM0LESS_QEMU_CPU} \
        -smp ${DOM0LESS_QEMU_SMP} \
        -m ${DOM0LESS_QEMU_MEM} \
        -nographic \
        -machine dumpdtb=${B}/qemu-virt.dtb

    dtc -q -I dtb -O dts -o ${B}/qemu-virt.dts ${B}/qemu-virt.dtb

    # Bước 2: nối thêm node /chosen vào cuối file DTS. dtc gộp hai khai báo "/"
    # thành một cây, nên không cần sửa file gốc.
    {
        cat ${B}/qemu-virt.dts
        cat <<EOF

/ {
    chosen {
        #address-cells = <1>;
        #size-cells = <1>;
        xen,xen-bootargs = "${DOM0LESS_XEN_BOOTARGS}";

EOF
        dom0less_domu_node domU1 "${DOMU1_KERNEL}" ${DOMU1_KERNEL_ADDR} \
            "${DOMU1_RAMDISK}" ${DOMU1_RAMDISK_ADDR} \
            ${DOMU1_MEM_KB} ${DOMU1_CPUS} "${DOMU1_BOOTARGS}"
        echo
        dom0less_domu_node domU2 "${DOMU2_KERNEL}" ${DOMU2_KERNEL_ADDR} \
            "${DOMU2_RAMDISK}" ${DOMU2_RAMDISK_ADDR} \
            ${DOMU2_MEM_KB} ${DOMU2_CPUS} "${DOMU2_BOOTARGS}"
        cat <<EOF
    };
};
EOF
    } > ${B}/dualos-dom0less.dts

    # Bước 3: biên dịch thành DTB cho Xen.
    dtc -q -I dts -O dtb -o ${B}/dualos-dom0less.dtb ${B}/dualos-dom0less.dts

    # Bước 4: tham số cho script chạy, cùng nguồn với DTB để hai bên không lệch.
    cat > ${B}/dualos-dom0less.env <<EOF
# Sinh bởi dualos-dom0less-dtb.bb, không sửa tay
QEMU_MACHINE="${DOM0LESS_QEMU_MACHINE}"
QEMU_CPU="${DOM0LESS_QEMU_CPU}"
QEMU_SMP="${DOM0LESS_QEMU_SMP}"
QEMU_MEM="${DOM0LESS_QEMU_MEM}"
DOMU1_KERNEL_ADDR="${DOMU1_KERNEL_ADDR}"
DOMU1_RAMDISK_ADDR="${DOMU1_RAMDISK_ADDR}"
DOMU2_KERNEL_ADDR="${DOMU2_KERNEL_ADDR}"
DOMU2_RAMDISK_ADDR="${DOMU2_RAMDISK_ADDR}"
EOF
}

# Deploy DTB (để chạy), DTS (để đọc/gỡ lỗi) và .env (cho script chạy).
do_deploy() {
    install -m 0644 ${B}/dualos-dom0less.dtb ${DEPLOYDIR}/
    install -m 0644 ${B}/dualos-dom0less.dts ${DEPLOYDIR}/
    install -m 0644 ${B}/dualos-dom0less.env ${DEPLOYDIR}/
}
addtask deploy after do_compile before do_build
