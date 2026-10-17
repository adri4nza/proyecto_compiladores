#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# pruebas_m1.sh -- Suite de regresion del Modulo 1 (Analisis Semantico)
# Compilador Tiny++ -- UNET
#
# QUE HACE
#   1. Compila el motor del compilador el solo, a un directorio temporal
#      FUERA del repositorio (con -encoding ISO-8859-1, como exige el fuente).
#   2. Corre TODOS los programas .tny de ejemplo_fuente/tinypp/.
#   3. Verifica, para cada uno, el codigo de salida, el stderr (contrato B:
#      una linea JSON por error) y el stdout (contrato D: solo codigo .tm).
#
# CONTRATO QUE SE VERIFICA
#   stdout = solo el codigo objeto .tm
#   stderr = una linea JSON por error, con las claves tipo / linea / mensaje
#   exit 0 = compilo bien       exit 1 = hubo errores
#
# DOS SENALES DISTINTAS
#   Este guion separa a proposito dos cosas que no son lo mismo:
#     FALLA        -> regresion del Modulo 1. Bloquea: exit code 1.
#     PENDIENTE    -> deuda YA DIAGNOSTICADA de otro modulo, declarada en la
#                     tabla DEUDA_CONOCIDA. No bloquea.
#   Asi el guion puede estar en verde para el Modulo 1 aunque todavia haya
#   trabajo pendiente en otros modulos. Un guion que sale en rojo desde el
#   primer dia termina ignorandose, y entonces no sirve para nada.
#
#   Si una entrada de DEUDA_CONOCIDA pasa a cumplirse, el guion lo reporta
#   como RESUELTO y FALLA a proposito: hay que quitar la exencion de la
#   tabla. Las exenciones eternas tapan regresiones.
#
# USO
#   bash pruebas_m1.sh            # desde cualquier directorio
#   bash pruebas_m1.sh -v         # muestra tambien el detalle de los PASA
#
# SALIDA
#   Una linea por asercion y un resumen final con tres numeros separados:
#   pasadas, fallas reales y pendientes de otros modulos.
#   Codigo de salida 0 si no hay fallas reales ni exenciones obsoletas.
# ---------------------------------------------------------------------------

set -u

# ---------------------------------------------------------------------------
# TABLA DE CASOS DE ERROR
#
#   archivo.tny | tipo esperado | linea esperada
#
#   tipo esperado es uno de: lexico, sintactico, semantico
#
#   La asercion es "el stderr CONTIENE al menos una linea JSON de ese tipo
#   en esa linea". NUNCA se exige un numero total de errores: el analizador
#   semantico puede emitir errores adicionales sin que esta suite se rompa.
#
#   PARA AGREGAR UN CASO NUEVO:
#     1. cree el .tny en ejemplo_fuente/tinypp/ con nombre err_<algo>.tny
#     2. agregue aqui una linea con las tres columnas separadas por '|'
#   Cualquier .tny que NO aparezca en esta tabla se trata como caso VALIDO
#   (se le exige exit 0 y stderr vacio).
# ---------------------------------------------------------------------------
CASOS_ERROR=(
"err_no_declarada.tny       | semantico  | 5"
"err_tipos_asignacion.tny   | semantico  | 6"
"err_redeclaracion.tny      | semantico  | 5"
"err_indice_no_int.tny      | semantico  | 6"
"err_args_funcion.tny       | semantico  | 9"
"err_tipo_argumento.tny     | semantico  | 9"
"err_string_asignacion.tny  | semantico  | 6"
"err_condicion_no_bool.tny  | semantico  | 6"
"err_falta_return.tny       | semantico  | 6"
"err_arreglo_sin_indexar.tny| semantico  | 6"
"err_proc_en_expresion.tny  | semantico  | 10"
"err_lexico.tny             | lexico     | 6"
"err_sintactico.tny         | sintactico | 7"
)

# ---------------------------------------------------------------------------
# IDENTIFICADORES DE ASERCION
# Cada comprobacion que hace el guion tiene un id corto y estable. Son los
# que se usan en la tabla DEUDA_CONOCIDA de mas abajo:
#
#   stderr-json           todas las lineas de stderr son JSON del contrato B
#   exit                  el codigo de salida es el esperado (0 o 1)
#   stderr-vacio          un caso valido no emite ni una linea por stderr
#   stdout-sin-cabeceras  ninguna linea del .tm empieza por '-' (contrato D)
#   error-esperado        hay un error del tipo esperado en la linea esperada
#   ancla                 07_factorial_tpp genera el numero exacto de
#                         instrucciones de referencia
# ---------------------------------------------------------------------------

# ---------------------------------------------------------------------------
# MOTIVOS DE LA DEUDA CONOCIDA
# Texto que se le muestra al responsable del otro modulo, con la ubicacion
# EXACTA del codigo a arreglar para que no tenga que investigar nada.
# ---------------------------------------------------------------------------
# (sin deuda abierta)

# ---------------------------------------------------------------------------
# TABLA DE DEUDA CONOCIDA DE OTROS MODULOS
#
#   archivo.tny | id-asercion | modulo | motivo
#
#   Cada entrada EXIME a una asercion concreta de bloquear al Modulo 1,
#   porque el fallo ya esta diagnosticado y su arreglo le corresponde a
#   otro integrante del equipo.
#
#   REGLAS DE USO
#     - Si la asercion exenta FALLA  -> se reporta PENDIENTE <modulo>,
#       no cuenta para el exit code.
#     - Si la asercion exenta PASA   -> se reporta RESUELTO y el guion
#       FALLA: la deuda ya se pago, hay que BORRAR la entrada de aqui.
#     - Una exencion no es un permiso permanente. Si una entrada lleva
#       semanas sin resolverse, el problema es el equipo, no el guion.
#
#   ESTAS ENTRADAS NO SON DEL MODULO 1. El analisis semantico esta limpio
#   en los 20 casos; lo que falla es la generacion de codigo (Modulo 2).
# ---------------------------------------------------------------------------
DEUDA_CONOCIDA=(
# (vacia) Deuda A y Deuda B de M2 resueltas en la rama modulo-2.
)

# ---------------------------------------------------------------------------
# ANCLA DE REGRESION DEL CODIGO OBJETO
# 07_factorial_tpp.tny es el factorial del Tiny original mas las
# declaraciones que Tiny++ exige. El numero de instrucciones generadas
# demuestra que el analisis semantico asigna las MISMAS direcciones de
# memoria que el cargarTabla del compilador Tiny original.
# ---------------------------------------------------------------------------
ANCLA_ARCHIVO="07_factorial_tpp.tny"
ANCLA_INSTRUCCIONES=41

# ---------------------------------------------------------------------------
# Rutas: siempre relativas a la ubicacion de ESTE script, nunca al cwd.
# ---------------------------------------------------------------------------
DIR_SCRIPT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAIZ="$DIR_SCRIPT/../motor_compilador/Compiladores_Tiny_Generacion_2022_1"
DIR_FUENTES="$RAIZ/ejemplo_fuente/tinypp"
CUP_JAR="$RAIZ/src/especificacion/java-cup-11b-runtime.jar"
CLASE_PRINCIPAL="ve.edu.unet.parser"

VERBOSE=0
if [ "${1:-}" = "-v" ] || [ "${1:-}" = "--verbose" ]; then
	VERBOSE=1
fi

# ---------------------------------------------------------------------------
# Colores: solo si la salida es una terminal. Si se redirige a un archivo
# el reporte queda en texto plano y perfectamente legible.
# ---------------------------------------------------------------------------
C_OK=""; C_MAL=""; C_PEND=""; C_TIT=""; C_FIN=""
if [ -t 1 ] && [ -z "${NO_COLOR:-}" ]; then
	C_OK=$'\033[32m'; C_MAL=$'\033[31m'; C_PEND=$'\033[33m'
	C_TIT=$'\033[1m'; C_FIN=$'\033[0m'
fi

# Separador interno de los registros de pendientes. Se declara con $'\t'
# y NUNCA como un tabulador literal dentro de una expresion, para que el
# guion siga funcionando si un editor reindenta el archivo.
TAB=$'\t'

TOTAL=0          # aserciones ejecutadas
PASARON=0        # aserciones que se cumplieron
FALLAS=0         # regresiones reales: BLOQUEAN
PENDIENTES=0     # deuda conocida que sigue sin pagarse: no bloquea
OBSOLETAS=0      # deuda conocida ya resuelta: BLOQUEA (hay que limpiar la tabla)
OMITIDAS=0       # aserciones no evaluables en esta corrida: no cuentan

DETALLE_FALLAS=""
DETALLE_OBSOLETAS=""

titulo() { printf '%s%s%s\n' "$C_TIT" "$1" "$C_FIN"; }

# ---------------------------------------------------------------------------
# PASO 0 -- comprobaciones previas
# ---------------------------------------------------------------------------
titulo "== Suite de regresion -- Modulo 1 (Analisis Semantico) Tiny++ =="
echo "Raiz del motor : $RAIZ"
echo "Fuentes .tny   : $DIR_FUENTES"
echo

if [ ! -d "$RAIZ/src/ve" ]; then
	echo "ERROR: no encuentro los fuentes java en $RAIZ/src/ve" >&2
	echo "       revise la ubicacion de este script respecto al motor." >&2
	exit 1
fi
if [ ! -d "$DIR_FUENTES" ]; then
	echo "ERROR: no encuentro el directorio de pruebas $DIR_FUENTES" >&2
	exit 1
fi
if [ ! -f "$CUP_JAR" ]; then
	echo "ERROR: no encuentro el runtime de CUP en $CUP_JAR" >&2
	exit 1
fi
if ! command -v javac >/dev/null 2>&1 || ! command -v java >/dev/null 2>&1; then
	echo "ERROR: hacen falta 'javac' y 'java' en el PATH." >&2
	exit 1
fi

# ---------------------------------------------------------------------------
# PASO 1 -- construir el compilador en un directorio temporal fuera del repo
# ---------------------------------------------------------------------------
DIR_BUILD="$(mktemp -d "${TMPDIR:-/tmp}/pruebas_m1.XXXXXX")"
DIR_TRABAJO="$DIR_BUILD/run"
mkdir -p "$DIR_TRABAJO"
ARCHIVO_PEND="$DIR_TRABAJO/pendientes.tsv"
: > "$ARCHIVO_PEND"
limpiar() { rm -rf "$DIR_BUILD"; }
trap limpiar EXIT

titulo "-- Construccion del compilador --"
echo "Directorio de clases: $DIR_BUILD"

FUENTES_JAVA="$(find "$RAIZ/src/ve" -name '*.java' | sort)"
if [ -z "$FUENTES_JAVA" ]; then
	echo "ERROR: no hay fuentes .java bajo $RAIZ/src/ve" >&2
	exit 1
fi

# shellcheck disable=SC2086
javac -encoding ISO-8859-1 -nowarn -cp "$CUP_JAR" -d "$DIR_BUILD" $FUENTES_JAVA \
	> "$DIR_BUILD/build.log" 2>&1
BUILD_EXIT=$?
grep -v 'deprecat' "$DIR_BUILD/build.log" | grep -v '^[[:space:]]*$' || true

if [ $BUILD_EXIT -ne 0 ]; then
	echo
	echo "ERROR: la construccion del compilador FALLO (javac devolvio $BUILD_EXIT)." >&2
	echo "       no se ejecuta ninguna prueba. Log completo arriba." >&2
	exit 1
fi
echo "Construccion OK."
echo

# ---------------------------------------------------------------------------
# Utilidades de las tablas
# ---------------------------------------------------------------------------

# recorta espacios al inicio y al final
recortar() {
	local s="$1"
	s="${s#"${s%%[![:space:]]*}"}"
	s="${s%"${s##*[![:space:]]}"}"
	printf '%s' "$s"
}

# buscar_caso <archivo.tny> -> imprime "tipo|linea" y devuelve 0 si esta
# en la tabla de casos de error; devuelve 1 si no esta.
buscar_caso() {
	local fila nombre resto tipo lin
	for fila in "${CASOS_ERROR[@]}"; do
		nombre="$(recortar "${fila%%|*}")"
		if [ "$nombre" = "$1" ]; then
			resto="${fila#*|}"
			tipo="$(recortar "${resto%%|*}")"
			lin="$(recortar "${resto##*|}")"
			printf '%s|%s' "$tipo" "$lin"
			return 0
		fi
	done
	return 1
}

# buscar_deuda <archivo.tny> <id-asercion>
# -> imprime "modulo<TAB>motivo" y devuelve 0 si la asercion esta exenta.
buscar_deuda() {
	local fila nombre resto id modulo motivo
	for fila in "${DEUDA_CONOCIDA[@]}"; do
		nombre="$(recortar "${fila%%|*}")"
		[ "$nombre" = "$1" ] || continue
		resto="${fila#*|}"
		id="$(recortar "${resto%%|*}")"
		[ "$id" = "$2" ] || continue
		resto="${resto#*|}"
		modulo="$(recortar "${resto%%|*}")"
		motivo="$(recortar "${resto#*|}")"
		printf '%s%s%s' "$modulo" "$TAB" "$motivo"
		return 0
	done
	return 1
}

# ---------------------------------------------------------------------------
# registrar <archivo> <id-asercion> <cumplio:0|1|2> <descripcion> <esperado> <obtenido>
#
# Unico punto por el que pasan TODAS las aserciones. Aqui se decide si un
# incumplimiento es una regresion del Modulo 1 (FALLA, bloquea) o una deuda
# ya diagnosticada de otro modulo (PENDIENTE, no bloquea), y si una exencion
# se quedo obsoleta (RESUELTO, bloquea).
#
#   cumplio = 1  la asercion se cumple
#   cumplio = 0  la asercion NO se cumple
#   cumplio = 2  la asercion NO SE PUDO EVALUAR en esta corrida; el motivo
#                viaja en <esperado>. Se reporta N/A y no cuenta para nada.
#
# POR QUE HACE FALTA EL ESTADO 2
# Una asercion puede "cumplirse" de forma vacia cuando una etapa anterior
# fallo. Ejemplo real: si un fixture valido rompe el analisis semantico, el
# compilador no genera codigo, el stdout queda vacio y la comprobacion de
# cabeceras del .tm se cumpliria sola. Con la exencion puesta eso marcaria
# la deuda de M2 como RESUELTA sin que nadie la hubiera arreglado. Marcarla
# N/A evita ese falso positivo.
# ---------------------------------------------------------------------------
registrar() {
	local archivo="$1" id="$2" cumplio="$3" desc="$4" esp="$5" obt="$6"
	local etiqueta="$archivo :: $desc"
	local deuda modulo motivo

	# No evaluable: ni cuenta ni cambia el veredicto de ninguna deuda.
	if [ "$cumplio" -eq 2 ]; then
		OMITIDAS=$((OMITIDAS + 1))
		printf '  %s%-12s%s %s\n' "$C_PEND" "N/A" "$C_FIN" "$etiqueta"
		printf '          no evaluable: %s\n' "$esp"
		return
	fi

	TOTAL=$((TOTAL + 1))

	if deuda="$(buscar_deuda "$archivo" "$id")"; then
		modulo="${deuda%%$TAB*}"
		motivo="${deuda#*$TAB}"

		if [ "$cumplio" -eq 1 ]; then
			# La deuda ya esta pagada: la exencion sobra y hay que quitarla.
			PASARON=$((PASARON + 1))
			OBSOLETAS=$((OBSOLETAS + 1))
			printf '  %s%-12s%s %s\n' "$C_MAL" "RESUELTO" "$C_FIN" "$etiqueta"
			printf '          esta deuda ya esta resuelta: quita la entrada\n'
			printf '          "%s | %s | %s | ..." de la tabla DEUDA_CONOCIDA.\n' \
				"$archivo" "$id" "$modulo"
			DETALLE_OBSOLETAS="${DETALLE_OBSOLETAS}  - ${etiqueta}
      quita de DEUDA_CONOCIDA la entrada: ${archivo} | ${id} | ${modulo}
"
			return
		fi

		# La deuda sigue sin pagarse: se informa, pero no bloquea al Modulo 1.
		PENDIENTES=$((PENDIENTES + 1))
		printf '  %s%-12s%s %s\n' "$C_PEND" "PENDIENTE $modulo" "$C_FIN" "$etiqueta"
		printf '          esperado: %s\n' "$esp"
		printf '          obtenido: %s\n' "$obt"
		printf '%s%s%s%s%s\n' "$motivo" "$TAB" "$etiqueta" "$TAB" "$modulo" \
			>> "$ARCHIVO_PEND"
		return
	fi

	if [ "$cumplio" -eq 1 ]; then
		PASARON=$((PASARON + 1))
		printf '  %s%-12s%s %s\n' "$C_OK" "PASA" "$C_FIN" "$etiqueta"
		return
	fi

	FALLAS=$((FALLAS + 1))
	printf '  %s%-12s%s %s\n' "$C_MAL" "FALLA" "$C_FIN" "$etiqueta"
	printf '          esperado: %s\n' "$esp"
	printf '          obtenido: %s\n' "$obt"
	DETALLE_FALLAS="${DETALLE_FALLAS}  - ${etiqueta}
      esperado: ${esp}
      obtenido: ${obt}
"
}

# Toda linea no vacia de stderr debe ser JSON del contrato B:
# empezar con '{' y traer las tres claves tipo, linea y mensaje.
# Imprime la primera linea ofensora; silencio = todo bien.
primera_linea_no_json() {
	local archivo="$1" linea
	while IFS= read -r linea; do
		case "$linea" in
			'') continue ;;
		esac
		case "$linea" in
			'{'*) ;;
			*) printf '%s' "$linea"; return 0 ;;
		esac
		if ! printf '%s' "$linea" | grep -q '"tipo"' \
		   || ! printf '%s' "$linea" | grep -q '"linea"' \
		   || ! printf '%s' "$linea" | grep -q '"mensaje"'; then
			printf '%s' "$linea"
			return 0
		fi
	done < "$archivo"
	return 1
}

# Recorta un texto largo para que el reporte siga siendo legible.
resumir() {
	local t
	t="$(tr '\n' ' ' < "$1" | sed 's/[[:space:]][[:space:]]*/ /g')"
	if [ ${#t} -gt 140 ]; then
		printf '%s...' "${t:0:140}"
	elif [ -z "$t" ]; then
		printf '(vacio)'
	else
		printf '%s' "$t"
	fi
}

# ---------------------------------------------------------------------------
# PASO 2 -- ejecutar cada fixture
# ---------------------------------------------------------------------------
LISTA="$(find "$DIR_FUENTES" -maxdepth 1 -name '*.tny' | sort)"
if [ -z "$LISTA" ]; then
	echo "ERROR: no hay ningun .tny en $DIR_FUENTES" >&2
	exit 1
fi

TIMEOUT=""
if command -v timeout >/dev/null 2>&1; then
	TIMEOUT="timeout 30"
fi

titulo "-- Ejecucion de los casos de prueba --"

for ARCHIVO in $LISTA; do
	BASE="$(basename "$ARCHIVO")"
	SALIDA="$DIR_TRABAJO/$BASE.out"
	ERRORES="$DIR_TRABAJO/$BASE.err"

	printf '%s%s%s\n' "$C_TIT" "$BASE" "$C_FIN"

	# shellcheck disable=SC2086
	$TIMEOUT java -cp "$DIR_BUILD:$CUP_JAR" "$CLASE_PRINCIPAL" "$ARCHIVO" \
		> "$SALIDA" 2> "$ERRORES"
	CODIGO=$?

	# --- Asercion comun: todo el stderr debe ser JSON del contrato B -----
	OFENSORA="$(primera_linea_no_json "$ERRORES")"
	if [ -n "$OFENSORA" ]; then
		registrar "$BASE" "stderr-json" 0 "stderr es JSON valido (contrato B)" \
			"toda linea de stderr empieza con '{' y trae tipo/linea/mensaje" \
			"linea no conforme: $OFENSORA"
	else
		registrar "$BASE" "stderr-json" 1 "stderr es JSON valido (contrato B)" "" ""
	fi

	if ESPERA="$(buscar_caso "$BASE")"; then
		# ================= CASO DE ERROR ==================================
		TIPO_ESP="${ESPERA%%|*}"
		LINEA_ESP="${ESPERA##*|}"

		if [ "$CODIGO" -eq 1 ]; then
			registrar "$BASE" "exit" 1 "codigo de salida 1" "" ""
		else
			registrar "$BASE" "exit" 0 "codigo de salida 1" "1" "$CODIGO"
		fi

		PATRON="^\{.*\"tipo\":[[:space:]]*\"${TIPO_ESP}\".*\"linea\":[[:space:]]*${LINEA_ESP}[^0-9]"
		if grep -qE "$PATRON" "$ERRORES"; then
			registrar "$BASE" "error-esperado" 1 \
				"error $TIPO_ESP en la linea $LINEA_ESP" "" ""
			if [ $VERBOSE -eq 1 ]; then
				grep -E "$PATRON" "$ERRORES" | sed 's/^/          /'
			fi
		else
			registrar "$BASE" "error-esperado" 0 \
				"error $TIPO_ESP en la linea $LINEA_ESP" \
				"una linea JSON con tipo=$TIPO_ESP y linea=$LINEA_ESP" \
				"$(resumir "$ERRORES")"
		fi
	else
		# ================= CASO VALIDO ====================================
		if [ "$CODIGO" -eq 0 ]; then
			registrar "$BASE" "exit" 1 "codigo de salida 0" "" ""
		else
			registrar "$BASE" "exit" 0 "codigo de salida 0" "0" \
				"$CODIGO (stderr: $(resumir "$ERRORES"))"
		fi

		NLINEAS_ERR=$(grep -c . "$ERRORES" 2>/dev/null || true)
		NLINEAS_ERR=${NLINEAS_ERR:-0}
		if [ "$NLINEAS_ERR" -eq 0 ]; then
			registrar "$BASE" "stderr-vacio" 1 "stderr vacio (cero errores)" "" ""
		else
			registrar "$BASE" "stderr-vacio" 0 "stderr vacio (cero errores)" \
				"0 lineas en stderr" \
				"$NLINEAS_ERR lineas: $(resumir "$ERRORES")"
		fi

		# Contrato D: el stdout lleva SOLO el codigo objeto. Una linea que
		# empiece por '-' es una cabecera decorativa del Generador
		# contaminando el .tm.
		CABECERAS="$(grep -c '^-' "$SALIDA" 2>/dev/null || true)"
		CABECERAS=${CABECERAS:-0}
		if [ ! -s "$SALIDA" ]; then
			# Sin codigo objeto no hay .tm que revisar: la asercion se
			# cumpliria sola y daria por resuelta una deuda que nadie toco.
			registrar "$BASE" "stdout-sin-cabeceras" 2 \
				"stdout sin cabeceras (ninguna linea empieza por '-')" \
				"el compilador no genero codigo objeto (stdout vacio)" ""
		elif [ "$CABECERAS" -eq 0 ]; then
			registrar "$BASE" "stdout-sin-cabeceras" 1 \
				"stdout sin cabeceras (ninguna linea empieza por '-')" "" ""
		else
			registrar "$BASE" "stdout-sin-cabeceras" 0 \
				"stdout sin cabeceras (ninguna linea empieza por '-')" \
				"0 lineas que empiecen por '-'" \
				"$CABECERAS lineas, la primera: $(grep -m1 '^-' "$SALIDA")"
		fi
	fi

	# --- Ancla de regresion del codigo objeto -----------------------------
	if [ "$BASE" = "$ANCLA_ARCHIVO" ]; then
		N_INSTR="$(grep -cE '^[0-9]+:[[:space:]]' "$SALIDA" 2>/dev/null || true)"
		N_INSTR=${N_INSTR:-0}
		if [ "$N_INSTR" -eq "$ANCLA_INSTRUCCIONES" ]; then
			registrar "$BASE" "ancla" 1 \
				"ancla de regresion, $ANCLA_INSTRUCCIONES instrucciones generadas" "" ""
		else
			registrar "$BASE" "ancla" 0 \
				"ancla de regresion, $ANCLA_INSTRUCCIONES instrucciones generadas" \
				"$ANCLA_INSTRUCCIONES instrucciones (^[0-9]+: ...)" \
				"$N_INSTR instrucciones"
		fi
	fi

	echo
done

# ---------------------------------------------------------------------------
# PASO 3 -- resumen
# ---------------------------------------------------------------------------
titulo "-- Resumen --"

if [ "$PENDIENTES" -gt 0 ]; then
	titulo "Deuda conocida de otros modulos (no bloquea al Modulo 1):"
	ULTIMO_MOTIVO=""
	while IFS="$TAB" read -r MOT ETI MOD; do
		if [ "$MOT" != "$ULTIMO_MOTIVO" ]; then
			printf '\n  [%s] %s\n' "$MOD" "$MOT"
			printf '  aserciones afectadas:\n'
			ULTIMO_MOTIVO="$MOT"
		fi
		printf '      - %s\n' "$ETI"
	done < <(sort -t"$TAB" -k1,1 -s "$ARCHIVO_PEND")
	echo
fi

if [ -n "$DETALLE_OBSOLETAS" ]; then
	titulo "Exenciones OBSOLETAS (la deuda ya se resolvio: limpiar la tabla):"
	printf '%s' "$DETALLE_OBSOLETAS"
	echo
fi

if [ -n "$DETALLE_FALLAS" ]; then
	titulo "Fallas reales del Modulo 1 (bloquean):"
	printf '%s' "$DETALLE_FALLAS"
	echo
fi

printf '%s/%s pruebas pasaron\n' "$PASARON" "$TOTAL"
printf '  fallas reales (bloquean)              : %s\n' "$FALLAS"
printf '  pendientes de otros modulos (deuda)   : %s\n' "$PENDIENTES"
printf '  exenciones obsoletas (bloquean)       : %s\n' "$OBSOLETAS"
if [ "$OMITIDAS" -gt 0 ]; then
	printf '  no evaluables en esta corrida (N/A)   : %s\n' "$OMITIDAS"
fi
echo

if [ "$FALLAS" -eq 0 ] && [ "$OBSOLETAS" -eq 0 ]; then
	if [ "$PENDIENTES" -gt 0 ]; then
		printf '%sMODULO 1 EN VERDE%s (con %s pendientes de otros modulos)\n' \
			"$C_OK" "$C_FIN" "$PENDIENTES"
	else
		printf '%sTODO EN VERDE%s\n' "$C_OK" "$C_FIN"
	fi
	exit 0
fi

if [ "$FALLAS" -gt 0 ]; then
	printf '%s%s fallas REALES del Modulo 1%s\n' "$C_MAL" "$FALLAS" "$C_FIN"
fi
if [ "$OBSOLETAS" -gt 0 ]; then
	printf '%s%s exenciones obsoletas en DEUDA_CONOCIDA%s\n' "$C_MAL" "$OBSOLETAS" "$C_FIN"
fi
exit 1
