import type { TMRegisters } from '../../types/tm.types'
import './RegistersView.css'

interface RegistersViewProps {
  registros: TMRegisters
  resultado: string
}

// Nombres descriptivos para tooltips
const REG_TOOLTIP: Record<keyof TMRegisters, string> = {
  PC:  'Program Counter — Próxima instrucción a ejecutar',
  AC:  'Acumulador — Registro de trabajo principal',
  AC1: 'Acumulador 1 — Registro de trabajo secundario',
  GP:  'Global Pointer — Base de variables globales',
  MP:  'Memory Pointer — Tope de la pila temporal',
  FP:  'Frame Pointer — Base del marco de activación actual',
  R2:  'Registro de propósito general 2',
  R3:  'Registro de propósito general 3',
}

const RESULTADO_CLASS: Record<string, string> = {
  OK:          'badge--ok',
  HALT:        'badge--halt',
  IMEM_ERR:    'badge--err',
  DMEM_ERR:    'badge--err',
  ZERO_DIVIDE: 'badge--err',
}

export default function RegistersView({ registros, resultado }: RegistersViewProps) {
  return (
    <div className="reg-view">
      <div className="panel-header">
        <span className="panel-title">Registros</span>
        <span className={`resultado-badge ${RESULTADO_CLASS[resultado] ?? 'badge--ok'}`}>
          {resultado}
        </span>
      </div>

      <div className="reg-grid">
        {(Object.keys(registros) as (keyof TMRegisters)[]).map(name => (
          <div
            key={name}
            className={`reg-cell ${name === 'PC' ? 'reg-cell--pc' : ''}`}
            title={REG_TOOLTIP[name]}
          >
            <span className="reg-name">{name}</span>
            <span className="reg-val">{registros[name]}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
