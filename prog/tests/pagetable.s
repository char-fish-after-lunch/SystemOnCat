.org 0x0
    .global _start
_start:
nop
lw x1, sa
lw x2, pte1
lw x3, pte2
sw x2, addr1, x0
sw x3, addr2, x0
csrw satp, x1 
nop
nop
nop
mv x1, x0
mv x2, x0
mv x3, x0
addi x1, x0, 0x48
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
data:
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