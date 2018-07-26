.org 0x0
    .global _start
    .global hello
_start:
nop
nop
nop
la x1, hello
addi x2, x0, 10
addi x3, x0, 0
li x10, 0xf000
loop:
lbu x4, 0(x1)
sw x4, 0(x10)
addi x3, x3, 1
addi x1, x1, 1
bne x2, x3, loop

hello:
    .byte 104
    .byte 101
    .byte 108
    .byte 108
    .byte 111
    .byte 119
    .byte 111
    .byte 114
    .byte 108
    .byte 100
