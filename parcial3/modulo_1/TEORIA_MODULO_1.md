# Teoría y Funcionalidad — Módulo 1
## Analizador Frontend y Verificador Semántico del Compilador Tiny++

**Asignatura:** Compiladores e Intérpretes — UNET
**Módulo:** 1 de 4 · **Peso en la nota:** 30%
**Tecnología:** Java + JFlex (lexer) + CUP (parser LALR)

> Este documento existe porque la evaluación es presencial y **cada integrante debe poder explicar todo el proyecto**, incluido lo que no programó. Si algún integrante no puede explicar una parte, el equipo pierde como mínimo el 50% de la nota. Lee la sección 8 antes de la defensa.

---

## 1. Qué es el Módulo 1 y dónde encaja

El Módulo 1 es el **frente del compilador**: la parte que lee el texto que escribió el programador y decide si ese texto es un programa Tiny++ válido. No genera ni una sola instrucción de máquina — eso es trabajo del Módulo 2.

### El pipeline completo del proyecto

```
     archivo.tny   (texto que escribe el usuario en el IDE)
          │
┌─────────┼──────────────────────────────────────────────┐
│         ▼                                   MÓDULO 1   │
│   ┌───────────────┐                                    │
│   │ ANÁLISIS      │  Lexico.java (generado por JFlex)  │
│   │ LÉXICO        │  texto ──► lista de tokens         │
│   └───────┬───────┘                                    │
│           ▼                                            │
│   ┌───────────────┐                                    │
│   │ ANÁLISIS      │  parser.java (generado por CUP)    │
│   │ SINTÁCTICO    │  tokens ──► AST                    │
│   └───────┬───────┘                                    │
│           ▼                                            │
│   ┌───────────────┐                                    │
│   │ ANÁLISIS      │  AnalizadorSemantico.java          │
│   │ SEMÁNTICO     │  AST ──► AST anotado + tabla símb. │
│   └───────┬───────┘  + errores de tipo y declaración   │
└───────────┼────────────────────────────────────────────┘
            │
            ├────────────► stderr: JSON de errores ──► consola del IDE (M4)
            │              { "tipo":"semantico", "linea":12, "mensaje":"..." }
            ▼
     AST anotado + TablaSimbolos
            │
      ┌─────┴───────┐  MÓDULO 2
      │ GENERACIÓN  │  Generador.java + UtGen.java
      │ DE CÓDIGO   │  AST ──► instrucciones ensamblador TM
      └─────┬───────┘
            ▼
       archivo.tm  ──► MÓDULO 3 (simulador TM en C) ──► MÓDULO 4 (depurador visual)
```

### Las dos salidas del Módulo 1

| Salida | Formato | Consumidor |
|--------|---------|------------|
| **AST anotado + tabla de símbolos** | objetos Java en memoria | **M2** (`Generador.java`) |
| **Errores** | una línea JSON por error en `stderr` | **M4** (consola del IDE) |

Esto es lo que convierte a M1 en la dependencia aguas arriba de todo el proyecto: si M1 no marca el tipo y la dirección de cada variable, M2 no puede generar código correcto; y si M1 no reporta la línea del error, la consola del IDE no puede subrayar nada.

### Qué hace hoy el código base y qué falta

El código suministrado es un compilador **Tiny** funcional. Maneja `if/then/else/end`, `repeat/until`, `read`, `write`, asignación `:=`, enteros, identificadores y los operadores `+ - * / < =`.

Lo que **no** tiene y hay que construir:

- Ninguna fase de análisis semántico. Cero verificación de tipos.
- La tabla de símbolos es un `HashMap` plano: un solo ámbito global, sin tipos, sin tamaños.
- Los nodos del AST **no guardan su número de línea**, así que es imposible decir *dónde* está un error.
- No existen las palabras reservadas de Tiny++ (`int`, `bool`, `string`, `function`, `procedure`, `return`, `and`, `or`, `not`), ni `[`, `]`, `,`, ni los literales de cadena.

---

## 2. Análisis léxico

### Qué es un token

Un **token** es la unidad mínima con significado del lenguaje. El analizador léxico (o *scanner*) convierte una secuencia de caracteres en una secuencia de tokens, descartando lo irrelevante (espacios, saltos de línea, comentarios).

```
Texto:    i f   0   <   x   t h e n
Tokens:   [IF] [NUM,"0"] [LT] [ID,"x"] [THEN]
```

Cada token lleva tres cosas: su **tipo** (IF, NUM, ID…), opcionalmente un **valor o lexema** (el texto concreto: `"x"`, `"0"`) y su **posición** (la línea donde apareció).

### Autómatas finitos y por qué JFlex genera código

Cada token se describe con una **expresión regular**. Por ejemplo, en `lexico.flex`:

```
digito        = [0-9]
numero        = {digito}+
letra         = [a-zA-Z]
identificador = {letra}({letra}|{digito}|_)*
```

Un conjunto de expresiones regulares se puede traducir a un **autómata finito no determinista (AFN)** y de ahí a un **autómata finito determinista (AFD o DFA)**, que es una máquina de estados que lee un carácter a la vez y nunca tiene que retroceder para decidir. Eso es exactamente lo que hace JFlex: compila nuestras expresiones regulares a un DFA y escribe ese DFA como una tabla de transiciones dentro de `Lexico.java`.

> Dato real medido en este proyecto: JFlex construye **100 estados en el AFN**, los convierte a **57 estados** y los minimiza a **52 estados en el DFA final**.

**De aquí sale la regla más importante del módulo:** `Lexico.java` es una tabla de estados generada por una herramienta. Editarla a mano es como editar un binario. Se edita `lexico.flex` y se regenera.

### Dos reglas de desempate que hay que conocer

Cuando dos patrones podrían coincidir, JFlex decide así:

1. **El match más largo gana.** Por eso `intento` se lexa como `ID("intento")` y no como `INT` + `ID("ento")`.
2. **Si empatan en longitud, gana el que aparece primero en el archivo.** Por eso las palabras reservadas (`"if"`, `"int"`, `"and"`…) deben escribirse **antes** de la regla `{identificador}`; si no, todo se lexaría como identificador.

### Cómo se obtiene el número de línea

La directiva `%line` de JFlex mantiene la variable `yyline`, contada **desde 0**. También está `yytext()`, que devuelve el texto exacto que acaba de coincidir, y `yycolumn` con la columna.

```java
private int linea() { return yyline + 1; }   // los humanos cuentan desde 1
```

`yyline` es fiable incluso dentro de un comentario multilínea, porque JFlex cuenta los saltos de línea de todo el texto consumido, no solo del que produce tokens.

### Cómo viaja la línea hasta el parser

El lexer no imprime el token: construye un objeto `java_cup.runtime.Symbol` a través de una *fábrica de símbolos*. Para que el símbolo lleve la línea hay que usar el overload que acepta posiciones:

```java
return sf.newSymbol("ID", sym.ID, linea(), linea(), new String(yytext()));
```

Esos dos enteros se guardan en los campos públicos `Symbol.left` y `Symbol.right`. Sin ellos, el símbolo nace con `left = right = -1` y la información de posición se pierde para siempre.

**Detalle fino, verificado:** la *interfaz* `SymbolFactory` **no declara** ese overload con enteros; solo lo tiene la *clase* `DefaultSymbolFactory`. Por eso el campo del lexer debe estar tipado con la clase concreta. Si se deja como `SymbolFactory`, el código no compila.

---

## 3. Análisis sintáctico

### Gramáticas libres de contexto y BNF

Mientras las expresiones regulares bastan para describir tokens, no bastan para describir estructuras anidadas (un `if` dentro de un `if`, paréntesis balanceados). Para eso se usa una **gramática libre de contexto**, escrita en notación **BNF**: un conjunto de reglas de producción que dicen cómo se compone cada constructo a partir de otros.

```
if_stmt ::= IF exp THEN stmt_seq END
          | IF exp THEN stmt_seq ELSE stmt_seq END
```

Se distinguen dos clases de símbolos:

- **Terminales**: los tokens que entrega el lexer (`IF`, `ID`, `NUM`…).
- **No terminales**: constructos del lenguaje definidos por reglas (`exp`, `stmt_seq`, `factor`…).

### Precedencia por estratificación

Nuestra gramática no necesita declaraciones de precedencia porque está **estratificada**: cada nivel de precedencia es un no terminal distinto, y el nivel más alto se escribe más abajo.

```
exp         ::= exp_or
exp_or      ::= exp_or OR exp_and  | exp_and         ← precedencia MÁS BAJA
exp_and     ::= exp_and AND exp_not | exp_not
exp_not     ::= NOT exp_not | exp_rel
exp_rel     ::= simple_exp LT simple_exp | simple_exp EQ simple_exp | simple_exp
simple_exp  ::= simple_exp PLUS term | simple_exp MINUS term | term
term        ::= term TIMES factor | term OVER factor | factor
factor      ::= LPAREN exp RPAREN | NUM | ID | TRUE | FALSE | ...   ← precedencia MÁS ALTA
```

Dos consecuencias que se leen directamente en la gramática:

- **Precedencia**: `not` liga más fuerte que `and`, que liga más fuerte que `or`. Y `*` liga más fuerte que `+`, porque `term` está por debajo de `simple_exp`.
- **Asociatividad izquierda**: la recursión está a la **izquierda** (`simple_exp ::= simple_exp PLUS term`), así que `a - b - c` se agrupa como `(a - b) - c`, que es lo correcto para la resta.

### Qué es LALR(1)

CUP genera un parser **LALR(1)**: *Look-Ahead Left-to-right, Rightmost derivation, con 1 token de anticipación*. Es un parser **ascendente** (*bottom-up*): no adivina qué regla va a usar, sino que acumula símbolos en una pila y, cuando reconoce que los de arriba forman el lado derecho de una producción, los reemplaza por el no terminal correspondiente.

Solo tiene dos movimientos posibles:

- **shift** (desplazar): meter el token de entrada en la pila.
- **reduce** (reducir): sacar de la pila los símbolos que forman el lado derecho de una regla y meter el no terminal del lado izquierdo, ejecutando la acción semántica asociada.

La decisión entre shift y reduce se toma consultando una **tabla de parseo** precalculada, indexada por el estado actual y el **único token de anticipación**. CUP construye esa tabla al generar `parser.java`.

> Dato real: la gramática base produce **54 estados**; la gramática Tiny++ completa produce **125 estados**.

### Conflictos: el indicador de calidad de la gramática

Si la tabla no puede decidir con un solo token de anticipación, CUP reporta un **conflicto**:

- **shift/reduce**: en el mismo estado se podría desplazar o reducir. El caso clásico es el *dangling else* (`if a then if b then x else y`): ¿a qué `if` pertenece el `else`? Tiny++ no lo sufre porque cada `if` se cierra obligatoriamente con `end`.
- **reduce/reduce**: dos reglas distintas podrían reducirse. Casi siempre significa que la gramática es ambigua y hay que rediseñarla.

**Criterio de aceptación del módulo: CUP debe reportar `0 conflicts detected`.** Un conflicto no se tapa con declaraciones `precedence`; se corrige rediseñando las producciones. La gramática Tiny++ de este proyecto fue verificada y produce 0 conflictos.

### Acciones semánticas: dónde se construye el árbol

Cada producción puede llevar código Java entre `{: :}` que se ejecuta **en el momento de la reducción**. Ahí es donde se construye el AST.

```
assign_stmt ::= ID:variable ASSIGN exp:ex
    {: RESULT = new NodoAsignacion(variable.toString(), ex);
       RESULT.setLinea(variableleft);
    :}
```

Tres piezas de notación de CUP que hay que saber explicar:

| Notación | Significado |
|----------|-------------|
| `ID:variable` | **etiqueta**: dentro de la acción, `variable` es el valor del token `ID` (aquí, el lexema del identificador) |
| `RESULT` | el valor que esta producción devuelve hacia arriba; se convierte en el `value` del no terminal |
| `variableleft` | la **posición izquierda** del símbolo etiquetado `variable`, es decir su línea. CUP la genera automáticamente como un `int` para cada etiqueta usada |

**Propagación automática de la línea:** cuando CUP reduce una producción, construye el símbolo del no terminal heredando `left`/`right` de sus componentes. Por eso una `exp` compleja arrastra sola la línea de su primer token, sin que nosotros hagamos nada. Verificado en el runtime de CUP 11b.

### Manejo de errores sintácticos

El terminal especial `error` en una producción (`stmt ::= ... | error`) habilita la **recuperación de errores**: cuando el parser se atasca, descarta símbolos hasta poder reducir por la regla con `error`, y así puede seguir analizando y reportar varios errores en una sola corrida, en vez de morir en el primero.

Los mensajes se producen sobreescribiendo los *hooks* que `lr_parser` expone:

- `syntax_error(Symbol cur_token)` — se llama al detectar el error. De aquí sale `cur_token.left`, la línea.
- `unrecovered_syntax_error(Symbol)` — se llama cuando la recuperación falla y hay que abortar.
- `expected_token_ids()` — devuelve la lista de ids de los tokens que el parser *sí* esperaba en ese estado. Cruzándola con el arreglo `sym.terminalNames` se obtiene un mensaje útil como `"Se esperaba SEMI, se encontro WRITE"`.

---

## 4. El Árbol de Sintaxis Abstracta (AST)

### Por qué un árbol y no generar código de una vez

Un compilador de una sola pasada generaría código directamente en las acciones del parser. No sirve aquí, por tres razones:

1. **El análisis semántico necesita el programa completo.** Para verificar una llamada a una función declarada más abajo en el archivo, hay que haber leído todo primero.
2. **El orden de recorrido no es el orden del texto.** Para generar un `if`, M2 emite la prueba, luego un salto cuya dirección **todavía no conoce**, luego el cuerpo, y recién entonces vuelve atrás a rellenar el salto (*backpatching*). Eso requiere tener la estructura en memoria.
3. **Separa responsabilidades.** M1 produce el árbol; M2 lo consume. Son dos personas distintas trabajando sobre un contrato claro.

"Abstracto" significa que el árbol guarda la **estructura** del programa y descarta los detalles de escritura: los paréntesis, los `then`, los `;` no aparecen como nodos porque su único papel era guiar al parser.

### El modelo concreto del código base

El AST de este proyecto es deliberadamente simple. `NodoBase.java` lo dice en sus propios comentarios: *"ESTE MODELO DE AST PUEDE SER MEJORADO DE MUCHAS FORMAS, POR SIMPLIFICACION Y DIDACTICA EN CLASE, SE LLEVARA A CABO DE FORMA MUY SIMPLE Y POCO FLEXIBLE"*.

Funciona así:

- **`NodoBase` solo tiene un puntero: `HermanoDerecha`.** Las secuencias de sentencias son **listas enlazadas** por ese campo.
- **Los hijos no son una lista genérica**, son campos concretos de cada subclase: `NodoIf` tiene `prueba`, `parteThen`, `parteElse`; `NodoRepeat` tiene `cuerpo` y `prueba`.

```
int x;  read x;  if 0 < x then write x end

  NodoDeclaracion(int,x) ──hermano──► NodoLeer(x) ──hermano──► NodoIf
                                                                 │
                              prueba ├── NodoOperacion(menor)
                                     │      ├── izq: NodoValor(0)
                                     │      └── der: NodoIdentificador(x)
                           parteThen ├── NodoEscribir
                                     │      └── NodoIdentificador(x)
                            parteElse└── null
```

La consecuencia práctica, visible en las tres clases que recorren el árbol (`Util.imprimirAST`, `TablaSimbolos.cargarTabla` y `Generador.generar`): **todo recorrido es un `while` sobre `getHermanoDerecha()` con una cadena de `instanceof` por dentro**. Nuestro `AnalizadorSemantico` sigue exactamente ese mismo patrón, para que el código sea reconocible por el resto del equipo.

### Las anotaciones que agrega el Módulo 1

`NodoBase` se extiende con tres campos nuevos. Son el verdadero producto del módulo:

| Campo | Lo pone | Lo usa | Para qué |
|-------|---------|--------|----------|
| `linea` | el parser, en las acciones | `Errores` | reportar *dónde* está el error (Contrato B) |
| `tipo` | el analizador semántico | el analizador semántico | verificar compatibilidad de tipos |
| `registro` | el analizador semántico | **M2** | saber la dirección, el nivel y la categoría del símbolo ya resuelto |

---

## 5. Tabla de símbolos y ámbitos

### Qué es y qué guarda

La **tabla de símbolos** responde una pregunta: *"este nombre, ¿qué es?"*. Para cada identificador del programa guarda un `RegistroSimbolo` con:

| Campo | Significado |
|-------|-------------|
| `identificador` | el nombre escrito en el fuente |
| `tipo` | `int`, `bool`, `string`, o el tipo de retorno si es función |
| `categoria` | VARIABLE · ARREGLO · FUNCION · PROCEDIMIENTO · PARAMETRO |
| `direccionMemoria` | celda de `dMem[]` asignada (globales) |
| `desplazamiento` | índice dentro del marco de activación (locales y parámetros) |
| `tamano` | número de elementos, si es un arreglo |
| `nivel` | 0 = global; >0 = dentro de una función |
| `parametros` | la firma, si es función o procedimiento |
| `numLinea` | línea donde fue declarado |

### El problema que resuelve la pila de ámbitos

El enunciado exige **alcance léxico**: *"todas las variables declaradas tienen ámbito local al bloque o función dentro del cual son declaradas, manteniendo visibilidad global solo aquellas declaradas en el cuerpo principal"*.

La tabla base no puede expresar eso: es un único `HashMap<String, RegistroSimbolo>`, así que dos variables llamadas `x` en funciones distintas colisionan.

La solución es una **pila de ámbitos**. Cada ámbito es su propio diccionario:

```
                    ┌──────────────────────────┐
  tope de la pila   │ ámbito "suma"            │  ← ámbito actual
                    │   a : int, param, desp 0 │
                    │   b : int, param, desp 1 │
                    │   x : int, local, desp 2 │
                    ├──────────────────────────┤
  base de la pila   │ ámbito "global"          │
                    │   x    : int,   dir 0    │
                    │   A    : int[10], dir 1  │
                    │   suma : FUNCION         │
                    └──────────────────────────┘
```

Las tres operaciones:

- **`abrirAmbito(nombre)`** — al entrar a una función: *push* de un diccionario vacío.
- **`cerrarAmbito()`** — al salir: *pop*. Las locales dejan de ser visibles.
- **`buscar(nombre)`** — recorre **del tope hacia la base** y devuelve la **primera** coincidencia.

### Sombreado (shadowing)

Que `buscar` vaya del tope hacia la base es lo que produce el **sombreado**: dentro de `suma`, el nombre `x` resuelve a la local (desplazamiento 2 del marco), no a la global (dirección 0). La global sigue existiendo, pero queda *tapada* mientras ese ámbito esté abierto. Al hacer `cerrarAmbito()`, `x` vuelve a significar la global.

### Un detalle de diseño que vale explicar en la defensa

Si `cerrarAmbito()` destruyera el ámbito, M2 no podría generar código: cuando el generador recorre el árbol, **todos los ámbitos ya están cerrados**.

La solución es que el análisis semántico, al resolver cada identificador, **guarde el `RegistroSimbolo` dentro del nodo del AST** (`nodo.setRegistro(reg)`). El nodo queda apuntando al registro, así que el registro sobrevive al cierre del ámbito aunque ya no sea alcanzable por nombre. M2 no vuelve a buscar por nombre: lee `nodo.getRegistro().getDireccionMemoria()`.

Esto se llama **resolver los nombres en tiempo de análisis**, y es la forma estándar de hacerlo. Además se conserva el ámbito en un `historial` para poder volcar la tabla completa con el flag `-debug`.

### Reparto de responsabilidades con M2 sobre la memoria

M1 **no decide el layout del marco de activación** — eso es diseño de M2. M1 solo aporta los datos:

- Si `esGlobal`, asigna una `direccionMemoria` consecutiva (el contador arranca en 0, igual que el código base, porque el preludio de `UtGen` limpia `dMem[0]` y la primera global vive ahí).
- Si es local o parámetro, asigna un `desplazamiento` relativo que arranca en 0 por cada ámbito, y registra el `nivel`.

M2 traduce ese `desplazamiento` a `dMem[FP + k]` según el marco que haya diseñado.

---

## 6. Verificación de tipos

### Tipado débil vs. tipado fuerte

El Tiny original es **débilmente tipado**: todo es entero, no hay declaraciones, y la primera vez que aparece un nombre se le reserva una celda automáticamente. De hecho, así funciona el código base: `TablaSimbolos.cargarTabla()` inserta el símbolo **al encontrar un `NodoIdentificador`**, no al declararlo.

Tiny++ es **fuertemente tipado**: toda variable debe declararse antes de usarse, y cada operación exige tipos concretos. Esto **invierte** la lógica del código base: ahora encontrar un identificador no declarado es un error, no una orden de reservar memoria.

> **Consecuencia que hay que saber explicar:** `ejemplo_fuente/factorial.tny`, el programa de prueba original, **deja de compilar** con Tiny++, porque usa `x` y `fact` sin declararlas. Eso no es un defecto: es exactamente la verificación que el enunciado exige. Por eso existe la versión `tinypp/07_factorial_tpp.tny` con las declaraciones añadidas.

### Por qué el semántico es una pasada separada

No se puede verificar tipos dentro de las acciones del parser, porque el parser reduce de abajo hacia arriba y en un orden que no corresponde al flujo del programa: cuando reduce una llamada a función, puede que la declaración de esa función todavía no se haya leído.

Por eso el `AnalizadorSemantico` es un **recorrido completo e independiente del AST**, que corre después del parseo y antes de la generación de código.

Además usa **dos pasadas internas**:

1. **`registrarFirmas`** — registra en el ámbito global las firmas de todas las funciones y procedimientos. Así un procedimiento puede llamar a otro declarado más abajo en el archivo.
2. **`recorrer`** — el trabajo real: declaraciones, verificación de tipos, resolución de nombres y anotación de los nodos.

### Síntesis de tipos: de abajo hacia arriba

La verificación de una expresión es un **recorrido post-orden** que *sintetiza* el tipo: primero se obtiene el tipo de los hijos, y con eso se decide el tipo del padre.

```
          x := 3 < (y + 1)

   verificar(NodoOperacion menor)
        ├── verificar(NodoValor 3) ─────────────► ENTERO
        └── verificar(NodoOperacion mas)
                 ├── verificar(NodoIdentificador y) ──► ENTERO  (de la tabla)
                 └── verificar(NodoValor 1) ──────────► ENTERO
                 → ambos ENTERO, '+' exige int  ──────► ENTERO
        → ambos ENTERO, '<' compara y devuelve ──────► BOOLEANO

   luego: x es int, la expresión es bool
        → ERROR: "Tipos incompatibles: int vs bool", línea 1
```

Cada llamada a `verificar` devuelve un `Tipo` y además lo guarda en el nodo con `setTipo(...)`, de modo que el árbol queda anotado y M2 puede consultarlo.

### Reglas de tipos de Tiny++

| Construcción | Exige | Produce |
|--------------|-------|---------|
| `+ - * /` | ambos operandos `int` | `int` |
| `< =` | ambos del mismo tipo, `int` o `bool` | `bool` |
| `and or` | ambos `bool` | `bool` |
| `not` | un operando `bool` | `bool` |
| condición de `if` / `until` | `bool` | — |
| `x := exp` | tipo de `x` == tipo de `exp` | — |
| `A[i]` | `A` declarado como arreglo, `i` de tipo `int` | tipo base de `A` |
| `read x` | `x` de tipo `int` (la instrucción `IN` de la TM lee enteros) | — |
| llamada a función | aridad exacta y tipo exacto de cada argumento | tipo de retorno |

Sobre `string`: en esta versión son **literales de solo lectura**. `string s;` y `write "hola"` son válidos; `s := "hola"` y comparar cadenas son errores semánticos. Es la mitigación de riesgo que acordó el equipo para no obligar a M2 a gestionar memoria dinámica de cadenas en un sprint de 4 días.

### El tipo `ERROR` y los errores en cascada

El enum `Tipo` tiene un valor centinela: `ERROR`.

Sin él, un único error se multiplica. Si `y` no está declarada, la expresión `y + 1 * 2 < 3` produciría un error por `y`, otro por el `+` que recibió algo inválido, otro por el `<`… y el usuario vería cinco errores donde hay uno.

La regla que lo evita: **si algún operando ya es `Tipo.ERROR`, devolver `Tipo.ERROR` sin reportar nada nuevo.** El error se reporta una sola vez, en el punto exacto donde se detectó, y el `ERROR` se propaga en silencio hacia arriba.

### El contrato de errores (Contrato B del equipo)

Todo error sale por `stderr`, **una línea JSON por error**, con exactamente tres claves:

```json
{ "tipo": "lexico",     "linea": 3,  "mensaje": "Caracter ilegal: @" }
{ "tipo": "sintactico", "linea": 7,  "mensaje": "Se esperaba SEMI, se encontro WRITE" }
{ "tipo": "semantico",  "linea": 12, "mensaje": "Variable 'x' no declarada" }
{ "tipo": "semantico",  "linea": 15, "mensaje": "Tipos incompatibles: int vs bool" }
```

Y su contraparte, el **Contrato D**: el `stdout` del compilador contiene **únicamente** el código `.tm`. Todo volcado de depuración (traza de tokens, reglas reducidas, AST, tabla de símbolos) va a `stderr` y solo bajo el flag `-debug`. El servidor puente de M4 lee `stdout` como el `.tm` y `stderr` como los errores; cualquier `System.out.println` de depuración rompe la integración.

---

## 7. Cómo este módulo cumple el enunciado

Cruce de cada requisito del §2 de `Especificacion_Proyecto_TinyPlus.md` con el componente que lo implementa:

| Requisito del enunciado | Qué lo implementa en M1 | Verificación |
|------------------------|-------------------------|--------------|
| **Tipado estricto**: toda variable declarada antes de usarse | `AnalizadorSemantico` + `buscar()` de la pila de ámbitos | `err_no_declarada.tny` |
| **Tipos `int`, `bool`, `string`** | enum `Tipo`, tokens `INT`/`BOOL`/`STRING`, regla `decl_stmt` | `01_declaraciones_ok.tny` |
| **Arreglos unidimensionales** | `NodoDeclaracionArreglo`, `NodoAccesoArreglo`, campo `tamano` | `02_arreglos_ok.tny` |
| **Índices con expresión compuesta** (`A[i*2+1]`) | la regla `ID LBRACKET exp RBRACKET`, donde `exp` es la expresión completa | `02_arreglos_ok.tny` |
| **Procedimientos** (sin retorno) | `NodoProcedimiento`, `Tipo.VACIO` | `04_procedimientos_ok.tny` |
| **Funciones** (con retorno) | `NodoFuncion`, `NodoRetorno`, verificación de que exista un `return` | `03_funciones_ok.tny` |
| **Paso formal de parámetros** | `Categoria.PARAMETRO`, lista `parametros` del registro, verificación de aridad y tipos | `err_args_funcion.tny`, `err_tipo_argumento.tny` |
| **Ámbito local a la función** | `abrirAmbito` / `cerrarAmbito`, campo `nivel` | `06_scopes_ok.tny` |
| **Visibilidad global solo del cuerpo principal** | el ámbito base de la pila es el único global | `06_scopes_ok.tny` |
| **Prohibir variables no declaradas** | `buscar()` devuelve `null` → error semántico | `err_no_declarada.tny` |
| **Compatibilidad de tipos en operaciones, asignaciones y parámetros** | `verificar()` con las reglas de la sección 6 | `err_tipos_asignacion.tny`, `err_indice_no_int.tny`, `err_condicion_no_bool.tny` |
| **Indicar la línea de cada error** (§3.2, consola del IDE) | `yyline` → `Symbol.left` → `NodoBase.linea` → `Errores` | toda la suite |

---

## 8. Preguntas de defensa

Prepara estas respuestas. Son las preguntas más probables sobre el Módulo 1.

**¿Qué pasa si uso una variable no declarada, y en qué fase se detecta?**
Se detecta en el **análisis semántico**, no antes. El lexer la reconoce perfectamente como un token `ID` y el parser la acepta como un `factor` válido: sintácticamente el programa está bien escrito. Es el analizador semántico el que llama a `tabla.buscar(nombre)`, obtiene `null` y emite `{ "tipo":"semantico", "linea":N, "mensaje":"Variable 'x' no declarada" }`.

**¿Por qué el análisis semántico es una pasada separada y no se hace dentro del parser?**
Porque el parser LALR reduce de abajo hacia arriba y en un orden que no sigue el flujo del programa. Cuando reduce una llamada a una función, la declaración de esa función puede no haberse leído todavía. Un recorrido separado del AST ya completo permite además hacer dos pasadas: una para registrar todas las firmas de funciones y otra para verificar los cuerpos.

**¿Cómo se resuelve una variable local que tiene el mismo nombre que una global?**
Con la pila de ámbitos. `buscar()` recorre la pila **del tope hacia la base** y devuelve la primera coincidencia, así que dentro de la función gana la local: eso es el *sombreado*. Al cerrar el ámbito con `cerrarAmbito()`, la local desaparece y el nombre vuelve a resolver a la global.

**¿Qué diferencia hay entre un error léxico, uno sintáctico y uno semántico? Da un ejemplo de cada uno.**
El **léxico** es un carácter que no puede formar ningún token: `write @`. El **sintáctico** es una secuencia de tokens válidos en un orden que la gramática no acepta: `if x then` sin `end`. El **semántico** es un programa bien formado sintácticamente pero sin sentido: `int x; bool f; x := f` — la gramática lo acepta sin problema, pero los tipos no son compatibles.

**¿De dónde sale el número de línea de un error?**
De una cadena de cuatro pasos: JFlex mantiene `yyline` con la directiva `%line`; el lexer la pasa a la fábrica de símbolos con `newSymbol(..., linea(), linea(), ...)`, que la guarda en `Symbol.left`; en las acciones de la gramática se lee como `<etiqueta>left` y se fija en el nodo con `setLinea()`; y el analizador semántico la recupera con `nodo.getLinea()` al reportar. Los no terminales heredan la posición de sus componentes automáticamente.

**¿Por qué no se puede editar `Lexico.java` ni `parser.java`?**
Porque son **generados**. `Lexico.java` es una tabla de transiciones de un autómata finito determinista de 52 estados que produce JFlex a partir de `lexico.flex`; `parser.java` es la tabla de parseo LALR que produce CUP a partir de `sintactico.cup`. Cualquier edición manual se pierde en la siguiente regeneración. Se edita la especificación y se regenera.

**¿Qué es un conflicto LALR y cuántos tiene nuestra gramática?**
Es cuando la tabla de parseo no puede decidir entre desplazar y reducir (shift/reduce) o entre dos reducciones distintas (reduce/reduce), con un solo token de anticipación. Nuestra gramática tiene **0 conflictos**, y eso es un criterio de aceptación del módulo. La gramática está estratificada por niveles de precedencia y cada bloque se cierra con `end`, lo que evita incluso el *dangling else*.

**¿Cómo sabe el Módulo 2 la dirección de una variable local si los ámbitos ya se cerraron?**
Porque el análisis semántico **resolvió los nombres y anotó el árbol**: cada nodo identificador guarda una referencia directa a su `RegistroSimbolo` vía `setRegistro()`. Esa referencia mantiene vivo el registro aunque el ámbito ya no sea alcanzable por nombre. M2 no busca por nombre, lee `nodo.getRegistro()`.

**¿Por qué el tipo `ERROR` existe en el enum `Tipo`?**
Para cortar los errores en cascada. Si una subexpresión ya falló, se devuelve `ERROR` hacia arriba y los operadores que lo reciben no vuelven a reportar. Así un error del programador produce un solo mensaje en la consola del IDE, en la línea donde realmente está.

**¿Por qué `factorial.tny` ya no compila?**
Porque usa `x` y `fact` sin declararlas. En Tiny eso era legal (tipado débil: el primer uso reservaba memoria); en Tiny++ es precisamente el error que el enunciado obliga a detectar. La versión equivalente con declaraciones es `tinypp/07_factorial_tpp.tny`.

---

## Referencias

- `Especificacion_Proyecto_TinyPlus.md` — enunciado oficial de la asignatura
- `00_CONTEXTO_PROYECTO.md` — arquitectura del proyecto y contratos entre módulos
- `02_INSTRUCCIONES_IA_EQUIPO.md` — contratos inmutables y protocolo de alerta de integración
- `modulo_1/PLAN_MODULO_1.md` — plan de implementación por fases
- Kenneth C. Louden, *Compiler Construction: Principles and Practice* — origen del lenguaje Tiny y de la Tiny Machine
