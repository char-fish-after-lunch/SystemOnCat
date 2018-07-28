.org 0x0
    .global _start
_start:
nop
nop
li x1, 0x2000 # test memory space
li x11, 0x2004
li x2, 0x61
li x5, 0x62
li x6, 0x63
li x7, 0x64
sw x2, (x1)
lr.w x3, (x1)
sc.w x4, x5, (x1) # first try - should succeed
lw x5, (x1)

sw x4, 0(x11) # should be 0
sw x5, 4(x11) # should be 0x62

li x4, 0x22

lr.w x3, (x1)
sw x6, (x1)
nop
nop
nop
nop
sc.w x4, x7, (x1) # second try - should fail
lw x5, (x1)

sw x4, 8(x11) # should be a non-zero value, typically 1
sw x5, 12(x11) # should be 0x63

nop
nop
nop
nop
nop
loop:
nop
j loop
