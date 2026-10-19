/**
 * servidor_puente — Puente entre el IDE React (M4), compilador Java (M1+M2) y simulador TM (M3)
 *
 * HTTP:
 *   GET  /health   → verifica configuración y binarios
 *   POST /compile  → compila código Tiny++, devuelve .tm + errores (SIEMPRE HTTP 200)
 *
 * WebSocket (socket.io):
 *   Cliente → servidor:
 *     iniciar_simulador({ tm_code }) → carga el .tm en el simulador
 *     step                           → ejecuta una instrucción
 *     run                            → ejecuta hasta HALT o breakpoint
 *     reset                          → reinicia el simulador
 *     stop                           → detiene el run continuo
 *     break({ linea })               → toggle breakpoint
 *
 *   Servidor → cliente:
 *     tm_state(TMState)              → estado actualizado después de cada comando
 *     tm_error(string)               → error del simulador
 *
 * Responsable: Francisco (M4)
 * Contratos: 02_INSTRUCCIONES_IA_EQUIPO.md, Sección 2
 */

import { createServer }   from 'http'
import { Server as IO }   from 'socket.io'
import express            from 'express'
import cors               from 'cors'
import { config }         from 'dotenv'
import { spawn }          from 'child_process'
import { join, resolve }  from 'path'
import { writeFileSync, unlinkSync, existsSync, readFileSync } from 'fs'
import { tmpdir }         from 'os'
import { randomUUID }     from 'crypto'
import { fileURLToPath }  from 'url'
import { dirname }        from 'path'

config()

const __dirname = dirname(fileURLToPath(import.meta.url))

// ── Configuración ──────────────────────────────────────────────────────────────
const PORT             = process.env.PORT              ?? 3001
const JAVA_PROJECT_REL = process.env.JAVA_PROJECT_PATH ?? '../motor_compilador/Compiladores_Tiny_Generacion_2022_1'
const JAVA_MAIN_CLASS  = process.env.JAVA_MAIN_CLASS   ?? 've.edu.unet.parser'
const JAVA_BIN         = process.env.JAVA_BIN          ?? 'java'
const COMPILE_TIMEOUT  = parseInt(process.env.COMPILE_TIMEOUT_MS ?? '15000')
const TM_BINARY_REL    = process.env.TM_BINARY_PATH    ?? '../simulador_tm/tm'

const JAVA_PROJECT_PATH = resolve(__dirname, JAVA_PROJECT_REL)
const CLASSES_DIR  = join(JAVA_PROJECT_PATH, 'out', 'production', 'Compiladores_Lexico_2022_1')
const RUNTIME_JAR  = join(JAVA_PROJECT_PATH, 'src', 'especificacion', 'java-cup-11b-runtime.jar')
const CLASSPATH    = `${CLASSES_DIR}:${RUNTIME_JAR}`
const TM_BINARY    = resolve(__dirname, TM_BINARY_REL)
// USE_REAL_TM=true requiere que Carlos haya agregado el modo servidor (-s) a TM.C.
// Con false, el servidor usa el simulador mock aunque el binario exista.
const USE_REAL_TM  = process.env.USE_REAL_TM === 'true'

// Mock de estado para cuando el binario TM aún no existe
const TM_MOCK_PATH = resolve(__dirname, '../interfaz-ide/src/data/estado_tm_mock.json')

// ── Express + HTTP server + socket.io ─────────────────────────────────────────
const app        = express()
const httpServer = createServer(app)
const io         = new IO(httpServer, {
  cors: { origin: 'http://localhost:5173', methods: ['GET', 'POST'] },
})

app.use(cors({ origin: 'http://localhost:5173' }))
app.use(express.json({ limit: '1mb' }))

// ══════════════════════════════════════════════════════════════════════════════
// HTTP ROUTES
// ══════════════════════════════════════════════════════════════════════════════

app.get('/health', (_req, res) => {
  res.json({
    status:            'ok',
    java_project_path: JAVA_PROJECT_PATH,
    classes_compiled:  existsSync(CLASSES_DIR),
    runtime_jar_found: existsSync(RUNTIME_JAR),
    tm_binary_found:   existsSync(TM_BINARY),
    tm_binary_path:    TM_BINARY,
    using_mock_tm:     !existsSync(TM_BINARY),
    ready:             existsSync(CLASSES_DIR) && existsSync(RUNTIME_JAR),
  })
})

// POST /compile — SIEMPRE devuelve HTTP 200 con JSON (nunca 500 por errores de Java)
app.post('/compile', async (req, res) => {
  const { codigo } = req.body

  if (typeof codigo !== 'string' || !codigo.trim()) {
    return res.json({
      success: false, tm_code: '', stdout_raw: '', stderr_raw: '',
      errors: [{ tipo: 'lexico', linea: 0, mensaje: 'El body debe tener { "codigo": "..." }' }],
    })
  }

  if (!existsSync(CLASSES_DIR)) {
    return res.json({
      success: false, tm_code: '', stdout_raw: '', stderr_raw: '',
      errors: [{
        tipo: 'lexico', linea: 0,
        mensaje: `Clases Java no encontradas en: ${CLASSES_DIR}. Compila el proyecto en IntelliJ primero.`,
      }],
    })
  }

  const tmpFile = join(tmpdir(), `tiny_${randomUUID()}.tny`)
  writeFileSync(tmpFile, codigo, 'utf8')

  // runJavaCompiler SIEMPRE resuelve — nunca rechaza
  const { stdout, stderr, exitCode, spawnError } = await runJavaCompiler(tmpFile)

  try { unlinkSync(tmpFile) } catch { /* ignorar */ }

  // Si Java no se pudo ni lanzar (ENOENT, permisos, etc.)
  if (spawnError) {
    return res.json({
      success: false, tm_code: '', stdout_raw: '', stderr_raw: '',
      errors: [{ tipo: 'lexico', linea: 0, mensaje: spawnError }],
    })
  }

  const tmCode = extractTmCode(stdout)
  const errors = parseErrors(stderr, stdout, exitCode)

  return res.json({
    success:    errors.length === 0 && tmCode.trim() !== '',
    tm_code:    tmCode,
    errors,
    stdout_raw: stdout,
    stderr_raw: stderr,
  })
})

// ══════════════════════════════════════════════════════════════════════════════
// HELPERS — COMPILADOR JAVA
// ══════════════════════════════════════════════════════════════════════════════

/**
 * Lanza el compilador Java y captura stdout/stderr.
 *
 * IMPORTANTE: Esta función SIEMPRE resuelve (nunca rechaza).
 * Un exit code != 0 es resultado normal de una compilación con errores —
 * no es un error del servidor.
 */
function runJavaCompiler(inputFile) {
  return new Promise(resolve => {
    const args = ['-cp', CLASSPATH, JAVA_MAIN_CLASS, inputFile]
    console.log(`[compilador] ${JAVA_BIN} ${args.join(' ')}`)

    let proc
    try {
      // Sin la opción `timeout` — manejamos el timeout manualmente para
      // evitar que Node 24 emita un evento `error` extra al matar el proceso
      proc = spawn(JAVA_BIN, args)
    } catch (err) {
      return resolve({ stdout: '', stderr: '', exitCode: 1, spawnError: String(err) })
    }

    let stdout = '', stderr = '', settled = false

    // Timeout manual: mata el proceso y resuelve si tarda demasiado
    const timer = setTimeout(() => {
      if (settled) return
      settled = true
      try { proc.kill('SIGKILL') } catch { /* ignorar */ }
      resolve({
        stdout, stderr,
        exitCode: 1,
        spawnError: `Timeout: el compilador tardó más de ${COMPILE_TIMEOUT}ms.`,
      })
    }, COMPILE_TIMEOUT)

    proc.stdout.on('data', chunk => { stdout += chunk.toString() })
    proc.stderr.on('data', chunk => { stderr += chunk.toString() })

    proc.on('close', (code) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      resolve({ stdout, stderr, exitCode: code ?? 1, spawnError: null })
    })

    // Error de proceso (ENOENT, permisos, etc.) — NO rechazamos, resolvemos
    proc.on('error', (err) => {
      if (settled) return
      settled = true
      clearTimeout(timer)
      const msg = err.code === 'ENOENT'
        ? `No se encontró '${JAVA_BIN}'. ¿Está Java instalado en WSL? Ejecuta: java -version`
        : `Error al lanzar el compilador: ${err.message}`
      resolve({ stdout, stderr, exitCode: 1, spawnError: msg })
    })
  })
}

function extractTmCode(stdout) {
  const INICIO = 'CODIGO OBJETO DEL LENGUAJE TINY GENERADO PARA LA TM'
  const FIN    = 'FIN DEL CODIGO OBJETO DEL LENGUAJE TINY GENERADO PARA LA TM'
  const si = stdout.indexOf(INICIO)
  const fi = stdout.indexOf(FIN)
  if (si !== -1 && fi !== -1) {
    return stdout.slice(si + INICIO.length, fi)
      .split('\n').filter(l => l.trim() && !l.includes('------')).join('\n').trim()
  }
  // Fallback: filtrar líneas TM por regex
  return stdout.split('\n')
    .filter(l => /^\s*\d+:/.test(l) || /^\s*\*/.test(l))
    .join('\n').trim()
}

function parseErrors(stderr, stdout, exitCode) {
  const errors = []

  for (const line of stderr.split('\n')) {
    const t = line.trim()
    if (!t) continue
    try {
      // Contrato B: JSON por línea { tipo, linea, mensaje }
      const p = JSON.parse(t)
      if (p.tipo && p.linea !== undefined && p.mensaje) { errors.push(p); continue }
    } catch { /* no es JSON */ }

    // Texto plano del compilador base actual
    if (/caracter ilegal/i.test(t))
      errors.push({ tipo: 'lexico', linea: 0, mensaje: t })
    else if (/error/i.test(t) && t.length > 4)
      errors.push({ tipo: 'lexico', linea: 0, mensaje: t })
  }

  for (const line of stdout.split('\n')) {
    const t = line.trim()
    if (/ocurrio error en cup/i.test(t))
      errors.push({ tipo: 'sintactico', linea: 0, mensaje: t })
  }

  if (exitCode !== 0 && errors.length === 0)
    errors.push({ tipo: 'sintactico', linea: 0,
      mensaje: `El compilador salió con código ${exitCode}. Revisa la sintaxis del programa.` })

  return errors
}

// ══════════════════════════════════════════════════════════════════════════════
// WEBSOCKET — SIMULADOR TM
// ══════════════════════════════════════════════════════════════════════════════

// Una sesión por socket: puede ser una sesión real (TM.C) o mock
const sessions = new Map() // socketId → Session

io.on('connection', socket => {
  console.log(`[ws] Conectado:  ${socket.id}`)

  // ── iniciar_simulador ────────────────────────────────────────────────────
  socket.on('iniciar_simulador', ({ tm_code }) => {
    // Limpiar sesión anterior si existe
    cleanupSession(socket.id)

    const instructions = parseTmInstructions(tm_code ?? '')

    if (existsSync(TM_BINARY) && USE_REAL_TM) {
      // ── Modo real: TM.C con soporte JSON por stdin/stdout (Carlos M3) ──
      console.log(`[ws] ${socket.id} → modo REAL: ${TM_BINARY}`)
      const session = spawnRealTm(socket, instructions, tm_code)
      sessions.set(socket.id, session)
    } else {
      // ── Modo mock: simulador en memoria ───────────────────────────────
      const reason = existsSync(TM_BINARY)
        ? 'USE_REAL_TM=false en .env (modo servidor de Carlos pendiente)'
        : 'binario TM no compilado aún'
      console.log(`[ws] ${socket.id} → modo MOCK (${reason})`)
      const session = createMockSession(instructions)
      sessions.set(socket.id, session)
      socket.emit('tm_state', session.getState())
      socket.emit('tm_info',
        existsSync(TM_BINARY)
          ? '⚠ Binario TM encontrado pero en modo MOCK. Activa USE_REAL_TM=true en .env cuando Carlos implemente el modo -s.'
          : '⚠ Simulador en modo MOCK — binario TM no encontrado.'
      )
    }
  })

  // ── step ─────────────────────────────────────────────────────────────────
  socket.on('step', () => {
    const session = sessions.get(socket.id)
    if (!session) return socket.emit('tm_error', 'No hay programa cargado. Compila primero.')
    session.step(socket)
  })

  // ── run ──────────────────────────────────────────────────────────────────
  socket.on('run', () => {
    const session = sessions.get(socket.id)
    if (!session) return socket.emit('tm_error', 'No hay programa cargado. Compila primero.')
    session.run(socket)
  })

  // ── stop ─────────────────────────────────────────────────────────────────
  socket.on('stop', () => {
    const session = sessions.get(socket.id)
    if (session) session.stop?.()
  })

  // ── reset ────────────────────────────────────────────────────────────────
  socket.on('reset', () => {
    const session = sessions.get(socket.id)
    if (!session) return
    session.reset(socket)
  })

  // ── break (toggle breakpoint) ─────────────────────────────────────────────
  socket.on('break', ({ linea }) => {
    const session = sessions.get(socket.id)
    if (!session) return
    session.toggleBreakpoint(linea, socket)
  })

  // ── input (valor para instrucción IN pendiente) ───────────────────────────
  socket.on('input', ({ value }) => {
    const session = sessions.get(socket.id)
    if (!session) return socket.emit('tm_error', 'No hay programa cargado.')
    session.sendInput(value, socket)
  })

  // ── desconexión ───────────────────────────────────────────────────────────
  socket.on('disconnect', () => {
    console.log(`[ws] Desconectado: ${socket.id}`)
    cleanupSession(socket.id)
  })
})

function cleanupSession(socketId) {
  const session = sessions.get(socketId)
  if (session) {
    session.destroy?.()
    sessions.delete(socketId)
  }
}

// ── Sesión con binario real TM.C ──────────────────────────────────────────────
function spawnRealTm(socket, instructions, tmCode) {
  const proc = spawn(TM_BINARY, ['-s'])
  let lineBuffer = ''

  proc.stdout.on('data', chunk => {
    lineBuffer += chunk.toString()
    const lines = lineBuffer.split('\n')
    lineBuffer = lines.pop() ?? ''          // retener la línea incompleta
    for (const line of lines) {
      const t = line.trim()
      if (!t) continue
      try {
        const state = JSON.parse(t)
        socket.emit('tm_state', state)
      } catch { /* output no-JSON del proceso TM, ignorar */ }
    }
  })

  proc.stderr.on('data', chunk => {
    socket.emit('tm_error', chunk.toString().trim())
  })

  proc.on('close', () => {
    socket.emit('tm_info', 'Proceso TM terminado.')
  })

  proc.on('error', err => {
    socket.emit('tm_error', `Error del proceso TM: ${err.message}`)
  })

  // Escribir el .tm en un temp y cargar
  const tmpTm = join(tmpdir(), `prog_${randomUUID()}.tm`)
  writeFileSync(tmpTm, tmCode, 'utf8')
  proc.stdin.write(`load ${tmpTm}\n`)

  return {
    type: 'real',
    step:  (_sock) => proc.stdin.write('step\n'),
    run:   (_sock) => proc.stdin.write('run\n'),
    reset: (_sock) => proc.stdin.write('reset\n'),
    stop:  ()      => proc.stdin.write('stop\n'),
    sendInput: (value, _sock) => proc.stdin.write(`input ${value}\n`),
    toggleBreakpoint: (linea, _sock) => proc.stdin.write(`break ${linea}\n`),
    destroy: () => {
      try { proc.kill(); unlinkSync(tmpTm) } catch { /* ignorar */ }
    },
  }
}

// ── Sesión mock: simulador en memoria ─────────────────────────────────────────
function createMockSession(instructions) {
  // Estado inicial con las instrucciones reales del .tm compilado
  // (o mock si no hay instrucciones)
  let state = buildInitialMockState(instructions)
  let breakpoints = []
  let runInterval = null

  return {
    type: 'mock',

    getState: () => ({ ...state }),

    step: (socket) => {
      state = advanceMockState(state, instructions, breakpoints)
      socket.emit('tm_state', { ...state })
    },

    run: (socket) => {
      if (runInterval) return
      runInterval = setInterval(() => {
        state = advanceMockState(state, instructions, breakpoints)
        socket.emit('tm_state', { ...state })
        const hitBp = breakpoints.includes(state.registros.PC)
        if (state.resultado_paso === 'HALT' || hitBp) {
          clearInterval(runInterval)
          runInterval = null
        }
      }, 300)
    },

    stop: () => {
      if (runInterval) { clearInterval(runInterval); runInterval = null }
    },

    reset: (socket) => {
      if (runInterval) { clearInterval(runInterval); runInterval = null }
      state = buildInitialMockState(instructions)
      socket.emit('tm_state', { ...state })
    },

    toggleBreakpoint: (linea, socket) => {
      const idx = breakpoints.indexOf(linea)
      if (idx === -1) breakpoints.push(linea)
      else breakpoints.splice(idx, 1)
      state = { ...state, breakpoints: [...breakpoints] }
      socket.emit('tm_state', { ...state })
    },

    destroy: () => {
      if (runInterval) clearInterval(runInterval)
    },
  }
}

function buildInitialMockState(instructions) {
  // Si no hay instrucciones del .tm, usar el mock estático
  const insts = instructions.length > 0
    ? instructions
    : loadStaticMock().memoria_instrucciones

  const firstPc = insts[0]?.linea ?? 0
  return {
    registros: { PC: firstPc, AC: 0, AC1: 0, GP: 0, MP: 1023, FP: 0, R2: 0, R3: 0 },
    memoria_datos: { '_size': 1024 },
    memoria_instrucciones: insts,
    total_instrucciones: insts.length,
    instruccion_ejecutada: null,
    estado: 'ejecutando',
    resultado_paso: 'OK',
    mensaje_error: null,
    esperando_input: false,
    breakpoints: [],
  }
}

function advanceMockState(prevState, instructions, breakpoints) {
  const { registros } = prevState
  const currentIdx = instructions.findIndex(i => i.linea === registros.PC)
  const inst = instructions[currentIdx]

  if (!inst || inst.op === 'HALT') {
    return { ...prevState, resultado_paso: 'HALT', estado: 'detenido', instruccion_ejecutada: inst ?? null }
  }

  // Simulación simplificada de efectos de instrucciones (para feedback visual)
  const newRegs   = { ...registros }
  const newMem    = { ...prevState.memoria_datos }

  switch (inst.op) {
    case 'LDC': newRegs.AC = inst.d ?? 0; break
    case 'LD':  newRegs.AC = newMem[String(inst.d ?? 0)] ?? 0; break
    case 'ST':  newMem[String(inst.d ?? 0)] = newRegs.AC; break
    case 'ADD': newRegs.AC = newRegs.AC + newRegs.AC1; break
    case 'SUB': newRegs.AC = newRegs.AC1 - newRegs.AC; break
    case 'MUL': newRegs.AC = newRegs.AC * newRegs.AC1; break
    case 'DIV':
      if (newRegs.AC === 0) return { ...prevState, resultado_paso: 'ZERO_DIVIDE', estado: 'error' }
      newRegs.AC = Math.trunc(newRegs.AC1 / newRegs.AC); break
    case 'IN': {
      // Emitir esperando_input — la UI debe mostrar diálogo
      const nextInst = instructions[currentIdx + 1]
      newRegs.PC = nextInst?.linea ?? inst.linea + 1
      return { ...prevState, registros: newRegs, memoria_datos: newMem,
        instruccion_ejecutada: inst, esperando_input: true,
        estado: 'esperando_input', resultado_paso: 'OK', breakpoints }
    }
    case 'OUT': break  // La salida real viene del proceso TM.C
  }

  // Avanzar PC al siguiente índice
  const nextInst = instructions[currentIdx + 1]
  newRegs.PC = nextInst?.linea ?? (inst.linea + 1)

  // Saltos condicionales simples (JEQ, JLT, etc.) — lógica básica
  if (['JEQ','JNE','JLT','JLE','JGT','JGE'].includes(inst.op)) {
    const d = inst.d ?? 0
    const s = inst.s ?? 7
    const target = d + (s === 7 ? newRegs.PC : 0)
    const cond = checkJump(inst.op, newRegs.AC)
    if (cond) newRegs.PC = target
  }

  return {
    ...prevState,
    registros: newRegs,
    memoria_datos: newMem,
    instruccion_ejecutada: inst,
    estado: 'ejecutando',
    resultado_paso: 'OK',
    esperando_input: false,
    breakpoints,
  }
}

function checkJump(op, ac) {
  switch (op) {
    case 'JEQ': return ac === 0
    case 'JNE': return ac !== 0
    case 'JLT': return ac < 0
    case 'JLE': return ac <= 0
    case 'JGT': return ac > 0
    case 'JGE': return ac >= 0
    default:    return false
  }
}

// ── Parsear instrucciones de un archivo .tm ────────────────────────────────────
// Formato: "  N:   OP   r, d(s)   comentario"  (RM/RA)
//          "  N:   OP   r, s, t   comentario"  (RR)
//          "*   comentario"                     (comentario)
function parseTmInstructions(tmCode) {
  const instructions = []
  for (const raw of tmCode.split('\n')) {
    const line = raw.trim()
    // Comentarios
    if (line.startsWith('*')) continue
    // Instrucciones: empieza con dígito seguido de ':'
    const m = line.match(/^(\d+):\s+(\w+)\s+(.+?)(?:\s{2,}(.*))?$/)
    if (!m) continue

    const linea     = parseInt(m[1])
    const op        = m[2].toUpperCase()
    const argStr    = m[3].trim()
    const comentario = (m[4] ?? '').trim()

    // Intentar parsear "r, d(s)" (RM/RA)
    const rm = argStr.match(/^(\d+),\s*(-?\d+)\((\d+)\)/)
    if (rm) {
      instructions.push({ linea, op, r: +rm[1], d: +rm[2], s: +rm[3], comentario })
      continue
    }
    // Intentar parsear "r, s, t" (RR)
    const rr = argStr.match(/^(\d+),(\d+),(\d+)/)
    if (rr) {
      instructions.push({ linea, op, r: +rr[1], s: +rr[2], t: +rr[3], comentario })
      continue
    }
    // Fallback
    instructions.push({ linea, op, r: 0, s: 0, comentario })
  }
  return instructions
}

// Cargar el mock estático como fallback de último recurso
function loadStaticMock() {
  try {
    return JSON.parse(readFileSync(TM_MOCK_PATH, 'utf8'))
  } catch {
    return { memoria_instrucciones: [] }
  }
}

// ══════════════════════════════════════════════════════════════════════════════
// ARRANCAR
// ══════════════════════════════════════════════════════════════════════════════
httpServer.listen(PORT, () => {
  console.log(`\n🟢  servidor_puente en http://localhost:${PORT}`)
  console.log(`    POST /compile  → compilador Java`)
  console.log(`    WS   /         → socket.io (simulador TM)\n`)
  console.log(`    Java:      ${JAVA_BIN}`)
  console.log(`    Clases:    ${existsSync(CLASSES_DIR) ? '✅' : '❌ NO compiladas'}  ${CLASSES_DIR}`)
  const tmStatus = !existsSync(TM_BINARY) ? '❌ No compilado'
    : USE_REAL_TM ? '✅ Real (modo -s)'
    : '✅ Compilado — usando MOCK (USE_REAL_TM=false)'
  console.log(`    TM Binary: ${tmStatus}`)
  console.log(`               ${TM_BINARY}\n`)
})
