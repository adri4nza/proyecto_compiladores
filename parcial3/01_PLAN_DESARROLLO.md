# Plan de Desarrollo — 4 Días
## Compilador Tiny++ + IDE con Depurador Visual

> **Premisa:** 4 integrantes, 4 módulos, 4 días. El objetivo de cada día tiene un **producto verificable** al final. Si un módulo no termina su entregable del día, bloquea la integración del día siguiente.

---

## Arquitectura de Integración

```
[React Frontend — M4]
   │  WebSocket: recibe eventos "tm_state"
   │  HTTP POST /compile: envía código fuente, recibe { tm_code, errors }
   ▼
[servidor_puente — Node.js/Express]
   │  spawn() con stdio piped
   ├──────────────────────────────────────────► [TM.C ejecutable — M3]
   │   stdin:  "load path\n" / "step\n" / "run\n" / "reset\n" / "break N\n"
   │   stdout: una línea JSON por comando (ver estado_tm_mock.json)
   │◄──────────────────────────────────────────────────────────────────
   └──────────────────────────────────────────► [java ve.edu.unet.parser archivo.tny — M1+M2]
       stdout: código .tm en texto plano
       stderr: JSON de errores con número de línea
```

### Contratos entre módulos — FIJADOS, no cambiar sin consenso del equipo

**Contrato 1: Estado de la TM** (ver `estado_tm_mock.json` para el ejemplo completo)
```json
{
  "registros":   { "PC": 0, "AC": 0, "AC1": 0, "GP": 0, "MP": 0, "FP": 0, "R2": 0, "R3": 0 },
  "memoria_datos": { "0": 0, "_size": 1024 },
  "memoria_instrucciones": [ { "linea": 0, "op": "LD", "r": 6, "d": 0, "s": 0, "comentario": "..." } ],
  "total_instrucciones": 42,
  "instruccion_ejecutada": { "linea": 0, "op": "LD", "r": 6, "d": 0, "s": 0 },
  "estado": "ejecutando",
  "resultado_paso": "OK",
  "mensaje_error": null,
  "esperando_input": false,
  "breakpoints": []
}
```

**Contrato 2: Errores del compilador** (una línea JSON por error en stderr)
```json
{ "tipo": "lexico",    "linea": 3,  "mensaje": "Caracter ilegal: @" }
{ "tipo": "sintactico","linea": 7,  "mensaje": "Se esperaba SEMI" }
{ "tipo": "semantico", "linea": 12, "mensaje": "Variable 'x' no declarada" }
```

**Contrato 3: Comandos stdin → TM.C**

| Comando | Respuesta |
|---------|-----------|
| `load <ruta>\n` | JSON con estado inicial + `memoria_instrucciones` completa |
| `step\n` | JSON con estado tras ejecutar una instrucción |
| `run\n` | JSON con estado final (HALT o breakpoint) |
| `reset\n` | JSON con estado reseteado |
| `break <linea>\n` | JSON confirmando el toggle del breakpoint |

---

## DÍA 1 — Infraestructura y Base Funcional

**Meta del día:** Todos los módulos tienen su estructura base corriendo. No hay features de Tiny++ todavía, pero la cadena completa funciona con el Tiny original.

### Mañana — Setup general (todos juntos, ~2 horas)

- [ ] Crear el repositorio Git con la estructura de carpetas del monorepo acordada
- [ ] **M1+M2 — Configurar IntelliJ (ver guía completa en `00_CONTEXTO_PROYECTO.md` sección 9):**
  - Abrir `motor_compilador/` como proyecto
  - Cambiar el SDK a cualquier JDK 11 disponible (`File → Project Structure → Project`)
  - En `Libraries`: eliminar `especificacion` (rota), conservar solo `especificacion1`
  - Build → Build Project → debe compilar sin errores rojos
  - Correr con `factorial.tny` y verificar salida en consola
- [ ] **M3 — Compilar TM.C original en WSL:**
  ```bash
  cd simulador_tm && gcc TM.C -o tm && ./tm ejemplo_generado/facto.tm
  ```
- [ ] **M4 — Inicializar proyecto React:**
  ```bash
  cd interfaz-ide && npm create vite@latest . -- --template react-ts && npm install
  ```
- [ ] Copiar `estado_tm_mock.json` al proyecto React como fixture de datos
- [ ] Acordar en voz alta los 3 contratos (ya están fijados arriba) y que todos los lean

### Módulo 1 — Frontend del Compilador (Lexico + Parser + AST)

**Tarea del día:** Entender profundamente el código base y definir QUÉ hay que agregar.

- [ ] Leer y anotar `Lexico.java`, `parser.java`, todos los nodos del AST
- [ ] Leer los archivos fuente `.flex` y `.cup` originales si están disponibles (o reconstruir la especificación a partir del código generado)
- [ ] Listar los nuevos tokens necesarios para Tiny++:
  - Palabras reservadas: `int`, `bool`, `string`, `true`, `false`, `function`, `procedure`, `return`
  - Símbolos: `[`, `]`, `,`, `"` (inicio de string literal)
- [ ] Listar los nuevos nodos AST necesarios:
  - `NodoDeclaracion` (tipo + nombre)
  - `NodoDeclaracionArreglo` (tipo + nombre + tamaño)
  - `NodoAccesoArreglo` (nombre + expresión de índice)
  - `NodoFuncion` (nombre + parámetros + tipo retorno + cuerpo)
  - `NodoProcedimiento` (nombre + parámetros + cuerpo)
  - `NodoLlamada` (nombre + argumentos)
  - `NodoRetorno` (expresión)
  - `NodoBooleano` (valor true/false)
  - `NodoString` (literal de cadena)
- [ ] Diseñar la nueva gramática Tiny++ (reglas BNF/EBNF de las nuevas construcciones)
- [ ] **Entregable del día:** Documento con los nuevos tokens, nodos y reglas gramaticales. El compilador base sigue funcionando sin errores.

### Módulo 2 — Backend / Generación de Código

**Tarea del día:** Entender el generador actual y planificar las estructuras de memoria nuevas.

- [ ] Leer y anotar `Generador.java` y `UtGen.java` completamente
- [ ] Entender el layout de memoria actual de la TM:
  ```
  dMem[0]       ← valor de init (GP apunta aquí en ejecución)
  dMem[1..N]    ← variables globales (asignadas por TablaSimbolos)
  dMem[MP]      ← tope de pila temporal (decrece hacia abajo)
  ```
- [ ] Diseñar el layout de memoria para Tiny++:
  ```
  dMem[0]       ← init
  dMem[1..G]    ← variables globales
  dMem[G+1..]   ← arreglos globales (tamaño fijo en compile time)
  dMem[MP..]    ← pila de marcos de activación (crece hacia abajo)
  ```
- [ ] Diseñar el marco de activación (Activation Record) para funciones:
  ```
  [ return value   ]  ← dMem[FP + 0]
  [ return address ]  ← dMem[FP + 1]  (old PC)
  [ old FP         ]  ← dMem[FP + 2]
  [ parámetro 1    ]  ← dMem[FP + 3]
  [ parámetro 2    ]  ← dMem[FP + 4]
  [ var local 1    ]  ← dMem[FP + 5]
  ...
  ```
- [ ] Identificar los nuevos registros necesarios: `FP` (frame pointer, usar `reg[4]`)
- [ ] **Entregable del día:** Documento con el diseño de memoria y el AR. El generador base sigue funcionando.

### Módulo 3 — Simulador TM

**Tarea del día:** Agregar el modo interactivo a TM.C y exponer el estado como JSON.

- [ ] **Primera línea del `main()`**: `setbuf(stdout, NULL);` — sin esto el JSON no llega al pipe
- [ ] Agregar flag `-s` (server mode): cuando se pasa, TM.C no ejecuta el programa, sino que entra en un loop leyendo comandos de stdin
- [ ] Implementar el loop de comandos:
  ```c
  // Pseudocódigo del modo interactivo
  char comando[256];
  while(fgets(comando, sizeof(comando), stdin)) {
      if (startsWith(comando, "load ")) { /* cargar .tm, imprimir JSON estado */ }
      else if (strcmp(comando, "step\n")==0) { /* ejecutar una instrucción, imprimir JSON */ }
      else if (strcmp(comando, "run\n")==0)  { /* ejecutar hasta HALT/breakpoint, imprimir JSON */ }
      else if (strcmp(comando, "reset\n")==0){ /* resetear PC y registros, imprimir JSON */ }
      fflush(stdout); // OBLIGATORIO después de cada printf de JSON
  }
  ```
- [ ] Implementar `imprimirEstadoJSON()`: función que arma el JSON completo del estado actual
- [ ] La función debe imprimir **todo en una línea** (sin saltos internos) y terminar con `\n`
- [ ] Probar manualmente: `echo "load facto.tm" | ./tm -s` debe devolver JSON válido
- [ ] **Entregable del día:** `./tm -s` funciona en modo interactivo. M4 puede usarlo con el mock y ya tiene el real.

### Módulo 4 — IDE React + Integración

**Tarea del día:** Estructura base del IDE con depurador renderizando el mock desde el Día 1.

- [ ] Instalar dependencias: `@monaco-editor/react`, `socket.io-client`
- [ ] Crear el layout principal (inspirado en VS Code):
  ```
  ┌─────────────────────────────────────────────────────────┐
  │  Toolbar: [Compilar] [▶ Run] [⏭ Step] [⏹ Stop] [↺ Reset] │
  ├───────────────────────────┬─────────────────────────────┤
  │                           │  DEBUGGER PANEL             │
  │   EDITOR DE CÓDIGO        │  Registros: PC GP MP AC AC1 │
  │   (Monaco Editor)         │  ─────────────────────────  │
  │                           │  Memoria de Instrucciones   │
  │                           │  (tabla, resalta línea PC)  │
  │                           │  ─────────────────────────  │
  │                           │  Memoria de Datos           │
  │                           │  (tabla sparse, resalta     │
  │                           │   celdas que cambiaron)     │
  ├───────────────────────────┴─────────────────────────────┤
  │  CONSOLA (errores compilación + output OUT de la TM)    │
  └─────────────────────────────────────────────────────────┘
  ```
- [ ] **Renderizar el depurador con `estado_tm_mock.json`** — no esperar a M3. Crear un hook `useTMState` que empiece con el mock y luego se conecte al WebSocket real.
- [ ] Implementar el componente `RegisterPanel`: muestra los 8 registros, resalta en verde el PC
- [ ] Implementar el componente `InstructionMemoryPanel`: tabla de instrucciones, fila actual resaltada
- [ ] Implementar el componente `DataMemoryPanel`: tabla de direcciones/valores, resalta celdas cambiadas
- [ ] Crear el `servidor_puente/index.js` básico con Express + WebSocket que:
  - `POST /compile`: spawna el Java compiler, devuelve `{ tm_code, errors }`
  - WebSocket: reenvía JSON del TM.C al cliente React
- [ ] Probar flujo completo con el compilador Tiny original: escribir `factorial.tny` → compilar → ver `.tm` en consola
- [ ] **Entregable del día:** El depurador renderiza correctamente el mock JSON. Los paneles de registros, instrucciones y datos funcionan visualmente. El botón Compilar invoca el Java y muestra resultado.

---

## DÍA 2 — Implementación de Features Tiny++

**Meta del día:** El compilador entiende declaraciones de variables, tipos básicos y arreglos. El IDE muestra estado real de la TM al hacer step.

### Módulo 1 — Lexer y Parser Tiny++

**Prioridad:** Variables tipadas primero, arreglos segundo, funciones al final.

**Mañana:**
- [ ] Modificar el archivo `.flex` para agregar los nuevos tokens (o editar `Lexico.java` directamente si no se tiene el .flex)
- [ ] Regenerar `Lexico.java` con JFlex (o editar el código Java del DFA manualmente)
- [ ] Agregar los nuevos nodos AST: `NodoDeclaracion`, `NodoBooleano`, `NodoString`
- [ ] Modificar el archivo `.cup` (o `parser.java`) para agregar las reglas de declaración:
  ```
  decl_stmt ::= tipo ID SEMI
  tipo      ::= INT | BOOL | STRING
  ```
- [ ] Probar: el parser debe aceptar `int x;` sin errores

**Tarde:**
- [ ] Agregar soporte de arreglos al Lexico (tokens `[`, `]`) y al Parser:
  ```
  decl_stmt     ::= tipo ID SEMI | tipo ID LBRACKET NUM RBRACKET SEMI
  assign_stmt   ::= ID ASSIGN exp | ID LBRACKET exp RBRACKET ASSIGN exp
  factor        ::= ID | ID LBRACKET exp RBRACKET | NUM | LPAREN exp RPAREN
  ```
- [ ] Agregar nodos: `NodoDeclaracionArreglo`, `NodoAccesoArreglo`
- [ ] Probar: el parser acepta `int A[10];` y `A[i*2+1] := 5;`
- [ ] **Entregable del día:** El compilador parsea programas Tiny++ con declaraciones y arreglos. El AST se imprime correctamente.

### Módulo 2 — Generación para Variables y Arreglos

- [ ] Ampliar `TablaSimbolos` para manejar tipos (`int`, `bool`, `string`) y arreglos (dirección base + tamaño)
- [ ] Modificar `Generador` para emitir código de declaraciones (reservar espacio en memoria global o en el frame)
- [ ] Implementar generación de código para acceso a arreglos:
  - Calcular offset: `base + indice` → emitir instrucciones para calcular la dirección dinámica
  - Para `A[i*2+1] := 10`: generar expresión del índice, sumar base del arreglo, hacer `ST`
  - Para `read A[i]`: similar pero con `LD`
- [ ] Probar con un programa Tiny++ simple que use arreglos
- [ ] **Entregable del día:** El generador produce código TM correcto para declaraciones y arreglos.

### Módulo 3 — Simulador: Instrucciones IN/OUT gráficas

- [ ] Implementar el manejo de `IN` en el simulador: cuando se ejecuta `IN`, pausar y emitir un evento JS solicitando input al usuario (en lugar de leer de stdin)
- [ ] Implementar el manejo de `OUT`: cuando se ejecuta `OUT`, emitir un evento JS con el valor a mostrar en la consola del IDE
- [ ] Implementar `run()` con soporte de breakpoints: ejecutar hasta `HALT` o hasta un breakpoint
- [ ] Probar que el ciclo completo `load → step → step → ...` devuelve estados consistentes
- [ ] **Entregable del día:** El simulador maneja IN/OUT gráficamente. Los breakpoints funcionan.

### Módulo 4 — IDE: Panel de Depuración Funcional

- [ ] Integrar `TMSimulator.ts` (de M3) en el componente React del depurador
- [ ] Implementar el panel de registros: muestra `PC`, `GP`, `MP`, `AC`, `AC1` actualizados en cada step
- [ ] Implementar la tabla de memoria de instrucciones: lista las instrucciones `.tm`, resalta la línea que apunta el PC
- [ ] Implementar la tabla de memoria de datos: muestra `dMem[0..N]` con los valores actuales, resalta en amarillo los que cambiaron en el último step
- [ ] Implementar el diálogo gráfico para `IN`: cuando el simulador pide input, mostrar un modal/input en el IDE
- [ ] Implementar la visualización de `OUT` en la consola del IDE
- [ ] Conectar los botones: Run, Step, Stop, Reset con el simulador
- [ ] **Entregable del día:** El depurador visual funciona completamente con `facto.tm`. Se puede ejecutar paso a paso y ver todos los registros y la memoria cambiar en tiempo real.

---

## DÍA 3 — Análisis Semántico y Funciones

**Meta del día:** El compilador valida tipos y scopes. El generador produce código para llamadas a funciones.

### Módulo 1 — Análisis Semántico

**Esta es la parte más crítica del 30% de nota del frontend.**

- [ ] Rediseñar `TablaSimbolos` con **pila de scopes**:
  ```java
  class TablaSimbolos {
      private Stack<HashMap<String, RegistroSimbolo>> pilaScopes;
      void abrirScope()    // push nuevo HashMap
      void cerrarScope()   // pop
      void insertar(String nombre, String tipo, ...)
      RegistroSimbolo buscar(String nombre)  // busca en toda la pila, de arriba a abajo
  }
  ```
- [ ] Implementar el **verificador semántico** como un recorrido separado del AST (después del parsing, antes de la generación):
  - Al encontrar `NodoDeclaracion`: insertar en tabla, error si ya existe en scope actual
  - Al encontrar `NodoIdentificador`: buscar en tabla, error si no existe
  - Al encontrar `NodoAsignacion`: verificar que los tipos son compatibles
  - Al encontrar `NodoOperacion`: verificar que operandos son del tipo correcto (`int` para aritméticas, `bool` para lógicas)
  - Al encontrar `NodoFuncion`/`NodoProcedimiento`: abrir nuevo scope, insertar parámetros, procesar cuerpo, cerrar scope
  - Al encontrar `NodoLlamada`: verificar que la función existe y que los argumentos coinciden en número y tipo
- [ ] Los errores semánticos deben generarse en el formato acordado: `{ "tipo": "semantico", "linea": N, "mensaje": "..." }`
- [ ] Agregar soporte de funciones y procedimientos al Lexico y Parser:
  ```
  func_decl  ::= FUNCTION tipo ID LPAREN param_list RPAREN stmt_seq END
  proc_decl  ::= PROCEDURE ID LPAREN param_list RPAREN stmt_seq END
  param_list ::= param | param_list COMMA param | ε
  param      ::= tipo ID
  call_stmt  ::= ID LPAREN arg_list RPAREN
  return_stmt::= RETURN exp SEMI | RETURN SEMI
  ```
- [ ] **Entregable del día:** El compilador rechaza variables no declaradas y tipos incompatibles con mensajes de error con número de línea. Acepta sintaxis de funciones y procedimientos.

### Módulo 2 — Generación de Código para Funciones

- [ ] Implementar un nuevo registro: `FP` = `reg[4]` (frame pointer)
- [ ] Implementar el **prólogo de función** (código que se emite al inicio de cada función):
  ```
  ; reservar espacio en la pila para el AR
  ST  FP, -1(MP)    ; guardar FP anterior
  LDA FP, -1(MP)    ; FP = nueva base del frame
  LDA MP, -N(MP)    ; MP -= tamaño del AR
  ; copiar parámetros al frame
  ```
- [ ] Implementar el **epílogo de función** (al final de la función o en `RETURN`):
  ```
  ; restaurar FP y MP
  LD  MP, 0(FP)     ; MP = FP anterior
  LD  FP, -1(MP)    ; FP = FP guardado
  ; saltar a la dirección de retorno
  LD  PC, -2(FP)    ; PC = return address guardado
  ```
- [ ] Implementar la **instrucción de llamada** (`call_stmt`):
  ```
  ; antes de llamar: guardar return address y saltar
  LDA reg, retAddr(PC)   ; calcular dirección de retorno
  ST  reg, retOffset(MP) ; guardar en el AR
  LDA PC, funcLabel(PC)  ; saltar a la función
  ```
- [ ] Probar con una función simple: `function int suma(int a, int b) ... end`
- [ ] **Entregable del día:** El generador produce código TM correcto para al menos una llamada a función con parámetros y valor de retorno.

### Módulo 3 — Robustez del Simulador

- [ ] Probar el simulador con programas que usen el stack (funciones generadas por M2)
- [ ] Verificar que el panel de memoria de datos muestra correctamente los frames de activación
- [ ] Manejar `srZERODIVIDE`, `srIMEM_ERR`, `srDMEM_ERR`: mostrar el error en la consola del IDE
- [ ] Implementar velocidad de ejecución configurable para el modo `run` automático
- [ ] **Entregable del día:** El simulador es robusto ante errores de runtime. El Run automático tiene velocidad configurable.

### Módulo 4 — Pulido del IDE y Semántica Visual

- [ ] Implementar resaltado de errores en el editor: subrayar la línea con error cuando la consola reporta un error con número de línea
- [ ] Mostrar tooltip/hover con el mensaje de error al pasar sobre la línea marcada
- [ ] En la memoria de datos, mostrar los nombres de variables cuando sea posible (mapear dirección → nombre desde la tabla de símbolos exportada)
- [ ] Implementar el marcador visual de PC en el panel de instrucciones: la instrucción actual destacada en verde
- [ ] Implementar visualización de breakpoints: clic en el margen izquierdo del panel de instrucciones para toggle
- [ ] **Entregable del día:** El IDE tiene integración completa entre errores del compilador y el editor. El depurador muestra nombres de variables.

---

## DÍA 4 — Integración Total, Pruebas y Preparación para Defensa

**Meta del día:** La demo funciona de punta a punta. Todos entienden todo.

### Mañana — Integración y pruebas de extremo a extremo (~4 horas)

- [ ] **Prueba de integración 1:** Compilar y ejecutar `factorial.tny` (Tiny original) — debe funcionar perfectamente
- [ ] **Prueba de integración 2:** Compilar y ejecutar un programa Tiny++ con:
  - Declaración de variables `int` y `bool`
  - Un arreglo con acceso indexado
  - Verificar errores semánticos (variable no declarada, tipo incorrecto)
- [ ] **Prueba de integración 3 (si M2 terminó funciones):** Programa Tiny++ con una función que recibe parámetros y retorna valor
- [ ] Resolver bugs críticos que bloqueen la demo (los de menor prioridad se documentan como "conocidos")
- [ ] Verificar que los errores léxicos, sintácticos y semánticos muestran número de línea correcto

### Tarde — Preparación para defensa (~4 horas)

- [ ] Cada integrante explica en 5 minutos (a los demás) cómo funciona su módulo
- [ ] Preparar el **script de la demo en vivo**:
  1. Mostrar el IDE vacío
  2. Escribir un programa Tiny++ con error semántico → mostrar el error con línea
  3. Corregir el error → compilar exitosamente → ver el .tm generado
  4. Ejecutar en el depurador paso a paso → mostrar registros y memoria cambiando
  5. Llegar a una instrucción `IN` → ingresar un valor desde el IDE → continuar
  6. Ver el `OUT` aparecer en la consola del IDE
  7. `HALT` → ejecución terminada
- [ ] Asegurarse de que **todos** pueden explicar:
  - El pipeline Lexico → Parser → AST → Semántico → Generador → .tm
  - Qué es y cómo funciona la pila de scopes
  - Qué es un Activation Record y para qué sirve
  - Cómo funciona el registro PC en la TM y cómo los saltos condicionales lo modifican
  - Cómo el depurador lee el estado de la TM
- [ ] Commit final: `git tag v1.0-entrega`
- [ ] Limpiar warnings obvios del código, sin romper funcionalidad

---

## Dependencias Críticas entre Módulos

```
M1 (Lexico/Parser) ──► M2 (Generador)
M1 (Semántico)     ──► M4 (errores en consola con línea)
M2 (Generador)     ──► M3 (nuevas instrucciones que el simulador debe manejar)
M3 (TMSimulator.ts)──► M4 (depurador visual)
```

**Regla de oro:** M1 y M3 son los que menos dependen de otros. M4 depende de todos. M2 depende de M1 (necesita el AST extendido).

---

## Prioridades si el tiempo se acaba

Si al final del Día 3 no está todo completo, el orden de prioridad para el Día 4 es:

| Prioridad | Feature | Por qué |
|-----------|---------|---------|
| 1 | Declaraciones de variables con tipos | 30% de nota, base de todo |
| 2 | Errores semánticos con número de línea | 30% de nota, muy visible en demo |
| 3 | Depurador visual paso a paso | 25% de nota, impacto visual enorme |
| 4 | Arreglos | 30% de nota, dentro del backend |
| 5 | IDE con Monaco Editor | 15% de nota |
| 6 | Funciones con Activation Records | 30% pero es lo más complejo |
| 7 | Tipos `bool` y `string` | Secundario si `int` ya funciona |
| 8 | Electron/Tauri packaging | 0% de nota directa |

> Una demo que muestra variables tipadas + errores semánticos con línea + depurador paso a paso con registros y memoria cubre el **70% del proyecto** de forma visual y comprobable.

---

## Resumen de Entregables por Día

| Día | M1 | M2 | M3 | M4 |
|-----|----|----|----|----|
| 1 | Diseño de tokens/nodos/gramática | Diseño de layouts de memoria | `TMSimulator.ts` funcional con `step()` | IDE base + compilación Tiny original |
| 2 | Parser acepta declaraciones y arreglos | Generación de código para arreglos | IN/OUT gráficos + breakpoints | Depurador visual funcional con `facto.tm` |
| 3 | Semántico funcional + sintaxis de funciones | Activation Records + llamadas | Robustez + velocidad configurable | Errores marcados en editor |
| 4 | Bug fixes + demo | Bug fixes + demo | Bug fixes + demo | Integración final + script de demo |

---

## Checklist de la Demo Final

- [ ] El IDE abre y muestra el editor de código
- [ ] Se puede escribir/pegar código Tiny++
- [ ] Al compilar con errores: la consola muestra el tipo de error y la línea exacta
- [ ] Al compilar sin errores: se genera el código .tm visible
- [ ] Al ejecutar Step: los registros PC, GP, MP, AC, AC1 se actualizan
- [ ] Al ejecutar Step: la instrucción actual se resalta en la memoria de instrucciones
- [ ] Al ejecutar Step: los valores de `dMem[]` se actualizan y los cambios se resaltan
- [ ] Al llegar a `IN`: aparece un diálogo en el IDE para ingresar el valor
- [ ] Al llegar a `OUT`: el valor aparece en la consola del IDE
- [ ] Al llegar a `HALT`: la ejecución se detiene correctamente
- [ ] Los breakpoints funcionan en modo Run
- [ ] Un programa Tiny++ con variable no declarada da error semántico
- [ ] Un programa Tiny++ con tipos incompatibles da error semántico
- [ ] Un arreglo con índice compuesto compila y ejecuta correctamente
