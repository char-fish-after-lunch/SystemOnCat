#define ADR_SERIAL_BUF 0b00000000011111111111111111111100
#define ADR_SERIAL_DAT 0b00000000011111111111111111111000
#define ADR_FLASH 0b00000000011111111111111111100000
.org 0x0
.global _start
# load 4KB data (starting from adr 0)
_start:
csrr a1, mhartid
bnez a1, core0_start
is_core1:
li a1, ADR_SERIAL_BUF
li a3, ADR_SERIAL_DAT
li a2, 64
lw a1, (a1)

andi a1, a1, 0xf
beqz a1, is_core1
sw a2, (a3)
core1_dead_loop:
nop
j core1_dead_loop
core0_start:
li a1, 0
li a2, 4096
li a3, ADR_FLASH

loop:
sw a1, 4(a3)

wait:
lw a4, 8(a3)
beq x0, a4, wait

lbu a4, 0(a3)
sb a4, 0(a1)
addi a1, a1, 1
bne a1, a2, loop

jalr x0, x0, 0
