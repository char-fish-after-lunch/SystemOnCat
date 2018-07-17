.org 0x0
    .global _start
_start:
nop
li s0, 0x800
li a5, 0xf000
lbu a5, 0(a5)
sb a5, -17(s0)
lui a5, 0x6
lbu a4, -17(s0)
sb a4, 0(a5)
nop
loop:
lui a5, 0xf
addi a5, a5, 4
lw a5, 0(a5)
andi a5,a5,15
beqz a5, loop
lui a5, 0xf
lbu a4, -17(s0)
sb a4, 0(a5)
nop
nop
nop
nop
