# UNIVERSIDAD NACIONAL EXPERIMENTAL DEL TÁCHIRA
## VICERRECTORADO ACADÉMICO
### DECANATO DE DOCENCIA
### DEPARTAMENTO DE INFORMÁTICA
**ASIGNATURA:** COMPILADORES E INTERPRETES  
**Proyecto Final: Compilador "Tiny++" e IDE con Depurador Visual**

---

## 1. Descripción General
El proyecto final de la materia consiste en ampliar las características que posee el lenguaje de programación diseñado durante las clases de laboratorio de la materia denominado Tiny, evolucionándolo hacia la especificación **"Tiny++"**. Adicionalmente, el equipo debe desarrollar un **Entorno de Desarrollo Integrado (IDE)** que integre un editor de código y un **Depurador Visual** acoplado a la Tiny Machine (TM).

El compilador creado debe partir de un código base que será suministrado, el cual genera como código objeto instrucciones para la máquina virtual Tiny. A continuación se procede a enumerar lo solicitado:

---

## 2. Características del Lenguaje (Tiny++)
El lenguaje debe contar con las características descritas a continuación:

1. **Tipado Estricto y Nuevos Tipos de Datos:** El lenguaje se debe convertir a fuertemente tipado. Todas las variables deben ser declaradas previo a su uso. Las variables declaradas pueden ser de los tipos: enteros (`int`), booleanos (`bool`) y cadenas de texto (`string`).
2. **Arreglos Unidimensionales:** Se permite la declaración y uso de vectores indexados. El uso de vectores se permite en expresiones, aceptando expresiones compuestas en su índice (ej: `A[i*2+1] := 10;`).
3. **Subrutinas y Funciones:** Están permitidas las declaraciones de procedimientos (sin valor de retorno) y funciones (con valor de retorno), incluyendo el paso formal de parámetros.
4. **Ámbito de Variables (Scopes):** Todas las variables declaradas tienen ámbito local al bloque o función dentro del cual son declaradas, manteniendo visibilidad global solo aquellas declaradas en el cuerpo principal del programa.
5. **Verificaciones Semánticas Obligatorias:** 
   - No permitir el uso de variables no declaradas.
   - Verificar la compatibilidad de tipos a la hora de llevar a cabo cualquier operación aritmética, asignación o paso de parámetros.

---

## 3. Características del Entorno de Desarrollo (IDE)
El equipo tiene libertad para elegir el lenguaje, framework o tecnología para crear el entorno gráfico del IDE. El mismo debe cumplir con las siguientes especificaciones:

1. **Editor de Código:** Área principal que permita la escritura de código fuente en lenguaje Tiny++.
2. **Consola de Resultados y Errores:** Visualización de mensajes de compilación, indicando claramente la línea en caso de detectar errores léxicos, sintácticos o semánticos.
3. **Depurador Visual de la Tiny Machine:** 
   - El IDE debe contar con un modo de ejecución paso a paso (Step-by-Step).
   - Durante la ejecución, la interfaz gráfica debe mostrar de forma explícita el estado en tiempo real de la **Memoria de Datos (Data Memory)**, la **Memoria de Instrucciones (Instruction Memory)** y el valor de los **Registros de la TM (PC, GP, MP, AC, AC1)**.
   - Las instrucciones nativas de entrada y salida (`read` y `write`) deben resolverse gráficamente dentro de la aplicación.

---

## 4. Distribución del Trabajo y Uso de Control de Versiones
El trabajo debe ser desarrollado colaborativamente. Es de carácter obligatorio el uso de Git como sistema de control de versiones. Para el desarrollo del proyecto, el equipo debe organizarse distribuyendo las responsabilidades en los siguientes 4 módulos principales:

* **Módulo 1: Analizador Frontend y Verificador Semántico**
  - Ampliación del Lexer, Parser y el Árbol de Sintaxis Abstracta (AST). Implementación del control de ámbitos (scopes) y la verificación estricta de tipos.
* **Módulo 2: Generación de Código Objetivo (Backend)**
  - Generación del código en lenguaje ensamblador de la TM para el cálculo de desplazamientos de arreglos, reserva de memoria de cadenas, e implementación de Marcos de Pila (Activation Records) para llamadas a funciones.
* **Módulo 3: Interfaz de Depuración del Simulador TM**
  - Modificación del código fuente original del simulador de la TM (en C o Java) para permitir el control externo (pausado, avance por instrucción) y la exposición del estado de sus memorias y registros al exterior.
* **Módulo 4: Interfaz Gráfica del IDE e Integración**
  - Desarrollo de la aplicación visual, integración del llamado al compilador, y enlace con los datos proporcionados por el simulador modificado para animar el depurador gráfico.

---

## 5. Criterios de Evaluación y Notas

### Ponderación Sugerida
* **Análisis Léxico, Sintáctico y Semántico (Tiny++):** 30%
* **Generación de Código (Funciones, Arreglos, Scopes en TM):** 30%
* **Interfaz General del IDE y Consola:** 15%
* **Depurador Visual (Estado en vivo de TM):** 25%

### Condiciones de Evaluación
* El trabajo debe ser presentado en equipos de máximo 4 personas, donde **TODOS** los integrantes deben trabajar en el proyecto y conocer cómo se hace cada parte del mismo.
* Debido a que la universidad además de enseñarles conocimientos técnicos, les debe enseñar un mínimo de ética, el hecho de entregar un compilador cuyo funcionamiento interno EN CUALQUIER ASPECTO sea una incógnita para UNO o todos los integrantes de un equipo conllevará a la pérdida de nota correspondiente al equipo (mínimo 50% de la nota del proyecto A TODOS SUS INTEGRANTES). En caso de ser una falta considerada grave, se elevará el caso hasta las instancias correspondientes en la universidad para la aplicación del reglamento interno.
* Se validará mediante una entrega en vivo con asistencia física de TODOS los integrantes del equipo. En caso de no asistir, el estudiante no tendrá nota en el proyecto final de la materia.
* Si se usaron asistentes basados en Inteligencia Artificial para el desarrollo del proyecto, el estudiante debe estar en la capacidad de explicar el flujo de ejecución, el manejo de memoria en la máquina virtual y la lógica subyacente del código presentado. No se aceptarán explicaciones basadas en el desconocimiento del código integrado.
