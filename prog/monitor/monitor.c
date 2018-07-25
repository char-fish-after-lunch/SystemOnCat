#include "inst.h"
#include "arch.h"

#define STR_PROMPT ">>> "
#define bool int
#define true 1
#define false 0
#define BUFSIZE 64
#define REGSIZE 32
#define HEX "0123456789abcdef"

#define GETBITS(src, ld, rd) (((src) >> (ld)) & ((1 << ((rd) - (ld) + 1)) - 1))
#define SETBITS(dest, ld, rd, src) ((dest) | (GETBITS((src), 0, (rd) - (ld)) << (ld)))



#define GETCHAR_BLK(c) {while(!((*((unsigned*)ADR_SERIAL_BUF)) & 0xf0)); \
    (c) = *((char*)ADR_SERIAL_DAT); }
#define PUTCHAR(c) {while(!((*((unsigned*)ADR_SERIAL_BUF)) & 0xf)); \
    (*((char*)ADR_SERIAL_DAT) = (c));}

#if defined(WITH_CSR) && defined(WITH_IRQ) && defined(WITH_INTERRUPT)
char input_buffer[BUFSIZE];
int bufh, buft;

#define GETCHAR(c) {while(bufh == buft); \
    (c) = input_buffer[bufh ++]; \
    if(bufh == BUFSIZE) bufh = 0;}
#else
#define GETCHAR(c) {GETCHAR_BLK((c))}
#endif

extern void _trap_entry(void);
extern void _entry(unsigned adr);
extern void _exit(void);

void print(const char* str){
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

#if defined(WITH_CSR) && defined(WITH_INTERRUPT)
extern bool in_user;
extern unsigned time_count, time_lim; // secs
extern unsigned* trap_frame;

#define TRAP_REG(x) (trap_frame[(x) - 1])
#endif

void get_line(){
    int bn = -1;
    do{
        ++ bn;
        GETCHAR(buffer[bn]);
    } while(buffer[bn] != '\n');
    if(bn > 0 && buffer[bn - 1] == '\r')
        -- bn;
    buffer[bn] = '\0';
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
    print("V <x> <y>  - View y words that follow address x.\n");
    print("D <x> <y>  - Disassemble y instructions that follow address x.\n");
#if defined(WITH_CSR) && defined(WITH_INTERRUPT)
    print("T [<x>]    - Set time limit to x * 10 ms, "
        "or check the current time limit setting if x is not specified.\n");
#endif
}

#if defined(WITH_CSR) && defined(WITH_INTERRUPT)
void trap(){
#ifdef WITH_IRQ
    unsigned irq_source;
#endif
    unsigned cause = read_csr(mcause);
    bool ret = false;
    if((int)cause < 0){
        // asynchronous interrupt
        switch((cause << 1) >> 1){
            case INT_MTIMER:
                *((unsigned*)ADR_TMEH) = 0;
                *((unsigned*)ADR_TMEL) = 0;
                if(in_user){
                    ++ time_count;
                    // time is up
                    if(time_count >= time_lim)
                        ret = true;
                }
                break;
#ifdef WITH_IRQ
            case INT_MIRQ:
                irq_source = *((unsigned*)ADR_PLIC);
                switch(irq_source){
                    case IRQ_SERIAL:
                        while(*((unsigned*)ADR_SERIAL_BUF) & 0xf0){
                            input_buffer[buft ++] = *((char*)ADR_SERIAL_DAT);
                            if(buft == BUFSIZE)
                                buft = 0;
                        }
                        break;
                }
                *((unsigned*)ADR_PLIC) = irq_source;
                break;
#endif
            default:
                print("An unrecognized interrupt received!\n");
                print("Cause: ");
                print_hex(cause);
                print("\n");
                
                print("EPC: ");
                print_hex(read_csr(mepc));
                print("\n");
                ret = true;
        }
        clear_csr(mip, 1 << ((cause << 1) >> 1));
    } else{
        switch(cause){
            case EXC_INST_MISALIGN:
                print("Exception: instruction misaligned @ ");
                print_hex(read_csr(mtval));
                print("\n");
                ret = true;
                break;
            case EXC_ILLEGAL_INST:
                print("Exception: illegal instruction\n");
                ret = true;
                break;
            case EXC_LOAD_MISALIGN:
                print("Exception: load misaligned @ ");
                print_hex(read_csr(mtval));
                print("\n");
                break;
            case EXC_STORE_MISALIGN:
                print("Exception: store misaligned @ ");
                print_hex(read_csr(mtval));
                print("\n");
                ret = true;
                break;
#ifdef WITH_ECALL
            case EXC_ECALL:
                // check a1 (request code)
                switch(TRAP_REG(11)){
                    case ECALL_EXIT:
                        ret = true;
                        break;
                    case ECALL_PUTCHAR:
                        PUTCHAR(TRAP_REG(12));
                        break;
                    case ECALL_GETCHAR:
                        GETCHAR_BLK(TRAP_REG(10)); // a0 stores the return value
                        break;
                    default:
                        print("Unsupported request: ");
                        print_hex(TRAP_REG(11));
                        print("\n");
                        ret = true;
                }

                write_csr(mepc, read_csr(mepc) + 4); // for ecall epc points to itself
                break;
#endif
            default:
                print("An unrecognized exception received!\n");
                print("Cause: ");
                print_hex(cause);
                print("\n");
                
                print("EPC: ");
                print_hex(read_csr(mepc));
                print("\n");
                
                ret = true;
        }
    }
    if(ret && in_user){
        // kill the user process
        write_csr(mepc, _exit);
    }
}
#endif

void init(){
#if defined(WITH_CSR) && defined(WITH_INTERRUPT)
#ifdef WITH_IRQ
    bufh = buft = 0;
#endif

    // set up the interrupt stack
    write_csr(mscratch, ADR_KSTACK_TOP);

    // set up trap vector
    write_csr(mtvec, _trap_entry);
    set_csr(mstatus, 8);
    // timecmp = 125000 = clockfreq / 100
    // timer precision: 10ms
    *((unsigned*)ADR_CMPH) = 0;
    *((unsigned*)ADR_CMPL) = 125000;
    *((unsigned*)ADR_TMEH) = 0;
    *((unsigned*)ADR_TMEL) = 0;
#ifdef WITH_IRQ 
    set_csr(mie, (1 << INT_MTIMER) | (1 << INT_MIRQ));
#else
    set_csr(mie, (1 << INT_MTIMER));
#endif
    in_user = false;

    time_lim = 100; // initial time limit 1000 ms
#endif

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


#if defined(WITH_CSR) && defined(WITH_INTERRUPT)
    if(time_lim <= time_count){
        print("User process killed prematurely for running out of time.\n");
    }
    
    print("Time used: 10 * ");
    print_hex(time_count);
    print(" ms\n");

    print("Time limit: 10 * ");
    print_hex(time_lim);
    print(" ms\n");
#endif
}

void reg_exe(){
    int i;
    for(i = 0; i < REGSIZE; i ++){
        print("x(");
        print_hex(i);
        print(")  =  ");
        
        print_hex(regs[i]);
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
        print_hex(adr);
        print("] ");
        get_line();
        if(!*buffer || !*(buffer+1))
            break;
        val = str2hex(buffer);

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
        if(!INST_NAMES[i][j] && (!c[j] || c[j] == ' '))
            break;
    }
    if(i == INST_N)
        return 0;
    unsigned res = INST_CONFIG[i][1];
    unsigned inst_type = INST_CONFIG[i][0];
    unsigned tmp_val;
    bool re;
    if(inst_type == ITYPE_F)
        res = SETBITS(res, 7, 31, INST_CONFIG[i][2]);
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
    if(inst_type != ITYPE_R && inst_type != ITYPE_F){
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
        print_hex(adr);
        print("] ");
        get_line();
        val = inst2int(buffer);
        if(val == 0)
            break;

        *((unsigned*)adr) = val;
        adr += 4;
    }
}

void view_exe(){
    char* c = next_word(buffer);
    if(!*c || !*(c+1))
        return;
    unsigned adr = str2hex(c), val;
    c = next_word(c);
    if(!*c || !*(c+1))
        return;
    unsigned cnt = str2hex(c);
    while(cnt --){
        print("[");
        print_hex(adr);
        print("] ");
        val = *((unsigned*)adr);
        print_hex(val);
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
        if(itype == ITYPE_F && INST_CONFIG[i][2] != GETBITS(val, 7, 31))
            continue;
        break;
    }
    if(i == INST_N){
        // no instruction matched
        print("<Invalid>");
        return;
    }
    print(INST_NAMES[i]);
    if(itype == ITYPE_R || itype == ITYPE_I || itype == ITYPE_U || itype == ITYPE_J){
        print(" ");
        print_hex(GETBITS(val, 7, 11));
    }
    if(itype == ITYPE_R || itype == ITYPE_I || itype == ITYPE_S || itype == ITYPE_B){
        print(" ");
        print_hex(GETBITS(val, 15, 19));
    }
    if(itype == ITYPE_R || itype == ITYPE_S || itype == ITYPE_B){
        print(" ");
        print_hex(GETBITS(val, 20, 24));
    }
    unsigned imm = 0;
    if(itype != ITYPE_R && itype != ITYPE_F){
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
        print_hex(imm);
    }
}

#if defined(WITH_CSR) && defined(WITH_INTERRUPT)
void timelim_exe(){
    char* c = next_word(buffer);
    if(!*c || !*(c+1)){
        print("Time limit: 10 * ");
        print_hex(time_lim);
        print(" ms\n");
    } else
        time_lim = str2hex(c);
}
#endif

void disas_exe(){
    char* c = next_word(buffer);
    if(!*c || !*(c+1))
        return;
    unsigned adr = str2hex(c), val;
    c = next_word(c);
    if(!*c || !*(c+1))
        return;
    unsigned cnt = str2hex(c);
    while(cnt --){
        print("[");
        print_hex(adr);
        print("] ");
        val = *((unsigned*)adr);
        print_int2inst(val);
        print("\n");
        adr += 4;
    }
}

void start(){
    print("Welcome to System on Cat!\n");
    print("Monitor v0.1\n");
    print("Build specs:\n");
    print("  WITH_CSR = ");
#ifdef WITH_CSR
    print("on\n");
#else
    print("off\n");
#endif
    print("  WITH_INTERRUPT = ");
#ifdef WITH_INTERRUPT
    print("on\n");
#else
    print("off\n");
#endif
    print("  WITH_IRQ = ");
#ifdef WITH_IRQ
    print("on\n");
#else
    print("off\n");
#endif
    print("  WITH_ECALL = ");
#ifdef WITH_ECALL
    print("on\n");
#else
    print("off\n");
#endif

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
#if defined(WITH_CSR) && defined(WITH_INTERRUPT)
            case 'T':
                timelim_exe();
                break;
#endif
            default:
                // print help
                print_help();
        }
    }
}