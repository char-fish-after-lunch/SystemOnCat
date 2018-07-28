#ifndef INST_H
#define INST_H

#define INST_LUI 0
#define INST_AUIPC 1
#define INST_JAL 2
#define INST_JALR 3
#define INST_BEQ 4
#define INST_BNE 5
#define INST_BLT 6
#define INST_BGE 7
#define INST_BLTU 8
#define INST_BGEU 9
#define INST_LB 10
#define INST_LH 11
#define INST_LW 12
#define INST_LBU 13
#define INST_LHU 14
#define INST_SB 15
#define INST_SH 16
#define INST_SW 17
#define INST_ADDI 18
#define INST_SLTI 19
#define INST_SLTIU 20
#define INST_XORI 21
#define INST_ORI 22
#define INST_ANDI 23
#define INST_SLLI 24
#define INST_SRLI 25
#define INST_SRAI 26
#define INST_ADD 27
#define INST_SUB 28
#define INST_SLL 29
#define INST_SLT 30
#define INST_SLTU 31
#define INST_XOR 32
#define INST_SRL 33
#define INST_SRA 34
#define INST_OR 35
#define INST_AND 36
#define INST_ECALL 37


#define INST_N 38

#define ITYPE_R 0
#define ITYPE_I 1
#define ITYPE_S 2
#define ITYPE_B 3
#define ITYPE_U 4
#define ITYPE_J 5
#define ITYPE_F 6

#define MAX_INST_NAME_LEN 7

const char INST_NAMES[INST_N][MAX_INST_NAME_LEN] = 
{
    "lui",
    "auipc",
    "jal",
    "jalr",
    "beq",
    "bne",
    "blt",
    "bge",
    "bltu",
    "bgeu",
    "lb",
    "lh",
    "lw",
    "lbu",
    "lhu",
    "sb",
    "sh",
    "sw",
    "addi",
    "slti",
    "sltiu",
    "xori",
    "ori",
    "andi",
    "slli",
    "srli",
    "srai",
    "add",
    "sub",
    "sll",
    "slt",
    "sltu",
    "xor",
    "srl",
    "sra",
    "or",
    "and",
    "ecall"
};


const unsigned INST_CONFIG[INST_N][4] = {
    // type, primary opcode, funct3, funct7
    {ITYPE_U, 0b0110111, 0, 0},
    {ITYPE_U, 0b0010111, 0, 0},
    {ITYPE_J, 0b1101111, 0, 0},
    {ITYPE_I, 0b1100111, 0b000, 0},
    {ITYPE_B, 0b1100011, 0b000, 0},
    {ITYPE_B, 0b1100011, 0b001, 0},
    {ITYPE_B, 0b1100011, 0b100, 0},
    {ITYPE_B, 0b1100011, 0b101, 0},
    {ITYPE_B, 0b1100011, 0b110, 0},
    {ITYPE_B, 0b1100011, 0b111, 0},
    {ITYPE_I, 0b0000011, 0b000, 0},
    {ITYPE_I, 0b0000011, 0b001, 0},
    {ITYPE_I, 0b0000011, 0b010, 0},
    {ITYPE_I, 0b0000011, 0b100, 0},
    {ITYPE_I, 0b0000011, 0b101, 0},
    {ITYPE_S, 0b0100011, 0b000, 0},
    {ITYPE_S, 0b0100011, 0b001, 0},
    {ITYPE_S, 0b0100011, 0b010, 0},
    {ITYPE_I, 0b0010011, 0b000, 0},
    {ITYPE_I, 0b0010011, 0b010, 0},
    {ITYPE_I, 0b0010011, 0b011, 0},
    {ITYPE_I, 0b0010011, 0b100, 0},
    {ITYPE_I, 0b0010011, 0b110, 0},
    {ITYPE_I, 0b0010011, 0b111, 0},
    {ITYPE_R, 0b0010011, 0b001, 0b0000000},
    {ITYPE_R, 0b0010011, 0b101, 0b0000000},
    {ITYPE_R, 0b0010011, 0b101, 0b0100000},
    {ITYPE_R, 0b0110011, 0b000, 0b0000000},
    {ITYPE_R, 0b0110011, 0b000, 0b0100000},
    {ITYPE_R, 0b0110011, 0b001, 0b0000000},
    {ITYPE_R, 0b0110011, 0b010, 0b0000000},
    {ITYPE_R, 0b0110011, 0b011, 0b0000000},
    {ITYPE_R, 0b0110011, 0b100, 0b0000000},
    {ITYPE_R, 0b0110011, 0b101, 0b0000000},
    {ITYPE_R, 0b0110011, 0b101, 0b0100000},
    {ITYPE_R, 0b0110011, 0b110, 0b0000000},
    {ITYPE_R, 0b0110011, 0b111, 0b0000000},
    {ITYPE_F, 0b1110011, 0, 0}
};

#endif
