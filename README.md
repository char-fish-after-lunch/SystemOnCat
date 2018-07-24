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

1. 执行`sbt run`编译CPU Core；
1. 在原样例工程中，导入out/SystemOnCat.v, 以及src/main/resources/vsrc/下所有.v文件；
1. 使用vsrc/thinpad_top.v替换原工程的同名文件，作为新的顶层模块。

这样即可编译成Bitstream。

# 相关说明

安装verilator(`sudo apt-get install verilator`)之后，
执行`sbt test`可以执行相关模块的仿真测试。
