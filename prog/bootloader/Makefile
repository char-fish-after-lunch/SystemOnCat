ARCH_PREF := riscv32-unknown-elf-
GCC := $(ARCH_PREF)gcc
LD := $(ARCH_PREF)ld
OBJDUMP := $(ARCH_PREF)objdump
OBJCOPY := $(ARCH_PREF)objcopy
MARCH := rv32i
VA_BASE := 0xC0000000
PA_BOUND := 0x800000
CFLAGS := -DWITH_AT
START_ADR := 0x0

bootedcat.bin: out.o
	$(OBJCOPY) -O binary out.o bootedcat.bin
	truncate --size=4096 bootedcat.bin

out.o: bootasm.o bootedcat.o
	$(LD) -nostdlib -N -e _start -Ttext $(START_ADR) --no-relax bootasm.o bootedcat.o -o out.o

bootedcat.o: bootedcat.c types.h soc.h
	$(GCC) -c -march=$(MARCH) $(CFLAGS) -DVA_BASE=$(VA_BASE) -DPA_BOUND=$(PA_BOUND) -fno-builtin -o bootedcat.o bootedcat.c

bootasm.o: bootasm.S asm.h
	$(GCC) -c -march=$(MARCH) $(CFLAGS) -DVA_BASE=$(VA_BASE) -DPA_BOUND=$(PA_BOUND) -fno-builtin -o bootasm.o bootasm.S

.PHONY: clean

clean:
	rm -rf out.o bootedcat.o bootedcat.bin bootasm.o
