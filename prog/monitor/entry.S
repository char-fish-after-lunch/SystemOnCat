#include "arch.h"

.section .text
.org 0x0
    .global _start

_start:
    nop
    la gp, __global_pointer$
    li sp, ADR_USTACK_TOP
    # set up the stack
    j start

.global _entry
_entry:
    addi sp, sp, -4*31

    sw x1, 4*0(sp)
    sw x2, 4*1(sp)
    sw x3, 4*2(sp)
    sw x4, 4*3(sp)
    sw x5, 4*4(sp)
    sw x6, 4*5(sp)
    sw x7, 4*6(sp)
    sw x8, 4*7(sp)
    sw x9, 4*8(sp)
    sw x10, 4*9(sp)
    sw x11, 4*10(sp)
    sw x12, 4*11(sp)
    sw x13, 4*12(sp)
    sw x14, 4*13(sp)
    sw x15, 4*14(sp)
    sw x16, 4*15(sp)
    sw x17, 4*16(sp)
    sw x18, 4*17(sp)
    sw x19, 4*18(sp)
    sw x20, 4*19(sp)
    sw x21, 4*20(sp)
    sw x22, 4*21(sp)
    sw x23, 4*22(sp)
    sw x24, 4*23(sp)
    sw x25, 4*24(sp)
    sw x26, 4*25(sp)
    sw x27, 4*26(sp)
    sw x28, 4*27(sp)
    sw x29, 4*28(sp)
    sw x30, 4*29(sp)
    sw x31, 4*30(sp)

    addi ra, a0, 0

    la a2, stack_top
    sw sp, 0(a2)

    addi x2, x0, 0
    addi x3, x0, 0
    addi x4, x0, 0
    addi x5, x0, 0
    addi x6, x0, 0
    addi x7, x0, 0
    addi x8, x0, 0
    addi x9, x0, 0
    addi x10, x0, 0
    addi x11, x0, 0
    addi x12, x0, 0
    addi x13, x0, 0
    addi x14, x0, 0
    addi x15, x0, 0
    addi x16, x0, 0
    addi x17, x0, 0
    addi x18, x0, 0
    addi x19, x0, 0
    addi x20, x0, 0
    addi x21, x0, 0
    addi x22, x0, 0
    addi x23, x0, 0
    addi x24, x0, 0
    addi x25, x0, 0
    addi x26, x0, 0
    addi x27, x0, 0
    addi x28, x0, 0
    addi x29, x0, 0

#if defined(WITH_CSR) && defined(WITH_INTERRUPT)
    # reset the timing
    la x30, time_count
    sw x0, 0(x30)

    # set the state
    # mark in user routine
    la x30, in_user
    li x31, 1

    sw x31, 0(x30)
#endif
    addi x30, x0, 0
    addi x31, x0, 0

    # jump to the entrance of the user routine
    jalr ra, ra, 0

.global _exit
    _exit:

    la ra, regs
    sw x31, 4*31(ra)
    sw x30, 4*30(ra)

#if defined(WITH_CSR) && defined(WITH_INTERRUPT)
    # clear in_user
    la x31, in_user
    sw x0, 0(x31)
#endif


    sw x29, 4*29(ra)
    sw x28, 4*28(ra)
    sw x27, 4*27(ra)
    sw x26, 4*26(ra)
    sw x25, 4*25(ra)
    sw x24, 4*24(ra)
    sw x23, 4*23(ra)
    sw x22, 4*22(ra)
    sw x21, 4*21(ra)
    sw x20, 4*20(ra)
    sw x19, 4*19(ra)
    sw x18, 4*18(ra)
    sw x17, 4*17(ra)
    sw x16, 4*16(ra)
    sw x15, 4*15(ra)
    sw x14, 4*14(ra)
    sw x13, 4*13(ra)
    sw x12, 4*12(ra)
    sw x11, 4*11(ra)
    sw x10, 4*10(ra)
    sw x9, 4*9(ra)
    sw x8, 4*8(ra)
    sw x7, 4*7(ra)
    sw x6, 4*6(ra)
    sw x5, 4*5(ra)
    sw x4, 4*4(ra)
    sw x3, 4*3(ra)
    sw x2, 4*2(ra)
    sw x1, 4*1(ra)
    sw x0, 4*0(ra)

    la sp, stack_top
    lw sp, 0(sp)

    lw x1, 4*0(sp)
    lw x2, 4*1(sp)
    lw x3, 4*2(sp)
    lw x4, 4*3(sp)
    lw x5, 4*4(sp)
    lw x6, 4*5(sp)
    lw x7, 4*6(sp)
    lw x8, 4*7(sp)
    lw x9, 4*8(sp)
    lw x10, 4*9(sp)
    lw x11, 4*10(sp)
    lw x12, 4*11(sp)
    lw x13, 4*12(sp)
    lw x14, 4*13(sp)
    lw x15, 4*14(sp)
    lw x16, 4*15(sp)
    lw x17, 4*16(sp)
    lw x18, 4*17(sp)
    lw x19, 4*18(sp)
    lw x20, 4*19(sp)
    lw x21, 4*20(sp)
    lw x22, 4*21(sp)
    lw x23, 4*22(sp)
    lw x24, 4*23(sp)
    lw x25, 4*24(sp)
    lw x26, 4*25(sp)
    lw x27, 4*26(sp)
    lw x28, 4*27(sp)
    lw x29, 4*28(sp)
    lw x30, 4*29(sp)
    lw x31, 4*30(sp)

    addi sp, sp, 31*4

    ret

#if defined(WITH_CSR) && defined(WITH_INTERRUPT)
.global _trap_entry
_trap_entry:
    csrrw sp, mscratch, sp
    
    addi sp, sp, -4*31
    sw x1, 4*0(sp)
    sw x2, 4*1(sp)
    sw x3, 4*2(sp)
    sw x4, 4*3(sp)
    sw x5, 4*4(sp)
    sw x6, 4*5(sp)
    sw x7, 4*6(sp)
    sw x8, 4*7(sp)
    sw x9, 4*8(sp)
    sw x10, 4*9(sp)
    sw x11, 4*10(sp)
    sw x12, 4*11(sp)
    sw x13, 4*12(sp)
    sw x14, 4*13(sp)
    sw x15, 4*14(sp)
    sw x16, 4*15(sp)
    sw x17, 4*16(sp)
    sw x18, 4*17(sp)
    sw x19, 4*18(sp)
    sw x20, 4*19(sp)
    sw x21, 4*20(sp)
    sw x22, 4*21(sp)
    sw x23, 4*22(sp)
    sw x24, 4*23(sp)
    sw x25, 4*24(sp)
    sw x26, 4*25(sp)
    sw x27, 4*26(sp)
    sw x28, 4*27(sp)
    sw x29, 4*28(sp)
    sw x30, 4*29(sp)
    sw x31, 4*30(sp)

    la ra, trap_frame
    sw sp, 0(ra)

    la ra, trap
    jalr ra, ra, 0

    lw x1, 4*0(sp)
    lw x2, 4*1(sp)
    lw x3, 4*2(sp)
    lw x4, 4*3(sp)
    lw x5, 4*4(sp)
    lw x6, 4*5(sp)
    lw x7, 4*6(sp)
    lw x8, 4*7(sp)
    lw x9, 4*8(sp)
    lw x10, 4*9(sp)
    lw x11, 4*10(sp)
    lw x12, 4*11(sp)
    lw x13, 4*12(sp)
    lw x14, 4*13(sp)
    lw x15, 4*14(sp)
    lw x16, 4*15(sp)
    lw x17, 4*16(sp)
    lw x18, 4*17(sp)
    lw x19, 4*18(sp)
    lw x20, 4*19(sp)
    lw x21, 4*20(sp)
    lw x22, 4*21(sp)
    lw x23, 4*22(sp)
    lw x24, 4*23(sp)
    lw x25, 4*24(sp)
    lw x26, 4*25(sp)
    lw x27, 4*26(sp)
    lw x28, 4*27(sp)
    lw x29, 4*28(sp)
    lw x30, 4*29(sp)
    lw x31, 4*30(sp)

    addi sp, sp, 4*31

    csrrw sp, mscratch, sp
    mret
#endif

.section .data
    .align 0x4
stack_top:
    .word 0x0
#if defined(WITH_CSR) && defined(WITH_INTERRUPT)
.global in_user
in_user:
    .word 0x0
.global time_count
time_count:
    .word 0x0
.global time_lim
time_lim:
    .word 0x0
.global trap_frame
trap_frame:
    .word 0x0
#endif