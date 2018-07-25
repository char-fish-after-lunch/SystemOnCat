#include "asm.h"
.org 0
.globl _start
_start:
    li sp, 4096
    la ra, bootmain
    jalr x0, ra, 0
