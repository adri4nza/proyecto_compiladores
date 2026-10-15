import Editor, { useMonaco } from '@monaco-editor/react'
import { useEffect } from 'react'
import type { CompilerError } from '../../types/tm.types'
import './CodeEditor.css'

interface CodeEditorProps {
  value:    string
  onChange: (value: string | undefined) => void
  errors?:  CompilerError[]
}

// Palabras reservadas del lenguaje Tiny++
const KEYWORDS      = ['if','then','else','end','repeat','until','read','write','function','procedure','return']
const TYPE_KEYWORDS = ['int','bool','string']
const BOOLEANS      = ['true','false']

export default function CodeEditor({ value, onChange, errors = [] }: CodeEditorProps) {
  const monaco = useMonaco()

  // Registrar lenguaje y tema una sola vez cuando Monaco esté disponible
  useEffect(() => {
    if (!monaco) return
    if (monaco.languages.getLanguages().some(l => l.id === 'tiny-plus')) return

    monaco.languages.register({ id: 'tiny-plus', extensions: ['.tny'] })

    monaco.languages.setMonarchTokensProvider('tiny-plus', {
      keywords:     KEYWORDS,
      typeKeywords: TYPE_KEYWORDS,
      booleans:     BOOLEANS,
      tokenizer: {
        root: [
          [/\{[^}]*\}/, 'comment'],               // { comentarios }
          [/"[^"]*"/,   'string'],                 // "literales de string"
          [/\d+/,       'number'],                 // enteros
          [/[a-zA-Z][a-zA-Z0-9_]*/, {             // palabras clave e identificadores
            cases: {
              '@keywords':     'keyword',
              '@typeKeywords': 'type',
              '@booleans':     'constant',
              '@default':      'identifier',
            },
          }],
          [/:=/,            'operator'],           // asignación
          [/[<>=+\-*/]/,   'operator'],            // operadores
          [/[;,[\]()]/,    'delimiter'],           // delimitadores
          [/\s+/,           'white'],
        ],
      },
    } as Parameters<typeof monaco.languages.setMonarchTokensProvider>[1])

    // Tema oscuro alineado con la paleta del proyecto
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
  }, [monaco])

  // Marcar errores del compilador como subrayados en el editor
  useEffect(() => {
    if (!monaco) return
    const model = monaco.editor.getModels()[0]
    if (!model) return

    const markers = errors.map(e => ({
      startLineNumber: e.linea,
      startColumn:     1,
      endLineNumber:   e.linea,
      endColumn:       model.getLineLength(e.linea) + 1,
      message:         `[${e.tipo}] ${e.mensaje}`,
      severity:        monaco.MarkerSeverity.Error,
    }))

    monaco.editor.setModelMarkers(model, 'tiny-compiler', markers)
  }, [monaco, errors])

  return (
    <div className="code-editor-wrapper">
      <div className="code-editor-tab">
        <span className="tab-label">main.tny</span>
      </div>
      <Editor
        height="calc(100% - 28px)"
        language="tiny-plus"
        theme="tiny-dark"
        value={value}
        onChange={onChange}
        options={{
          fontSize:              13,
          fontFamily:            "'IBM Plex Mono', 'Fira Code', monospace",
          fontLigatures:         true,
          lineNumbers:           'on',
          minimap:               { enabled: false },
          scrollBeyondLastLine:  false,
          wordWrap:              'on',
          renderLineHighlight:   'line',
          cursorBlinking:        'phase',
          smoothScrolling:       true,
          padding:               { top: 12, bottom: 12 },
          overviewRulerLanes:    0,
          scrollbar:             { verticalScrollbarSize: 6, horizontalScrollbarSize: 6 },
        }}
      />
    </div>
  )
}
