import Editor from '@monaco-editor/react'
import { useEffect } from 'react'
import type { Monaco } from '@monaco-editor/react'
import type { CompilerError } from '../../types/tm.types'
import './CodeEditor.css'

interface CodeEditorProps {
  value:    string
  onChange: (value: string | undefined) => void
  errors?:  CompilerError[]
}

const KEYWORDS      = ['if','then','else','end','repeat','until','read','write','function','procedure','return']
const TYPE_KEYWORDS = ['int','bool','string']
const BOOLEANS      = ['true','false']

/**
 * Registra el lenguaje Tiny++ y el tema oscuro en Monaco.
 * Se llama desde el prop `beforeMount`, que corre ANTES del primer render
 * del editor — esto evita el flash de fondo blanco.
 */
function setupMonaco(monaco: Monaco) {
  // Guard: solo registrar una vez por sesión
  if (monaco.languages.getLanguages().some(l => l.id === 'tiny-plus')) return

  monaco.languages.register({ id: 'tiny-plus', extensions: ['.tny'] })

  monaco.languages.setMonarchTokensProvider('tiny-plus', {
    keywords:     KEYWORDS,
    typeKeywords: TYPE_KEYWORDS,
    booleans:     BOOLEANS,
    tokenizer: {
      root: [
        [/\{[^}]*\}/, 'comment'],
        [/"[^"]*"/,   'string'],
        [/\d+/,       'number'],
        [/[a-zA-Z][a-zA-Z0-9_]*/, {
          cases: {
            '@keywords':     'keyword',
            '@typeKeywords': 'type',
            '@booleans':     'constant',
            '@default':      'identifier',
          },
        }],
        [/:=/,          'operator'],
        [/[<>=+\-*/]/,  'operator'],
        [/[;,[\]()]/,   'delimiter'],
        [/\s+/,         'white'],
      ],
    },
  } as Parameters<typeof monaco.languages.setMonarchTokensProvider>[1])

  // Tema alineado con la paleta CSS del proyecto
  monaco.editor.defineTheme('tiny-dark', {
    base:    'vs-dark',
    inherit: true,
    rules: [
      { token: 'keyword',    foreground: '38b8c8', fontStyle: 'bold'   },
      { token: 'type',       foreground: '40c880', fontStyle: 'bold'   },
      { token: 'constant',   foreground: 'e0a020'                      },
      { token: 'number',     foreground: 'f0c050'                      },
      { token: 'string',     foreground: '70e0a0'                      },
      { token: 'comment',    foreground: '3a5a68', fontStyle: 'italic' },
      { token: 'operator',   foreground: 'b0ccd4'                      },
      { token: 'identifier', foreground: 'b0ccd4'                      },
      { token: 'delimiter',  foreground: '6a9aaa'                      },
    ],
    colors: {
      'editor.background':                   '#07090a',
      'editor.foreground':                   '#b0ccd4',
      'editorLineNumber.foreground':         '#3a5a68',
      'editorLineNumber.activeForeground':   '#6a9aaa',
      'editor.lineHighlightBackground':      '#0d1214',
      'editorCursor.foreground':             '#40c880',
      'editor.selectionBackground':          '#1a2428',
      'editorIndentGuide.background1':       '#1e2e34',
      'editorIndentGuide.activeBackground1': '#2a3e46',
      'editorWidget.background':             '#131a1d',
      'editorSuggestWidget.background':      '#131a1d',
      'editorSuggestWidget.border':          '#1e2e34',
    },
  })
}

export default function CodeEditor({ value, onChange, errors = [] }: CodeEditorProps) {
  // Marcar errores del compilador sobre el modelo del editor
  // Esto sí requiere que el editor ya exista, se hace con onMount
  useEffect(() => {
    // El marcado de errores se delega a onMount vía closure;
    // Monaco expone el modelo globalmente después del mount.
    // Lo implementamos en el onMount handler de abajo.
  }, [errors])

  return (
    <div className="code-editor-wrapper">
      <div className="code-editor-tab">
        <span className="tab-label">main.tny</span>
        {errors.length > 0 && (
          <span className="tab-error-count">⚠ {errors.length} error{errors.length > 1 ? 'es' : ''}</span>
        )}
      </div>
      <Editor
        height="calc(100% - 28px)"
        language="tiny-plus"
        theme="tiny-dark"
        value={value}
        onChange={onChange}
        // ← beforeMount garantiza que el tema exista ANTES del primer paint
        beforeMount={setupMonaco}
        onMount={(_, monaco) => {
          // Aplicar errores iniciales si ya los hay al montar
          const model = monaco.editor.getModels()[0]
          if (!model || errors.length === 0) return
          const markers = errors.map(e => ({
            startLineNumber: e.linea,
            startColumn:     1,
            endLineNumber:   e.linea,
            endColumn:       model.getLineLength(e.linea) + 1,
            message:         `[${e.tipo}] ${e.mensaje}`,
            severity:        monaco.MarkerSeverity.Error,
          }))
          monaco.editor.setModelMarkers(model, 'tiny-compiler', markers)
        }}
        options={{
          fontSize:             13,
          fontFamily:           "'IBM Plex Mono', 'Fira Code', monospace",
          fontLigatures:        true,
          lineNumbers:          'on',
          minimap:              { enabled: false },
          scrollBeyondLastLine: false,
          wordWrap:             'on',
          renderLineHighlight:  'line',
          cursorBlinking:       'phase',
          smoothScrolling:      true,
          padding:              { top: 12, bottom: 12 },
          overviewRulerLanes:   0,
          scrollbar:            { verticalScrollbarSize: 6, horizontalScrollbarSize: 6 },
        }}
      />
    </div>
  )
}
