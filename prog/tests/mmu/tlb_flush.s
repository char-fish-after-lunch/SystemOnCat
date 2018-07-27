.org 0x0
    .global _start
_start:
nop

li x19, 0x80000001
li x2, 0x00000801
li x3, 0x00000001 # page 1 -> page 2 -> page 0 (self mapping)
li x4, 0x1000
sw x2, 0(x4)
li x4, 0x2000
sw x3, 0(x4)
li x3, 0x00000801 # virtual page 2 -> physical page 2
sw x3, 8(x4)

li x10, 0x2010
li x11, 0x00001401 # page 1 -> page 4 -> page 5 (virtual page 4 mapped to physical page 5)
sw x11, 0(x10)

li x11, 0x80000006
li x2, 0x00001c01 
li x3, 0x00000001 # virtual page 0 -> page 6 -> page 7 -> page 0 (self mapping)
li x4, 0x6000
sw x2, 0(x4)
li x4, 0x7000
sw x3, 0(x4)
li x3, 0x00002001 # virtual page 2 -> physical page 8
sw x3, 8(x4)

li x21, 0x2220
li x2, 97

loop:
csrw satp, x19
sw x2, 0(x21)
addi x2, x2, 1
csrw satp, x11
sw x2, 0(x21)
addi x2, x2, 1
addi x21, x21, 4
nop
j loop
