import './Toolbar.css'

interface ToolbarProps {
  isConnected:    boolean
  isHalted:       boolean
  isWaitingInput: boolean
  onCompile: () => void
  onRun:     () => void
  onStep:    () => void
  onStop:    () => void
  onReset:   () => void
}

export default function Toolbar({
  isConnected, isHalted, isWaitingInput,
  onCompile, onRun, onStep, onStop, onReset,
}: ToolbarProps) {
  const canExecute = !isHalted && !isWaitingInput

  return (
    <header className="toolbar">
      <span className="toolbar-logo">TM<span>++</span>&nbsp;IDE</span>

      <div className="toolbar-sep" />

      <button
        className="tb-btn tb-btn--compile"
        onClick={onCompile}
        title="Compilar código fuente Tiny++"
      >
        ⚡ Compilar
      </button>

      <div className="toolbar-sep" />

      <button
        className="tb-btn tb-btn--go"
        onClick={onRun}
        disabled={!canExecute}
        title="Ejecutar hasta HALT o breakpoint"
      >
        ▶ Run
      </button>
      <button
        className="tb-btn tb-btn--step"
        onClick={onStep}
        disabled={!canExecute}
        title="Ejecutar una instrucción TM"
      >
        ⏭ Step
      </button>
      <button
        className="tb-btn tb-btn--stop"
        onClick={onStop}
        title="Detener ejecución"
      >
        ⏹ Stop
      </button>
      <button
        className="tb-btn"
        onClick={onReset}
        title="Reiniciar simulador al estado inicial"
      >
        ↺ Reset
      </button>

      <div className="toolbar-spacer" />

      <span className={`toolbar-status ${isConnected ? 'toolbar-status--on' : 'toolbar-status--off'}`}>
        {isConnected ? '● Servidor activo' : '○ Sin servidor'}
      </span>
    </header>
  )
}
