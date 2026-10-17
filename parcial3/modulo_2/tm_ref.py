#!/usr/bin/env python3
"""
tm_ref.py -- Interprete de referencia de la Tiny Machine, SOLO para pruebas.

Reproduce la semantica de parcial3/TM.C (Louden) lo justo para verificar
el codigo que genera el Modulo 2 sin depender de gcc ni del simulador del
Modulo 3. No es el simulador del proyecto: no habla los contratos A ni C.

Estado inicial (igual que TM.C): registros en 0, dMem[0] = 1023, resto en 0.
Carga (igual que readInstructions): ignora lineas vacias y las que empiezan
por '*'; cualquier otra linea debe ser 'N: OP r,s,t' o 'N: OP r,d(s)'.

USO
    python3 tm_ref.py PROGRAMA.tm [ENTRADA ...]

Imprime una linea por cada OUT (el entero) y por cada OUTC (el caracter
entre comillas simples, p. ej. 'h'). Codigo de salida:
    0 = HALT   2 = .tm no cargable   3 = error de ejecucion (IMEM/DMEM/
    division por cero/entrada agotada/limite de pasos)
"""
import re
import sys

IADDR_SIZE = 1024
DADDR_SIZE = 1024
PC = 7
LIMITE_PASOS = 200000

# OUTC no existe en el TM.C original: es la salida de caracter que agrega el
# Modulo 2 para write "cadena" (ver INTEGRACION_M2.md)
OPS_RO = {"HALT", "IN", "OUT", "OUTC", "ADD", "SUB", "MUL", "DIV"}
OPS_RM = {"LD", "ST"}
OPS_RA = {"LDA", "LDC", "JLT", "JLE", "JGT", "JGE", "JEQ", "JNE"}

RE_RO = re.compile(r"^\s*(\d+):\s*([A-Z]+)\s+(-?\d+)\s*,\s*(-?\d+)\s*,\s*(-?\d+)")
RE_RM = re.compile(r"^\s*(\d+):\s*([A-Z]+)\s+(-?\d+)\s*,\s*(-?\d+)\s*\(\s*(-?\d+)\s*\)")


class ErrorTM(Exception):
    pass


def cargar(texto):
    imem = {}
    for num, linea in enumerate(texto.splitlines(), 1):
        if not linea.strip() or linea.lstrip().startswith("*"):
            continue
        m = RE_RM.match(linea) or RE_RO.match(linea)
        if not m:
            raise ErrorTM("Bad location en la linea %d: %r" % (num, linea))
        loc, op, a, b, c = m.groups()
        loc = int(loc)
        if op not in OPS_RO | OPS_RM | OPS_RA:
            raise ErrorTM("Opcode desconocido en la linea %d: %s" % (num, op))
        if not 0 <= loc < IADDR_SIZE:
            raise ErrorTM("Localidad fuera de rango en la linea %d" % num)
        imem[loc] = (op, int(a), int(b), int(c))
    return imem


def ejecutar(imem, entradas):
    reg = [0] * 8
    dmem = [0] * DADDR_SIZE
    dmem[0] = DADDR_SIZE - 1
    salidas = []
    entradas = list(entradas)

    def direccion(d):
        if not 0 <= d < DADDR_SIZE:
            raise ErrorTM("DMEM_ERR: direccion %d" % d)
        return d

    for _ in range(LIMITE_PASOS):
        pc = reg[PC]
        if not 0 <= pc < IADDR_SIZE:
            raise ErrorTM("IMEM_ERR: PC = %d" % pc)
        op, r, s, t = imem.get(pc, ("HALT", 0, 0, 0))
        reg[PC] = pc + 1

        if op == "HALT":
            return salidas
        if op == "IN":
            if not entradas:
                raise ErrorTM("IN sin entrada disponible (PC = %d)" % pc)
            reg[r] = entradas.pop(0)
        elif op == "OUT":
            salidas.append(str(reg[r]))
        elif op == "OUTC":
            salidas.append("'%s'" % chr(reg[r]))
        elif op == "ADD":
            reg[r] = reg[s] + reg[t]
        elif op == "SUB":
            reg[r] = reg[s] - reg[t]
        elif op == "MUL":
            reg[r] = reg[s] * reg[t]
        elif op == "DIV":
            if reg[t] == 0:
                raise ErrorTM("ZERO_DIVIDE (PC = %d)" % pc)
            reg[r] = int(reg[s] / reg[t])   # TM.C trunca hacia cero
        elif op == "LD":
            reg[r] = dmem[direccion(s + reg[t])]
        elif op == "ST":
            dmem[direccion(s + reg[t])] = reg[r]
        elif op == "LDA":
            reg[r] = s + reg[t]
        elif op == "LDC":
            reg[r] = s
        else:
            salta = {
                "JLT": reg[r] < 0, "JLE": reg[r] <= 0,
                "JGT": reg[r] > 0, "JGE": reg[r] >= 0,
                "JEQ": reg[r] == 0, "JNE": reg[r] != 0,
            }[op]
            if salta:
                reg[PC] = s + reg[t]
    raise ErrorTM("Limite de %d pasos alcanzado (bucle infinito?)" % LIMITE_PASOS)


def main(argv):
    if len(argv) < 2:
        sys.stderr.write(__doc__)
        return 2
    try:
        with open(argv[1]) as f:
            imem = cargar(f.read())
    except ErrorTM as e:
        sys.stderr.write(str(e) + "\n")
        return 2
    try:
        salidas = ejecutar(imem, [int(x) for x in argv[2:]])
    except ErrorTM as e:
        sys.stderr.write(str(e) + "\n")
        return 3
    for v in salidas:
        print(v)
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
