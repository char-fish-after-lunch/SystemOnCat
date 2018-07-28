## Meownitor: monitor routine for System on Cat

Inspired by the monitor routine for 16-bit THINPAD.

### Features
* All the features the 16-bit THINPAD monitor has.
* An optional timer that can report the time used by a user process when it terminates and kill it when it runs out of time.
* The optional choice of using the IRQ mechanism for serial inputting.
* Optional environment calls that allow user processes to send requests to and receive responses from the monitor.

### Prerequisites

* 32-bit RISC-V GNU toolchain
* GNU make

### Build

Bare mainbody:
```
make CFLAGS=   
```

With interrupt functionalities:
```
make CFLAGS="-DWITH_CSR -DWITH_INTERRUPT"
```

With interrupt and environment call (system call) functionalities:
```
make CFLAGS="-DWITH_CSR -DWITH_INTERRUPT -DWITH_ECALL"
```

With interrupt and IRQ functionalities:
```
make CFLAGS="-DWITH_CSR -DWITH_INTERRUPT -DWITH_IRQ"
```

With full functionalities:
```
make
```

To create an image `bootable.bin` with BootedCat that can be booted by System on Cat with MasterCat:
```
make START_ADR=<where to put it in memory> bootable.bin
```

### Notes

* Most arch-dependent configurations are placed in `arch.h`. You may modify it according to the architecture of your target platform if you are not building it for System on Cat.
* All input integers (including immediates and register ids) must be in the hexadecimal form with prefix `0x`. Spaces shall be used to separate the operands (no comma).
* All input letters are case-sensitive. Input instructions must be lowercased.
* The user process is invoked with the return address placed in register `ra` (`x1`) and could therefore exit with `jalr zero, ra, 0`. If environment call functionalities are available, a user process may also exit by sending an `exit` environment call.
* Environment calls:
    * Request code in `a1`, return value in `a0`, arguments in `a2`, `a3`, ...
    * Requests:
        * `exit`: exit the process and return to the monitor (`a1 = 0`)
        * `putchar`: write a character to the serial port (`a1 = 1`, character in `a2`)
        * `getchar`: read a character from the serial port (`a1 = 2`, result in `a0`)


