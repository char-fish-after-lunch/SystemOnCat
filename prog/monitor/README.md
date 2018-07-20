## Monitor routine for System on Cat

Inspired by the monitor routine for 16-bit THINPAD.

### Features
* All the features the 16-bit THINPAD monitor has.
* An optional timer that can report the time used by a user process when it terminates and kill it when it runs out of time.

### Prerequisites

* 32-bit RISC-V GNU toolchain
* GNU make

### Build

Bare mainbody:
```
make CFLAGS=   
```

With interrupt and CSR functionalities:
```
make
```

### Notes

* Most arch-dependent configurations are placed in `arch.h`. You may modify it according to the architecture of your target platform if you are not building it for System on Cat.
* All input integers (including immediates and register ids) must be in the hexadecimal form with prefix `0x`.
* All input letters are case-sensitive. Input instructions must be lowercased.
