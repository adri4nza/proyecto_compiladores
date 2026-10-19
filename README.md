# TM++ IDE — Compilador Tiny++ con Depurador Visual

Proyecto final de la asignatura **Compiladores e Intérpretes** — UNET.  
Compilador completo para el lenguaje **Tiny++** (fuertemente tipado, con arreglos, funciones y procedimientos) integrado con un IDE web que incluye un depurador visual paso a paso de la Tiny Machine.

---

## Tabla de Contenidos

- [TM++ IDE — Compilador Tiny++ con Depurador Visual](#tm-ide--compilador-tiny-con-depurador-visual)
  - [Tabla de Contenidos](#tabla-de-contenidos)
  - [Requisitos Previos](#requisitos-previos)
  - [Estructura del Proyecto](#estructura-del-proyecto)
  - [Setup Completo (Paso a Paso)](#setup-completo-paso-a-paso)
    - [Paso 1 — Compilar el Proyecto Java](#paso-1--compilar-el-proyecto-java)
      - [1a. Compilar todas las clases Java](#1a-compilar-todas-las-clases-java)
      - [1b. Regenerar el Lexer (solo si se modificó `lexico.flex`)](#1b-regenerar-el-lexer-solo-si-se-modificó-lexicoflex)
      - [1c. Regenerar el Parser (solo si se modificó `sintactico.cup`)](#1c-regenerar-el-parser-solo-si-se-modificó-sintacticocup)
    - [Paso 2 — Compilar el Simulador TM](#paso-2--compilar-el-simulador-tm)
    - [Paso 3 — Configurar e Iniciar el Servidor Puente](#paso-3--configurar-e-iniciar-el-servidor-puente)
      - [3a. Instalar dependencias](#3a-instalar-dependencias)
      - [3b. Crear el archivo `.env`](#3b-crear-el-archivo-env)
      - [3c. Iniciar el servidor](#3c-iniciar-el-servidor)
    - [Paso 4 — Iniciar el IDE React](#paso-4--iniciar-el-ide-react)
  - [Verificación](#verificación)
  - [Cómo Usar el IDE](#cómo-usar-el-ide)
  - [Documentación del Lenguaje](#documentación-del-lenguaje)
  - [Equipo](#equipo)
  - [Referencia Rápida de Comandos](#referencia-rápida-de-comandos)

---

## Requisitos Previos

Todo se ejecuta en **WSL con Ubuntu**. Verifica antes de empezar:

```bash
# Java 11 (compilador Tiny++)
java -version      # debe decir openjdk 11 o similar
javac -version

# Si no está instalado:
sudo apt update && sudo apt install -y openjdk-11-jdk

# Node.js >= 18 (servidor puente + IDE React)
node -v            # recomendado: v18 o superior (instalado con NVM)
npm -v

# GCC (simulador TM en C)
gcc --version

# Make
make --version
# Si no está instalado:
sudo apt install -y make
```

---

## Estructura del Proyecto

```
parcial3/
│
├── motor_compilador/                  ← M1 + M2: Compilador Java
│   └── Compiladores_Tiny_Generacion_2022_1/
│       ├── src/
│       │   ├── especificacion/        ← lexico.flex, sintactico.cup, JARs de JFlex/CUP
│       │   └── ve/edu/unet/           ← código fuente Java + nodos AST
│       └── out/production/            ← clases compiladas (generadas, no versionadas)
│
├── simulador_tm/                      ← M3: Simulador TM en C
│   ├── TM.C                           ← código fuente con modo servidor (-s)
│   ├── Makefile
│   └── tm                             ← binario compilado (no versionado)
│
├── servidor_puente/                   ← M4: Puente Node.js entre IDE y compilador/simulador
│   ├── index.js
│   ├── package.json
│   ├── .env                           ← configuración local (no versionado)
│   └── .env.example                   ← plantilla de configuración
│
├── interfaz-ide/                      ← M4: IDE React + TypeScript
│   ├── src/
│   │   ├── components/                ← Editor, Depurador, Consola, Toolbar
│   │   ├── hooks/                     ← useTMState, useSocket
│   │   └── types/                     ← contratos TypeScript (Contrato A y B)
│   └── package.json
│
├── modulo_1/                          ← Documentación y pruebas de M1 (Iván)
├── modulo_2/                          ← Documentación y pruebas de M2 (Ricardo)
│
├── 00_CONTEXTO_PROYECTO.md            ← Contexto general y decisiones del equipo
├── 01_PLAN_DESARROLLO.md              ← Plan de 4 días con entregables
├── 02_INSTRUCCIONES_IA_EQUIPO.md      ← Prompt de contexto para asistentes IA
├── 03_MANUAL_TINY_PLUS.md             ← Manual de referencia del lenguaje
└── README.md                          ← Este archivo
```

---

## Setup Completo (Paso a Paso)

Ejecuta estos pasos **en orden** desde una terminal WSL.

### Paso 1 — Compilar el Proyecto Java

El compilador Tiny++ se construye con `javac`. Los archivos fuente del lexer y parser son **generados** (JFlex y CUP); si `Lexico.java` o `parser.java` se pierden o quedan desactualizados, regenerarlos con los comandos de la sección 1b.

#### 1a. Compilar todas las clases Java

```bash
cd "/mnt/c/Users/<tu_usuario>/Documents/UNET/Compiladores/Entregables Materia/parcial3/motor_compilador/Compiladores_Tiny_Generacion_2022_1"

mkdir -p out/production/Compiladores_Lexico_2022_1

find src/ve/edu/unet -name "*.java" | xargs javac \
  -cp "src/especificacion/java-cup-11b.jar:src/especificacion/java-cup-11b-runtime.jar" \
  -d out/production/Compiladores_Lexico_2022_1 \
  --release 11
```

No debe haber errores. Los mensajes `Note: ... uses unchecked or unsafe operations` son advertencias normales del código generado por CUP y pueden ignorarse.

#### 1b. Regenerar el Lexer (solo si se modificó `lexico.flex`)

```bash
cd src/   # desde la carpeta Compiladores_Tiny_Generacion_2022_1

java -jar especificacion/jflex-full-1.8.2.jar \
     -d ve/edu/unet/ \
     especificacion/lexico.flex

# Luego volver a compilar (paso 1a)
```

#### 1c. Regenerar el Parser (solo si se modificó `sintactico.cup`)

```bash
cd src/   # desde la carpeta Compiladores_Tiny_Generacion_2022_1

java -jar especificacion/java-cup-11b.jar \
     -parser parser -symbols sym \
     -package ve.edu.unet \
     -destdir ve/edu/unet/ \
     especificacion/sintactico.cup

# Luego volver a compilar (paso 1a)
```

> **Regla de oro:** Nunca editar `Lexico.java`, `parser.java` ni `sym.java` directamente.
> Esos archivos son generados y se sobreescriben al regenerar.

---

### Paso 2 — Compilar el Simulador TM

```bash
cd "/mnt/c/Users/<tu_usuario>/Documents/UNET/Compiladores/Entregables Materia/parcial3/simulador_tm"

make
```

Esto genera el binario `tm`. Verificar que sea un ejecutable Linux:

```bash
file tm
# Debe decir: ELF 64-bit LSB executable, x86-64
# Si dice "Mach-O" (macOS), borrar y recompilar: make clean && make

./tm
# Debe imprimir: usage: ./tm <filename>
```

> El `Makefile` usa `-x c` para forzar compilación como C (no C++),
> porque la extensión `.C` en mayúscula haría que GCC lo trate como C++ por defecto.

**Limpiar y recompilar:**

```bash
make clean && make
```

---

### Paso 3 — Configurar e Iniciar el Servidor Puente

#### 3a. Instalar dependencias

```bash
cd "/mnt/c/Users/<tu_usuario>/Documents/UNET/Compiladores/Entregables Materia/parcial3/servidor_puente"

npm install
```

#### 3b. Crear el archivo `.env`

```bash
cp .env.example .env
```

El `.env.example` ya tiene los valores por defecto correctos. Solo debes revisar que las rutas relativas coincidan con tu estructura si moviste carpetas. El campo más importante:

```env
USE_REAL_TM=true    # true = usa el binario real de Carlos
                    # false = usa el simulador mock (para desarrollo sin el binario)
```

#### 3c. Iniciar el servidor

```bash
npm run dev
```

Al iniciar verás una salida similar a:

```
🟢  servidor_puente en http://localhost:3001
    Clases:    ✅  .../out/production/Compiladores_Lexico_2022_1
    TM Binary: ✅ Real (modo -s)   .../simulador_tm/tm
```

Si `Clases` muestra `❌`, volver al Paso 1.  
Si `TM Binary` muestra `Mock`, compilar el simulador (Paso 2) o cambiar `USE_REAL_TM=true` en `.env`.

---

### Paso 4 — Iniciar el IDE React

En una **segunda terminal WSL** (el servidor del paso 3 debe seguir corriendo):

```bash
cd "/mnt/c/Users/<tu_usuario>/Documents/UNET/Compiladores/Entregables Materia/parcial3/interfaz-ide"

npm install    # solo la primera vez

npm run dev
```

Abrir en el navegador: **`http://localhost:5173`**

---

## Verificación

Con el servidor puente corriendo, verificar que todo está conectado:

```bash
curl http://localhost:3001/health
```

Respuesta esperada (todos los campos en `true`):

```json
{
  "status":            "ok",
  "classes_compiled":  true,
  "runtime_jar_found": true,
  "tm_binary_found":   true,
  "using_mock_tm":     false,
  "ready":             true
}
```

**Prueba de compilación rápida:**

```bash
curl -s -X POST http://localhost:3001/compile \
  -H "Content-Type: application/json" \
  -d '{"codigo":"int x;\nread x;\nwrite x"}' \
  | python3 -m json.tool
```

Respuesta esperada: `"success": true` con `tm_code` no vacío.

---

## Cómo Usar el IDE

Una vez que el IDE está abierto en `http://localhost:5173`:

| Acción | Descripción |
|--------|-------------|
| **⚡ Compilar** | Envía el código fuente al compilador Java. Los errores léxicos, sintácticos y semánticos aparecen en la consola con número de línea. Si la compilación es exitosa, el código `.tm` se carga automáticamente en el simulador. |
| **⏭ Step** | Ejecuta una instrucción TM. Los registros, la instrucción resaltada y la memoria de datos se actualizan en tiempo real. |
| **▶ Run** | Ejecuta el programa hasta `HALT` o hasta el siguiente breakpoint. |
| **⏹ Stop** | Interrumpe la ejecución en modo Run. |
| **↺ Reset** | Reinicia el simulador al estado inicial (PC=0, memoria limpia). |
| **Diálogo de entrada** | Cuando el programa ejecuta `read x`, aparece automáticamente un diálogo para ingresar el valor entero. |
| **Breakpoints** | (Pendiente: clic en el número de línea del panel de instrucciones.) |

**Flujo típico de una sesión:**

1. Escribir (o pegar) código Tiny++ en el editor
2. Presionar **⚡ Compilar** — verificar que no haya errores en la consola
3. Presionar **⏭ Step** repetidamente para seguir la ejecución instrucción por instrucción
4. Observar el panel de **Registros** (especialmente el **PC**) y la **Memoria de Datos**
5. Cuando aparezca el diálogo de `read`, ingresar el valor y presionar Enter
6. La consola mostrará el resultado del `write` y finalmente `Programa terminado (HALT).`

---

## Documentación del Lenguaje

El manual completo de sintaxis y semántica de Tiny++ está en:

```
parcial3/03_MANUAL_TINY_PLUS.md
```

Incluye: tipos de datos, operadores, estructuras de control, funciones, procedimientos y 4 ejemplos de código listos para ejecutar.

---

## Equipo

| Integrante | Módulo | Responsabilidad |
|------------|--------|-----------------|
| Francisco | M4 | IDE React + TypeScript, servidor puente Node.js |
| Iván | M1 | Analizador léxico (JFlex), sintáctico (CUP), semántico, AST |
| Ricardo | M2 | Generación de código TM: arreglos, scopes, funciones, Activation Records |
| Carlos | M3 | Simulador TM en C con modo servidor (stdin/stdout JSON) |

---

## Referencia Rápida de Comandos

```bash
# Compilar el proyecto Java
find src/ve/edu/unet -name "*.java" | xargs javac \
  -cp "src/especificacion/java-cup-11b.jar:src/especificacion/java-cup-11b-runtime.jar" \
  -d out/production/Compiladores_Lexico_2022_1 --release 11

# Compilar el simulador TM
cd simulador_tm && make clean && make

# Iniciar el servidor puente (terminal 1)
cd servidor_puente && npm run dev

# Iniciar el IDE (terminal 2)
cd interfaz-ide && npm run dev

# Verificar integración
curl http://localhost:3001/health
```
