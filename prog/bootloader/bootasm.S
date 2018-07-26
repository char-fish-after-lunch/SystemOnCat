#include "asm.h"
.org 0
.globl _start
_start:
    li sp, 4096
    la gp, __global_pointer$
    la ra, bootmain
    jalr x0, ra, 0
