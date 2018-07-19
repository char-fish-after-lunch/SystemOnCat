.org 0x0
    .global _start
_start:
nop
nop
li x10, 0xf000 
nop
addi x1, x0, 0x68
csrw mscratch, x1
csrr x2, mscratch
sw x2, 0(x10)
csrr x2, mscratch
sw x2, 0(x10)
nop
addi x1, x0, 0x65
csrw mtvec, x1
csrr x3, mtvec
sw x3, 0(x10)
csrr x3, mtvec
sw x3, 0(x10)
nop
addi x1, x0, 97
loop:
sw x1, 0(x10)
addi x1, x1, 1
j loop
nop
nop
nop
