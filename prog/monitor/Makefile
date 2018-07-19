ARCH_PREF := riscv32-unknown-elf-
GCC := $(ARCH_PREF)gcc
LD := $(ARCH_PREF)ld
OBJDUMP := $(ARCH_PREF)objdump
OBJCOPY := $(ARCH_PREF)objcopy
MARCH := rv32i
CFLAGS := -DWITH_CSR -DWITH_INTERRUPT

all: disas.S monitor.bin

disas.S: out.o
	$(OBJDUMP) -S out.o > disas.S

monitor.bin: out.o
	$(OBJCOPY) -O binary out.o monitor.bin
	./align.sh monitor.bin

out.o: entry.o monitor.o
	$(LD) -nostdlib -N -e _start -Ttext 0x0 entry.o monitor.o -o out.o

entry.o: entry.S
	$(GCC) -c -march=$(MARCH) $(CFLAGS) -fno-builtin -o entry.o entry.S

monitor.o: monitor.c inst.h arch.h
	$(GCC) -c -march=$(MARCH) $(CFLAGS) -fno-builtin -o monitor.o monitor.c

clean:
	rm -rf *.o
	rm -rf monitor.bin
	rm -rf disas.S

.PHONY: all clean

