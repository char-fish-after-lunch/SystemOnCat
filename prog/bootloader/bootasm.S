#include "asm.h"
.org 0
.globl _start
_start:
    li sp, 0xe700
    la ra, bootmain
    jalr x0, ra, 0
