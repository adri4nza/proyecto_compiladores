# servidor_puente

Puente HTTP entre el IDE React (M4) y el compilador Java (M1+M2).

## Requisitos previos

1. **El proyecto Java debe estar compilado.** Abre `motor_compilador/` en IntelliJ y haz `Build → Build Project`. Las clases van a `out/production/Compiladores_Lexico_2022_1/`.
2. **Java 11+ en el PATH de WSL.** Verifica con `java -version`.
3. **Node.js en WSL.** Ya instalado vía NVM.

## Configuración

```bash
cp .env.example .env
# Edita .env si el proyecto Java está en una ruta distinta
```

## Levantar el servidor

```bash
cd servidor_puente
npm install
npm run dev      # modo desarrollo — se reinicia al cambiar index.js
# o
npm start        # modo producción
```

## Verificar que todo está conectado

```bash
curl http://localhost:3001/health
```

Respuesta esperada:
```json
{
  "status": "ok",
  "classes_compiled": true,
  "runtime_jar_found": true,
  "ready": true
}
```

Si `classes_compiled` es `false` → compilar el proyecto Java en IntelliJ.

## Probar la compilación

```bash
curl -X POST http://localhost:3001/compile \
  -H "Content-Type: application/json" \
  -d '{"codigo": "read x;\nwrite x"}'
```

## API

### POST /compile

**Body:**
```json
{ "codigo": "read x;\nwrite x" }
```

**Respuesta exitosa:**
```json
{
  "success": true,
  "tm_code": "0:   LD   6,0(0)  ...",
  "errors":  [],
  "stdout_raw": "...",
  "stderr_raw": ""
}
```

**Respuesta con errores:**
```json
{
  "success": false,
  "tm_code": "",
  "errors": [
    { "tipo": "lexico", "linea": 3, "mensaje": "Caracter ilegal: @" }
  ]
}
```

> **Nota para Ricardo e Ivan (M1+M2):** El servidor ya maneja el formato JSON del Contrato B.
> Cuando implementen el análisis semántico, los errores deben ir a `stderr` con exactamente
> este formato (una línea JSON por error):
> ```
> { "tipo": "semantico", "linea": 7, "mensaje": "Variable 'x' no declarada" }
> ```
