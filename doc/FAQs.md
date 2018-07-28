# System on Cat FAQs

## Which version of RISC-V toolchain to use?

Here are the version signatures of the tools we use for development and testing:
```
https://github.com/riscv/riscv-gnu-toolchain

`master` branch @168ef95ba72b

riscv-binutils @ 8db5daf
riscv-dejagnu @ 2e99dc0
riscv-gcc @ 3c148a7
riscv-gdb @ 635c14e
riscv-glibc @ 2f626de
riscv-newlib @ 320b28e
riscv-qemu @ ff36f2f
```

Those tools support RISC-V user-level ISA v2.2 and privileged architecture v1.10. Note that problems might emerge if the version of RISC-V your tools support differs from those aforementioned. A typical error of this kind when you try building Meownitor:

```
riscv32-unknown-elf-gcc -c -march=rv32i -DWITH_CSR -DWITH_INTERRUPT -DWITH_IRQ -DWITH_ECALL -fno-builtin -o entry.o entry.S
riscv32-unknown-elf-gcc -c -march=rv32i -DWITH_CSR -DWITH_INTERRUPT -DWITH_IRQ -DWITH_ECALL -fno-builtin -o monitor.o monitor.c
monitor.c: Assembler messages:
monitor.c:168: Error: Instruction csrr requires absolute expression
monitor.c:178: Error: Instruction csrr requires absolute expression
monitor.c:183: Error: Instruction csrr requires absolute expression
Makefile:26: recipe for target 'monitor.o' failed
make: *** [monitor.o] Error 1
```

## It seems that some tests failed.

Please check the version of your `verilator`. It is recommended by chisel3 to use `verilator` of 3.922 or later. You may find the latest `verilator` here: http://git.veripool.org/git/verilator

## It is prompted that `./prog/firmware/mastercat.bin` is missing when I try `sbt run`.

Please enter `prog/firmware` and run `make` first. Make sure that `mastercat.bin` is present in that directory when you run `sbt run`.
