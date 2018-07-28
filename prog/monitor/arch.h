#ifndef ARCH_H
#define ARCH_H

#define ADR_USTACK_TOP (0x30000 + VA_BASE)
#define ADR_SERIAL_BUF (0b00000000011111111111111111111100 + VA_BASE)
#define ADR_SERIAL_DAT (0b00000000011111111111111111111000 + VA_BASE)

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
#define ADR_CMPL (0b00000000011111111111111111000000 + VA_BASE)
#define ADR_CMPH (0b00000000011111111111111111000100 + VA_BASE)
#define ADR_TMEL (0b00000000011111111111111111001000 + VA_BASE)
#define ADR_TMEH (0b00000000011111111111111111001100 + VA_BASE)
#define ADR_MSIP (0b00000000011111111111111111010000 + VA_BASE)

#define INT_MTIMER 7
#define INT_MIRQ 11

#define EXC_INST_MISALIGN 0
#define EXC_ILLEGAL_INST 2
#define EXC_LOAD_MISALIGN 4
#define EXC_STORE_MISALIGN 6
#define EXC_ECALL 11

#define MIP_MTIP 7

#define ADR_KSTACK_TOP (0x40000 + VA_BASE)


#endif


#ifdef WITH_IRQ
#define ADR_PLIC (0b00000000011111111111111111110000 + VA_BASE)
#define IRQ_SERIAL 1
#define IRQ_KEYBOARD 2
#define IRQ_NETWORK 3
#define IRQ_RESERVED 4
#endif

#ifdef WITH_ECALL
#define ECALL_EXIT 0
#define ECALL_PUTCHAR 1
#define ECALL_GETCHAR 2
#endif

#endif

#endif
