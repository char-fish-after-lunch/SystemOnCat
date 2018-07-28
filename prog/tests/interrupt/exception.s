.org 0x0
    .global _start
_start:
nop
la x1, expt_handler
csrw mtvec, x1
li x20, 0x2004000
li x10, 0xf000
li x5, 64
csrwi mstatus, 0x8
csrwi mie, 0x8
j expt_init

expt_handler:
sw x5, 0(x10)
csrr x4, mcause
addi x4, x4, 48
sw x4, 0(x10)
li x6, 0
csrw mip, x6
mret
nop

expt_init:
nop
nop
li x23, 0x1001
sw x5, 0(x23)
nop
nop
nop
loop:
nop
nop
j loop
nop
nop
nop
nop
