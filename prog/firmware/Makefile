ARCH_PREF := riscv32-unknown-elf-
GCC := $(ARCH_PREF)gcc
LD := $(ARCH_PREF)ld
OBJDUMP := $(ARCH_PREF)objdump
OBJCOPY := $(ARCH_PREF)objcopy
MARCH := rv32i
CFLAGS := -DWITH_SMP
START_ADR := 0xffffffc0

mastercat.bin: out.o
	$(OBJCOPY) -O binary out.o mastercat.bin

out.o: mastercat.o
	$(LD) -nostdlib -N -e _start -Ttext $(START_ADR) mastercat.o -o out.o

mastercat.o: mastercat.S
	$(GCC) -c -march=$(MARCH) $(CFLAGS) -fno-builtin -o mastercat.o mastercat.S

.PHONY: clean

clean:
	rm -rf out.o mastercat.o mastercat.bin
