import { useState, useEffect } from 'react'
import { useTMState }  from './hooks/useTMState'
import { useSocket }   from './hooks/useSocket'
import Toolbar         from './components/Toolbar/Toolbar'
import CodeEditor      from './components/Editor/CodeEditor'
import DebuggerPanel   from './components/Debugger/DebuggerPanel'
import ConsolePanel    from './components/Console/ConsolePanel'
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
  } = useSocket()

  // Propagar estado TM recibido por WebSocket al hook de estado
  useEffect(() => {
    if (latestTmState) updateTmState(latestTmState)
  }, [latestTmState, updateTmState])

  // Mostrar errores del simulador en consola
  useEffect(() => {
    if (latestTmError) addConsoleEntry('error', `[Simulador] ${latestTmError}`)
  }, [latestTmError, addConsoleEntry])

  // Mostrar mensajes informativos del simulador (ej: "usando mock")
  useEffect(() => {
    if (latestTmInfo) addConsoleEntry('warn', latestTmInfo)
  }, [latestTmInfo, addConsoleEntry])

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

      // El servidor SIEMPRE devuelve 200 — si no, hay un problema de red
      if (!resp.ok) {
        addConsoleEntry('error', `Error HTTP inesperado del servidor: ${resp.status}. ¿Está corriendo?`)
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

        // ── Conectar con M3: enviar el .tm al simulador por WebSocket ──────
        if (isConnected) {
          addConsoleEntry('info', 'Enviando programa al simulador TM...')
          iniciarSimulador(result.tm_code)
        } else {
          addConsoleEntry('warn', 'Simulador no conectado — inicia el servidor_puente para depurar.')
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

  // ── Handlers de depuración ─────────────────────────────────────────────────
  const handleStep  = () => { step() }
  const handleRun   = () => { run()  }
  const handleStop  = () => { stop(); addConsoleEntry('warn', 'Ejecución detenida.') }
  const handleReset = () => { reset(); addConsoleEntry('info', 'Simulador reiniciado.') }

  return (
    <div className="app-layout">
      <Toolbar
        isConnected={isConnected}
        isHalted={isHalted}
        isWaitingInput={isWaitingInput}
        isCompiling={isCompiling}
        onCompile={handleCompile}
        onRun={handleRun}
        onStep={handleStep}
        onStop={handleStop}
        onReset={handleReset}
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
    </div>
  )
}
