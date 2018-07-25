.org 0x0
    .global _start
_start:
nop
lw x1, sa
lw x2, pte1
lw x3, pte2
sw x2, addr1, x10
sw x3, addr2, x10
csrw satp, x1 
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

vaddr:
    .word 0x3000
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
