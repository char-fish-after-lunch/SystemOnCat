.org 0x0
    .global _start
_start:
nop
nop
li x1, 0x4000 # test memory space
li x11, 0x4004
li x9, 0x1111
li x2, 0x61
li x3, 0x62
li x4, 0x6300
li x5, 0x64
sw x2, (x1)
amoswap.w x9, x3, (x1) # swap 0x62 for 0x61
sw x9, 0(x11) # should be 0x61
lw x12, (x1)
sw x12, 4(x11) # should be 0x62
amoadd.w x9, x4, (x1) # add 0x62 + 0x6300
sw x9, 8(x11) # should be 0x62
lw x12, (x1)
sw x12, 12(x11) # should be 0x6362

nop
nop
loop:
nop
nop
j loop
