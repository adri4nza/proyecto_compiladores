# Plan de Implementación — Módulo 1
## Analizador Frontend y Verificador Semántico (Tiny++)

**Peso en la nota:** 30% · **Tecnología:** Java + JFlex + CUP
**Teoría y fundamentos:** ver `TEORIA_MODULO_1.md` en esta misma carpeta

> Este plan está escrito para ser ejecutado paso a paso, incluso por un asistente de IA de capacidad limitada. Cada fase es secuencial, trae el código concreto y termina en una **compuerta de verificación** con comando exacto y salida esperada. **No avanzar a la fase siguiente si la compuerta falla.**

---

## Tabla de progreso

| Fase | Qué entrega | Estado |
|------|-------------|--------|
| 0 | Documentación del módulo (este archivo + `TEORIA_MODULO_1.md`) | [x] |
| 1 | Infraestructura de errores (`Errores.java`) y números de línea | [x] |
| 2 | Tokens de Tiny++ en el lexer | [x] |
| 3 | Gramática Tiny++ y nodos AST nuevos | [x] |
| 4 | Tipos y tabla de símbolos con pila de ámbitos | [x] |
| 5 | Analizador semántico | [x] |
| 6 | Integración en `main()` y disciplina de salida | [x] |
| 7 | Fixtures de prueba y guion de regresión | [x] |

---

## Punto de partida

El código base en `parcial3/motor_compilador/` es un compilador **Tiny** funcional. Lo que **no** tiene:

- Ninguna fase de análisis semántico.
- Tabla de símbolos: un `HashMap` plano, un solo ámbito, sin tipos ni tamaños.
- Los nodos del AST **no guardan su número de línea**.
- Faltan todas las palabras reservadas y símbolos de Tiny++.

## Decisiones ya tomadas por el equipo (no reabrir)

| Decisión | Valor fijado |
|----------|--------------|
| Operadores lógicos | **Sí**: `and`, `or`, `not`. Precedencia `not` > `and` > `or` |
| Tipo `string` | **Literales de solo lectura**. `string s;` y `write "hola"` válidos; `s := "hola"` y comparar cadenas son errores semánticos |
| Interfaz con M2 | **Anotar el AST + mantener compatibilidad**: se añade `getRegistro()` a los nodos y se conservan `getDireccion(String)` y `cargarTabla()` |
| Recursión | **No soportada** en esta versión |

## Reglas inquebrantables

1. **Nunca editar a mano `Lexico.java`, `parser.java` ni `sym.java`.** Son generados. Se edita `lexico.flex` / `sintactico.cup` y se regenera.
2. **Siempre compilar con `-encoding ISO-8859-1`.** Los fuentes están en latin-1; sin el flag `javac` aborta con "unmappable character".
3. **No reescribir el compilador base.** Se *extiende*.
4. **Contrato D — stdout limpio.** El `stdout` solo lleva el `.tm`. Toda depuración va a `stderr` y solo bajo el flag `-debug`.
5. **Contrato B — formato de error exacto.** Una línea JSON por error en `stderr`, con las claves `tipo`, `linea`, `mensaje` y nada más.
6. Ejecutar la compuerta tras cada fase. No acumular fases.

## Comandos canónicos (verificados)

Desde `parcial3/motor_compilador/Compiladores_Tiny_Generacion_2022_1/`:

```bash
export ESP=src/especificacion
export OUT=/tmp/m1out          # fuera del repo: out/ está versionado

# 1. Regenerar el lexer (tras editar lexico.flex)
java -jar $ESP/jflex-full-1.8.2.jar -d src/ve/edu/unet/ $ESP/lexico.flex

# 2. Regenerar el parser (tras editar sintactico.cup)
java -jar $ESP/java-cup-11b.jar -parser parser -symbols sym \
     -package ve.edu.unet -destdir src/ve/edu/unet/ $ESP/sintactico.cup

# 3. Compilar
javac -encoding ISO-8859-1 -nowarn -cp $ESP/java-cup-11b-runtime.jar \
      -d $OUT $(find src/ve -name '*.java')

# 4. Ejecutar
java -cp $OUT:$ESP/java-cup-11b-runtime.jar ve.edu.unet.parser ARCHIVO.tny
```

**Línea base medida de la gramática actual:** `22 terminales, 13 no terminales, 30 producciones, 54 estados, 0 conflictos`. El paso 2 debe seguir reportando **0 conflictos** tras cada cambio. Si aparece un conflicto, el cambio está mal — **no taparlo con `precedence`**.

---

# FASE 1 — Infraestructura de errores y números de línea

Habilita todo lo demás. Sin esto no hay Contrato B.

## 1.1 Clase nueva `src/ve/edu/unet/Errores.java`

```java
package ve.edu.unet;

public class Errores {
    private static int totalLexicos = 0;
    private static int totalSintacticos = 0;
    private static int totalSemanticos = 0;

    /* Escapa el mensaje para que el JSON sea valido (Contrato B) */
    private static String escapar(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ");
    }

    private static void emitir(String tipo, int linea, String mensaje) {
        System.err.println("{ \"tipo\": \"" + tipo + "\", \"linea\": " + linea
                         + ", \"mensaje\": \"" + escapar(mensaje) + "\" }");
    }

    public static void lexico(int linea, String mensaje) {
        totalLexicos++;     emitir("lexico", linea, mensaje);
    }
    public static void sintactico(int linea, String mensaje) {
        totalSintacticos++; emitir("sintactico", linea, mensaje);
    }
    public static void semantico(int linea, String mensaje) {
        totalSemanticos++;  emitir("semantico", linea, mensaje);
    }

    public static int getTotal()       { return totalLexicos + totalSintacticos + totalSemanticos; }
    public static boolean hayErrores() { return getTotal() > 0; }
    public static boolean hayErroresPrevios() { return totalLexicos + totalSintacticos > 0; }
}
```

## 1.2 Números de línea en `lexico.flex`

Mecanismo **verificado** contra el runtime de CUP 11b:

- `DefaultSymbolFactory` **sí** tiene `newSymbol(String nombre, int id, int left, int right [, Object valor])`.
- La **interfaz** `SymbolFactory` **no** declara esos overloads → el campo `sf` debe tiparse con la clase concreta.
- Los símbolos creados sin posición quedan con `left = right = -1`.
- Los no terminales **heredan** `left`/`right` de sus componentes automáticamente.

En el bloque `%{ ... %}`:

```java
    public Lexico(Reader r, DefaultSymbolFactory sf){     // <-- tipo concreto
        this(r);
        this.sf = sf;
        debug = false;                                     // <-- Contrato D
    }
    private DefaultSymbolFactory sf;                       // <-- antes: SymbolFactory
    private boolean debug;
    /* La linea la provee yyline (directiva %line de JFlex), base 0 -> +1 */
    private int linea() { return yyline + 1; }
```

Eliminar el campo `lineanum` y su inicialización: queda muerto, `%line` ya lleva la cuenta (incluso dentro de comentarios multilínea).

Reescribir **todas** las reglas para pasar la línea:

```
"if"     { return sf.newSymbol("IF", sym.IF, linea(), linea()); }
{numero} { return sf.newSymbol("NUM", sym.NUM, linea(), linea(), new String(yytext())); }
```

Quitar de todas las reglas los `if(debug) System.out.println("token ...")`. La regla de carácter ilegal pasa a `Errores`:

```
. { Errores.lexico(linea(), "Caracter ilegal: " + yytext()); }
```

Y el `%eofval` conserva la línea:

```
%eofval{
    return sf.newSymbol("EOF", sym.EOF, linea(), linea());
%eofval}
```

## 1.3 Corregir el patrón de identificadores

El base define `identificador = {letra}+`: **no admite dígitos**, así que `A1` se lexa como `ID("A")` + `NUM("1")`. Cambiar:

```
identificador = {letra}({letra}|{digito}|_)*
```

## 1.4 Hooks de error sintáctico en `sintactico.cup`

En el bloque `parser code {: ... :}` (verificado: `lr_parser` expone `expected_token_ids()` pública que devuelve `List<Integer>`; `sym.terminalNames` ya existe en el `sym.java` generado):

```java
    /* Construye "Se esperaba SEMI" a partir de los tokens que CUP esperaba */
    private String tokensEsperados() {
        StringBuilder sb = new StringBuilder();
        java.util.List<Integer> ids = expected_token_ids();
        for (int i = 0; i < ids.size() && i < 4; i++) {
            if (i > 0) sb.append(" o ");
            sb.append(sym.terminalNames[ids.get(i).intValue()]);
        }
        return sb.toString();
    }

    private String nombreToken(java_cup.runtime.Symbol s) {
        if (s == null || s.sym < 0 || s.sym >= sym.terminalNames.length) return "desconocido";
        return sym.terminalNames[s.sym];
    }

    public void syntax_error(java_cup.runtime.Symbol cur_token) {
        String esperados = tokensEsperados();
        String msg = esperados.isEmpty()
            ? "Token inesperado: " + nombreToken(cur_token)
            : "Se esperaba " + esperados + ", se encontro " + nombreToken(cur_token);
        Errores.sintactico(cur_token.left, msg);
    }

    public void unrecovered_syntax_error(java_cup.runtime.Symbol cur_token) {
        Errores.sintactico(cur_token.left,
            "Error sintactico irrecuperable en: " + nombreToken(cur_token));
        /* No relanzar: se sale ordenadamente para que M4 lea el JSON */
    }
```

En el bloque `action code {: ... :}` poner `private boolean debug=false;` y eliminar de **todas** las acciones los `System.out.println("\t regla N")`. Los `System.out.println("Ocurrio error en cup # ...")` se sustituyen por `Errores.sintactico(<etiqueta>left, "...")`.

En el `main()`, cambiar el tipo de `sf` a `DefaultSymbolFactory`.

## 1.5 Línea en `NodoBase`

**No tocar los constructores de los nodos existentes.** Solo añadir en `nodosAST/NodoBase.java`:

```java
    private int linea = -1;
    public int  getLinea()      { return linea; }
    public void setLinea(int l) { linea = l; }
```

Y en cada acción que construya un nodo, fijar la línea con la etiqueta (CUP ya genera `int <etiqueta>left`):

```
assign_stmt ::= ID:variable ASSIGN exp:ex
    {: RESULT = new NodoAsignacion(variable.toString(), ex);
       RESULT.setLinea(variableleft);
    :}
```

### ✅ Compuerta 1

```bash
printf 'read x;\nwrite @\n' > /tmp/err1.tny
java -cp $OUT:$ESP/java-cup-11b-runtime.jar ve.edu.unet.parser /tmp/err1.tny 2>&1 >/dev/null
```

Exactamente una línea JSON por error, con `"linea": 2`. Nada en stdout salvo el `.tm`. CUP sigue en **0 conflictos**.

---

# FASE 2 — Tokens de Tiny++ en el lexer

Agregar a `lexico.flex` **antes** de la regla `{identificador}` (si empatan en longitud, JFlex resuelve por orden de aparición):

| Lexema | Terminal | Lexema | Terminal |
|--------|----------|--------|----------|
| `int` | `INT` | `function` | `FUNCTION` |
| `bool` | `BOOL` | `procedure` | `PROCEDURE` |
| `string` | `STRING` | `return` | `RETURN` |
| `true` | `TRUE` | `and` | `AND` |
| `false` | `FALSE` | `or` | `OR` |
| `[` | `LBRACKET` | `not` | `NOT` |
| `]` | `RBRACKET` | `,` | `COMMA` |

Más el literal de cadena:

```
cadena = \"[^\"\n]*\"
```

```
{cadena} { /* se entrega sin las comillas */
           String t = yytext();
           return sf.newSymbol("CADENA", sym.CADENA, linea(), linea(),
                               t.substring(1, t.length()-1)); }
```

### ✅ Compuerta 2

`jflex` genera sin error, y `javac` falla **solo** por `sym.INT` y compañía inexistentes (la gramática aún no los declara). Eso confirma que las reglas están bien. La Fase 3 cierra el círculo.

---

# FASE 3 — Gramática y nodos AST de Tiny++

## 3.1 Gramática verificada

Probada con CUP 11b: **0 conflictos** (`37 terminales, 27 no terminales, 69 producciones, 125 estados`). Transcribirla tal cual, añadiendo las acciones semánticas.

```
/* Terminales nuevos */
terminal INT, BOOL, STRING, TRUE, FALSE, FUNCTION, PROCEDURE, RETURN;
terminal LBRACKET, RBRACKET, COMMA, AND, OR, NOT;
terminal String CADENA;

/* No terminales nuevos */
non terminal NodoBase decl_stmt, func_decl, proc_decl, return_stmt, call_stmt;
non terminal Tipo tipo;
non terminal java.util.List params, param_list, args, arg_list;
non terminal NodoDeclaracion param;
non terminal NodoBase exp_or, exp_and, exp_not, exp_rel;

/* Producciones */
program     ::= stmt_seq ;
stmt_seq    ::= stmt_seq SEMI stmt | stmt | stmt_seq SEMI ;
stmt        ::= if_stmt | repeat_stmt | assign_stmt | read_stmt | write_stmt
              | decl_stmt | func_decl | proc_decl | return_stmt | call_stmt | error ;

decl_stmt   ::= tipo ID | tipo ID LBRACKET NUM RBRACKET ;
tipo        ::= INT | BOOL | STRING ;

func_decl   ::= FUNCTION tipo ID LPAREN params RPAREN stmt_seq END ;
proc_decl   ::= PROCEDURE ID LPAREN params RPAREN stmt_seq END ;
params      ::= param_list | ;
param_list  ::= param | param_list COMMA param ;
param       ::= tipo ID | tipo ID LBRACKET RBRACKET ;

return_stmt ::= RETURN exp | RETURN ;
call_stmt   ::= ID LPAREN args RPAREN ;
args        ::= arg_list | ;
arg_list    ::= exp | arg_list COMMA exp ;

assign_stmt ::= ID ASSIGN exp | ID LBRACKET exp RBRACKET ASSIGN exp ;
read_stmt   ::= READ ID | READ ID LBRACKET exp RBRACKET ;
write_stmt  ::= WRITE exp ;
if_stmt     ::= IF exp THEN stmt_seq END | IF exp THEN stmt_seq ELSE stmt_seq END ;
repeat_stmt ::= REPEAT stmt_seq UNTIL exp ;

exp         ::= exp_or ;
exp_or      ::= exp_or OR exp_and | exp_and ;
exp_and     ::= exp_and AND exp_not | exp_not ;
exp_not     ::= NOT exp_not | exp_rel ;
exp_rel     ::= simple_exp LT simple_exp | simple_exp EQ simple_exp | simple_exp ;
simple_exp  ::= simple_exp PLUS term | simple_exp MINUS term | term ;
term        ::= term TIMES factor | term OVER factor | factor ;
factor      ::= LPAREN exp RPAREN | NUM | ID | ID LBRACKET exp RBRACKET
              | ID LPAREN args RPAREN | TRUE | FALSE | CADENA | error ;
```

**Notas verificadas — no "arreglar" lo que no está roto:**

- `stmt_seq ::= stmt_seq SEMI` hace tolerante el punto y coma final (`int x;` justo antes de `end`), **sin** introducir conflicto.
- La **acción intermedia** `{: :}` que el base tiene en `assign_stmt ::= ID {: ... :} ASSIGN exp` **no genera conflicto** aunque se añadan `ID LBRACKET ...` y `ID LPAREN ...`. Probado. Es código muerto (está comentado): eliminarla es opcional.
- Las declaraciones `precedence left PLUS/MINUS/TIMES` del base son redundantes (la gramática ya está estratificada). Dejarlas o quitarlas es indiferente; **no agregar nuevas**.
- `ID` al inicio de una sentencia es decidible con un token de anticipación (`ASSIGN` → asignación, `LBRACKET` → asignación a arreglo, `LPAREN` → llamada), porque ninguna sentencia puede empezar por una expresión.

## 3.2 Tipos y nodos nuevos en `nodosAST/`

```java
// Tipo.java
package ve.edu.unet.nodosAST;
public enum Tipo {
    ENTERO, BOOLEANO, CADENA,
    VACIO,   /* procedimientos: no retornan valor */
    ERROR;   /* tipo centinela: corta los errores en cascada */
    public String comoTexto() {
        switch (this) {
            case ENTERO:   return "int";
            case BOOLEANO: return "bool";
            case CADENA:   return "string";
            case VACIO:    return "void";
            default:       return "error";
        }
    }
}
```

```java
// Categoria.java
package ve.edu.unet.nodosAST;
public enum Categoria { VARIABLE, ARREGLO, FUNCION, PROCEDIMIENTO, PARAMETRO }
```

Extender `tipoOp.java` — **reutiliza `NodoOperacion`**, con `opDerecho == null` para `no`:

```java
public enum tipoOp { menor, igual, mas, menos, por, entre, y, o, no }
```

Nodos nuevos, todos extendiendo `NodoBase`, todos con getters, con la línea fijada desde la acción:

| Clase | Campos |
|-------|--------|
| `NodoDeclaracion` | `Tipo tipoDeclarado`, `String nombre` |
| `NodoDeclaracionArreglo` | **`extends NodoDeclaracion`** + `int tamano` |
| `NodoAccesoArreglo` | `String nombre`, `NodoBase indice` |
| `NodoFuncion` | `Tipo tipoRetorno`, `String nombre`, `List<NodoDeclaracion> parametros`, `NodoBase cuerpo` |
| `NodoProcedimiento` | `String nombre`, `List<NodoDeclaracion> parametros`, `NodoBase cuerpo` |
| `NodoLlamada` | `String nombre`, `List<NodoBase> argumentos` |
| `NodoRetorno` | `NodoBase expresion` (puede ser `null`) |
| `NodoBooleano` | `boolean valor` |
| `NodoString` | `String valor` |

### Correccion verificada: los arreglos como DESTINO

La especificacion original de este plan tenia un hueco: definia `NodoAccesoArreglo` para **leer** un arreglo dentro de una expresion, pero no definia como se representa un arreglo como **destino**. Sin eso, `A[i*2+1] := 5` (el ejemplo literal del enunciado) perdia el indice al construir el AST. Comprobado ejecutando el parser: el volcado mostraba `Asignacion a: A` seguido solo del valor, sin rastro del indice.

La solucion NO es crear nodos nuevos, sino extender los existentes de forma aditiva:

| Nodo | Campo anadido | Semantica |
|---|---|---|
| `NodoAsignacion` | `NodoBase indice` + ctor `(String, NodoBase indice, NodoBase expresion)` | `null` = `x := e`; poblado = `A[i] := e`. Metodo `esIndexada()` |
| `NodoLeer` | `NodoBase indice` + ctor `(String, NodoBase indice)` | idem para `read A[i]` |

Se eligio extender en vez de crear `NodoAsignacionArreglo` para que M2 conserve su rama `instanceof NodoAsignacion` existente sin cambios y solo agregue el chequeo de `esIndexada()`.

### ⚠ Trampa de orden en los `instanceof`

`NodoDeclaracionArreglo` **hereda de** `NodoDeclaracion` (necesario para que un parametro de tipo arreglo quepa en la `List<NodoDeclaracion>` de una funcion). Consecuencia:

> `unNodoDeclaracionArreglo instanceof NodoDeclaracion` es **true**.

En toda cadena de `if / else if` hay que chequear **`NodoDeclaracionArreglo` ANTES que `NodoDeclaracion`**. Si se invierte, los arreglos se clasifican como declaraciones simples, en silencio y sin error de compilacion. Aplica a `Util.java`, a `AnalizadorSemantico` y al `Generador` de M2.

Y extender `NodoBase` con las anotaciones que produce el semántico y consume M2:

```java
    /* Anotaciones que rellena AnalizadorSemantico */
    private Tipo tipo = Tipo.ERROR;                 // tipo sintetizado de la expresion
    private ve.edu.unet.RegistroSimbolo registro;   // simbolo resuelto (ID, arreglo, llamada)
    public Tipo getTipo()           { return tipo; }
    public void setTipo(Tipo t)     { tipo = t; }
    public ve.edu.unet.RegistroSimbolo getRegistro()       { return registro; }
    public void setRegistro(ve.edu.unet.RegistroSimbolo r) { registro = r; }
```

> Esto introduce un import del paquete padre `ve.edu.unet` desde `ve.edu.unet.nodosAST`, que ya importa en sentido inverso. Es legal en Java y compila; **no** "arreglarlo" moviendo clases.

## 3.3 Extender `Util.imprimirAST`

Añadir una rama por cada nodo nuevo e imprimir también `getLinea()` y `getTipo()`. **Cambiar todos los `System.out` por `System.err`** (Contrato D).

### ✅ Compuerta 3

```bash
java -jar $ESP/java-cup-11b.jar -parser parser -symbols sym \
     -package ve.edu.unet -destdir src/ve/edu/unet/ $ESP/sintactico.cup
```

Debe reportar **`0 conflicts detected`**. Luego compilar sin errores y parsear este fuente sin emitir ningún JSON:

```
int x; int A[10]; bool f; string s;
read x;
A[x*2+1] := 5;
f := not (x = 0) and 3 < x;
write A[0]
```

---

# FASE 4 — Tipos y tabla de símbolos con pila de ámbitos

## 4.1 `RegistroSimbolo.java` — extender sin romper

**Conservar** el constructor actual `(String, int, int)` y los getters existentes (`getIdentificador`, `getNumLinea`, `getDireccionMemoria`, `setDireccionMemoria`): `Generador.java` depende de ellos. Añadir:

| Campo | Para qué |
|-------|----------|
| `Tipo tipo` | tipo de la variable, o tipo de retorno si es función |
| `Categoria categoria` | VARIABLE / ARREGLO / FUNCION / PROCEDIMIENTO / PARAMETRO |
| `int tamano` | nº de elementos si es ARREGLO; 1 en otro caso |
| `int nivel` | 0 = ámbito global; >0 = dentro de función/procedimiento |
| `boolean esGlobal` | atajo de `nivel == 0`, lo consulta M2 |
| `int desplazamiento` | para locales/parámetros: índice dentro del marco, desde 0 |
| `List<RegistroSimbolo> parametros` | firma, si es FUNCION o PROCEDIMIENTO |

Añadir un constructor completo y dejar el viejo delegando en él con `tipo = ENTERO, categoria = VARIABLE, nivel = 0`.

## 4.2 `TablaSimbolos.java` — pila de ámbitos

```java
public class TablaSimbolos {
    /* Un ambito = nombre + sus simbolos */
    private static class Ambito {
        String nombre;
        HashMap<String, RegistroSimbolo> simbolos = new HashMap<String, RegistroSimbolo>();
        int siguienteDesplazamiento = 0;
        Ambito(String n) { nombre = n; }
    }

    private ArrayList<Ambito> pila      = new ArrayList<Ambito>();  // el ultimo es el actual
    private ArrayList<Ambito> historial = new ArrayList<Ambito>();  // todos, para -debug
    private int direccion = 0;                       // direcciones GLOBALES (igual que el base)
    private HashMap<String, RegistroSimbolo> tabla;  // alias del ambito global: compatibilidad
```

Métodos nuevos:

- `void abrirAmbito(String nombre)` — push de un `Ambito` nuevo; lo registra también en `historial`.
- `void cerrarAmbito()` — pop. **No descarta el `Ambito`**: queda en `historial`, y los `RegistroSimbolo` siguen referenciados desde los nodos del AST que el semántico anotó. Por eso M2 puede generar código después de que los ámbitos se cerraron.
- `int nivelActual()` — `pila.size() - 1`.
- `boolean insertar(RegistroSimbolo r)` — inserta en el ámbito actual; devuelve `false` si el nombre **ya existe en ese mismo ámbito** (redeclaración). Asigna dirección: si `nivelActual() == 0`, usa `direccion++` (y `direccion += tamano` para arreglos); si no, usa `siguienteDesplazamiento` del ámbito actual.
- `RegistroSimbolo buscar(String nombre)` — recorre la pila **del tope hacia la base**; primera coincidencia o `null`. Esto implementa el sombreado léxico.
- `RegistroSimbolo buscarEnAmbitoActual(String nombre)` — solo el tope.
- `RegistroSimbolo buscarGlobal(String nombre)` — solo la base.

**Compatibilidad obligatoria con M2 — no eliminar ni cambiar la firma de:**

- `getDireccion(String clave)` → delega en `buscar(clave)`; si no encuentra devuelve `-1` en vez de lanzar `NullPointerException` (hoy revienta).
- `BuscarSimbolo(String)` → delega en `buscar`.
- `InsertarSimbolo(String, int)` → se mantiene, insertando en el ámbito actual como `VARIABLE`/`ENTERO`.
- `cargarTabla(NodoBase)` → se mantiene pero **deja de usarse** en el flujo normal; queda solo para el modo `-permisivo` (§7.3). Añadirle un comentario que lo diga.
- `ImprimirClaves()` → recorre `historial` mostrando ámbito, nombre, tipo, categoría, nivel y dirección. Imprime por **`System.err`**.

El constructor abre el ámbito global de inmediato (`abrirAmbito("global")`) y `tabla` apunta al `HashMap` de ese ámbito. **`direccion` arranca en 0**, igual que el base: el preludio de `UtGen` limpia `dMem[0]` y la primera global vive ahí; cambiarlo desalinearía el código que M2 ya genera.

### ✅ Compuerta 4

Ejecutar `ejemplo_fuente/factorial.tny` (Tiny original). El AST y el `.tm` generado deben ser **byte a byte idénticos** a los de antes de esta fase — todavía no hay semántico que lo rechace. Guardar la salida antes y después y comparar con `diff`.

---

# FASE 5 — Analizador semántico (el núcleo del 30%)

Clase nueva `src/ve/edu/unet/AnalizadorSemantico.java`: un recorrido del AST **separado**, después del parseo y antes de la generación.

## 5.1 Estructura

```java
public class AnalizadorSemantico {
    private TablaSimbolos tabla;
    private RegistroSimbolo funcionActual = null;   // null = cuerpo principal
    private boolean vioRetorno = false;

    public AnalizadorSemantico(TablaSimbolos t) { tabla = t; }

    /* Punto de entrada. Dos pasadas. */
    public void analizar(NodoBase raiz) {
        registrarFirmas(raiz);   // pasada 1: funciones y procedimientos del nivel superior
        recorrer(raiz);          // pasada 2: todo lo demas
    }
    private void registrarFirmas(NodoBase n) { ... }
    private void recorrer(NodoBase n)        { ... }  // sentencias (sigue HermanoDerecha)
    private Tipo verificar(NodoBase n)       { ... }  // expresiones: devuelve el tipo sintetizado
}
```

**Por qué dos pasadas:** la pasada 1 registra las firmas de todas las funciones y procedimientos en el ámbito global antes de analizar cualquier cuerpo, de modo que un `procedure` pueda llamar a otro declarado más abajo en el archivo.

`recorrer` sigue siempre la cadena `getHermanoDerecha()`, igual que `TablaSimbolos.cargarTabla` y `Generador.generar` en el código base.

`verificar` devuelve el `Tipo` sintetizado y lo guarda con `setTipo(...)`. **Regla de oro contra errores en cascada: si algún operando ya es `Tipo.ERROR`, devolver `Tipo.ERROR` sin emitir un error nuevo.**

## 5.2 Reglas por tipo de nodo

### Sentencias (`recorrer`)

| Nodo | Acción |
|------|--------|
| `NodoDeclaracion` | `insertar` en el ámbito actual como `VARIABLE`. Si devuelve `false` → redeclaración |
| `NodoDeclaracionArreglo` | Igual, categoría `ARREGLO`, `tamano` del literal. Si `tamano <= 0` → error |
| `NodoFuncion` | `abrirAmbito(nombre)`; insertar parámetros como `PARAMETRO`; fijar `funcionActual`; `vioRetorno = false`; recorrer cuerpo; si el retorno no es `VACIO` y `!vioRetorno` → error; `cerrarAmbito()`; restaurar `funcionActual` |
| `NodoProcedimiento` | Igual, con `tipoRetorno = VACIO` y sin exigir `return` |
| `NodoAsignacion` | `buscar(id)`; si `null` → no declarada. Si es `ARREGLO` → "debe indexarse". Si es `CADENA` → solo lectura. Verificar la expresión y comparar tipos. Anotar `setRegistro(reg)` |
| `NodoAccesoArreglo` como destino | `buscar(id)`; debe existir y ser `ARREGLO`; índice `ENTERO`; anotar `setRegistro` |
| `NodoLeer` | El identificador debe existir y ser `ENTERO` (la instrucción `IN` de la TM lee enteros). Anotar `setRegistro` |
| `NodoEscribir` | Verificar la expresión: `ENTERO`, `BOOLEANO` o `CADENA` **literal**. Un `string` variable no tiene valor en esta versión → error |
| `NodoIf` / `NodoRepeat` | La prueba debe ser `BOOLEANO`; recorrer ramas / cuerpo |
| `NodoRetorno` | Si `funcionActual == null` → "return fuera de funcion". Si es procedimiento y trae expresión → error. Si es función, la expresión debe coincidir con el tipo de retorno. Marcar `vioRetorno = true` |
| `NodoLlamada` (sentencia) | Debe resolver a `PROCEDIMIENTO` o `FUNCION` (se permite descartar el valor); verificar aridad y tipos; anotar `setRegistro` |

### Expresiones (`verificar`)

| Nodo | Tipo resultante / regla |
|------|-------------------------|
| `NodoValor` | `ENTERO` |
| `NodoBooleano` | `BOOLEANO` |
| `NodoString` | `CADENA` |
| `NodoIdentificador` | Tipo del símbolo. Si no existe → error, `ERROR`. Si es `ARREGLO` → "debe indexarse". Anotar `setRegistro` |
| `NodoAccesoArreglo` | Índice `ENTERO`; resultado = tipo base del arreglo. Anotar `setRegistro` |
| `NodoOperacion` `mas/menos/por/entre` | Ambos `ENTERO` → `ENTERO` |
| `NodoOperacion` `menor/igual` | Ambos del **mismo** tipo y `ENTERO` o `BOOLEANO` (nunca `CADENA`) → `BOOLEANO` |
| `NodoOperacion` `y/o` | Ambos `BOOLEANO` → `BOOLEANO` |
| `NodoOperacion` `no` | `opIzquierdo` `BOOLEANO`, `opDerecho == null` → `BOOLEANO` |
| `NodoLlamada` (expresión) | Debe ser `FUNCION` (un `PROCEDIMIENTO` aquí es error); aridad y tipos exactos → tipo de retorno |

## 5.3 Mensajes de error — texto exacto

```
Variable 'X' no declarada
La variable 'X' ya fue declarada en este ambito
Tipos incompatibles: T1 vs T2
El indice de un arreglo debe ser de tipo int, se encontro T
'X' no es un arreglo
'X' es un arreglo y debe indexarse
El tamano del arreglo 'X' debe ser mayor que cero
Operacion aritmetica requiere operandos int, se encontro T
Operacion logica requiere operandos bool, se encontro T
No se pueden comparar valores de tipo T1 y T2
La condicion debe ser de tipo bool, se encontro T
read solo acepta variables de tipo int, 'X' es T
write no puede escribir un valor de tipo T
No se puede asignar a 'X': las cadenas son de solo lectura en esta version
'X' no es una funcion ni un procedimiento
'X' es un procedimiento y no retorna un valor
La funcion 'X' espera N argumentos, se recibieron M
Argumento N de 'X': se esperaba T1, se encontro T2
return con valor dentro del procedimiento 'X'
return fuera de una funcion o procedimiento
La funcion 'X' debe retornar un valor de tipo T
```

Todos se emiten con `Errores.semantico(nodo.getLinea(), mensaje)`.

### ✅ Compuerta 5

Cada `.tny` de error de la Fase 7 produce **exactamente** el tipo y la línea esperados, y ningún programa correcto produce errores.

---

# FASE 6 — Integración en `main()` y disciplina de salida

Reescribir el `main()` del bloque `parser code` de `sintactico.cup`:

```java
public static void main(String args[]) throws Exception {
    String archivo = null;
    boolean debug = false;
    for (int i = 0; i < args.length; i++) {
        if (args[i].equals("-debug")) debug = true;
        else archivo = args[i];
    }

    DefaultSymbolFactory sf = new DefaultSymbolFactory();
    parser parser_obj;
    if (archivo == null)
        parser_obj = new parser(new Lexico(new InputStreamReader(System.in), sf), sf);
    else
        parser_obj = new parser(new Lexico(
            new InputStreamReader(new java.io.FileInputStream(archivo)), sf), sf);

    parser_obj.parse();
    NodoBase root = parser_obj.action_obj.getASTroot();

    /* 1. Si el frontend ya fallo, no tiene sentido seguir */
    if (Errores.hayErroresPrevios() || root == null) {
        System.exit(1);
    }

    if (debug) Util.imprimirAST(root);     // a stderr

    /* 2. Analisis semantico */
    TablaSimbolos ts = new TablaSimbolos();
    new AnalizadorSemantico(ts).analizar(root);
    if (debug) ts.ImprimirClaves();        // a stderr

    if (Errores.hayErrores()) {
        System.exit(1);                    // no se genera codigo con errores
    }

    /* 3. Generacion de codigo: unico productor de stdout */
    Generador.setTablaSimbolos(ts);
    Generador.generarCodigoObjeto(root);
    System.exit(0);
}
```

**Códigos de salida** (dato nuevo que M4 necesita, no cambia ningún contrato): `0` = compilación exitosa y `.tm` en stdout; `1` = hubo errores, ya emitidos como JSON en stderr.

> `Generador.generarCodigoObjeto` todavía imprime cabeceras `------ CODIGO OBJETO ...` en stdout sin prefijo `*`, lo que rompe el cargador de la TM. **Eso es de M2**: no corregirlo aquí.

### ✅ Compuerta 6

```bash
java -cp $OUT:$ESP/java-cup-11b-runtime.jar ve.edu.unet.parser PROGRAMA_OK.tny 2>/dev/null | head -5   # solo .tm
java -cp $OUT:$ESP/java-cup-11b-runtime.jar ve.edu.unet.parser PROGRAMA_MAL.tny 2>&1 >/dev/null        # solo JSON
echo $?   # 1 para el que tiene errores, 0 para el correcto
```

---

# FASE 7 — Fixtures de prueba y guion de regresión

## 7.1 Programas de prueba

Crear `ejemplo_fuente/tinypp/`:

| Archivo | Qué prueba | Error esperado |
|---------|-----------|----------------|
| `01_declaraciones_ok.tny` | `int`, `bool`, `string`, asignaciones, `read`/`write` | — |
| `02_arreglos_ok.tny` | `int A[10]`, `A[i*2+1] := 5`, `write A[0]`, `read A[i]` | — |
| `03_funciones_ok.tny` | `function int suma(int a, int b)` + llamada en expresión | — |
| `04_procedimientos_ok.tny` | `procedure imprimir(int x)` + llamada como sentencia | — |
| `05_booleanos_ok.tny` | `not`, `and`, `or`, precedencia, `bool` en `if` | — |
| `06_scopes_ok.tny` | variable local que sombrea una global del mismo nombre | — |
| `07_factorial_tpp.tny` | el `factorial.tny` original, con declaraciones añadidas | — |
| `err_no_declarada.tny` | uso de `y` sin declarar | `semantico` |
| `err_tipos_asignacion.tny` | `int x; bool f; x := f` | `semantico` |
| `err_redeclaracion.tny` | `int x; bool x` | `semantico` |
| `err_indice_no_int.tny` | `int A[5]; bool f; A[f] := 1` | `semantico` |
| `err_args_funcion.tny` | llamada con nº de argumentos incorrecto | `semantico` |
| `err_tipo_argumento.tny` | llamada con tipo de argumento incorrecto | `semantico` |
| `err_string_asignacion.tny` | `string s; s := "hola"` | `semantico` |
| `err_condicion_no_bool.tny` | `int x; if x then ... end` | `semantico` |
| `err_falta_return.tny` | `function int f(int a) write a end` | `semantico` |
| `err_lexico.tny` | carácter `@` | `lexico` |
| `err_sintactico.tny` | falta un `;` entre sentencias | `sintactico` |

## 7.2 Guion de regresión `pruebas_m1.sh`

En esta misma carpeta. Para cada fixture ejecuta el compilador, captura stdout y stderr por separado y verifica:

- los `*_ok` salen con código `0` y **cero** líneas en stderr;
- los `err_*` salen con código `1` y stderr contiene al menos una línea JSON con el `tipo` esperado;
- **todas** las líneas de stderr son JSON válido (empiezan con `{` y traen `tipo`, `linea`, `mensaje`);
- el stdout de los `*_ok` no contiene ninguna línea que empiece por `-` (síntoma de las cabeceras de `Generador` contaminando el `.tm`).

Imprime `N/M pruebas pasaron` y devuelve código ≠ 0 si alguna falla.

## 7.3 Opcional — modo `-permisivo`

La Prueba de Integración 1 de `01_PLAN_DESARROLLO.md` ("compilar `factorial.tny` original") **dejará de pasar** con el tipado estricto: ese programa usa `x` y `fact` sin declararlas, que es justo el error que el enunciado obliga a detectar. Es correcto y esperado, pero bloquea a M2/M3/M4 si usan los fixtures viejos.

Solución primaria: usar `07_factorial_tpp.tny` como fixture de integración. Solución opcional, si el equipo la pide: un flag `-permisivo` que auto-declare como `int` las variables no declaradas (vía la vieja `cargarTabla`), reporte el hallazgo pero **no** impida la generación.

### ✅ Compuerta 7

`bash pruebas_m1.sh` reporta 100% de pruebas pasadas.

---

## Archivos tocados

**Nuevos**
```
parcial3/modulo_1/TEORIA_MODULO_1.md
parcial3/modulo_1/PLAN_MODULO_1.md
parcial3/modulo_1/pruebas_m1.sh
.../src/ve/edu/unet/Errores.java
.../src/ve/edu/unet/AnalizadorSemantico.java
.../src/ve/edu/unet/nodosAST/Tipo.java
.../src/ve/edu/unet/nodosAST/Categoria.java
.../src/ve/edu/unet/nodosAST/Nodo{Declaracion,DeclaracionArreglo,AccesoArreglo,
                                 Funcion,Procedimiento,Llamada,Retorno,Booleano,String}.java
.../ejemplo_fuente/tinypp/*.tny
```

**Modificados**
```
.../src/especificacion/lexico.flex         ← tokens, lineas, identificadores, sin prints
.../src/especificacion/sintactico.cup      ← gramatica, acciones, hooks de error, main()
.../src/ve/edu/unet/TablaSimbolos.java     ← pila de ambitos (compatible hacia atras)
.../src/ve/edu/unet/RegistroSimbolo.java   ← tipo, categoria, nivel, desplazamiento, firma
.../src/ve/edu/unet/nodosAST/NodoBase.java ← linea, tipo, registro
.../src/ve/edu/unet/nodosAST/tipoOp.java   ← y, o, no
.../src/ve/edu/unet/nodosAST/Util.java     ← nodos nuevos, salida a stderr
```

**Regenerados, nunca editados a mano:** `Lexico.java`, `parser.java`, `sym.java`.

---

## ⚠ Alerta de integración

Protocolo de la §3 de `02_INSTRUCCIONES_IA_EQUIPO.md`. Notificar **antes de hacer commit**:

| Cambio | A quién | Qué se rompe o cambia |
|--------|---------|----------------------|
| `tipoOp` gana `y`, `o`, `no` | **M2** | El `switch` de `Generador.generarOperacion` cae en `default` ("tipo de operacion desconocida"). M2 debe añadir los casos |
| Nodos AST nuevos | **M2** | La cadena de `instanceof` de `Generador.generar` imprime "Tipo de nodo a generar desconocido". M2 debe añadir ramas |
| `NodoBase` gana `getRegistro()` / `getTipo()` | **M2** | **Aditivo**, nada se rompe. Es la vía recomendada para resolver direcciones con ámbitos, en lugar de `getDireccion(String)` |
| `TablaSimbolos` con pila de ámbitos | **M2** | `getDireccion`, `BuscarSimbolo`, `InsertarSimbolo`, `cargarTabla` e `ImprimirClaves` **se conservan**. `getDireccion` deja de lanzar `NullPointerException` y devuelve `-1` si no encuentra |
| Nuevos terminales en `sym.java` | **M2** | Los valores numéricos de los terminales existentes **cambian** al regenerar. Solo importa si alguien hardcodeó un `sym.X` numérico — nadie lo hace hoy |
| Códigos de salida 0/1 y stderr solo-JSON | **M4** | Habilita leer el resultado sin heurísticas. Es una adición al Contrato B, no una modificación |
| Cabeceras de `Generador` en stdout | **M2** | **No es de M1.** `Generador` imprime `------ CODIGO OBJETO ...` sin prefijo `*`, lo que hace fallar a `readInstructions` de `TM.C` con "Bad location" |
| `Generador` revienta con NPE en `not` | **M2** | **No es de M1.** `generarOperacion` hace `generar(getOpDerecho())` sin chequear null, y el operador unario `no` tiene `opDerecho == null`. Crash duro verificado al compilar `f := not (x = 0)`. M2 debe anadir el caso unario |
| Comentarios de `UtGen` sin `*` | **M2 y M4** | **No es de M1.** `tmsimv1.html` mis-parsea `3: ST 0,0(5)  comentario` y pierde el registro base (`a3=0` en vez de `5`). Verificado |

---

## Verificación de extremo a extremo

```bash
cd parcial3/motor_compilador/Compiladores_Tiny_Generacion_2022_1
export ESP=src/especificacion OUT=/tmp/m1out

# 1. La gramatica no tiene conflictos
java -jar $ESP/java-cup-11b.jar -parser parser -symbols sym \
     -package ve.edu.unet -destdir src/ve/edu/unet/ $ESP/sintactico.cup | grep conflict
#    -> "0 conflicts detected (0 expected)."

# 2. El lexer se regenera
java -jar $ESP/jflex-full-1.8.2.jar -d src/ve/edu/unet/ $ESP/lexico.flex

# 3. Compila
javac -encoding ISO-8859-1 -nowarn -cp $ESP/java-cup-11b-runtime.jar \
      -d $OUT $(find src/ve -name '*.java')

# 4. Suite completa
bash ../../modulo_1/pruebas_m1.sh
#    -> "18/18 pruebas pasaron"

# 5. Demo del requisito estrella: error semantico con linea exacta
java -cp $OUT:$ESP/java-cup-11b-runtime.jar ve.edu.unet.parser \
     ejemplo_fuente/tinypp/err_tipos_asignacion.tny 2>&1 >/dev/null
#    -> { "tipo": "semantico", "linea": 3, "mensaje": "Tipos incompatibles: int vs bool" }

# 6. El .tm sigue siendo cargable por la TM (prueba cruzada con M3)
java -cp $OUT:$ESP/java-cup-11b-runtime.jar ve.edu.unet.parser \
     ejemplo_fuente/tinypp/07_factorial_tpp.tny 2>/dev/null > /tmp/facto_tpp.tm
grep -c '^[0-9]*:' /tmp/facto_tpp.tm                # debe haber instrucciones
grep -vcE '^\*|^[0-9]+:|^\s*$' /tmp/facto_tpp.tm    # lineas que romperian TM.C; ideal 0
```

El paso 6 fallará mientras M2 no limpie las cabeceras de `Generador`: es una dependencia cruzada documentada, no un defecto de M1.

## Orden de prioridad si falta tiempo

Según `01_PLAN_DESARROLLO.md`, si hay que recortar se abandona en orden inverso a esta lista:

1. Fases 0–1 (errores con línea) — **imprescindible**: sin esto M4 no tiene consola y se pierde lo más visible de la demo
2. Fases 2–4 limitadas a `int` + declaraciones + redeclaración
3. Fase 5 con verificación de tipos en asignaciones y aritmética
4. Arreglos
5. `bool` y los operadores lógicos
6. Funciones y procedimientos (lo más complejo; su generación de código en M2 es lo último que debe intentarse)
7. `string`


---

# Registro de ejecución — estado final

Las 7 fases estan implementadas y verificadas. Medidas reales al cierre:

| Metrica | Valor |
|---|---|
| Conflictos LALR | **0** (37 terminales, 27 no terminales, 69 producciones, 125 estados) |
| DFA del lexer | 106 estados minimizados (antes 52) |
| Build | limpio con `-encoding ISO-8859-1` |
| Suite de regresion | **58/68**, 0 fallas reales, 10 pendientes de M2, exit 0 |
| Ancla `07_factorial_tpp.tny` | **41 instrucciones identicas** al Tiny original |
| ASCII puro | todos los archivos nuevos y modificados |

El ancla es la prueba mas fuerte del modulo: reemplazar por completo la construccion de la tabla de simbolos (de un `HashMap` plano a una pila de ambitos con tipos) **no movio ni un byte** del codigo objeto generado. Eso demuestra que la asignacion de direcciones de memoria es equivalente a la del compilador base.

## Huecos de este plan que se descubrieron al ejecutarlo

Documentados porque la version original del plan los tenia mal y alguien podria volver a leerla:

1. **Arreglos como destino.** El plan definia `NodoAccesoArreglo` para leer un arreglo en una expresion, pero no definia como representar un arreglo como destino. `A[i*2+1] := 5` perdia el indice al construir el AST. Resuelto con el campo `indice` en `NodoAsignacion` y `NodoLeer` (ver seccion de la Fase 3).
2. **Colision de `getTipo()`.** El plan daba a `NodoDeclaracion` un campo `tipo` sin notar que `NodoBase` ya expone `getTipo()/setTipo()` para el tipo sintetizado. En Java los metodos se SOBRESCRIBEN, asi que el campo del padre quedaba inalcanzable. Resuelto con `getTipoDeclarado()`.
3. **Parametros de tipo arreglo inutilizables.** La regla "un identificador que resuelve a ARREGLO debe indexarse" hacia imposible pasar un arreglo como argumento. Se acepta ahora el identificador desnudo **solo** en posicion de argumento cuyo formal es `ARREGLO`.
4. **Cadenas de solo lectura, incoherente.** La regla estaba escrita solo para el destino no indexado, asi que `string A[3]; A[0] := "hola"` se aceptaba. Cerrado.

## Desviaciones aceptadas respecto del plan original

Todas son mejoras adoptadas durante la ejecucion:

| Desviacion | Motivo |
|---|---|
| Se eliminaron las declaraciones `precedence` heredadas | Podian hacer que CUP resolviera en silencio un conflicto real en vez de reportarlo. Sigue en 0 conflictos |
| Las producciones `error` no llaman a `Errores.sintactico` | `syntax_error()` ya emitio el JSON con el token y la linea reales; llamarlo otra vez duplicaba cada error |
| `lineaFin()` para el token EOF | Un error al final del archivo apuntaba a una linea inexistente y el IDE no podria subrayarla |
| `\r` suelto aceptado como salto de linea | Un fuente con finales Mac clasicos hacia reventar el lexer con un stack trace crudo, rompiendo el Contrato B |
| Guard de `NumberFormatException` en el lexer | Un literal fuera de rango mataba el compilador con stack trace. Ahora sale como error lexico con su linea |
| Dedup de "no declarada" **por ambito** | Sin dedup, 2 variables faltantes producian 10 mensajes. Con dedup global, un error dentro de una funcion podia quedar invisible. Por ambito es el comportamiento de gcc |
| Dos mensajes nuevos fuera de la lista | `'X' no es una variable` y `Argumento N de 'X': se esperaba int[], se encontro int`. No habia literal para esos casos |

## ⚠ Traspaso al Modulo 2 — dos defectos que bloquean la integracion

La suite los reporta como `PENDIENTE M2` y no bloquean al Modulo 1, pero **si bloquean la demo de extremo a extremo**.

**Deuda A — cabeceras decorativas en stdout.** `Generador.java:45` y `Generador.java:55` imprimen `------ CODIGO OBJETO ... ------`, que viola el Contrato D y hace fallar a `readInstructions` de `TM.C` con *"Bad location"*. Arreglo: borrar esos dos `System.out.println` y los `println()` vacios que los rodean.

**Deuda B — `NullPointerException` con el `not` unario.** `Generador.java:186` llama `generar(n.getOpDerecho())` sin chequear null, pero `exp_not ::= NOT exp_not` construye el nodo con `opDerecho == null` a proposito. Faltan ademas los `case y:`, `case o:` y `case no:` en el `switch` de `generarOperacion`. Repro minimo:

```
bool f;
f := not false
```

Lo grave no es solo el crash: el stack trace sale crudo por `stderr`, asi que **viola el Contrato B** y la consola del IDE (M4) no podra parsearlo.

Cuando M2 arregle cada deuda, la suite reportara `RESUELTO` y fallara hasta que se borren las entradas correspondientes de la tabla `DEUDA_CONOCIDA` de `pruebas_m1.sh`. Es intencional: evita que las exenciones queden puestas tapando problemas.
