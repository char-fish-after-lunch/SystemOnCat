import os

f = open("./interrupt/exception.bin", "r")
bs = f.read()

for i in range(len(bs)/4):
    z = [ord(k) for k in bs[i*4:(i+1)*4]][::-1]
    z = [hex(k).replace("0x","") for k in z]
    print("0x"+"".join(["0"+c if len(c)==1 else c for c in z]) + ".S(32.W),")