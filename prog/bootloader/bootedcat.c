#include "soc.h"
#include "types.h"
#include "elf.h"

#define ELF_START 4096
#define BUFSIZE 64
#define HEX "0123456789abcdef"

#define CEIL(x, b) (((x) >> (b)) + (((x) & ((1 << (b)) - 1)) != 0))
#define FLOOR(x, b) ((x) >> (b))
#define ROUND_UP(x, b) (CEIL((x), (b)) << (b))
#define ROUND_DOWN(x, b) (FLOOR((x), (b)) << (b))

#define PGDIR ((uint32_t*)4096)
#define PG_COUNT 1024
#define PG_SIZE 4096
#define PG_WIDTH 12
#define PTE_VALID 1
#define PTE_READ 2
#define PTE_WRITE 4
#define PTE_EXE 8
#define PTE_USER 16
#define PTE_GLOBAL 32
#define PTE_ACCESSED 64
#define PTE_DIRTY 128
#define PTE_PPN(x) ((x) >> 10)
#define VA_VPN0(x) (((x) >> 12) & 1023)
#define VA_VPN1(x) (((x) >> 22) & 1023)

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

#ifdef WITH_AT

uint32_t pt_count;

void memset(char* adr, char v, uint32_t sz){
    uint32_t i;
    for(i = 0; i < sz; i ++)
        adr[i] = v;
}

void address_map(uint32_t va, uint32_t pa){
    uint32_t* pde = PGDIR + VA_VPN1(va);
    uint32_t* pt;
    uint32_t* pte;
    if(!(PTE_VALID & *pde)){
        *pde = ((2 + pt_count) << 10) | PTE_VALID;
        memset((char*)((2 + pt_count) << PG_WIDTH), 0, PG_SIZE);
        ++ pt_count;
    }
    pt = (uint32_t*)(PTE_PPN(*pde) << PG_WIDTH);
    pte = pt + VA_VPN0(va);
    *pte = (pa >> 2) | PTE_VALID | PTE_WRITE | PTE_READ | PTE_EXE;
}

void at_buildup(uintptr_t va_base, uint32_t pa_bound, uintptr_t pa_base){
    uintptr_t start_vp = FLOOR(va_base, PG_WIDTH);
    uint32_t page_count = CEIL(pa_bound, PG_WIDTH);
    uintptr_t start_pp = FLOOR(pa_base, PG_WIDTH);

    uint32_t cur_page;
    for(cur_page = 0; cur_page < page_count; cur_page ++)
        address_map((start_vp + cur_page) << PG_WIDTH, 
            (start_pp + cur_page) << PG_WIDTH);
}
#endif

void bootmain(void){
    print("BootedCat!\n\n");

    struct elfhdr elf_header;
    read_flash((char*)&elf_header, sizeof(struct elfhdr), ELF_START);

    if(elf_header.e_magic != ELF_MAGIC || elf_header.e_machine != EM_RISCV)
        goto bad;

#ifdef WITH_AT
    pt_count = 0;
    memset((char*)PGDIR, 0, PG_SIZE);
    at_buildup(VA_BASE, PA_BOUND, 0);
    at_buildup(0, PG_SIZE, 0); // the bootloader itself also needs mapping
    at_buildup(PA_BOUND - PG_SIZE, PG_SIZE, PA_BOUND - PG_SIZE); 

    // map the devices for convenience

    write_csr(satp, ((uint32_t)1 << 31) | 1);
#endif
    
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
