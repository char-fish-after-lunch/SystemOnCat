.org 0x0
    .global _start
    .global hello
_start:
nop
nop
nop
addi x1, x0, 0x48
addi x2, x0, 10
addi x3, x0, 0
li x10, 0xf000
loop:
lw x4, 0(x1)
sw x4, 0(x10)
addi x3, x3, 1
addi x1, x1, 4
bne x2, x3, loop
nop
nop
nop
nop
nop
nop

hello:
    .word 104
    .word 101
    .word 108
    .word 108
    .word 111
    .word 119
    .word 111
    .word 114
    .word 108
    .word 100
