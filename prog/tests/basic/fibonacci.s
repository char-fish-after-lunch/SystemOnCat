.org 0x0
    .global _start
_start:
nop
nop
addi x10, x0, 1
addi x1, x0, 1
addi x2, x0, 1
addi x3, x0, 15
loop:
add x1, x1, x2 
add x2, x1, x2
addi x10, x10, 1
bne x10, x3, loop
nop
nop
nop
nop
nop
nop
