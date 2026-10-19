import { useEffect, useRef } from 'react'
import type { ConsoleEntry } from '../../types/tm.types'
import './ConsolePanel.css'

interface ConsolePanelProps {
  entries: ConsoleEntry[]
  onClear: () => void
}

const ICON: Record<ConsoleEntry['tipo'], string> = {
  info:    '·',
  success: '✓',
  error:   '✗',
  output:  '›',
  warn:    '⚠',
}

export default function ConsolePanel({ entries, onClear }: ConsolePanelProps) {
  const bottomRef = useRef<HTMLDivElement>(null)

  // Auto-scroll cuando llegan nuevas entradas
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [entries.length])

  return (
    <div className="console-panel">
      <div className="panel-header">
        <span className="panel-title">Consola</span>
        <button className="console-btn-clear" onClick={onClear} title="Limpiar consola">
          ✕ Limpiar
        </button>
      </div>

      <div className="console-output">
        {entries.map(entry => (
          <div key={entry.id} className={`console-line console-line--${entry.tipo}`}>
            <span className="console-icon">{ICON[entry.tipo]}</span>
            {entry.linea !== undefined && (
              <span className="console-lineno">L{entry.linea}:</span>
            )}
            <span className="console-msg">{entry.mensaje}</span>
          </div>
        ))}
        <div ref={bottomRef} />
      </div>
    </div>
  )
}
