#include "inst.h"

#define ADR_SERIAL_BUF 0xf004
#define ADR_SERIAL_DAT 0xf000
#define PUTCHAR(c) {while(!((*((char*)ADR_SERIAL_BUF)) & 0xf)); \
    (*((char*)ADR_SERIAL_DAT) = (c));}
#define GETCHAR(c) ((c) = *((char*)ADR_SERIAL_DAT))
#define STR_PROMPT ">>> "
#define bool int
#define true 1
#define false 0
#define BUFSIZE 64
#define REGSIZE 32
#define HEX "0123456789abcdef"

#define GETBITS(src, ld, rd) (((src) >> (ld)) & ((1 << ((rd) - (ld) + 1)) - 1))
#define SETBITS(dest, ld, rd, src) ((dest) | (GETBITS((src), 0, (rd) - (ld)) << (ld)))

extern void _entry(unsigned adr);


void print(char* str){
    int i;
    for(i = 0; str[i]; i ++)
        PUTCHAR(str[i]);
}

bool strcmp(char* a, char* b){
    int i;
    for(i = 0; a[i] && b[i] && a[i] == b[i]; i ++);
    return a[i] && b[i];
}

char buffer[BUFSIZE];
unsigned regs[REGSIZE];

void get_line(){
    int bn = -1;
    do{
        ++ bn;
        GETCHAR(buffer[bn]);
    } while(buffer[bn] != '\n');
    buffer[bn] = '\0';
}

void hex2str(unsigned x){
    buffer[0] = '0'; buffer[1] = 'x';
    int i;
    for(i = 0; i < 8; i ++){
        buffer[2 + i] = HEX[(x >> (i << 2)) & (0xf)];
    }
    buffer[10] = '\0';
}

unsigned hexchar2int(char c){
    if(c >= '0' && c <= '9')
        return c - '0';
    return c - 'a' + 10;
}

unsigned str2hex(char* c){
    c += 2; // skip 0x
    unsigned res = 0;
    while(*c && *c != ' '){
        res = (res << 4) | hexchar2int(*c);
        ++ c;
    }
    return res;
}

void print_help(){
    print("Instructions:\n");
    print("J <x>      - Jump to address x.\n");
    print("R          - List values of registers after last run.\n");
    print("E <x>      - Edit data starting at address x.\n");
    print("I <x>      - Edit instructions starting at address x.\n");
    print("V <x> <y>  - View contents in [x, y].\n");
    print("D <x> <y>  - Disassemble contents in [x, y].\n");
}

void init(){
    int i;
    for(i = 0; i < REGSIZE; i ++)
        regs[i] = 0;
}

char* next_word(char* c){
    while(*c && *c != ' ')
        ++ c;
    while(*c && *c == ' ')
        ++ c;
    return c;
}

void jump_exe(){
    char* c = next_word(buffer);
    if(!*c || !*(c+1))
        return;
    unsigned target_adr = str2hex(c);
    _entry(target_adr);
}

void reg_exe(){
    int i;
    for(i = 0; i < REGSIZE; i ++){
        print("x(");
        hex2str(i);
        print(buffer);
        print(")  =  ");
        hex2str(regs[i]);
        print(buffer);
        print("\n");
    }
}

void edit_exe(){
    char* c = next_word(buffer);
    if(!*c || !*(c+1))
        return;
    unsigned adr = str2hex(c), val;
    while(true){
        print("[");
        hex2str(adr);
        print(buffer);
        print("] ");
        get_line();
        c = next_word(buffer);
        if(!*c || !*(c+1))
            break;
        val = str2hex(c);

        *((unsigned*)adr) = val;
        adr += 4;
    }
}

bool parse_arg(char* c, unsigned* res){
    if(!*c || !*(c + 1))
        return false;
    if(*c == '0' && *(c + 1) == 'x')
        *res = str2hex(c);
    else if(*c == 'x' && *(c + 1) == '0' && *(c + 2) == 'x')
        *res = str2hex(c + 1);
    return true;
}

// translate an instruction to an unsigned
// 0 for invalid instruction
unsigned inst2int(char* c){
    int i, j;
    for(i = 0; i < INST_N; i ++){
        for(j = 0; INST_NAMES[i][j] && c[j] && INST_NAMES[i][j] == c[j]; j ++);
        if(!INST_NAMES[i][j] || (!c[j] && c[j] != ' '))
            return 0;
        break;
    }
    if(i == INST_N)
        return 0;
    unsigned res = INST_CONFIG[i][1];
    unsigned inst_type = INST_CONFIG[i][0];
    unsigned tmp_val;
    bool re;
    if(inst_type == ITYPE_R || inst_type == ITYPE_I ||
        inst_type == ITYPE_S || inst_type == ITYPE_B)
        res = SETBITS(res, 12, 14, INST_CONFIG[i][2]);
    if(inst_type == ITYPE_R)
        res = SETBITS(res, 25, 31, INST_CONFIG[i][3]);
    if(inst_type == ITYPE_R || inst_type == ITYPE_I ||
        inst_type == ITYPE_U || inst_type == ITYPE_J){
        c = next_word(c);
        re = parse_arg(c, &tmp_val);
        if(!re)
            return 0;
        res = SETBITS(res, 7, 11, tmp_val);
    }
    if(inst_type == ITYPE_R || inst_type == ITYPE_I ||
        inst_type == ITYPE_S || inst_type == ITYPE_B){
        c = next_word(c);
        re = parse_arg(c, &tmp_val);
        if(!re)
            return 0;
        res = SETBITS(res, 15, 19, tmp_val);
    }
    if(inst_type == ITYPE_R || inst_type == ITYPE_S ||
        inst_type == ITYPE_B){
        c = next_word(c);
        re = parse_arg(c, &tmp_val);
        if(!re)
            return 0;
        res = SETBITS(res, 20, 24, tmp_val);
    }
    if(inst_type != ITYPE_R){
        c = next_word(c);
        re = parse_arg(c, &tmp_val);
        if(!re)
            return 0;
        switch(inst_type){
            case ITYPE_I:
                res = SETBITS(res, 20, 31, tmp_val);
                break;
            case ITYPE_S:
                res = SETBITS(res, 7, 11, tmp_val);
                res = SETBITS(res, 25, 31, tmp_val >> 5);
                break;
            case ITYPE_B:
                res = SETBITS(res, 7, 7, tmp_val >> 11);
                res = SETBITS(res, 8, 11, tmp_val >> 1);
                res = SETBITS(res, 25, 30, tmp_val >> 5);
                res = SETBITS(res, 31, 31, tmp_val >> 12);
                break;
            case ITYPE_U:
                res = SETBITS(res, 12, 31, tmp_val >> 12);
                break;
            case ITYPE_J:
                res = SETBITS(res, 12, 19, tmp_val >> 12);
                res = SETBITS(res, 20, 20, tmp_val >> 11);
                res = SETBITS(res, 21, 30, tmp_val >> 1);
                res = SETBITS(res, 31, 31, tmp_val >> 20);
        }
    }
    return res;
}

void inst_exe(){
    char *c = next_word(buffer);
    if(!*c || !*(c+1))
        return;
    unsigned adr = str2hex(c), val;
    while(true){
        print("[");
        hex2str(adr);
        print(buffer);
        print("] ");
        get_line();
        c = next_word(buffer);
        if(!*c || !*(c+1))
            break;
        val = inst2int(c);
        if(val == 0)
            continue;

        *((unsigned*)adr) = val;
        adr += 4;
    }
}

void view_exe(){
    char* c = next_word(buffer);
    if(!*c || !*(c+1))
        return;
    unsigned adr = str2hex(c), val;
    while(true){
        print("[");
        hex2str(adr);
        print(buffer);
        print("] ");
        val = *((unsigned*)adr);
        hex2str(adr);
        print(buffer);
        print("\n");
        adr += 4;
    }
}

void print_int2inst(unsigned val){
    int i, itype;
    for(i = 0; i < INST_N; i ++){
        if(INST_CONFIG[i][1] != GETBITS(val, 0, 6))
            continue;
        itype = INST_CONFIG[i][0];
        if(itype == ITYPE_R && INST_CONFIG[i][3] != GETBITS(val, 25, 31))
            continue;
        if((itype == ITYPE_R || itype == ITYPE_I || itype == ITYPE_S || itype == ITYPE_B) &&
            INST_CONFIG[i][2] != GETBITS(val, 12, 14))
            continue;
        
        break;
    }
    if(i == INST_N){
        // no instruction matched
        print("<Invalid>");
        return;
    }
    if(itype == ITYPE_R || itype == ITYPE_I || itype == ITYPE_U || itype == ITYPE_J){
        print(" ");
        hex2str(GETBITS(val, 7, 11));
        print(buffer);
    }
    if(itype == ITYPE_R || itype == ITYPE_I || itype == ITYPE_S || itype == ITYPE_B){
        print(" ");
        hex2str(GETBITS(val, 15, 19));
        print(buffer);
    }
    if(itype == ITYPE_R || itype == ITYPE_S || itype == ITYPE_B){
        print(" ");
        hex2str(GETBITS(val, 20, 24));
        print(buffer);        
    }
    unsigned imm = 0;
    if(itype != ITYPE_R){
        switch(itype){
            case ITYPE_I:
                imm = GETBITS(val, 20, 31);
                break;
            case ITYPE_S:
                imm = GETBITS(val, 7, 11) | (GETBITS(val, 25, 31) << 5);
                break;
            case ITYPE_B:
                imm |= GETBITS(val, 7, 7) << 11;
                imm |= GETBITS(val, 8, 11) << 1;
                imm |= GETBITS(val, 25, 30) << 5;
                imm |= GETBITS(val, 31, 31) << 12;
                break;
            case ITYPE_U:
                imm = GETBITS(val, 12, 31) << 12;
                break;
            case ITYPE_J:
                imm |= GETBITS(val, 12, 19) << 12;
                imm |= GETBITS(val, 20, 20) << 11;
                imm |= GETBITS(val, 21, 30) << 1;
                imm |= GETBITS(val, 31, 31) << 20;
        }
        print(" ");
        hex2str(imm);
        print(buffer);
    }
}

void disas_exe(){
    char* c = next_word(buffer);
    if(!*c || !*(c+1))
        return;
    unsigned adr = str2hex(c), val;
    while(true){
        print("[");
        hex2str(adr);
        print(buffer);
        print("] ");
        val = *((unsigned*)adr);
        print_int2inst(val);
        print("\n");
        adr += 4;
    }
}

void start(){
    // register unsigned cc = *((unsigned*)ADR_SERIAL_BUF);
    // asm("nop;nop;nop;nop;nop;");
    // *((char*)0x6000) = cc;
    print("Welcome to System on Cat!\n");
    print("Monitor v0.1\n");

    init();

    
    while(true){
        print(STR_PROMPT);
        get_line();
        switch(buffer[0]){
            case '\0':
                break;
            case 'J':
                jump_exe();
                break;
            case 'R':
                reg_exe();
                break;
            case 'E':
                edit_exe();
                break;
            case 'I':
                inst_exe();
                break;
            case 'V':
                view_exe();
                break;
            case 'D':
                disas_exe();
                break;
            default:
                // print help
                print_help();
        }
    }
}