#define ADR_FLASH 0b00000010000000000100000000101000
.org 0x0
.global _start
# load 4KB data (starting from adr 0)
_start:
li a1, 0
li a2, 4096
li a3, ADR_FLASH

loop:
sw a1, 4(a3)
lbu a4, 0(a3)
sb a4, 0(a1)
addi a1, a1, 1
bne a1, a2, loop

jalr x0, x0, 0
