.section .text
.org 0x0
    .global _start
_start:

nop
li sp, 0xe700
li s0, 0x30
li a1, 0xf000
sw s0, 0(a1)


addi	sp,sp,-48
sw	s0,44(sp)
addi	s0,sp,48
sw	a0,-36(s0)
lui	a5,0x2
li	a4,48
sb	a4,-1040(a5) # 1bf0 <buffer>
lui	a5,0x2
addi	a5,a5,-1040 # 1bf0 <buffer>
li	a4,120
sb	a4,1(a5)
sw	zero,-20(s0)
j	check
cont:
li	a4,7
lw	a5,-20(s0)
sub	a5,a4,a5
slli	a5,a5,0x2
lw	a4,-36(s0)
srl	a5,a4,a5
andi	a4,a5,15
lw	a5,-20(s0)
addi	a3,a5,2
lui	a5,0x2
addi	a5,a5,-1508 # 1a1c <INST_CONFIG+0x250>
add	a5,a4,a5
lbu	a4,0(a5)
lui	a5,0x2
addi	a5,a5,-1040 # 1bf0 <buffer>
add	a5,a3,a5
sb	a4,0(a5)
lw	a5,-20(s0)
addi	a5,a5,1
sw	a5,-20(s0)
check:
lw	a4,-20(s0)
li	a5,0
blt	a4,a5, cont
lui	a5,0x2
addi	a5,a5,-1040 # 1bf0 <buffer>
sb	zero,10(a5)
nop
lw	s0,44(sp)
addi	sp,sp,48

li a1, 0xf000
sw s0, 0(a1)
nop
nop
nop
nop
loop:
nop
nop
nop
j loop

