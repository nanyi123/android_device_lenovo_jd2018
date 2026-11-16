#!/system/bin/sh
# ZUI 原厂机制: 开机早期把上次 warm reset 留下的 ramoops 拷进 logdump 分区.
# 布局(全部按 bs=4096 seek=块号 写入):
#   块0..7   = 诊断行(每行一个 4K 块)
#   块8..519 = console-ramoops (最大 2MB)
#   块520    = console 结束标记(含时间戳)
#   块530..593  = dmesg-ramoops-0 (panic dump, 最大 256KB)
#   块594..657  = dmesg-ramoops-1 (上上次 panic dump)
#   块700    = dmesg 拷贝数量标记(带时间戳)
# 注意: dmesg(panic dump) 优先拷, 它含崩溃现场+调用栈, 价值最高且可能被消费
L=/dev/block/bootdevice/by-name/logdump

echo "== p2l $(date) ==" | dd of="$L" bs=4096 seek=0 conv=notrunc 2>/dev/null
echo "cmdline: $(cat /proc/cmdline 2>&1 | cut -c1-3800)" | dd of="$L" bs=4096 seek=1 conv=notrunc 2>/dev/null
echo "pstore dir: $(ls -d /sys/fs/pstore 2>&1)" | dd of="$L" bs=4096 seek=2 conv=notrunc 2>/dev/null
echo "mount: $(mount 2>/dev/null | grep -i pstore | cut -c1-3800)" | dd of="$L" bs=4096 seek=3 conv=notrunc 2>/dev/null
echo "pstore ls: $(ls /sys/fs/pstore/ 2>&1)" | dd of="$L" bs=4096 seek=4 conv=notrunc 2>/dev/null
echo "params: $(cat /sys/module/ramoops/parameters/mem_size 2>&1)/$(cat /sys/module/ramoops/parameters/mem_address 2>&1)/$(cat /sys/module/ramoops/parameters/console_size 2>&1)" | dd of="$L" bs=4096 seek=5 conv=notrunc 2>/dev/null
echo "dmesg: $(dmesg 2>/dev/null | grep -i -m3 ramoops | head -c 3800)" | dd of="$L" bs=4096 seek=6 conv=notrunc 2>/dev/null
echo "dbg: $(dmesg 2>/dev/null | grep -i -m3 ramoops-dbg | head -c 3800)" | dd of="$L" bs=4096 seek=7 conv=notrunc 2>/dev/null

# --- dmesg panic dump 优先 (dmesg-ramoops-0 本轮, -1 上轮) ---
# pstore 的 dmesg 记录可能在挂载后稍晚才出现, 最多等 10 秒
W=0
while [ $W -lt 20 ]; do
    ls /sys/fs/pstore/dmesg-ramoops-0 >/dev/null 2>&1 && break
    sleep 0.5; W=$((W + 1))
done
N=0
for F in $(ls /sys/fs/pstore/dmesg-ramoops-0 /sys/fs/pstore/dmesg-ramoops-1 2>/dev/null); do
    case "$F" in
        *-0) SEEK=530 ;;
        *-1) SEEK=594 ;;
        *)   continue ;;
    esac
    dd if="$F" of="$L" bs=4096 seek=$SEEK conv=notrunc 2>/dev/null
    N=$((N + 1))
done
echo "dmesg dumps copied: $N $(date)" | dd of="$L" bs=4096 seek=700 conv=notrunc 2>/dev/null

# --- console (新内核文件名带后缀, 老内核无后缀, glob 兼容) ---
P=$(ls /sys/fs/pstore/console-ramoops* 2>/dev/null | head -n 1)
if [ -n "$P" ] && [ -r "$P" ]; then
    dd if="$P" of="$L" bs=4096 seek=8 count=512 conv=notrunc 2>/dev/null
    echo "== end of console file=$P $(date) ==" | dd of="$L" bs=4096 seek=520 conv=notrunc 2>/dev/null
else
    echo "== console-ramoops 不存在 $(date) ==" | dd of="$L" bs=4096 seek=520 conv=notrunc 2>/dev/null
fi
