# SystemOnCat
An SoC with multiple RISC-V IMA processors.

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

## chisel3

详见[chisel3安装文档](https://github.com/ucb-bar/chisel3#installation)

# 项目配置说明

由于chisel不支持inout端口（变量定义也不支持），和inout相关部分只能用Verilog实现。因此，需要用chisel与verilog共同编程。
Vivado项目的构建方式如下：

1. 在`prog/firmware`目录下执行命令`make`；
1. 在项目根目录下执行`sbt run`编译CPU Core；
1. 在原样例工程中，导入out/SystemOnCat.v, 以及src/main/resources/vsrc/下所有.v文件；
1. 使用vsrc/thinpad_top.v替换原工程的同名文件，作为新的顶层模块。

这样即可编译成Bitstream。

# 编译uCat操作系统

1. 切换到`prog/os/ucore`目录，执行`make defconfig ARCH=soc && make sfsimg && make sfsimg2 && make`
2. 用`cat`将准备好的`prog/bootloader/bootedcat.bin`和`prog/os/ucore/obj/kernel/kernel-soc.elf`拼接，前者在前，得到的文件即为可引导的镜像

# 在THINPAD平台上运行程序

1. 将可引导的镜像文件写入flash（起始地址为0）
2. 随便读一下ram数据（如设置 存储选择　baseRAM,  addr 0, size 2，点击"读取"）
3. 更新设计文件，再次写入 `thinpat_top.bit`
4. 在开启高频时钟前，进行系统复位（按住reset时按一下clock）
5. 最左的两个和最右的两个拨码开关均为1时为高频时钟，否则为手动时钟
6. 为了利用串口进行输入输出，将串口设置为直连，波特率9600


# 相关说明

安装verilator(`sudo apt-get install verilator`)之后，
执行`sbt test`可以执行相关模块的仿真测试。

# FAQs

 如果你在配置或使用System on Cat的过程中遇到问题，请查阅[FAQs文档](./doc/FAQs.md) 。
