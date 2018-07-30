.org 0x0
    .global _start
_start:
nop
lw x1, sa
lw x2, pte1
lw x3, pte2
lw x5, pte3

la x4, addr1
lw x4, 0(x4)
sw x2, 0(x4)

la x4, addr2
lw x4, 0(x4)
sw x3, 0(x4)

la x4, addr3
lw x4, 0(x4)
sw x5, 0(x4)

csrw satp, x1 
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
nop
nop
nop
nop
nop
nop

vaddr:
    .word 0x0000
sa:
	.word 0x80000001
pte1:
	.word 0x00000801 
pte2:
	.word 0x00000001
addr1:
	.word 0x1000
addr2:
	.word 0x2000

pte3:
    .word 0x00000001
addr3:
    .word 0x200c


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
