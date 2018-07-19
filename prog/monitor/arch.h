#ifndef ARCH_H
#define ARCH_H

#define ADR_SERIAL_BUF 0xf004
#define ADR_SERIAL_DAT 0xf000
#define PUTCHAR(c) {while(!((*((unsigned*)ADR_SERIAL_BUF)) & 0xf)); \
    (*((char*)ADR_SERIAL_DAT) = (c));}
#define GETCHAR(c) {while(!((*((unsigned*)ADR_SERIAL_BUF)) & 0xf0)); \
    (c) = *((char*)ADR_SERIAL_DAT); }


#ifdef WITH_CSR


#define read_csr(reg) ({ unsigned long __tmp; \
  asm volatile ("csrr %0, " #reg : "=r"(__tmp)); \
  __tmp; })

#define write_csr(reg, val) ({ \
  asm volatile ("csrw " #reg ", %0" :: "rK"(val)); })

#define swap_csr(reg, val) ({ unsigned long __tmp; \
  asm volatile ("csrrw %0, " #reg ", %1" : "=r"(__tmp) : "rK"(val)); \
  __tmp; })

#define set_csr(reg, bit) ({ unsigned long __tmp; \
  asm volatile ("csrrs %0, " #reg ", %1" : "=r"(__tmp) : "rK"(bit)); \
  __tmp; })

#define clear_csr(reg, bit) ({ unsigned long __tmp; \
  asm volatile ("csrrc %0, " #reg ", %1" : "=r"(__tmp) : "rK"(bit)); \
  __tmp; })

#ifdef WITH_INTERRUPT

// memory mapped status registers
#define ADR_CMPL 0b00000010000000000100000000000000
#define ADR_CMPH 0b00000010000000000100000000000100
#define ADR_TMEL 0b00000010000000000100000000001000
#define ADR_TMEH 0b00000010000000000100000000001100
#define ADR_MSIP 0b00000010000000000100000000010000

#define INT_MTIMER 7

#define MIP_MTIP 7

#endif



#endif

#endif
