import './DataMemoryView.css'

interface DataMemoryViewProps {
  memoriaDatos:     Record<string, number>
  changedAddresses: Set<string>
}

export default function DataMemoryView({ memoriaDatos, changedAddresses }: DataMemoryViewProps) {
  // Excluir la metaclave _size y ordenar por dirección numérica
  const entries = Object.entries(memoriaDatos)
    .filter(([k]) => k !== '_size')
    .sort(([a], [b]) => Number(a) - Number(b))

  const totalSize = memoriaDatos['_size'] ?? 1024

  return (
    <div className="dmem-view">
      <div className="panel-header">
        <span className="panel-title">Memoria de Datos</span>
        <span className="dmem-count">
          {entries.length} usadas / {totalSize} total
        </span>
      </div>

      <div className="dmem-scroll">
        {entries.length === 0 ? (
          <p className="dmem-empty">Memoria vacía</p>
        ) : (
          <table className="dmem-table">
            <thead>
              <tr>
                <th>Dir.</th>
                <th>Valor</th>
              </tr>
            </thead>
            <tbody>
              {entries.map(([addr, val]) => (
                <tr
                  key={addr}
                  className={changedAddresses.has(addr) ? 'dmem-row--changed' : ''}
                >
                  <td className="dmem-addr">[{addr.padStart(4, '\u00A0')}]</td>
                  <td className="dmem-val">{val}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
