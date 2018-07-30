.org 0x0
    .global _start
_start:
nop
nop
addi x10, x0, 1
lw x11, vaddr
addi x1, x0, 1
addi x2, x0, 1
addi x3, x0, 15
loop:
add x1, x1, x2 
add x2, x1, x2
addi x10, x10, 1
sw x1, 128(x11)
sw x2, 132(x11)
addi x11, x11, 8
bne x10, x3, loop
end:
j end
nop
nop
nop
nop
nop
nop
loopp:
nop
j loopp

vaddr:
    .word 0x3000
