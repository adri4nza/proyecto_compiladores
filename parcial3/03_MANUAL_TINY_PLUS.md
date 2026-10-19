# Manual de Referencia — Lenguaje Tiny++
### Universidad Nacional Experimental del Táchira — Compiladores e Intérpretes

---

## Tabla de Contenidos

1. [Introducción](#1-introducción)
2. [Sintaxis Fundamental](#2-sintaxis-fundamental)
3. [Tipos de Datos y Declaraciones](#3-tipos-de-datos-y-declaraciones)
4. [Arreglos](#4-arreglos)
5. [Operadores](#5-operadores)
6. [Estructuras de Control](#6-estructuras-de-control)
7. [Entrada y Salida](#7-entrada-y-salida)
8. [Funciones y Procedimientos](#8-funciones-y-procedimientos)
9. [Limitaciones de esta Versión](#9-limitaciones-de-esta-versión)
10. [Ejemplos Completos](#10-ejemplos-completos)

---

## 1. Introducción

**Tiny++** es una extensión del lenguaje **Tiny** (del libro *Compiler Construction: Principles and Practice*, Kenneth C. Louden). El Tiny original es un lenguaje mínimo con un solo tipo de dato implícito (entero) y sin declaraciones de variables.

Tiny++ lo convierte en un **lenguaje fuertemente tipado** al agregar:

| Característica | Tiny original | Tiny++ |
|---|---|---|
| Tipado | Implícito (todo es entero) | Explícito y obligatorio (`int`, `bool`, `string`) |
| Declaración de variables | No existe | Obligatoria antes del primer uso |
| Arreglos | No soportado | `int A[N]`, con índice calculado |
| Subrutinas | No soportado | `function` con retorno, `procedure` sin retorno |
| Operadores lógicos | No soportado | `and`, `or`, `not` |
| Verificación semántica | Ninguna | Tipos, declaraciones y compatibilidad |

El compilador genera código ensamblador para la **Tiny Machine (TM)**, una máquina virtual de 8 registros y 1024 posiciones de memoria.

---

## 2. Sintaxis Fundamental

### Comentarios

Los comentarios van entre llaves `{ }` y no se anidan:

```
{ Esto es un comentario de una línea }

{
  Esto también es un comentario
  que ocupa múltiples líneas
}
```

### Identificadores

Un identificador empieza con una **letra** y puede contener letras, dígitos y guiones bajos:

```
x          contador    suma_total    A1    miVariable
```

Las **palabras reservadas** no pueden usarse como identificadores:
`int` `bool` `string` `true` `false` `if` `then` `else` `end` `repeat` `until`
`read` `write` `function` `procedure` `return` `and` `or` `not`

### El Punto y Coma `;`

El `;` es un **separador de sentencias**, no un terminador. La última sentencia de una secuencia **no necesita** punto y coma (aunque tenerlo no es un error):

```
{ Correcto: ; separa las sentencias entre sí }
int x;
int y;
x := 10;
y := x + 5;
write y

{ También correcto: ; final es tolerado }
write y;
```

---

## 3. Tipos de Datos y Declaraciones

Toda variable debe declararse **antes de su primer uso**. Las variables globales se declaran en el cuerpo principal del programa; las variables locales, dentro de la función o procedimiento correspondiente.

### `int` — Entero

Valores enteros en el rango de un entero de 32 bits con signo.

```
int contador;
int resultado;
int i;
```

### `bool` — Booleano

Solo puede tener los valores literales `true` o `false`. Internamente la TM lo representa como 1 (verdadero) y 0 (falso).

```
bool activo;
bool esValido;

activo  := true;
esValido := 0 < contador;   { una expresión relacional produce bool }
```

### `string` — Cadena de Texto

Solo admite **literales de texto** entre comillas dobles. Las cadenas son de **solo lectura** en esta versión: pueden imprimirse con `write` pero no pueden asignarse a variables ni leerse con `read`.

```
string saludo;   { declaración válida }

write "Hola mundo"   { uso correcto: literal directo en write }
```

> **Nota:** `read` y la asignación a variables de tipo `string` no están soportados en esta versión del compilador.

---

## 4. Arreglos

Tiny++ soporta **arreglos unidimensionales** de tamaño fijo conocido en tiempo de compilación. El índice empieza en 0.

### Declaración

```
int  datos[10];     { arreglo de 10 enteros }
bool flags[5];      { arreglo de 5 booleanos }
```

El tamaño debe ser un literal entero positivo.

### Acceso a elementos

El índice puede ser cualquier expresión entera, incluyendo expresiones compuestas:

```
datos[0]      := 42;
datos[i]      := datos[i] + 1;
datos[i*2+1]  := 100;    { índice calculado en tiempo de ejecución }
```

### Paso a subrutinas

Un arreglo se pasa a una función o procedimiento declarando el parámetro con `[]` sin tamaño:

```
function int suma(int A[], int n) ... end
```

Dentro de la subrutina, el arreglo se accede por índice normalmente.

---

## 5. Operadores

### Aritméticos (producen `int`)

| Operador | Operación | Ejemplo |
|----------|-----------|---------|
| `+` | Suma | `a + b` |
| `-` | Resta | `a - b` |
| `*` | Multiplicación | `a * b` |
| `/` | División entera | `a / b` |

Ambos operandos deben ser `int`. La división entera trunca hacia cero.

> **Atención:** Tiny++ **no tiene unario `-`**. Para negar un número usa `0 - expresion`.

### Relacionales (producen `bool`)

| Operador | Operación | Ejemplo |
|----------|-----------|---------|
| `<` | Menor que | `a < b` |
| `=` | Igual que | `a = b` |

Se comparan valores del **mismo tipo** (`int` con `int`, o `bool` con `bool`). El resultado es siempre `bool`.

> **Importante:** `=` es **comparación de igualdad**, no asignación. La asignación usa `:=`.

### Lógicos (producen `bool`)

| Operador | Aridad | Operación | Ejemplo |
|----------|--------|-----------|---------|
| `and` | Binario | Conjunción | `a and b` |
| `or` | Binario | Disyunción | `a or b` |
| `not` | Unario | Negación | `not a` |

Ambos operandos de `and` y `or` deben ser `bool`. El operando de `not` también debe ser `bool`.

### Precedencia (de mayor a menor)

| Nivel | Operadores |
|-------|-----------|
| 1 (mayor) | `not` (unario) |
| 2 | `*` `/` |
| 3 | `+` `-` |
| 4 | `<` `=` |
| 5 | `and` |
| 6 (menor) | `or` |

Usa paréntesis `( )` para controlar el orden de evaluación explícitamente.

---

## 6. Estructuras de Control

### Condicional — `if`

```
{ Solo rama then }
if <condición_bool> then
  <sentencias>
end

{ Con rama else }
if <condición_bool> then
  <sentencias_si_verdadero>
else
  <sentencias_si_falso>
end
```

La condición **debe ser de tipo `bool`**. Se pueden anidar `if` dentro de cualquier rama.

**Ejemplo:**

```
if x < 0 then
  write 0
else
  write x
end
```

### Ciclo — `repeat...until`

```
repeat
  <sentencias>
until <condición_bool>
```

El cuerpo se ejecuta **al menos una vez**. Después de cada iteración se evalúa la condición: si es `true`, el ciclo **termina**; si es `false`, se repite.

**Ejemplo — contar de 1 a 5:**

```
int i;
i := 1;
repeat
  write i;
  i := i + 1
until 5 < i
```

> Para un ciclo de N iteraciones comenzando en 0 la condición típica es `N < i` o `i = N`.

---

## 7. Entrada y Salida

### `read` — Lectura de enteros

Lee un entero desde la entrada estándar (o el diálogo del IDE) y lo almacena en una variable:

```
int n;
read n;            { lee un entero y lo guarda en n }
read A[i];         { lee un entero y lo guarda en A[i] }
```

> `read` solo acepta variables de tipo `int`. No puede leer en `bool` ni en `string`.

### `write` — Escritura de valores

Escribe el valor de una expresión en la consola del IDE:

```
write 42;            { imprime el literal 42 }
write n;             { imprime el valor de n }
write n * 2;         { imprime el resultado de la expresión }
write "Resultado:";  { imprime un literal de cadena }
write true;          { imprime 1 }
```

`write` acepta expresiones de tipo `int`, `bool` y **literales** de tipo `string`.

---

## 8. Funciones y Procedimientos

### Diferencia fundamental

| | `function` | `procedure` |
|---|---|---|
| Valor de retorno | **Sí** — debe declararse el tipo | **No** — no retorna nada |
| Uso en expresión | Sí (`resultado := miFuncion(...)`) | No |
| `return` | Obligatorio | Opcional (terminar anticipadamente) |

### Sintaxis de `function`

```
function <tipo_retorno> <nombre>(<parámetros>)
  <cuerpo>
end
```

**Ejemplo:**

```
function int maximo(int a, int b)
  int m;
  if a < b then
    m := b
  else
    m := a
  end;
  return m
end
```

### Sintaxis de `procedure`

```
procedure <nombre>(<parámetros>)
  <cuerpo>
end
```

**Ejemplo:**

```
procedure imprimirLinea(int n)
  int i;
  i := 0;
  repeat
    write n;
    i := i + 1
  until i = 5
end
```

### Parámetros

Los parámetros se declaran igual que las variables, separados por `,`:

```
function int suma3(int a, int b, int c)
  return a + b + c
end
```

Para pasar un **arreglo completo** como parámetro, el tipo lleva `[]` sin tamaño:

```
function int primerElemento(int A[])
  return A[0]
end
```

### Llamadas

Una función se llama dentro de una expresión:

```
int r;
r := maximo(10, 25);
write suma3(1, 2, 3)
```

Un procedimiento se llama como sentencia independiente:

```
imprimirLinea(7)
```

### Alcance (scopes)

- Variables declaradas en el cuerpo principal son **globales** — visibles en todo el programa.
- Variables declaradas dentro de una función o procedimiento son **locales** — solo existen durante esa llamada.
- Una variable local puede tener el mismo nombre que una global; dentro de la subrutina la local **sombrea** a la global.

### Llamadas anticipadas (forward calls)

Una función puede llamar a otra declarada **más abajo** en el código. El compilador registra todas las firmas antes de verificar los cuerpos.

---

## 9. Limitaciones de esta Versión

| Limitación | Descripción |
|---|---|
| `string` es solo lectura | Se puede imprimir un literal pero no asignar a variable ni leer con `read` |
| `read` solo acepta `int` | La instrucción IN de la TM solo maneja enteros |
| Sin operador unario `-` | Usar `0 - expresion` para negar |
| Sin `>`, `<=`, `>=`, `<>` | Solo existen `<` y `=`; derivar el resto: `b < a` en vez de `a > b` |
| Sin verificación de límites en arreglos | Un índice fuera de rango produce `DMEM_ERR` en tiempo de ejecución |
| Recursión soportada pero sin optimización de cola | Programas muy recursivos pueden agotar la memoria de la TM |

---

## 10. Ejemplos Completos

Los siguientes programas pueden copiarse directamente en el IDE y ejecutarse paso a paso con el depurador.

---

### Ejemplo 1 — Factorial Iterativo

Calcula `n!` para un entero positivo leído por el usuario.

```
{ Calculo del factorial de N }
int n;
int fact;

read n;

if 0 < n then
  fact := 1;
  repeat
    fact := fact * n;
    n := n - 1
  until n = 0;
  write fact
else
  write 1
end
```

**Prueba sugerida:** ingresar `5` → resultado esperado: `120`.

---

### Ejemplo 2 — Suma de Arreglo con Función

Llena un arreglo con los primeros N cuadrados y los suma usando una función.

```
{ Suma de cuadrados usando arreglo y funcion }
int N[5];
int total;
int i;

{ Llenar el arreglo con los cuadrados de 1..5 }
i := 0;
repeat
  N[i] := (i + 1) * (i + 1);
  i := i + 1
until i = 5;

{ Funcion que suma todos los elementos de un arreglo de tamano fijo }
function int sumarTodo(int A[], int tam)
  int acumulador;
  int j;
  acumulador := 0;
  j := 0;
  repeat
    acumulador := acumulador + A[j];
    j := j + 1
  until j = tam;
  return acumulador
end

total := sumarTodo(N, 5);
write total
```

**Resultado esperado:** `1 + 4 + 9 + 16 + 25 = 55`.

---

### Ejemplo 3 — Clasificador con Booleanos y Funciones

Determina si un número es positivo, negativo o cero, usando variables booleanas y un procedimiento de impresión.

```
{ Clasifica un numero leido por el usuario }

function bool esPositivo(int n)
  return 0 < n
end

function bool esCero(int n)
  return n = 0
end

procedure imprimirResultado(bool pos, bool cero)
  if pos then
    write 1     { 1 = positivo }
  else
    if cero then
      write 0   { 0 = cero }
    else
      write 0   { negativo: mostramos 0 (no hay unario -) }
    end
  end
end

int numero;
bool positivo;
bool nulo;

read numero;

positivo := esPositivo(numero);
nulo     := esCero(numero);

imprimirResultado(positivo, nulo)
```

**Prueba sugerida:** ingresar `7` → imprime `1` (positivo). Ingresar `0` → imprime `0` (cero).

---

### Ejemplo 4 — Fibonacci Recursivo

Calcula el N-ésimo número de Fibonacci con una función recursiva.

```
{ N-esimo numero de Fibonacci (recursivo) }

function int fib(int n)
  if n < 2 then
    return n
  else
    return fib(n - 1) + fib(n - 2)
  end
end

int n;
int resultado;

read n;
resultado := fib(n);
write resultado
```

**Prueba sugerida:** ingresar `10` → resultado esperado: `55`.

> **Nota:** Para valores grandes de N (> 20) el cálculo recursivo puede ser lento, pues la TM ejecuta instrucción por instrucción. Usar en el depurador con valores pequeños (≤ 10).

---

*Manual generado como documentación oficial del proyecto Tiny++ — UNET, Compiladores e Intérpretes.*
