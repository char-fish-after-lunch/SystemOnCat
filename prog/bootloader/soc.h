#ifndef SOC_H
#define SOC_H

#define VADDR(x) ((x) + VA_BASE)

#define ADR_FLASH_DAT 0b00000000011111111111111111100000
#define ADR_FLASH_ADR 0b00000000011111111111111111100100
#define ADR_FLASH_READY 0b00000000011111111111111111101000

#define ADR_SERIAL_BUF 0b00000000011111111111111111111100
#define ADR_SERIAL_DAT 0b00000000011111111111111111111000

#define ADR_SERIAL 0b

#define FLASH_READY (*((unsigned*)ADR_FLASH_READY))
#define FLASH_DAT (*((unsigned*)ADR_FLASH_DAT))
#define FLASH_ADR_SET(x) (*((unsigned*)ADR_FLASH_ADR) = (x))

#define FLASH_READY_VA (*((unsigned*)ADR_FLASH_READY))
#define FLASH_DAT_VA (*((unsigned*)ADR_FLASH_DAT))
#define FLASH_ADR_SET_VA(x) (*((unsigned*)ADR_FLASH_ADR) = (x))

#define EM_RISCV 243 

#define PUTCHAR(c) {while(!((*((unsigned*)ADR_SERIAL_BUF)) & 0xf)); \
    (*((char*)ADR_SERIAL_DAT) = (c));}



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


#endif
