#define ADR_SERIAL_BUF 0b00000000011111111111111111111100
#define ADR_SERIAL_DAT 0b00000000011111111111111111111000
#define ADR_FLASH 0b00000000011111111111111111100000
.org 0x0
.global _start
# load 4KB data (starting from adr 0)
_start:

    li a2, 4096

#ifdef WITH_SMP
    sw x0, 0(a2)
    csrr a1, mhartid
    # check the hart id, if not BSP
    bnez a1, ap
#endif

    li a1, 0
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

    # data loading finished
#ifdef WITH_SMP
    li a1, 1
    sw a1, 0(a2)
#endif

    jalr x0, x0, 0


#ifdef WITH_SMP
ap:
    lw a1, 0(a2)
    beqz a1, ap

    jalr x0, x0, 0
#endif

