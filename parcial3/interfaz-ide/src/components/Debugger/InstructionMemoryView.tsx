import { useEffect, useRef } from 'react'
import type { TMInstruction } from '../../types/tm.types'
import './InstructionMemoryView.css'

interface InstructionMemoryViewProps {
  instrucciones: TMInstruction[]
  pc:            number
  breakpoints:   number[]
}

/** Formatea una instrucción TM como texto legible */
function formatInst(inst: TMInstruction): string {
  const op = inst.op.padEnd(5)
  if (inst.t !== undefined) {
    // Instrucción RR:  OP  r, s, t
    return `${op} ${inst.r},${inst.s},${inst.t}`
  }
  // Instrucción RM/RA: OP  r, d(s)
  const d = inst.d ?? 0
  return `${op} ${inst.r}, ${d}(${inst.s})`
}

export default function InstructionMemoryView({
  instrucciones, pc, breakpoints,
}: InstructionMemoryViewProps) {
  const activeRowRef = useRef<HTMLDivElement>(null)

  // Mantener la instrucción activa visible en el panel
  useEffect(() => {
    activeRowRef.current?.scrollIntoView({ block: 'nearest', behavior: 'smooth' })
  }, [pc])

  return (
    <div className="imem-view">
      <div className="panel-header">
        <span className="panel-title">Memoria de Instrucciones</span>
        <span className="imem-pc-label">PC: <strong>{pc}</strong></span>
      </div>

      <div className="imem-list">
        {instrucciones.length === 0 && (
          <p className="imem-empty">Sin programa cargado</p>
        )}
        {instrucciones.map(inst => {
          const isActive = inst.linea === pc
          const isBp     = breakpoints.includes(inst.linea)
          return (
            <div
              key={inst.linea}
              ref={isActive ? activeRowRef : undefined}
              className={[
                'imem-row',
                isActive ? 'imem-row--active' : '',
                isBp     ? 'imem-row--bp'     : '',
              ].join(' ')}
              title={inst.comentario}
            >
              {/* Indicador de breakpoint */}
              <span className="imem-bp">{isBp ? '●' : '\u00A0'}</span>
              {/* Flecha de instrucción activa */}
              <span className="imem-arrow">{isActive ? '▶' : '\u00A0'}</span>
              {/* Número de línea */}
              <span className="imem-lineno">{String(inst.linea).padStart(3, '\u00A0')}:</span>
              {/* Código ensamblador */}
              <span className="imem-code">{formatInst(inst)}</span>
            </div>
          )
        })}
      </div>
    </div>
  )
}
