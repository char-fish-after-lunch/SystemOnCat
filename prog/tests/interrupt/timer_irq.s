.org 0x0
    .global _start
_start:
nop
la x1, timer_handler
csrw mtvec, x1
li x20, 0x2004000
li x10, 0xf000
li x5, 64
li x2, 0x80
csrwi mstatus, 0x8
csrw mie, x2
j timer_init

timer_handler:
li x3, 0
sw x3, 8(x20)
sw x5, 0(x10)
addi x5, x5, 1
li x6, 0
csrw mip, x6
mret

timer_init:
li x3, 0x30
sw x3, 0(x20)
li x3, 0
sw x3, 8(x20)
loop:
lw x22, 0(x5)
j loop
li x5, 64
nop
