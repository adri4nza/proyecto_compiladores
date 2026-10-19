import type { TMState } from '../../types/tm.types'
import RegistersView from './RegistersView'
import InstructionMemoryView from './InstructionMemoryView'
import DataMemoryView from './DataMemoryView'
import './DebuggerPanel.css'

interface DebuggerPanelProps {
  tmState:          TMState
  changedAddresses: Set<string>
}

export default function DebuggerPanel({ tmState, changedAddresses }: DebuggerPanelProps) {
  return (
    <div className="debugger-panel">
      <RegistersView
        registros={tmState.registros}
        resultado={tmState.resultado_paso}
      />
      <InstructionMemoryView
        instrucciones={tmState.memoria_instrucciones}
        pc={tmState.registros.PC}
        breakpoints={tmState.breakpoints}
      />
      <DataMemoryView
        memoriaDatos={tmState.memoria_datos}
        changedAddresses={changedAddresses}
      />
    </div>
  )
}
