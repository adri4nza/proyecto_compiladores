# NOTA IMPORTANTE: gcc/clang clasifican los archivos por EXTENSION. Un
# archivo llamado "TM.C" (con C mayuscula) se compila como C++, no como C
# (regla documentada de gcc: .c = C, .C/.cc/.cpp = C++). Como TM.C es C
# clasico (enums, estilo K&R), compilarlo como C++ falla (p.ej. "op++" sobre
# un enum no es valido en C++). La flag "-x c" fuerza el modo C sin tener
# que renombrar el archivo que el resto del equipo/documentacion referencia
# como "TM.C". Si alguna vez corren "gcc TM.C -o tm" a mano SIN -x c, va a
# fallar con errores raros de C++: usen siempre este Makefile o agreguen -x c.
CC = gcc
CFLAGS = -x c -Wall -g

all: tm

tm: TM.C
	$(CC) $(CFLAGS) -o tm TM.C

clean:
	rm -f tm

.PHONY: all clean
