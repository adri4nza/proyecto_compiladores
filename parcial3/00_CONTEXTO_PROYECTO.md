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

### 6.1 Compilador
- **Lenguaje**: Java (sin cambio)
- **Herramientas**: JFlex (lexer) + CUP (parser), igual que el código base
- **Punto de partida**: `Compiladores_Tiny_Generacion_2022_3` tal cual está

### 6.2 IDE Frontend
- **Framework**: React + TypeScript
- **Estilo**: Similar a VS Code / Kiro IDE (paneles laterales, editor central, consola inferior)
- **Empaquetado**: Electron o Tauri si el tiempo lo permite; de lo contrario queda como web app local
- **Base del depurador**: Migrar/integrar la lógica de `tmsimv1.html` dentro del proyecto React

### 6.3 Comunicación Frontend ↔ Compilador Java
- El compilador Java se expone como proceso externo
- El IDE lo invoca y lee su salida (código `.tm` + errores con número de línea)
- Mecanismo: servidor local Node.js/Express que actúa como puente, o directamente desde el proceso main de Electron

### 6.4 Simulador TM
- **Estrategia principal**: Extraer y portar la lógica JS del `tmsimv1.html` directamente al componente React del depurador (elimina dependencia de TM.C para la visualización)
- **Estrategia alternativa**: Modificar `TM.C` para comunicación por stdin/stdout y llamarlo como subproceso desde el backend Node.js

### 6.5 Control de Versiones
- **Git obligatorio** (requisito del enunciado)
- Repositorio compartido entre los 4 integrantes
- Ramas por módulo, integración en `main`

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
| El código base Java no compila de entrada | Alto | Primera tarea del Día 1: levantarlo en IntelliJ y resolverlo |
| Integración entre módulos deja para el final | Alto | Definir interfaces/contratos entre módulos el Día 1 |
| Activations records para funciones son complejos | Alto | Simplificar: funciones sin recursión en primera versión funcional |
| Tiempo insuficiente para Electron/Tauri | Medio | Dejar como web app, es igualmente válido para la entrega |
| Un integrante no entiende la parte del otro | Alto | Reuniones diarias de 15 min donde cada uno explica su avance |
| Tipos `string` requieren gestión de heap/memoria | Medio | Implementar `string` como literales de solo lectura en primera versión |

---

## 9. Artefactos que se Entregarán

1. Código fuente del compilador Tiny++ (Java, JFlex, CUP)
2. Código fuente del IDE (React + TypeScript)
3. Repositorio Git con historial de commits de los 4 módulos
4. Demostración en vivo: compilar un programa Tiny++, ejecutarlo paso a paso en el depurador visual
