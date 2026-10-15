import { useState } from 'react'
import { useTMState } from './hooks/useTMState'
import Toolbar from './components/Toolbar/Toolbar'
import CodeEditor from './components/Editor/CodeEditor'
import DebuggerPanel from './components/Debugger/DebuggerPanel'
import ConsolePanel from './components/Console/ConsolePanel'
import './App.css'

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
  const [sourceCode, setSourceCode] = useState(CODIGO_INICIAL)

  const {
    tmState,
    consoleEntries,
    isConnected,
    addConsoleEntry,
    clearConsole,
    getChangedAddresses,
  } = useTMState()

  const isHalted       = tmState.resultado_paso === 'HALT'
  const isWaitingInput = tmState.esperando_input

  return (
    <div className="app-layout">
      <Toolbar
        isConnected={isConnected}
        isHalted={isHalted}
        isWaitingInput={isWaitingInput}
        onCompile={() => addConsoleEntry('info', 'Compilando... (servidor puente — Día 2)')}
        onRun={()     => addConsoleEntry('info', 'Run: pendiente conexión con M3')}
        onStep={()    => addConsoleEntry('info', 'Step: pendiente conexión con M3')}
        onStop={()    => addConsoleEntry('warn', 'Ejecución detenida.')}
        onReset={()   => addConsoleEntry('info', 'Reset: pendiente conexión con M3')}
      />

      <div className="editor-area">
        <CodeEditor
          value={sourceCode}
          onChange={val => setSourceCode(val ?? '')}
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
