.org 0x0
    .global _start
_start:
nop
li x1, 0x30
csrw mtvec, x1
nop
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
