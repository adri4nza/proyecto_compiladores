// ─────────────────────────────────────────────────────────────────────────────
// Tipos TypeScript del Contrato A — estado_tm_mock.json
// ⚠️  NO renombrar ni eliminar claves sin notificar a Carlos (M3).
//     Ver 02_INSTRUCCIONES_IA_EQUIPO.md, Sección 3.
// ─────────────────────────────────────────────────────────────────────────────

export interface TMRegisters {
  PC:  number;
  AC:  number;
  AC1: number;
  GP:  number;
  MP:  number;
  FP:  number;
  R2:  number;
  R3:  number;
}

export interface TMInstruction {
  linea:      number;
  op:         string;
  r:          number;
  d?:         number;
  s:          number;
  t?:         number;
  comentario: string;
}

export interface TMState {
  registros:             TMRegisters;
  /** Representación sparse: claves son la dirección como string, "_size" es metadata */
  memoria_datos:         Record<string, number>;
  memoria_instrucciones: TMInstruction[];
  total_instrucciones:   number;
  instruccion_ejecutada: TMInstruction | null;
  estado:                'ejecutando' | 'detenido' | 'error' | 'esperando_input';
  resultado_paso:        'OK' | 'HALT' | 'IMEM_ERR' | 'DMEM_ERR' | 'ZERO_DIVIDE';
  mensaje_error:         string | null;
  esperando_input:       boolean;
  breakpoints:           number[];
}

// ─────────────────────────────────────────────────────────────────────────────
// Tipos del Contrato B — errores del compilador Java (stderr)
// ⚠️  NO renombrar sin notificar a Ricardo (M1) e Ivan (M2).
// ─────────────────────────────────────────────────────────────────────────────

export interface CompilerError {
  tipo:    'lexico' | 'sintactico' | 'semantico';
  linea:   number;
  mensaje: string;
}

// ─────────────────────────────────────────────────────────────────────────────
// Tipos internos del IDE (no forman parte de ningún contrato externo)
// ─────────────────────────────────────────────────────────────────────────────

export type ConsoleTipo = 'info' | 'error' | 'output' | 'success' | 'warn';

export interface ConsoleEntry {
  id:      string;
  tipo:    ConsoleTipo;
  mensaje: string;
  linea?:  number;
}
