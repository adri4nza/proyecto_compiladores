# Proyecto Final — Compilador Tiny++ e IDE con Depurador Visual
## Contexto General del Equipo

**Universidad:** Universidad Nacional Experimental del Táchira (UNET)  
**Departamento:** Informática  
**Asignatura:** Compiladores e Intérpretes  
**Proyecto:** Compilador "Tiny++" + IDE con Depurador Visual de la Tiny Machine

---

## 1. Equipo y Roles

| Integrante | Módulo | Responsabilidad Principal |
|------------|--------|---------------------------|
| Francisco | A definir | Coordinación general + módulo asignado |
| Ivan | A definir | Módulo asignado |
| Carlos | A definir | Módulo asignado |
| Ricardo | A definir | Módulo asignado |

> La distribución sigue exactamente los 4 módulos del enunciado. Todos los integrantes deben conocer el flujo completo del proyecto para la defensa presencial, donde la nota individual depende del entendimiento de TODA la solución.

---

## 2. Descripción del Proyecto

El proyecto tiene dos componentes principales que deben integrarse:

### 2.1 Compilador Tiny++
Extensión del lenguaje Tiny trabajado en laboratorio. El compilador recibe código fuente `.tny` y genera instrucciones ensamblador `.tm` para la Tiny Machine (TM).

### 2.2 IDE con Depurador Visual
Entorno de desarrollo integrado con editor de código, consola de errores y un depurador visual que muestra el estado interno de la TM en tiempo real (registros, memoria de datos, memoria de instrucciones).

---

## 3. Requerimientos del Lenguaje Tiny++

Partiendo del lenguaje Tiny base (que ya maneja `if/then/else/end`, `repeat/until`, `read`, `write` y expresiones aritméticas/comparativas sobre enteros), se deben agregar:

### 3.1 Tipado Estricto y Nuevos Tipos
- Las variables **deben** declararse antes de usarse.
- Tipos soportados: `int`, `bool`, `string`.
- El lenguaje pasa de débilmente tipado a **fuertemente tipado**.

### 3.2 Arreglos Unidimensionales
- Declaración y uso de vectores indexados.
- Los índices pueden ser expresiones compuestas.
- Ejemplo válido: `A[i*2+1] := 10;`

### 3.3 Subrutinas y Funciones
- **Procedimientos**: sin valor de retorno.
- **Funciones**: con valor de retorno.
- Paso formal de parámetros.

### 3.4 Ámbito de Variables (Scopes)
- Las variables declaradas dentro de una función/bloque son **locales** a ese bloque.
- Solo las variables del cuerpo principal del programa tienen visibilidad **global**.

### 3.5 Verificaciones Semánticas Obligatorias
- Error si se usa una variable no declarada.
- Error si hay incompatibilidad de tipos en: asignaciones, operaciones aritméticas, paso de parámetros.

---

## 4. Requerimientos del IDE

### 4.1 Editor de Código
- Área de edición de código fuente Tiny++.
- Idealmente con resaltado de sintaxis.

### 4.2 Consola de Resultados y Errores
- Muestra mensajes de compilación.
- Indica claramente la **línea** donde ocurre cada error léxico, sintáctico o semántico.

### 4.3 Depurador Visual de la TM (Módulo estrella — 25% de la nota)
- Modo de ejecución **paso a paso** (Step-by-Step).
- Vista en tiempo real de:
  - **Registros**: `PC`, `GP`, `MP`, `AC`, `AC1`
  - **Memoria de Instrucciones**: código .tm cargado
  - **Memoria de Datos**: valores de variables en ejecución
- Las instrucciones `IN` (read) y `OUT` (write) deben resolverse **gráficamente** dentro del IDE, no en terminal externa.

---

## 5. Código Base Suministrado

### 5.1 Compilador Java: `Compiladores_Tiny_Generacion_2022_3`

Proyecto IntelliJ IDEA en Java, generado con **JFlex** (lexer) y **CUP** (parser LALR). Punto de partida definitivo y único para el compilador.

**Estructura de paquetes:**
```
src/ve/edu/unet/
├── Lexico.java          ← Analizador léxico (generado por JFlex)
├── sym.java             ← Constantes de tokens
├── parser.java          ← Analizador sintáctico (generado por CUP)
├── TablaSimbolos.java   ← Tabla de símbolos (HashMap plano, sin scopes)
├── RegistroSimbolo.java ← Registro individual de la tabla
├── Generador.java       ← Generador de código TM (recorre el AST)
├── UtGen.java           ← Utilidades de emisión de instrucciones TM
└── nodosAST/
    ├── NodoBase.java
    ├── NodoIf.java
    ├── NodoRepeat.java
    ├── NodoAsignacion.java
    ├── NodoLeer.java
    ├── NodoEscribir.java
    ├── NodoOperacion.java
    ├── NodoValor.java
    ├── NodoIdentificador.java
    ├── tipoOp.java       ← Enum de operaciones (mas, menos, por, entre, menor, igual)
    └── Util.java         ← Impresión del AST
```

**Capacidades actuales del compilador base:**
- Tokens: `IF`, `THEN`, `ELSE`, `END`, `REPEAT`, `UNTIL`, `READ`, `WRITE`, `:=`, `<`, `=`, `+`, `-`, `*`, `/`, `(`, `)`, `;`, números enteros, identificadores, comentarios `{...}`
- Genera código TM funcional para el lenguaje Tiny original.
- Tabla de símbolos: un solo scope global, sin tipos, sin arreglos, sin funciones.

**Lo que el compilador base NO tiene (a implementar):**
- Palabras reservadas: `int`, `bool`, `string`, `true`, `false`, `function`, `procedure`, `return`
- Tokens: `[`, `]`, `,`, literales string `"..."`, operadores booleanos
- Nodos AST para: declaraciones de variables, arreglos, funciones/procedimientos, retornos, llamadas
- Tabla de símbolos con pila de scopes, tipos de datos
- Verificación semántica de tipos y uso de variables no declaradas
- Generación de código para: activations records, offsets de arreglos, strings

### 5.2 TM.C — Simulador de la Tiny Machine (C)

Implementación original del libro de Louden. Define la arquitectura de la TM:

| Componente | Detalle |
|------------|---------|
| Memoria de instrucciones | 1024 slots (`iMem[]`) |
| Memoria de datos | 1024 slots (`dMem[]`) |
| Registros | 8 registros (`reg[0..7]`), `reg[7]` = PC |
| `reg[0]` = AC | Acumulador principal |
| `reg[1]` = AC1 | Acumulador secundario |
| `reg[5]` = GP | Global Pointer (base de variables globales) |
| `reg[6]` = MP | Memory Pointer (tope de pila temporal) |
| `reg[7]` = PC | Program Counter |

**Set de instrucciones:**
- **RR**: `HALT`, `IN`, `OUT`, `ADD`, `SUB`, `MUL`, `DIV`
- **RM**: `LD`, `ST`
- **RA**: `LDA`, `LDC`, `JLT`, `JLE`, `JGT`, `JGE`, `JEQ`, `JNE`

**Estado actual**: se ejecuta desde línea de comandos, no expone su estado interno al exterior. Requiere modificación para el depurador.

### 5.3 tmsimv1.html — Simulador TM Visual (Web)

Aplicación HTML/CSS/JavaScript de archivo único. Contiene:
- Interfaz visual completa con tema dark (IBM Plex Mono)
- Botones de control: Run, Step, Stop, Reset, Breakpoints
- Paneles de: memoria de instrucciones, memoria de datos, registros
- **Lógica del simulador TM embebida en JavaScript**

Este archivo es la **base absoluta** del depurador visual en el IDE.

---

## 6. Decisiones Tecnológicas del Equipo

### 6.1 Entorno Estándar
- **SO**: WSL con Ubuntu para todos los integrantes
- **Estructura**: Monorepo único con todo el código (compilador Java, simulador C y frontend web)

### 6.2 Estructura del Monorepo

```
parcial3/
├── motor_compilador/        ← M1 + M2: proyecto Java (IntelliJ IDEA)
│   └── Compiladores_Tiny_Generacion_2022_1/
│       └── src/
│           ├── especificacion/    ← lexico.flex, sintactico.cup, JARs
│           └── ve/edu/unet/       ← código Java fuente y nodos AST
├── simulador_tm/            ← M3: TM.C modificado + Makefile
├── servidor_puente/         ← Puente Node.js/Express (nuevo)
├── interfaz-ide/            ← M4: proyecto React + TypeScript
├── TM.C                     ← Original de referencia
├── tmsimv1.html             ← Referencia visual del depurador
├── estado_tm_mock.json      ← Contrato JSON oficial M3 → M4
├── 00_CONTEXTO_PROYECTO.md
└── 01_PLAN_DESARROLLO.md
```

### 6.3 Compilador (Módulo 1 + 2)
- **Lenguaje**: Java (sin cambio)
- **Herramientas**: JFlex + CUP — los JARs están en `src/especificacion/`
- **Flujo de trabajo**: editar `lexico.flex` → regenerar con JFlex → editar `sintactico.cup` → regenerar con CUP. **Nunca editar `Lexico.java`, `parser.java` o `sym.java` directamente.**
- **SDK requerido**: JDK 11 (el proyecto referencia "corretto-11" — ver sección 8 sobre bloqueos)

### 6.4 IDE Frontend (Módulo 4)
- **Framework**: React + TypeScript (Vite)
- **Estilo**: Inspirado en VS Code (editor central, panel depurador derecho, consola inferior)
- **Empaquetado**: web app local en primera entrega; Electron/Tauri si el tiempo lo permite
- **Base del depurador**: La lógica JS de `tmsimv1.html` portada a un componente React

### 6.5 Simulador TM (Módulo 3)
- **Base**: `TM.C` original compilado con `gcc` en WSL
- **Modificación requerida**: Agregar un modo interactivo (`-s` flag) donde TM.C lea comandos de `stdin` y emita JSON por `stdout` después de cada comando
- **Anti-buffering obligatorio**: `setbuf(stdout, NULL)` al inicio del `main()` — sin esto el JSON no llega al pipe

### 6.6 Arquitectura de Comunicación

```
[React Frontend]
   │  WebSocket (tm_state events)
   │  HTTP POST /compile
   ▼
[servidor_puente — Node.js/Express]
   │  child_process.spawn() con stdin/stdout piped
   │  child_process.spawn() para compilador Java
   ├──────────────────────────────────────────►  [TM.C ejecutable]
   │   stdin: "load path\n" / "step\n" / "run\n" / "reset\n"    │
   │   stdout: JSON estado (una línea por comando)               │
   │◄─────────────────────────────────────────────────────────── │
   └──────────────────────────────────────────►  [java ve.edu.unet.parser archivo.tny]
       stdout: código .tm                                         │
       stderr: errores JSON con número de línea                   │
```

### 6.7 Contrato JSON: Estado de la TM (M3 → M4)

El ejecutable TM.C emite **una línea JSON** por `stdout` después de cada comando. Estructura completa:

```json
{
  "registros": {
    "PC": 10, "AC": 5, "AC1": 0,
    "GP": 0,  "MP": 20, "FP": 0,
    "R2": 0,  "R3": 0
  },
  "memoria_datos": {
    "0": 15, "1": 25, "20": 100,
    "_size": 1024
  },
  "memoria_instrucciones": [
    { "linea": 0, "op": "LD", "r": 6, "d": 0, "s": 0, "comentario": "..." },
    { "linea": 1, "op": "ST", "r": 0, "d": 0, "s": 0, "comentario": "..." }
  ],
  "total_instrucciones": 42,
  "instruccion_ejecutada": { "linea": 9, "op": "JLT", "r": 0, "d": 2, "s": 7 },
  "estado": "ejecutando",
  "resultado_paso": "OK",
  "mensaje_error": null,
  "esperando_input": false,
  "breakpoints": []
}
```

**Comandos que acepta TM.C por stdin:**

| Comando | Acción |
|---------|--------|
| `load <ruta>\n` | Carga un archivo .tm, devuelve estado inicial |
| `step\n` | Ejecuta una instrucción, devuelve nuevo estado |
| `run\n` | Ejecuta hasta HALT o breakpoint, devuelve estado final |
| `reset\n` | Reinicia PC y registros, devuelve estado inicial |
| `break <linea>\n` | Toggle breakpoint en esa línea |

**Valores posibles de `resultado_paso`:**

| Valor | Significado |
|-------|-------------|
| `"OK"` | Instrucción ejecutada sin problemas |
| `"HALT"` | Programa terminó normalmente |
| `"IMEM_ERR"` | PC fuera del rango de instrucciones |
| `"DMEM_ERR"` | Acceso a memoria de datos fuera de rango |
| `"ZERO_DIVIDE"` | División por cero |

### 6.8 Contrato de Errores: Compilador Java → Consola del IDE

El compilador Java imprime errores a `stderr` en formato JSON por línea:

```json
{ "tipo": "lexico",    "linea": 3,  "mensaje": "Caracter ilegal: @" }
{ "tipo": "sintactico","linea": 7,  "mensaje": "Se esperaba SEMI" }
{ "tipo": "semantico", "linea": 12, "mensaje": "Variable 'x' no declarada" }
{ "tipo": "semantico", "linea": 15, "mensaje": "Tipos incompatibles: int vs bool" }
```

### 6.9 Control de Versiones
- **Git obligatorio** (requisito del enunciado)
- Monorepo compartido entre los 4 integrantes
- Ramas por módulo (`modulo-1`, `modulo-2`, `modulo-3`, `modulo-4`), integración en `main`

---

## 7. Criterios de Evaluación

| Componente | Peso |
|------------|------|
| Análisis Léxico, Sintáctico y Semántico (Tiny++) | 30% |
| Generación de Código (Funciones, Arreglos, Scopes en TM) | 30% |
| Depurador Visual (Estado en vivo de TM) | 25% |
| Interfaz General del IDE y Consola | 15% |

### Condiciones críticas de evaluación
- Entrega **presencial** con asistencia **obligatoria** de todos los integrantes. El que no asiste no tiene nota.
- Si un integrante **no puede explicar** cualquier parte del proyecto (incluido lo generado con IA), el equipo pierde mínimo el **50% de la nota**.
- El uso de IA es permitido pero el estudiante debe entender y poder explicar **todo** el código entregado.

---

## 8. Restricciones y Riesgos Conocidos

| Riesgo | Impacto | Mitigación |
|--------|---------|------------|
| El código base Java no compila de entrada | Alto | Primera tarea del Día 1: levantarlo en IntelliJ y resolverlo (ver sección 9) |
| Integración entre módulos deja para el final | Alto | Contratos JSON acordados (secciones 6.7 y 6.8), M4 usa mock desde el Día 1 |
| Activations records para funciones son complejos | Alto | Simplificar: funciones sin recursión en primera versión funcional |
| Tiempo insuficiente para Electron/Tauri | Medio | Dejar como web app, es igualmente válido para la entrega |
| Un integrante no entiende la parte del otro | Alto | Reuniones diarias de 15 min donde cada uno explica su avance |
| Tipos `string` requieren gestión de heap/memoria | Medio | Implementar `string` como literales de solo lectura en primera versión |
| stdout buffering en TM.C congela el depurador | Alto | `setbuf(stdout, NULL)` al inicio del `main()` de TM.C — obligatorio |
| IntelliJ no encuentra el JDK "corretto-11" | Alto | Ver sección 9 para instrucciones exactas de configuración |

---

## 9. Guía de Arranque para el Equipo Java ( Día 1)

### 9.1 Configurar IntelliJ IDEA

1. Clonar el monorepo y abrir la carpeta `motor_compilador/` como proyecto en IntelliJ.
2. Ir a `File → Project Structure → Project` y cambiar el SDK al JDK 11 disponible en la máquina (el proyecto referencia "corretto-11" pero cualquier JDK 11 funciona — solo cambiar el nombre).
3. En `File → Project Structure → Libraries`:
   - Eliminar la librería llamada `especificacion` (apunta a `out/` que no existe en un clon fresco).
   - Verificar que la librería `especificacion1` apunte a `src/especificacion/` — esa sí tiene los JARs en git.
4. Hacer `Build → Build Project`. Debe compilar sin errores.
5. Configurar un Run Configuration: Main class = `ve.edu.unet.parser`, Program arguments = ruta a un archivo `.tny`.
6. Ejecutar con `ejemplo_fuente/factorial.tny` y verificar que imprime el AST y el código TM en la consola.

### 9.2 Flujo para modificar el Lexer o el Parser

> **NUNCA editar `Lexico.java`, `parser.java` ni `sym.java` directamente. Son archivos generados.**

**Para agregar tokens (editar el lexer):**
```bash
# 1. Editar: src/especificacion/lexico.flex
# 2. Regenerar Lexico.java desde la carpeta del proyecto:
java -jar src/especificacion/jflex-full-1.8.2.jar \
     -d src/ve/edu/unet/ \
     src/especificacion/lexico.flex
```

**Para cambiar la gramática (editar el parser):**
```bash
# 1. Editar: src/especificacion/sintactico.cup
# 2. Regenerar parser.java y sym.java:
java -jar src/especificacion/java-cup-11b.jar \
     -parser parser -symbols sym \
     -package ve.edu.unet \
     -destdir src/ve/edu/unet/ \
     src/especificacion/sintactico.cup
```

Después de regenerar, hacer `Build → Build Project` de nuevo en IntelliJ.

---

## 9. Artefactos que se Entregarán

1. Código fuente del compilador Tiny++ (Java, JFlex, CUP)
2. Código fuente del IDE (React + TypeScript)
3. Repositorio Git con historial de commits de los 4 módulos
4. Demostración en vivo: compilar un programa Tiny++, ejecutarlo paso a paso en el depurador visual
