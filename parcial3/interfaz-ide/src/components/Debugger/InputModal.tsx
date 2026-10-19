import { useState, useEffect, useRef } from 'react'
import './InputModal.css'

interface InputModalProps {
  visible:   boolean
  onSubmit:  (value: number) => void
}

export default function InputModal({ visible, onSubmit }: InputModalProps) {
  const [raw, setRaw]    = useState('')
  const [error, setError] = useState('')
  const inputRef          = useRef<HTMLInputElement>(null)

  // Enfocar el input cada vez que el modal se abre
  useEffect(() => {
    if (visible) {
      setRaw('')
      setError('')
      setTimeout(() => inputRef.current?.focus(), 50)
    }
  }, [visible])

  const handleSubmit = () => {
    const n = parseInt(raw.trim(), 10)
    if (raw.trim() === '' || isNaN(n)) {
      setError('Ingresa un número entero válido')
      return
    }
    onSubmit(n)
    setRaw('')
    setError('')
  }

  const handleKey = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') handleSubmit()
    if (e.key === 'Escape') { setRaw(''); setError('') }
  }

  if (!visible) return null

  return (
    <div className="input-modal-overlay">
      <div className="input-modal">
        <div className="input-modal-header">
          <span className="input-modal-icon">⌨</span>
          <span className="input-modal-title">Instrucción <code>read</code></span>
        </div>
        <p className="input-modal-desc">
          El programa espera un valor entero de entrada.
        </p>
        <input
          ref={inputRef}
          className={`input-modal-field ${error ? 'input-modal-field--error' : ''}`}
          type="number"
          placeholder="Ingresa un entero (ej: 5)"
          value={raw}
          onChange={e => { setRaw(e.target.value); setError('') }}
          onKeyDown={handleKey}
        />
        {error && <p className="input-modal-error">{error}</p>}
        <button className="input-modal-btn" onClick={handleSubmit}>
          Enviar →
        </button>
        <p className="input-modal-hint">También puedes presionar Enter</p>
      </div>
    </div>
  )
}
