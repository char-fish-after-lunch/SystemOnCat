#include "asm.h"
.org 0
.globl _start
_start:
    la gp, __global_pointer$
    li sp, 4096

#ifdef WITH_SMP
    csrr a1, mhartid
    slli a2, a1, 8
    add sp, sp, a2
    bnez a1, ap
#endif

    la ra, bootmain
    jalr x0, ra, 0

#ifdef WITH_SMP
ap:
    la ra, bootap
    jalr x0, ra, 0
#endif