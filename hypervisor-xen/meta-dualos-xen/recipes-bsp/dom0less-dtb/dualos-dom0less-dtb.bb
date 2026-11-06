SUMMARY = "Device tree cho Xen dom0less trên QEMU virt: /chosen với domU1 và domU2"
DESCRIPTION = "Dump DTB của máy QEMU virt rồi thêm /chosen khai báo hai domU \
(kernel + ramdisk). Kích thước module lấy từ file thật trong deploy dir của \
từng multiconfig. Deploy kèm file .env để script chạy dùng đúng tham số QEMU \
và địa chỉ nạp."
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# Mẫu tham khảo: meta-virtualization classes/qemuboot-xen-dtb.bbclass
#   - Giống mẫu: dumpdtb bằng QEMU với cùng tham số máy; size module = stat file thật.
#   - Khác mẫu: mẫu chỉ thêm module phẳng cho dom0 (dùng lopper); ở đây cần node
#     xen,domain cho từng domU nên ghép /chosen bằng dtc (dtc gộp các lần khai báo
#     cùng một node). Ảnh domU nằm ở multiconfig khác nên dùng mcdepends.
# Tài liệu: Xen docs/misc/arm/device-tree/booting.txt (4.18), mục
#   "Creating Multiple Domains directly from Xen".

inherit deploy nopackages

INHIBIT_DEFAULT_DEPS = "1"
DEPENDS = "dtc-native qemu-system-native"
COMPATIBLE_MACHINE = "^qemuarm64$"

S = "${WORKDIR}/sources"
B = "${WORKDIR}/build"

# --- Máy QEMU: phải khớp với lúc chạy thật (script đọc lại từ file .env) ---
DOM0LESS_QEMU_MACHINE ?= "virt,gic-version=3,virtualization=true"
DOM0LESS_QEMU_CPU ?= "cortex-a57"
DOM0LESS_QEMU_SMP ?= "4"
DOM0LESS_QEMU_MEM ?= "2G"

# Không có dom0 nên không còn dom0_mem
DOM0LESS_XEN_BOOTARGS ?= "console=dtuart dtuart=/pl011@9000000"

# Địa chỉ nạp module bắt đầu từ 0x50000000: QEMU tự đặt DTB ở RAM base + 128 MiB
# (0x48000000), nên vùng dưới đó không dùng được (đã gặp lỗi "ROM regions overlapping").
# Chỗ chừa cho mỗi module (khoảng cách giữa các địa chỉ nạp bên dưới)
DOM0LESS_KERNEL_MAX ?= "0x2000000"
DOM0LESS_RAMDISK_MAX ?= "0x1000000"

# --- domU1: kernel 6.6 (multiconfig domu1) ---
# Đường dẫn phụ thuộc TMPDIR trong conf/multiconfig/domu1.conf
DOMU1_DEPLOY ?= "${TOPDIR}/tmp-mc-domu1/deploy/images/${MACHINE}"
DOMU1_KERNEL ?= "${DOMU1_DEPLOY}/Image"
DOMU1_RAMDISK ?= "${DOMU1_DEPLOY}/dualos-domu1-image-${MACHINE}.rootfs.cpio.gz"
DOMU1_KERNEL_ADDR ?= "0x50000000"
DOMU1_RAMDISK_ADDR ?= "0x52000000"
DOMU1_MEM_KB ?= "262144"
DOMU1_CPUS ?= "1"
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

# Build xong cả hai domU (image + kernel deploy) rồi mới sinh DTB.
# Xen không cần cho việc sinh DTB, nhưng kéo vào để một lệnh bitbake ra đủ file chạy.
do_compile[mcdepends] = "mc::domu1:dualos-domu1-image:do_build mc::domu2:dualos-domu2-image:do_build"
do_compile[depends] += "xen:do_deploy"

# Size của module đổi khi domU build lại, nhưng hash của task không thấy nội dung
# file ở multiconfig khác; chạy lại mỗi lần cho chắc (task rất nhẹ).
do_compile[nostamp] = "1"
do_deploy[nostamp] = "1"
do_compile[vardepsexclude] += "TOPDIR"

# In ra size (hex) của module; dừng nếu thiếu file hoặc lớn hơn chỗ chừa
dom0less_module_size() {
    f="$(readlink -f "$1")"
    [ -f "$f" ] || bbfatal "Thiếu module: $1"
    s=$(stat -c %s "$f")
    # printf '%d' đổi hex -> thập phân (parser shell của BitBake không hiểu $(( )))
    [ "$s" -le "$(printf '%d' "$2")" ] || bbfatal "$1 ($s B) lớn hơn chỗ chừa $2"
    printf '0x%x' "$s"
}

# $1 tên node, $2 kernel, $3 địa chỉ kernel, $4 ramdisk, $5 địa chỉ ramdisk,
# $6 RAM (KiB), $7 số vCPU, $8 bootargs
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
    # DTB gốc của máy, đúng tham số sẽ dùng khi chạy
    qemu-system-aarch64 \
        -machine ${DOM0LESS_QEMU_MACHINE} \
        -cpu ${DOM0LESS_QEMU_CPU} \
        -smp ${DOM0LESS_QEMU_SMP} \
        -m ${DOM0LESS_QEMU_MEM} \
        -nographic \
        -machine dumpdtb=${B}/qemu-virt.dtb

    dtc -q -I dtb -O dts -o ${B}/qemu-virt.dts ${B}/qemu-virt.dtb

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

    dtc -q -I dts -O dtb -o ${B}/dualos-dom0less.dtb ${B}/dualos-dom0less.dts

    # Tham số cho script chạy: cùng nguồn với DTB để không lệch nhau
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

do_deploy() {
    install -m 0644 ${B}/dualos-dom0less.dtb ${DEPLOYDIR}/
    install -m 0644 ${B}/dualos-dom0less.dts ${DEPLOYDIR}/
    install -m 0644 ${B}/dualos-dom0less.env ${DEPLOYDIR}/
}
addtask deploy after do_compile before do_build
