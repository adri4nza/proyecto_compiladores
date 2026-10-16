/**
 * useSocket — Gestiona la conexión WebSocket con el servidor_puente.
 *
 * - Conecta al montar, desconecta al desmontar.
 * - Expone acciones: iniciarSimulador, step, run, stop, reset, toggleBreakpoint.
 * - Devuelve el último estado TM recibido y el estado de conexión.
 *
 * Cuando el servidor no está corriendo, `isConnected` es false y
 * las acciones quedan silenciosas (el servidor puente mostrará el error).
 */

import { useEffect, useRef, useState, useCallback } from 'react'
import { io, type Socket } from 'socket.io-client'
import type { TMState } from '../types/tm.types'

const SERVIDOR = 'http://localhost:3001'

export interface UseSocketReturn {
  isConnected:       boolean
  latestTmState:     TMState | null
  latestTmError:     string | null
  latestTmInfo:      string | null
  iniciarSimulador:  (tm_code: string) => void
  step:              () => void
  run:               () => void
  stop:              () => void
  reset:             () => void
  toggleBreakpoint:  (linea: number) => void
}

export function useSocket(): UseSocketReturn {
  const socketRef              = useRef<Socket | null>(null)
  const [isConnected, setIsConnected]   = useState(false)
  const [latestTmState, setLatestState] = useState<TMState | null>(null)
  const [latestTmError, setLatestError] = useState<string | null>(null)
  const [latestTmInfo,  setLatestInfo]  = useState<string | null>(null)

  useEffect(() => {
    const socket = io(SERVIDOR, {
      reconnectionDelay:    1000,
      reconnectionAttempts: 10,
    })
    socketRef.current = socket

    socket.on('connect',    () => { setIsConnected(true);  console.log('[ws] conectado') })
    socket.on('disconnect', () => { setIsConnected(false); console.log('[ws] desconectado') })
    socket.on('tm_state',   (state: TMState) => setLatestState(state))
    socket.on('tm_error',   (msg: string)    => setLatestError(msg))
    socket.on('tm_info',    (msg: string)    => setLatestInfo(msg))

    return () => { socket.disconnect() }
  }, [])  // solo mount/unmount

  const emit = useCallback((event: string, data?: unknown) => {
    socketRef.current?.emit(event, data)
  }, [])

  return {
    isConnected,
    latestTmState,
    latestTmError,
    latestTmInfo,
    iniciarSimulador:  (tm_code) => emit('iniciar_simulador', { tm_code }),
    step:              ()        => emit('step'),
    run:               ()        => emit('run'),
    stop:              ()        => emit('stop'),
    reset:             ()        => emit('reset'),
    toggleBreakpoint:  (linea)   => emit('break', { linea }),
  }
}
