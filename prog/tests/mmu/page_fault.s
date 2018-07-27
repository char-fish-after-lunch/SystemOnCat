.org 0x0
    .global _start
_start:
nop
la x1, pf_handler
csrw mtvec, x1

lw x1, sa
lw x2, pte1
lw x3, pte2 # page 1 -> page 2 -> page 0 (self mapping)
li x4, 0x1000
sw x2, 0(x4)
li x4, 0x2000
sw x3, 0(x4)
li x3, 0x00000801 # virtual page 2 -> physical page 2
sw x3, 8(x4)

li x10, 0x2010
li x11, 0
sw x11, 0(x10)

csrw satp, x1

li x4, 97
li x6, 108
li x5, 0x4000
loop:
sw x4, 0(x5)
nop
addi x4, x4, 1
inner_loop:
nop
nop
beq x4, x6, inner_loop
addi x5, x5, 4
nop
j loop # after execution, 0x5000 in RAM should have been overwritten
nop

pf_handler:
csrr x12, mtval
li x10, 0x2010
sw x12, 8(x10)
csrr x12, mcause
sw x12, 16(x10)
li x11, 0x00001401 # page 1 -> page 4 -> page 5 (virtual page 4 mapped to physical page 5)
sw x11, 0(x10)
sfence.vma
mret


sa:
	.word 0x80000001
pte1:
	.word 0x00000801 
pte2:
	.word 0x00000001
