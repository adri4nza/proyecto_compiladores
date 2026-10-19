import { useState, useCallback, useRef } from 'react'
import type { TMState, ConsoleEntry, ConsoleTipo } from '../types/tm.types'
import mockData from '../data/estado_tm_mock.json'

// Carga inicial desde el mock — se reemplazará con datos del WebSocket (M3)
const INITIAL_STATE = mockData as unknown as TMState

export function useTMState() {
  const [tmState, setTmState]         = useState<TMState>(INITIAL_STATE)
  const [prevMemoria, setPrevMemoria] = useState<Record<string, number>>({})
  const [consoleEntries, setConsoleEntries] = useState<ConsoleEntry[]>([
    { id: 'boot', tipo: 'info', mensaje: 'TM++ IDE listo. Escribe código y presiona ⚡ Compilar.' },
  ])
  const [isConnected, setIsConnected] = useState(false)
  const counter = useRef(0)

  /** Actualiza el estado de la TM guardando la memoria anterior para resaltar cambios */
  const updateTmState = useCallback((next: TMState) => {
    setTmState(prev => {
      setPrevMemoria(prev.memoria_datos)
      return next
    })
  }, [])

  /** Devuelve el conjunto de direcciones que cambiaron en el último step */
  const getChangedAddresses = useCallback((): Set<string> => {
    const changed = new Set<string>()
    for (const key of Object.keys(tmState.memoria_datos)) {
      if (key === '_size') continue
      if (tmState.memoria_datos[key] !== prevMemoria[key]) {
        changed.add(key)
      }
    }
    return changed
  }, [tmState.memoria_datos, prevMemoria])

  const addConsoleEntry = useCallback((tipo: ConsoleTipo, mensaje: string, linea?: number) => {
    counter.current += 1
    setConsoleEntries(prev => [
      ...prev,
      { id: String(counter.current), tipo, mensaje, linea },
    ])
  }, [])

  const clearConsole = useCallback(() => setConsoleEntries([]), [])

  return {
    tmState,
    consoleEntries,
    isConnected,
    setIsConnected,
    updateTmState,
    getChangedAddresses,
    addConsoleEntry,
    clearConsole,
  }
}
