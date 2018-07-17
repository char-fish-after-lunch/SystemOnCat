.org 0x0
    .global _start
_start:
nop
la x1, sft_handler
csrw mtvec, x1
li x20, 0x2004000
li x10, 0xf000
li x5, 64
csrwi mstatus, 0x8
csrwi mie, 0x8
j sft_init

sft_handler:
sw x7, 0(x10)
li x9, 0
sw x9, 0x10(x20)
li x6, 0
csrw mip, x6
mret
nop

sft_init:
nop
li x9, 1
li x7, 64
sw x9, 0x10(x20)
li x7, 65
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