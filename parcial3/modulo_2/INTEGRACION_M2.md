# Integración — Módulo 2 (Generación de código TM)

Notas para los responsables de **M3** (simulador TM) y **M4** (IDE + servidor puente) sobre lo que el generador emite y necesita de ellos.

---

## ⚠️ ALERTA DE INTEGRACIÓN — instrucción nueva `OUTC`

```
⚠️  ALERTA DE INTEGRACIÓN
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Este cambio afecta al Módulo 3 y al Módulo 4.
Impacto detectado: el generador emite una instrucción TM nueva, OUTC,
para `write "cadena"`. El TM.C original la rechaza con "Illegal opcode".
Acción requerida: M3 debe implementarla en el simulador y M4 debe
mostrarla como carácter en la consola del IDE.
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

### Por qué

`write "hola"` es válido en Tiny++ (las cadenas son literales de solo lectura). La TM original solo tiene `OUT`, que imprime **enteros**: no hay forma de distinguir `write "h"` de `write 104`. Se decidió agregar una instrucción de salida de carácter en lugar de imprimir códigos numéricos.

### Especificación

| Campo | Valor |
|-------|-------|
| Mnemónico | `OUTC` |
| Clase | **RR/RO**, igual que `OUT` |
| Formato en el `.tm` | `N:       OUTC       r,0,0` (`s` y `t` se ignoran) |
| Semántica | imprime el carácter cuyo código ASCII es `reg[r]`, **sin** salto de línea |
| Registro que usa el generador | siempre `r = 0` (AC) |

Ejemplo real (programa `int a; write "ok"`; la cadena queda en `dMem[1..3]`, después de `a`):

```
*      cadena "ok" en dMem[1]
2:       LDC       0,2(0)      cadena: largo 2
3:       ST       0,1(5)      cadena: guardo el largo
4:       LDC       0,111(0)      cadena: caracter o
5:       ST       0,2(5)      cadena: guardo el caracter
6:       LDC       0,107(0)      cadena: caracter k
7:       ST       0,3(5)      cadena: guardo el caracter
...
8:       LD       0,2(5)      escribir: cargo el caracter o
9:       OUTC       0,0,0      escribir: salida del caracter
10:       LD       0,3(5)      escribir: cargo el caracter k
11:       OUTC       0,0,0      escribir: salida del caracter
```

### Qué tiene que hacer M3 (`simulador_tm/TM.C`)

1. Agregar `opOUTC` al `enum` de opcodes **dentro del bloque RR**, antes de `opRRLim` (por ejemplo, justo después de `opOUT`), para que `opClass()` la clasifique como RR.
2. Agregar `"OUTC"` en la **misma posición** de `opCodeTab[]`.
   - Ojo: `readInstructions` compara con `strncmp(..., 4)`. `"OUT"` y `"OUTC"` se distinguen bien porque difieren en el 4.º carácter, así que no hay ambigüedad.
3. En `stepTM()`, el `case opOUTC:` imprime `(char) reg[r]`.
   - En modo `-s` (servidor), la salida debe viajar al IDE igual que la de `OUT`, marcando que es un carácter.
4. El JSON de estado (Contrato A) **no cambia de claves**: `memoria_instrucciones[i].op` simplemente puede valer `"OUTC"`.

### Qué tiene que hacer M4

1. **Consola del IDE:** una salida de `OUTC` se agrega como carácter a la línea actual. Una de `OUT` se sigue mostrando como número.
2. **Panel de instrucciones:** mostrar `OUTC` como cualquier otro opcode RO.
3. **`servidor_puente/index.js` (`extractTmCode`):** el fallback que filtra por `^\s*\d+:` ya acepta las líneas de `OUTC`. No hace falta tocar nada.
4. Si se reutiliza el parser JS de `tmsimv1.html`, agregarle `OUTC` a su tabla de opcodes.

### Cómo se guardan las cadenas en memoria

Cada literal distinto se reserva **una sola vez** en la memoria de datos, justo **después de las variables globales**, con este layout:

```
dMem[base]       = largo
dMem[base + 1]   = código ASCII del 1.er carácter
...
dMem[base + n]   = código ASCII del n-ésimo carácter
```

El preludio del programa inicializa esas celdas (`LDC` + `ST`) antes de la primera sentencia, así que en el depurador se ven las cadenas cargadas en la memoria de datos desde el inicio. `write "..."` lee cada carácter de ahí (`LD`) y lo emite con `OUTC`.

---

## Otros cambios del generador que conviene conocer

| Cambio | Efecto para M3/M4 |
|--------|-------------------|
| stdout lleva **solo** el `.tm` (sin cabeceras `------ CODIGO OBJETO ...`) | El `.tm` se carga directo en TM.C. El recorte por cabeceras de `extractTmCode` ya no encuentra marcas y cae en su fallback, que funciona igual. |
| `bool` es un entero 0/1 | `write` de un `bool` imprime `0` o `1` con `OUT`. |
| Arreglos: celda = `base + índice + GP` | En la memoria de datos se ven contiguos, justo donde la tabla de símbolos ubicó el arreglo. Sin chequeo de límites. |
| `if` sin `else` ya no reserva un hueco vacío | Antes, el hueco quedaba como `HALT` y el programa se detenía al terminar el `then`. |

## Cómo verificar

```bash
bash parcial3/modulo_2/pruebas_m2.sh
```

Compila cada caso, ejecuta el `.tm` en `tm_ref.py` y compara las salidas. `tm_ref.py` es un intérprete de referencia que ya entiende `OUTC`, y sirve de oráculo para M3 mientras porta TM.C.
