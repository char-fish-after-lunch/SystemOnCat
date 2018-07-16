.org 0x0
    .global _start
_start:
nop
li x1, 0x38
li x2, 0x80
csrw mtvec, x1
csrwi mstatus, 0x8
csrw mie, x2
nop
nop
nop
nop
nop
nop
nop
nop
mtvc:
li x10, 0xf000
li x1, 0x21
sw x1, 0(x10)
nop
mret
