#include "soc.h"
#include "types.h"
#include "elf.h"

#define ELF_START 4096
#define BUFSIZE 64
#define HEX "0123456789abcdef"

void print(const char* str){
    int i;
    for(i = 0; str[i]; i ++)
        PUTCHAR(str[i]);
}

void hex2str(unsigned x, char* cbuffer){
    cbuffer[0] = '0'; cbuffer[1] = 'x';
    int i;
    for(i = 0; i < 8; i ++){
        cbuffer[2 + i] = HEX[(x >> ((7 - i) << 2)) & (0xf)];
    }
    cbuffer[10] = '\0';
}

void print_hex(unsigned x){
    char buf[16];
    hex2str(x, buf);
    print(buf);
}


static void read_flash(char* c, uint32_t num, uintptr_t adr){
    uint32_t i;
    for(i = 0; i < num; i ++){
        FLASH_ADR_SET(adr + i);
        while(!FLASH_READY);
        c[i] = FLASH_DAT;
    }
}

void bootmain(void){
    print("BootedCat!\n\n");

    struct elfhdr elf_header;
    read_flash((char*)&elf_header, sizeof(struct elfhdr), ELF_START);

    if(elf_header.e_magic != ELF_MAGIC || elf_header.e_machine != EM_RISCV)
        goto bad;
    
    uintptr_t flash_ph_adr = elf_header.e_phoff + ELF_START;
    struct proghdr ph;
    uint32_t i, num = elf_header.e_phnum, ph_sz = elf_header.e_phentsize;
    uintptr_t va, flash_adr;
    uint32_t sz;
    for(i = 0; i < num; i ++, flash_ph_adr += ph_sz){
        read_flash((char*)&ph, ph_sz, flash_ph_adr);
        va = ph.p_va;
        flash_adr = ph.p_offset + ELF_START;
        sz = ph.p_memsz;
        read_flash((char*)va, sz, flash_adr);
    }

    ((void (*)(void))(elf_header.e_entry)) ();

    bad:
        print("Fatal error: broken ELF image.\n");
        while(1){}
}
