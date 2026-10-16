# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Idioma

El código, los comentarios, los documentos y los nombres de identificadores están en español. Escribe en español al interactuar en este repo y mantén la nomenclatura existente (`NodoBase`, `TablaSimbolos`, `emitirRM`, claves JSON como `memoria_datos`).

## Documentos de referencia obligatorios

Antes de trabajar en un módulo, lee el documento que corresponde (están en `parcial3/`):

| Documento | Para qué |
|-----------|----------|
| `02_INSTRUCCIONES_IA_EQUIPO.md` | **Léelo primero.** Está redactado como prompt de sistema para asistentes de IA: define los contratos inmutables, el Protocolo de Alerta de Integración y las trampas técnicas del proyecto. |
| `00_CONTEXTO_PROYECTO.md` | Arquitectura completa, requisitos de Tiny++, layout de memoria de la TM, guía de arranque de IntelliJ. |
| `01_PLAN_DESARROLLO.md` | Plan de 4 días con entregables por módulo y orden de prioridad si falta tiempo. |
| `Especificacion_Proyecto_TinyPlus.md` | Enunciado original de la asignatura (UNET, Compiladores e Intérpretes). |
| `estado_tm_mock.json` (raíz del repo) | Referencia canónica del Contrato A (estado de la TM). |

## Estado actual del repositorio

| Rama | Contenido |
|------|-----------|
| `main` | Código base suministrado + documentación del equipo |
| `modulo-1` (PR #1, abierta) | Frontend Tiny++ completo: lexer, gramática (0 conflictos LALR), AST con línea/tipo/registro, pila de ámbitos, `AnalizadorSemantico`, `Errores` (Contrato B), suite `pruebas_m1.sh` |
| `modulo-2` | Generación de código TM. **Parte de `modulo-1`** (no de `main`) porque depende del AST extendido |
| `modulo-4` | IDE React + servidor puente (remota) |

```
estado_tm_mock.json                      ← Contrato A, fixture para M4
parcial3/
├── TM.C                                 ← simulador TM original de Louden (sin modificar)
├── tmsimv1.html                         ← simulador TM visual, archivo único, base del depurador
├── modulo_1/                            ← PLAN_MODULO_1.md, TEORIA_MODULO_1.md, pruebas_m1.sh
├── motor_compilador/                    ← M1 + M2: compilador Tiny++ (Java, proyecto IntelliJ)
│   └── Compiladores_Tiny_Generacion_2022_1/
│       ├── src/especificacion/          ← lexico.flex, sintactico.cup, JARs de JFlex y CUP
│       ├── src/ve/edu/unet/             ← fuentes Java
│       ├── ejemplo_fuente/*.tny         ← programas Tiny originales (factorial.tny ya NO compila: no declara variables)
│       ├── ejemplo_fuente/tinypp/       ← fixtures Tiny++ (*_ok.tny y err_*.tny)
│       └── ejemplo_generado/*.tm        ← código TM de referencia
└── *.md                                 ← documentación del equipo
```

## Módulo 2 — punto de partida (traspaso de M1)

Lee la sección "Traspaso al Modulo 2" y la tabla "Alerta de integración" de `parcial3/modulo_1/PLAN_MODULO_1.md`. Resumen:

- **Resuelto en `modulo-2`:** Deuda A (cabeceras en stdout) y Deuda B (NPE del `not` unario, faltaban `case y/o`). `bool` se representa como entero 0/1: `not` = `1 - AC`, `and` = `MUL`, `or` = `ADD` normalizado; sin cortocircuito. Las declaraciones no emiten código (la dirección ya la asignó la tabla).
- **Pendiente:** `Generador.generar` todavía no conoce `NodoAccesoArreglo`, `NodoFuncion`, `NodoProcedimiento`, `NodoLlamada`, `NodoRetorno` ni `NodoString` (caen en un comentario `BUG`, que con `UtGen.debug=false` no se imprime: `write "fin"` se pierde en silencio). Las asignaciones/lecturas indexadas tampoco están generadas. **Chequear `NodoDeclaracionArreglo` antes que `NodoDeclaracion`** (hereda de ella).
- `NodoAsignacion` / `NodoLeer` tienen `indice` (`esIndexada()`) para `A[i] := e` y `read A[i]`.
- Resolver direcciones con `nodo.getRegistro()` (anotado por el semántico; sobrevive al cierre de ámbitos), no con `getDireccion(String)`. `RegistroSimbolo` trae `tipo`, `categoria`, `tamano`, `nivel`, `esGlobal`, `desplazamiento`, `parametros`.
- Globales: direcciones desde 0 (igual que el base). Locales/parámetros: `desplazamiento` desde 0 dentro del marco. `FP = reg[4]` reservado para los Activation Records.
- Decisiones fijadas: `string` = literales de solo lectura (solo `write "..."`), **sin recursión**.
- Ancla de regresión: `tinypp/07_factorial_tpp.tny` genera 41 instrucciones idénticas al Tiny original; no debe moverse salvo por las cabeceras eliminadas.
- `pruebas_m1.sh` está en 68/68 con `DEUDA_CONOCIDA` vacía. Si se agrega deuda nueva y luego se arregla, la suite la reporta `RESUELTO` y **falla a propósito** hasta borrar la entrada.

No hay README ni reglas de Cursor/Copilot. La verificación automatizada es `bash parcial3/modulo_1/pruebas_m1.sh` (pensada para WSL: en Git Bash de Windows falla si la ruta tiene espacios y porque el classpath usa `:`).

Los directorios `out/` y los `.class` están versionados en git y no hay `.gitignore` propio. Al compilar desde terminal usa un directorio de salida fuera del repo o añade un `.gitignore` antes de hacer commit.

## Comandos

### Compilar y ejecutar el compilador Java (verificado con JDK 26)

Desde `parcial3/motor_compilador/Compiladores_Tiny_Generacion_2022_1/`:

```bash
# El flag -encoding es OBLIGATORIO: los fuentes están en ISO-8859-1, no en UTF-8.
javac -encoding ISO-8859-1 \
      -cp src/especificacion/java-cup-11b-runtime.jar \
      -d /tmp/outj $(find src/ve -name '*.java')

# La clase principal es ve.edu.unet.parser (el main está en el bloque
# "parser code" de sintactico.cup). Sin argumento lee de stdin.
java -cp /tmp/outj:src/especificacion/java-cup-11b-runtime.jar \
     ve.edu.unet.parser ejemplo_fuente/factorial.tny
```

La documentación del equipo indica JDK 11 por el `.iml` de IntelliJ, pero el código compila y corre sin cambios con JDK moderno.

### Regenerar lexer y parser

`Lexico.java`, `parser.java` y `sym.java` son **generados**. Nunca los edites a mano: edita `.flex` / `.cup` y regenera.

```bash
# Lexer: editar src/especificacion/lexico.flex
java -jar src/especificacion/jflex-full-1.8.2.jar \
     -d src/ve/edu/unet/ src/especificacion/lexico.flex

# Gramática: editar src/especificacion/sintactico.cup
java -jar src/especificacion/java-cup-11b.jar \
     -parser parser -symbols sym -package ve.edu.unet \
     -destdir src/ve/edu/unet/ src/especificacion/sintactico.cup
```

### Compilar el simulador TM

`TM.C` **no compila tal como está** con gcc actual, pese a lo que dicen los documentos del plan:

- Usa `gets()`, eliminado de C11 y C++14 → `error: 'gets' was not declared in this scope`.
- La extensión `.C` en mayúscula hace que gcc lo trate como C++, lo que añade un error en `opCodeTab`/`op++` (`no 'operator++(int)' declared for postfix '++'`, línea 257).

Al portar el archivo a `simulador_tm/`, renómbralo a `tm.c` (minúscula) y sustituye las dos llamadas a `gets(in_Line)` (líneas ~357 y ~410) por `fgets(in_Line, LINESIZE, stdin)`. Después:

```bash
gcc tm.c -o tm && ./tm ../motor_compilador/.../ejemplo_generado/facto.tm
```

Y recuerda la trampa documentada: `setbuf(stdout, NULL);` como primera línea del `main()`, o el JSON nunca sale del pipe hacia Node.

### Simulador visual

`parcial3/tmsimv1.html` es autocontenido: ábrelo directamente en el navegador. Carga `.tm` por selector de archivo o pegando texto.

## Arquitectura

### Pipeline de datos

```
.tny → [M1: Lexico.java + parser.java → AST → semántico] → AST
     → [M2: Generador.java + UtGen.java]  → stdout: .tm   | stderr: JSON de errores
     → [M3: tm (C, modo -s)]  stdin: comandos | stdout: 1 línea JSON por comando
     → [servidor_puente: Node/Express, spawn de ambos procesos]
     → [M4: React + TS, WebSocket + POST /compile]
```

Los 4 módulos (M1 frontend del compilador, M2 generación de código, M3 simulador, M4 IDE) tienen un responsable distinto. Los **contratos entre módulos son inmutables**: si un cambio toca el JSON de estado, el JSON de errores, los comandos stdin, los nodos del AST, `TablaSimbolos` o el paquete `ve.edu.unet`, activa el Protocolo de Alerta de la sección 3 de `02_INSTRUCCIONES_IA_EQUIPO.md` antes de mostrar el código.

### Compilador: cómo encaja todo

El `main()` vive dentro de `sintactico.cup` (bloque `parser code`) y ejecuta: `parse()` → si hubo errores léxicos/sintácticos, `exit 1` → (`-debug`: `Util.imprimirAST` a stderr) → `new AnalizadorSemantico(ts).analizar(root)` → si hubo errores, `exit 1` → `Generador.setTablaSimbolos(ts)` → `Generador.generarCodigoObjeto(root)` → `exit 0`. Los errores salen por `Errores.lexico/sintactico/semantico` (Contrato B).

- **AST deliberadamente simple**: `NodoBase` solo tiene `HermanoDerecha`. Las secuencias de sentencias son listas enlazadas por hermano derecho; los hijos son campos concretos de cada subclase (`NodoIf.getPrueba()`, `NodoRepeat.getCuerpo()`, …). `NodoBase` lleva además `getLinea()`, `getTipo()` (tipo sintetizado) y `getRegistro()` (símbolo resuelto).
- `TablaSimbolos` es una pila de ámbitos (`abrirAmbito`/`cerrarAmbito`/`buscar`); los ámbitos cerrados quedan en `historial`. Conserva las firmas viejas (`getDireccion` devuelve `-1` si no encuentra, `cargarTabla` ya no se usa en el flujo normal).
- `UtGen` es el emisor de instrucciones TM y lleva el contador `instruccionActual` / `instruccionMasAlta`. El **backpatching** (`emitirSalto` / `cargarRespaldo` / `restaurarRespaldo`) hace que las líneas se impriman **fuera de orden de PC** (p. ej. la línea 13 sale después de la 39). Es correcto: tanto `TM.C` como `tmsimv1.html` indexan por el prefijo `loc:`, no por orden de aparición.
- Registros fijados en `UtGen`: `AC=0`, `AC1=1`, `GP=5`, `MP=6`, `PC=7`. El plan reserva `reg[4]` para `FP` (frame pointer) en los Activation Records de M2.
- Layout de memoria actual: `dMem[0]` = dirección máxima (el preludio la carga en MP), `dMem[1..N]` = globales vía `GP`, pila temporal descendiendo desde `MP` con `desplazamientoTmp` en `Generador`.

### Formato `.tm` y las dos trampas de integración

El lector de `TM.C` (`readInstructions`) ignora líneas en blanco y las que empiezan por `*`, exige el prefijo `N:` y rellena todo `iMem` con `HALT` por defecto.

1. **El stdout del compilador no es un `.tm` válido.** `Generador` imprime cabeceras (`------ CODIGO OBJETO ...`), el volcado del AST y las claves de la tabla de símbolos; además `lexico.flex` y las reglas del `.cup` imprimen trazas de tokens con `debug=true`. Nada de eso lleva prefijo `*`, así que `readInstructions` falla con *"Bad location"*. El Contrato D exige que stdout contenga **solo** el `.tm`: hay que mandar todo lo demás a stderr o eliminarlo (los flags `debug` están en `UtGen.debug`, en `action code` del `.cup` y en el bloque de declaraciones de `lexico.flex`).

2. **`tmsimv1.html` mis-parsea los comentarios del generador.** El parser JS espera los comentarios precedidos de `*` dentro de la línea; `UtGen` los emite sin marcador. Resultado comprobado: `3: ST 0,0(5)  leer: almaceno...` se parsea como `a3=0` en lugar de `a3=5` — se pierde el registro base y el programa se ejecuta mal, en silencio. `TM.C` no sufre esto porque deja de parsear al terminar los argumentos. Al conectar el generador con el depurador visual: o `UtGen` emite `* ` antes del comentario, o se relaja el parser de `tmsimv1.html`.

## Lenguaje: Tiny base vs. Tiny++

El compilador base **ya funcionaba** para Tiny: `if/then/else/end`, `repeat/until`, `read`, `write`, `:=`, enteros, `+ - * /`, `<`, `=`, comentarios `{...}`. El trabajo es **extenderlo, no reescribirlo**.

M1 ya implementó el frontend de Tiny++ (tipos `int`/`bool`/`string`, arreglos, funciones/procedimientos, `and`/`or`/`not`, ámbitos, verificación de tipos). Lo que falta es el **backend (M2)**: generación TM para declaraciones, arreglos con índice dinámico, operadores lógicos, literales `string` en `write` y Activation Records para llamadas.

Orden de prioridad acordado si falta tiempo: declaraciones tipadas → errores semánticos con número de línea → depurador paso a paso → arreglos → funciones con Activation Records.

## Git

Ramas por módulo (`modulo-1` … `modulo-4`) e integración en `main`. El uso de Git es requisito evaluado del proyecto.

- Conventional Commits. **Sin** `Co-Authored-By` ni atribución a IA en commits ni en PRs: los participantes del repo son solo los integrantes del equipo.
