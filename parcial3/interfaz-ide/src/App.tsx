import { useState, useEffect } from 'react'
import { useTMState }  from './hooks/useTMState'
import { useSocket }   from './hooks/useSocket'
import Toolbar         from './components/Toolbar/Toolbar'
import CodeEditor      from './components/Editor/CodeEditor'
import DebuggerPanel   from './components/Debugger/DebuggerPanel'
import ConsolePanel    from './components/Console/ConsolePanel'
import InputModal      from './components/Debugger/InputModal'
import type { CompilerError } from './types/tm.types'
import './App.css'

const SERVIDOR_PUENTE = 'http://localhost:3001'

const CODIGO_INICIAL = `{ Programa de ejemplo en Tiny++ }
int x;
int resultado;

read x;
if 0 < x then
  resultado := x * x;
  write resultado
end
`

export default function App() {
  const [sourceCode,      setSourceCode]      = useState(CODIGO_INICIAL)
  const [compilerErrors,  setCompilerErrors]  = useState<CompilerError[]>([])
  const [isCompiling,     setIsCompiling]     = useState(false)

  // ── Estado TM ─────────────────────────────────────────────────────────────
  const {
    tmState,
    consoleEntries,
    addConsoleEntry,
    clearConsole,
    updateTmState,
    getChangedAddresses,
  } = useTMState()

  // ── WebSocket ─────────────────────────────────────────────────────────────
  const {
    isConnected,
    latestTmState,
    latestTmError,
    latestTmInfo,
    iniciarSimulador,
    step,
    run,
    stop,
    reset,
    sendInput,
  } = useSocket()

  // Propagar estado TM del WebSocket al hook de estado
  useEffect(() => {
    if (latestTmState) updateTmState(latestTmState)
  }, [latestTmState, updateTmState])

  useEffect(() => {
    if (latestTmError) addConsoleEntry('error', `[Simulador] ${latestTmError}`)
  }, [latestTmError, addConsoleEntry])

  useEffect(() => {
    if (latestTmInfo) addConsoleEntry('warn', latestTmInfo)
  }, [latestTmInfo, addConsoleEntry])

  // Mostrar salida OUT del simulador en la consola
  useEffect(() => {
    if (!latestTmState) return
    const s = latestTmState as TMStateWithOutput
    if (s.salida_consola !== undefined && s.salida_consola !== null) {
      // Si la instrucción ejecutada es OUTC, mostrar como carácter
      const esChar = s.instruccion_ejecutada?.op === 'OUTC'
      const texto = esChar
        ? String.fromCharCode(s.salida_consola)
        : String(s.salida_consola)
      addConsoleEntry('output', `OUT: ${texto}`)
    }
    if (s.resultado_paso === 'HALT') {
      addConsoleEntry('success', 'Programa terminado (HALT).')
    }
    if (s.resultado_paso === 'ZERO_DIVIDE') {
      addConsoleEntry('error', 'Error en ejecución: División por cero.')
    }
  }, [latestTmState])   // eslint-disable-line

  // ── Derivados de estado ───────────────────────────────────────────────────
  const isHalted       = tmState.resultado_paso === 'HALT'
  const isWaitingInput = tmState.esperando_input

  // ── Compilar ──────────────────────────────────────────────────────────────
  const handleCompile = async () => {
    clearConsole()
    setCompilerErrors([])
    setIsCompiling(true)
    addConsoleEntry('info', 'Compilando código Tiny++...')

    try {
      const resp = await fetch(`${SERVIDOR_PUENTE}/compile`, {
        method:  'POST',
        headers: { 'Content-Type': 'application/json' },
        body:    JSON.stringify({ codigo: sourceCode }),
      })

      if (!resp.ok) {
        addConsoleEntry('error', `Error HTTP inesperado del servidor: ${resp.status}.`)
        return
      }

      const result = await resp.json()

      if (result.errors?.length > 0) {
        result.errors.forEach((e: CompilerError) => {
          addConsoleEntry('error', e.mensaje, e.linea > 0 ? e.linea : undefined)
        })
        setCompilerErrors(result.errors)
        addConsoleEntry('warn', `Compilación fallida — ${result.errors.length} error(es).`)
      } else if (result.tm_code?.trim()) {
        const instrCount = (result.tm_code.match(/^\s*\d+:/gm) ?? []).length
        addConsoleEntry('success', `Compilación exitosa — ${instrCount} instrucciones TM generadas.`)

        if (isConnected) {
          addConsoleEntry('info', 'Enviando programa al simulador TM...')
          iniciarSimulador(result.tm_code)
        } else {
          addConsoleEntry('warn', 'Simulador no conectado — inicia el servidor_puente.')
          addConsoleEntry('output', result.tm_code)
        }
        setCompilerErrors([])
      } else {
        addConsoleEntry('warn', 'El compilador no produjo código ni errores.')
        if (result.stdout_raw) addConsoleEntry('output', result.stdout_raw)
      }
    } catch (err) {
      const isNet = err instanceof TypeError
      addConsoleEntry('error', isNet
        ? '¿Está el servidor puente corriendo? Ejecuta: cd servidor_puente && npm run dev'
        : `Error: ${err instanceof Error ? err.message : String(err)}`)
    } finally {
      setIsCompiling(false)
    }
  }

  // ── Input del simulador ────────────────────────────────────────────────────
  const handleInputSubmit = (value: number) => {
    sendInput(value)
    addConsoleEntry('info', `IN: ingresaste ${value}`)
  }

  return (
    <div className="app-layout">
      <Toolbar
        isConnected={isConnected}
        isHalted={isHalted}
        isWaitingInput={isWaitingInput}
        isCompiling={isCompiling}
        onCompile={handleCompile}
        onRun={()   => run()}
        onStep={()  => step()}
        onStop={()  => { stop(); addConsoleEntry('warn', 'Ejecución detenida.') }}
        onReset={() => { reset(); addConsoleEntry('info', 'Simulador reiniciado.') }}
      />

      <div className="editor-area">
        <CodeEditor
          value={sourceCode}
          onChange={val => setSourceCode(val ?? '')}
          errors={compilerErrors}
        />
      </div>

      <div className="debugger-area">
        <DebuggerPanel
          tmState={tmState}
          changedAddresses={getChangedAddresses()}
        />
      </div>

      <div className="console-area">
        <ConsolePanel entries={consoleEntries} onClear={clearConsole} />
      </div>

      {/* Diálogo gráfico para instrucción read (requerimiento del enunciado) */}
      <InputModal
        visible={isWaitingInput}
        onSubmit={handleInputSubmit}
      />
    </div>
  )
}

// Extensión local del tipo para el campo extra que emite Carlos
interface TMStateWithOutput {
  salida_consola?:       number | null
  instruccion_ejecutada?: { op: string } | null
  resultado_paso:        string
}
