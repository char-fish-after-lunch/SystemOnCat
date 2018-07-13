这里存放了一些简单测例（无监控程序，在机器上直接跑的那种短汇编程序）

首先确保riscv-gnu-toolchain for RV32IMA已被正确安装并将bin加入了PATH，然后执行make，所生成的bin即为裸二进制文件。丢进云端写入ram即可。