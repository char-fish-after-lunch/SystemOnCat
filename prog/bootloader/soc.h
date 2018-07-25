#ifndef SOC_H
#define SOC_H

#define ADR_FLASH_DAT 0b00000010000000000100000000110000
#define ADR_FLASH_ADR 0b00000010000000000100000000110100
#define ADR_FLASH_READY 0b00000010000000000100000000111000

#define ADR_SERIAL_BUF 0xf004
#define ADR_SERIAL_DAT 0xf000

#define ADR_SERIAL 0b

#define FLASH_READY (*((unsigned*)ADR_FLASH_READY))
#define FLASH_DAT (*((unsigned*)ADR_FLASH_DAT))
#define FLASH_ADR_SET(x) (*((unsigned*)ADR_FLASH_ADR) = (x))

#define EM_RISCV 243 

#define PUTCHAR(c) {while(!((*((unsigned*)ADR_SERIAL_BUF)) & 0xf)); \
    (*((char*)ADR_SERIAL_DAT) = (c));}

#endif
