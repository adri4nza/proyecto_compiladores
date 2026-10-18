#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# pruebas_m2.sh -- Suite de ejecucion del Modulo 2 (Generacion de codigo TM)
# Compilador Tiny++ -- UNET
#
# QUE HACE
#   1. Compila el motor del compilador a un directorio temporal FUERA del
#      repositorio (con -encoding ISO-8859-1, como exige el fuente).
#   2. Para cada caso de la tabla CASOS: compila el .tny, exige exit 0 y
#      stderr vacio, EJECUTA el .tm en tm_ref.py con la entrada dada y
#      compara lo que imprimen los OUT con la salida esperada.
#
#   pruebas_m1.sh verifica que el compilador ACEPTA y RECHAZA lo correcto.
#   Esta suite verifica que el codigo generado HACE lo correcto.
#
# USO
#   bash pruebas_m2.sh       # desde cualquier directorio (WSL o Git Bash)
#
# Codigo de salida 0 si todos los casos pasan.
# ---------------------------------------------------------------------------

set -u

DIR_M2="$(cd "$(dirname "$0")" && pwd)"
RAIZ="$DIR_M2/../motor_compilador/Compiladores_Tiny_Generacion_2022_1"
TINYPP="$RAIZ/ejemplo_fuente/tinypp"
CASOS_M2="$DIR_M2/casos"

# ---------------------------------------------------------------------------
# TABLA DE CASOS
#
#   fuente.tny | entradas (separadas por espacio) | salida esperada
#
#   La salida esperada son los valores de los OUT, en orden, separados por
#   espacio. Cada OUTC aparece como su caracter entre comillas: 'h'.
#   Un mismo fuente puede aparecer varias veces con otra entrada.
# ---------------------------------------------------------------------------
CASOS=(
"$TINYPP/01_declaraciones_ok.tny | 3   | 4 0 'f' 'i' 'n'"
"$TINYPP/02_arreglos_ok.tny      | 7   | 0 7"
"$TINYPP/03_funciones_ok.tny     |     | 8"
"$TINYPP/04_procedimientos_ok.tny |     | 7"
"$TINYPP/05_booleanos_ok.tny     | 0   | 0"
"$TINYPP/05_booleanos_ok.tny     | 1   | 1"
"$TINYPP/05_booleanos_ok.tny     | 5   | 5"
"$TINYPP/06_scopes_ok.tny        |     | 6"
"$TINYPP/07_factorial_tpp.tny    | 5   | 120"
"$TINYPP/07_factorial_tpp.tny    | 0   | "
"$CASOS_M2/arreglos_layout.tny   | 100 | 16 9 4 1 0 11 22 99 109"
"$CASOS_M2/funciones.tny         |     | 7 31 25 22 2 '!' 9 9 10 4 10"
"$CASOS_M2/recursion.tny         | 5   | 120"
"$CASOS_M2/arreglos_param.tny    | 100 | 18 100 112 11 9"
"$CASOS_M2/cadenas.tny           |     | 'h' 'i' 'o' 'k' 'h' 'i' 16"
"$CASOS_M2/logica.tny            | 0 0 | 1 0 0 0 0"
"$CASOS_M2/logica.tny            | 0 1 | 1 0 1 1 1"
"$CASOS_M2/logica.tny            | 1 0 | 0 0 1 1 0"
"$CASOS_M2/logica.tny            | 1 1 | 0 1 1 1 1"
)

recortar() { local s="$1"; s="${s#"${s%%[![:space:]]*}"}"; echo "${s%"${s##*[![:space:]]}"}"; }

# Java en Windows necesita rutas nativas y ';' como separador de classpath
if command -v cygpath >/dev/null 2>&1; then
	ruta_java() { cygpath -m "$1"; }
	SEP=";"
else
	ruta_java() { echo "$1"; }
	SEP=":"
fi

PYTHON="$(command -v python3 || command -v python || true)"
if [ -z "$PYTHON" ] || ! command -v javac >/dev/null 2>&1; then
	echo "ERROR: hacen falta 'javac', 'java' y 'python3' en el PATH." >&2
	exit 2
fi

DIR_BUILD="$(mktemp -d)"
trap 'rm -rf "$DIR_BUILD"' EXIT
CUP_JAR="$RAIZ/src/especificacion/java-cup-11b-runtime.jar"

echo "== Suite de ejecucion -- Modulo 2 (Generacion de codigo) Tiny++ =="
echo "-- Construccion del compilador --"
# Lista de fuentes en un argfile: las rutas pueden tener espacios
find "$RAIZ/src/ve" -name '*.java' | while read -r f; do echo "\"$(ruta_java "$f")\""; done > "$DIR_BUILD/fuentes.txt"
if ! javac -encoding ISO-8859-1 -nowarn -cp "$(ruta_java "$CUP_JAR")" \
		-d "$(ruta_java "$DIR_BUILD/clases")" "@$(ruta_java "$DIR_BUILD/fuentes.txt")" 2>&1 | grep -v '^Note:'; then
	:
fi
if [ ! -f "$DIR_BUILD/clases/ve/edu/unet/parser.class" ]; then
	echo "ERROR: la construccion del compilador FALLO." >&2
	exit 2
fi
CP="$(ruta_java "$DIR_BUILD/clases")$SEP$(ruta_java "$CUP_JAR")"

PASAN=0
FALLAN=0
echo "-- Casos --"
for fila in "${CASOS[@]}"; do
	IFS='|' read -r fuente entrada esperado <<< "$fila"
	fuente="$(recortar "$fuente")"
	entrada="$(recortar "$entrada")"
	esperado="$(recortar "$esperado")"
	nombre="$(basename "$fuente") [$entrada]"

	java -cp "$CP" ve.edu.unet.parser "$(ruta_java "$fuente")" \
		> "$DIR_BUILD/prog.tm" 2> "$DIR_BUILD/err.txt"
	codigo=$?
	if [ $codigo -ne 0 ] || [ -s "$DIR_BUILD/err.txt" ]; then
		echo "  FALLA  $nombre :: el compilador salio con $codigo"
		sed 's/^/           /' "$DIR_BUILD/err.txt" | head -5
		FALLAN=$((FALLAN + 1))
		continue
	fi

	# shellcheck disable=SC2086  # la entrada se separa en palabras a proposito
	obtenido="$("$PYTHON" -I "$DIR_M2/tm_ref.py" "$DIR_BUILD/prog.tm" $entrada 2> "$DIR_BUILD/run.txt" | tr -d '\r' | tr '\n' ' ')"
	obtenido="$(recortar "$obtenido")"
	if [ -s "$DIR_BUILD/run.txt" ]; then
		echo "  FALLA  $nombre :: $(head -1 "$DIR_BUILD/run.txt")"
		FALLAN=$((FALLAN + 1))
	elif [ "$obtenido" != "$esperado" ]; then
		echo "  FALLA  $nombre :: se esperaba [$esperado], se obtuvo [$obtenido]"
		FALLAN=$((FALLAN + 1))
	else
		echo "  PASA   $nombre -> [$obtenido]"
		PASAN=$((PASAN + 1))
	fi
done

echo
echo "$PASAN/$((PASAN + FALLAN)) casos pasaron"
[ $FALLAN -eq 0 ]
