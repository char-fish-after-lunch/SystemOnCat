
#define ADR_SERIAL_BUF 0b00000000011111111111111111111100
#define ADR_SERIAL_DAT 0b00000000011111111111111111111000

.org 0x0
    .global _start
_start:

nop

csrr tp, mhartid
la s1, lock
sw zero, (s1)

loop_begin:

li s3, 1
acquire:
nop
lr.w a5, (s1)
bnez a5, acquire
sc.w a4, s3, (s1)
bnez a4, acquire


cons_loop:
li a7, 0b00000000011111111111111111111100
lw a2, (a7)
andi a2, a2, 0xf
beqz a2, cons_loop
li a2, 48
add a2, a2, tp
li a7, 0b00000000011111111111111111111000
sw a2, (a7)

cons_loop2:
li a7, 0b00000000011111111111111111111100
lw a2, (a7)
andi a2, a2, 0xf
beqz a2, cons_loop2
li a2, 48
add a2, a2, tp
li a7, 0b00000000011111111111111111111000
sw a2, (a7)

release:
amoswap.w zero, zero, (s1)

j loop_begin

lock:
    .word 0
