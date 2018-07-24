# SystemOnCat
An SoC with multicore RISC-V IMA.

# 依赖项以及安装方法

## riscv-gnu-toolchain

安装[riscv-gnu-toolchain](https://github.com/riscv/riscv-gnu-toolchain)

配置方法如下：

```
cd riscv-gnu-toolchain
mkdir build; cd build
../configure --with-arch=rv32ima --prefix=$INSTALL/PATH
make -j$(nproc)
```

目前用的是
```
master分支　@168ef95ba72b

riscv-binutils @ 8db5daf
riscv-dejagnu @ 2e99dc0
riscv-gcc @ 3c148a7
riscv-gdb @ 635c14e
riscv-glibc @ 2f626de
riscv-newlib @ 320b28e
riscv-qemu @ ff36f2f
```

编译出来的是 gcc-8.1

## chisel3

详见[chisel3安装文档](https://github.com/ucb-bar/chisel3#installation)

## verilator
低版本的verilator会出现编译错误。需要安装3.922+（chisel3推荐3.922），建议最新的3.924版本  http://git.veripool.org/git/verilator  
```
$ git tag
...
verilator_3_924
```

## 设置好编译执行环境
```
export PATH=rv32-tools_install_dir:verilator_build_dir:$PATH
```

## 安装vivado

# 项目配置说明

在各自分支上开发，定期合并到dev分支上，形成稳定版本再合并到master上
```
master：稳定版分支
dev：合作开发分支
cache：cache开发分支
mmu：mmu开发分支
```

由于chisel不支持inout端口（变量定义也不支持），和inout相关部分只能用Verilog实现。因此，需要用chisel与verilog共同编程。

Vivado项目的构建方式如下：

1. 执行`sbt run`编译CPU Core；
1. 执行`sbt test`可以执行相关模块的仿真测试。ALUTests，MMUTests，RegFileTests，IFetchTests可通过
1. 在原样例工程中，导入out/SystemOnCat.v, 以及src/main/resources/vsrc/下所有.v文件；（必须要用vivado的 add source图形界面加入才生效。比较推荐直接把system on cat git仓库下的原文件加进项目，这样git仓库修改时vivado可以直接用最新的文件，不用再复制一次）
1. 使用vsrc/thinpad_top.v替换原工程的同名文件，作为新的顶层模块。
1. 这样即可编译成Bitstream。
1. 然后 在prog/bootloader下make; prog/monitor下make START_ADR=0x4000 bootable.bin
1. 烧写 thinpad_top.bit (./thinpad_top.runs/impl_1/thinpad_top.bit)
1. flash写入 bootable.bin
1. 随便读一下ram数据（如设置 存储选择　baseRAM,  addr 0, size 2，点击"读取"）
1. 更新设计文件，再次写入 thinpat_top.bit

然后应该可以看到串口输出！


NOTICE:
1. 如果执行 `sbt run`出现找不到　'./prog/firmware/mastercat.bin'　的编译错误。则进入prog/firmware，make一下。得到bin文件
1. 执行`sbt test`出现编译错误，如果是 DatapathTests.scala, PLICTests.scala，把这两个文件删除即可。原因是：仿真用的假元件的接口和现在的元件接不上了。这几个测试不通过的设备的共同特点都是被修改为了总线设备，直接连接到总线上。
1. 选择thinpad ver2进行烧写
1. 板子上那一排32个拨码开关最左边两个和最右边两个都拨成1才是高频时钟，不然是手动时钟;另外要用直连串口而不是cpld串口
1. 烧FLASH的话也要补充一下，烧进去之后得先随便读一下ram，然后再次update thinpad-top.bit

1. 编译错误1　

```
make START_ADR=0x4000 bootable.bin
riscv32-unknown-elf-gcc -c -march=rv32i -DWITH_CSR -DWITH_INTERRUPT -DWITH_IRQ -DWITH_ECALL -fno-builtin -o entry.o entry.S
riscv32-unknown-elf-gcc -c -march=rv32i -DWITH_CSR -DWITH_INTERRUPT -DWITH_IRQ -DWITH_ECALL -fno-builtin -o monitor.o monitor.c
monitor.c: Assembler messages:
monitor.c:168: Error: Instruction csrr requires absolute expression
monitor.c:178: Error: Instruction csrr requires absolute expression
monitor.c:183: Error: Instruction csrr requires absolute expression
Makefile:26: recipe for target 'monitor.o' failed
make: *** [monitor.o] Error 1
```

这是因为riscv-gnu-toolchain版本过低，实现的riscv-priv不是1.9以上的,于是无法识别CSR寄存器名。需要更新rv32-gnu-tools


