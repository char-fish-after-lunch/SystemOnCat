#define ADR_SERIAL 0xf000
#define PUTCHAR(c) (*((char*)ADR_SERIAL) = (c))
#define GETCHAR(c) (c = *((char*)ADR_SERIAL))
#define STR_PROMPT ">>> "
#define bool int
#define true 1
#define false 0
#define BUFSIZE 64
#define REGSIZE 32
#define HEX "0123456789abcdef"

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

void inst_exe(){

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

void disas_exe(){

}

void start(){
    init();

    print("Welcome to System on Cat!\n");
    print("Monitor v0.1\n");
    
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