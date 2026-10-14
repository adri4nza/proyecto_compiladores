0:IN 0,0,0 
* lee un valor en el registro 0
1:ST 0,0(1)
* almacena ese valor en la direccion de memoria 0+reg[1]=0
2:IN 0,0,0
* lee un valor en el registro 0
3:ST 0,1(1)
* almacena r[0] en la direccion de memoria 1+reg[1]=1
4:LD 3,0(1)
* reg[3] = dMem[0+reg[1]=0]
5:LD 4,1(1)
* reg[3] = dMem[1+reg[1]=1]
6:ADD 2,3,4
* reg[2] = reg[3]+reg[4]
7:ST 2,2(1)
* dMem[2+reg[1]=2]=reg[2]
8:LD 0,2(1)
* reg[0] = dMem[2+reg[1]=2]
9: OUT 0,0,0
* imprimir valor r[0]
