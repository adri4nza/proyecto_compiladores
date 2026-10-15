package ve.edu.unet;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ve.edu.unet.nodosAST.*;

/* AnalizadorSemantico -- verificador semantico de Tiny++.
 *
 * Se ejecuta DESPUES del parseo y ANTES de la generacion de codigo:
 *   parse() -> AnalizadorSemantico.analizar(raiz) -> Generador.generarCodigoObjeto(raiz)
 *
 * QUE HACE
 *  - construye la tabla de simbolos real a partir de las DECLARACIONES
 *    (ya no se usa el cargador cargarTabla del Tiny original, que creaba
 *     simbolos al primer uso porque no existian las declaraciones)
 *  - implementa alcance lexico apoyandose en la pila de ambitos de
 *    TablaSimbolos: un ambito por funcion o procedimiento
 *  - rechaza el uso de identificadores no declarados
 *  - verifica compatibilidad de tipos en asignaciones, aritmetica, logica,
 *    comparaciones, condiciones, read/write, return y llamadas
 *  - anota cada nodo con su tipo sintetizado (setTipo) y, cuando resuelve
 *    un nombre, con su simbolo (setRegistro). ESA ANOTACION ES EL CONTRATO
 *    CON EL MODULO 2: cuando el generador recorre el arbol todos los
 *    ambitos ya estan cerrados, asi que no puede buscar por nombre; tiene
 *    que leer nodo.getRegistro()
 *  - reporta cada hallazgo con Errores.semantico(linea, mensaje)
 *
 * DOS PASADAS
 *  Pasada 1 (registrarFirmas): registra en el ambito global las firmas de
 *  TODAS las funciones y procedimientos del nivel superior, antes de
 *  analizar ningun cuerpo. Asi un procedimiento puede llamar a otro que
 *  este declarado mas abajo en el archivo.
 *  Pasada 2 (recorrer): el trabajo real. Al reencontrar un NodoFuncion o
 *  NodoProcedimiento ya registrado NO lo vuelve a insertar (no se reporta
 *  redeclaracion contra uno mismo): abre su ambito y analiza el cuerpo.
 *
 * REGLA DE ORO CONTRA ERRORES EN CASCADA
 *  Si algun operando ya es Tipo.ERROR se devuelve Tipo.ERROR SIN emitir un
 *  error nuevo. Cada problema se reporta UNA sola vez, en el punto exacto
 *  donde se detecta. Sin esto una sola variable no declarada producia
 *  cinco mensajes y la consola del IDE (modulo 4) se volvia inutil.
 *  Por la misma razon un identificador no declarado se reporta UNA sola
 *  vez por nombre DENTRO DE CADA AMBITO (igual que gcc: "each undeclared
 *  identifier is reported only once for each function it appears in"):
 *  sin esto el factorial del Tiny original escupia diez mensajes para dos
 *  variables faltantes. La deduplicacion es por ambito y no por archivo a
 *  proposito: con un unico conjunto global, un nombre no declarado dentro
 *  de una funcion podia quedar INVISIBLE porque otro uso en otra parte del
 *  programa ya habia consumido el unico mensaje. Un error que no se
 *  muestra es peor que un error que se muestra dos veces.
 *
 * TRAMPA DE HERENCIA
 *  NodoDeclaracionArreglo HEREDA de NodoDeclaracion, de modo que
 *  (arreglo instanceof NodoDeclaracion) es true. En TODA cadena de
 *  if/else-if hay que chequear NodoDeclaracionArreglo ANTES que
 *  NodoDeclaracion o los arreglos se clasifican como variables simples en
 *  silencio. Los dos puntos donde eso importa estan marcados abajo con
 *  el comentario "ORDEN CRITICO".
 */
public class AnalizadorSemantico {

	private TablaSimbolos tabla;

	/* Simbolo de la funcion o procedimiento cuyo cuerpo se esta analizando.
	 * null = estamos en el cuerpo principal del programa. */
	private RegistroSimbolo funcionActual = null;

	/* true si ya se vio al menos un return en el cuerpo actual. */
	private boolean vioRetorno = false;

	/* Nombres ya reportados como "no declarada" EN EL AMBITO ACTUAL.
	 * Evita que un mismo identificador inexistente llene la consola del
	 * IDE con un mensaje por cada uso. NO se toca la tabla de simbolos:
	 * insertar ahi un simbolo centinela generaria falsos "ya fue
	 * declarada" si el programador declara ese nombre mas abajo.
	 *
	 * Se salva y se restaura en cada funcion y procedimiento, igual que
	 * funcionActual y vioRetorno, para que el cuerpo de cada rutina
	 * arranque con su propio conjunto. */
	private Set<String> noDeclaradosReportados = new HashSet<String>();

	public AnalizadorSemantico(TablaSimbolos t) {
		this.tabla = t;
	}

	/* ------------------------------------------------------------------
	 * Punto de entrada unico
	 * ------------------------------------------------------------------ */
	public void analizar(NodoBase raiz) {
		noDeclaradosReportados.clear();
		registrarFirmas(raiz);   /* pasada 1: firmas de funciones y procedimientos */
		recorrer(raiz);          /* pasada 2: todo lo demas */
	}

	public TablaSimbolos getTabla() {
		return tabla;
	}

	/* ==================================================================
	 * PASADA 1 -- firmas del nivel superior
	 * ================================================================== */

	/* Recorre SOLO la cadena de hermanos del nivel superior. No entra en
	 * cuerpos de if, repeat, funciones ni procedimientos: las rutinas
	 * anidadas se registran en la pasada 2, en el ambito donde aparecen. */
	private void registrarFirmas(NodoBase n) {
		while (n != null) {
			if (n instanceof NodoFuncion) {
				NodoFuncion f = (NodoFuncion) n;
				registrarRutina(f, crearFirmaFuncion(f));
			} else if (n instanceof NodoProcedimiento) {
				NodoProcedimiento p = (NodoProcedimiento) n;
				registrarRutina(p, crearFirmaProcedimiento(p));
			}
			n = n.getHermanoDerecha();
		}
	}

	/* Inserta la firma en el ambito actual y la deja anotada en el nodo.
	 * El registro se anota SIEMPRE, aunque la insercion falle, para que la
	 * pasada 2 sepa que esta rutina ya se intento registrar y no vuelva a
	 * reportar la misma redeclaracion. */
	private void registrarRutina(NodoBase nodo, RegistroSimbolo firma) {
		if (!tabla.insertar(firma)) {
			reportarRedeclaracion(firma.getIdentificador(), nodo.getLinea());
		}
		nodo.setRegistro(firma);
		nodo.setTipo(firma.getTipo());
	}

	private RegistroSimbolo crearFirmaFuncion(NodoFuncion f) {
		RegistroSimbolo r = new RegistroSimbolo(f.getNombre(), f.getLinea(),
				f.getTipoRetorno(), Categoria.FUNCION, 1, tabla.nivelActual());
		agregarParametros(r, f.getParametros());
		return r;
	}

	private RegistroSimbolo crearFirmaProcedimiento(NodoProcedimiento p) {
		RegistroSimbolo r = new RegistroSimbolo(p.getNombre(), p.getLinea(),
				Tipo.VACIO, Categoria.PROCEDIMIENTO, 1, tabla.nivelActual());
		agregarParametros(r, p.getParametros());
		return r;
	}

	/* Construye los RegistroSimbolo de los parametros formales y los cuelga
	 * de la firma. Son los MISMOS objetos que mas tarde se insertan en el
	 * ambito de la rutina, asi que el registro que ve el verificador de
	 * llamadas y el que ve el cuerpo son uno solo. */
	private void agregarParametros(RegistroSimbolo firma, List<NodoDeclaracion> parametros) {
		if (parametros == null) {
			return;
		}
		for (int i = 0; i < parametros.size(); i++) {
			firma.agregarParametro(crearRegistroParametro(parametros.get(i)));
		}
	}

	/* ORDEN CRITICO (1 de 2): NodoDeclaracionArreglo ANTES que NodoDeclaracion.
	 *
	 * Un parametro "int V[]" llega como NodoDeclaracionArreglo con tamano 0:
	 * el 0 es intencional y significa "arreglo por referencia, tamano
	 * desconocido". Se registra con Categoria.ARREGLO (para que al usarlo
	 * haya que indexarlo) pero con tamano 1, porque en el marco de
	 * activacion ocupa una sola celda: la referencia. Un arreglo local o
	 * global con tamano real (int A[10]) si reserva sus 10 celdas. */
	private RegistroSimbolo crearRegistroParametro(NodoDeclaracion d) {
		if (d instanceof NodoDeclaracionArreglo) {
			return new RegistroSimbolo(d.getNombre(), d.getLinea(),
					d.getTipoDeclarado(), Categoria.ARREGLO, 1, 0);
		}
		return new RegistroSimbolo(d.getNombre(), d.getLinea(),
				d.getTipoDeclarado(), Categoria.PARAMETRO, 1, 0);
	}

	/* ==================================================================
	 * PASADA 2 -- sentencias
	 * ================================================================== */

	/* Igual que TablaSimbolos.cargarTabla y Generador.generar: se sigue
	 * SIEMPRE la cadena getHermanoDerecha() con un while. */
	private void recorrer(NodoBase n) {
		while (n != null) {

			/* ORDEN CRITICO (2 de 2): NodoDeclaracionArreglo ANTES que
			 * NodoDeclaracion, porque el arreglo hereda de la declaracion
			 * simple y si no los arreglos entrarian como variables. */
			if (n instanceof NodoDeclaracionArreglo) {
				declararArreglo((NodoDeclaracionArreglo) n);
			} else if (n instanceof NodoDeclaracion) {
				declararVariable((NodoDeclaracion) n);
			} else if (n instanceof NodoFuncion) {
				analizarFuncion((NodoFuncion) n);
			} else if (n instanceof NodoProcedimiento) {
				analizarProcedimiento((NodoProcedimiento) n);
			} else if (n instanceof NodoAsignacion) {
				analizarAsignacion((NodoAsignacion) n);
			} else if (n instanceof NodoLeer) {
				analizarLeer((NodoLeer) n);
			} else if (n instanceof NodoEscribir) {
				analizarEscribir((NodoEscribir) n);
			} else if (n instanceof NodoIf) {
				analizarIf((NodoIf) n);
			} else if (n instanceof NodoRepeat) {
				analizarRepeat((NodoRepeat) n);
			} else if (n instanceof NodoRetorno) {
				analizarRetorno((NodoRetorno) n);
			} else if (n instanceof NodoLlamada) {
				/* Como sentencia se permite descartar el valor de retorno */
				verificarLlamada((NodoLlamada) n, true);
			} else {
				/* Una expresion suelta en posicion de sentencia no la produce
				 * la gramatica; se verifica igual por robustez. */
				verificar(n);
			}

			n = n.getHermanoDerecha();
		}
	}

	/* --- Declaraciones ------------------------------------------------ */

	private void declararVariable(NodoDeclaracion d) {
		RegistroSimbolo r = new RegistroSimbolo(d.getNombre(), d.getLinea(),
				d.getTipoDeclarado(), Categoria.VARIABLE, 1, tabla.nivelActual());
		if (!tabla.insertar(r)) {
			reportarRedeclaracion(d.getNombre(), d.getLinea());
			return;
		}
		d.setRegistro(r);
		d.setTipo(d.getTipoDeclarado());
	}

	private void declararArreglo(NodoDeclaracionArreglo d) {
		int tam = d.getTamano();

		/* Un arreglo declarado como sentencia (global o local) necesita un
		 * tamano real. El tamano 0 solo es legitimo en un parametro, y los
		 * parametros no pasan por aqui: los inserta insertarParametros. */
		if (tam <= 0) {
			Errores.semantico(d.getLinea(),
				"El tamano del arreglo '" + d.getNombre() + "' debe ser mayor que cero");
			tam = 1;   /* se sigue adelante con una celda para no desalinear la tabla */
		}

		RegistroSimbolo r = new RegistroSimbolo(d.getNombre(), d.getLinea(),
				d.getTipoDeclarado(), Categoria.ARREGLO, tam, tabla.nivelActual());
		if (!tabla.insertar(r)) {
			reportarRedeclaracion(d.getNombre(), d.getLinea());
			return;
		}
		d.setRegistro(r);
		d.setTipo(d.getTipoDeclarado());
	}

	/* --- Funciones y procedimientos ----------------------------------- */

	private void analizarFuncion(NodoFuncion f) {
		RegistroSimbolo reg = f.getRegistro();
		if (reg == null) {
			/* Rutina anidada: la pasada 1 solo miro el nivel superior, asi
			 * que esta se registra aqui, en el ambito donde aparece. */
			reg = crearFirmaFuncion(f);
			registrarRutina(f, reg);
		}

		RegistroSimbolo funcionPrevia = funcionActual;
		boolean retornoPrevio = vioRetorno;
		Set<String> noDeclaradosPrevios = noDeclaradosReportados;
		funcionActual = reg;
		vioRetorno = false;
		noDeclaradosReportados = new HashSet<String>();

		tabla.abrirAmbito(f.getNombre());
		insertarParametros(f.getParametros(), reg);
		recorrer(f.getCuerpo());

		if (f.getTipoRetorno() != Tipo.VACIO && !vioRetorno) {
			Errores.semantico(f.getLinea(),
				"La funcion '" + f.getNombre() + "' debe retornar un valor de tipo "
				+ textoTipo(f.getTipoRetorno()));
		}

		tabla.cerrarAmbito();
		funcionActual = funcionPrevia;
		vioRetorno = retornoPrevio;
		noDeclaradosReportados = noDeclaradosPrevios;
	}

	private void analizarProcedimiento(NodoProcedimiento p) {
		RegistroSimbolo reg = p.getRegistro();
		if (reg == null) {
			reg = crearFirmaProcedimiento(p);
			registrarRutina(p, reg);
		}

		RegistroSimbolo funcionPrevia = funcionActual;
		boolean retornoPrevio = vioRetorno;
		Set<String> noDeclaradosPrevios = noDeclaradosReportados;
		funcionActual = reg;
		vioRetorno = false;
		noDeclaradosReportados = new HashSet<String>();

		tabla.abrirAmbito(p.getNombre());
		insertarParametros(p.getParametros(), reg);
		recorrer(p.getCuerpo());
		/* Un procedimiento no esta obligado a tener return */

		tabla.cerrarAmbito();
		funcionActual = funcionPrevia;
		vioRetorno = retornoPrevio;
		noDeclaradosReportados = noDeclaradosPrevios;
	}

	/* Mete en el ambito recien abierto los MISMOS RegistroSimbolo que ya
	 * cuelgan de la firma, para que llamada y cuerpo compartan simbolo.
	 * Es tabla.insertar quien les fija nivel y desplazamiento. */
	private void insertarParametros(List<NodoDeclaracion> nodos, RegistroSimbolo firma) {
		List<RegistroSimbolo> formales = firma.getParametros();
		for (int i = 0; i < formales.size(); i++) {
			RegistroSimbolo p = formales.get(i);
			if (!tabla.insertar(p)) {
				reportarRedeclaracion(p.getIdentificador(), p.getNumLinea());
				continue;
			}
			if (nodos != null && i < nodos.size() && nodos.get(i) != null) {
				nodos.get(i).setRegistro(p);
				nodos.get(i).setTipo(p.getTipo());
			}
		}
	}

	/* --- Asignacion ---------------------------------------------------- */

	private void analizarAsignacion(NodoAsignacion a) {
		String nombre = a.getIdentificador();
		RegistroSimbolo r = tabla.buscar(nombre);

		if (r == null) {
			reportarNoDeclarada(nombre, a.getLinea());
			if (a.esIndexada()) {
				verificar(a.getIndice());
			}
			verificar(a.getExpresion());
			return;
		}

		a.setRegistro(r);

		if (esRutina(r)) {
			Errores.semantico(a.getLinea(), "'" + nombre + "' no es una variable");
			if (a.esIndexada()) {
				verificar(a.getIndice());
			}
			verificar(a.getExpresion());
			return;
		}

		Tipo destino;
		if (a.esIndexada()) {
			/* A[i] := e */
			if (r.getCategoria() != Categoria.ARREGLO) {
				Errores.semantico(a.getLinea(), "'" + nombre + "' no es un arreglo");
				verificar(a.getIndice());
				verificar(a.getExpresion());
				return;
			}
			verificarIndice(a.getIndice(), a.getLinea());
			if (r.getTipo() == Tipo.CADENA) {
				/* Coherente con la asignacion a una cadena simple: en esta
				 * version las cadenas son de solo lectura tambien cuando
				 * viven dentro de un arreglo. */
				Errores.semantico(a.getLinea(), "No se puede asignar a '" + nombre
					+ "': las cadenas son de solo lectura en esta version");
				verificar(a.getExpresion());
				return;
			}
			destino = r.getTipo();
		} else {
			/* x := e */
			if (r.getCategoria() == Categoria.ARREGLO) {
				Errores.semantico(a.getLinea(), "'" + nombre + "' es un arreglo y debe indexarse");
				verificar(a.getExpresion());
				return;
			}
			if (r.getTipo() == Tipo.CADENA) {
				Errores.semantico(a.getLinea(), "No se puede asignar a '" + nombre
					+ "': las cadenas son de solo lectura en esta version");
				verificar(a.getExpresion());
				return;
			}
			destino = r.getTipo();
		}

		Tipo fuente = verificar(a.getExpresion());
		a.setTipo(destino);

		if (fuente == Tipo.ERROR || destino == Tipo.ERROR) {
			return;   /* regla de oro: el error ya se reporto mas abajo */
		}
		if (fuente != destino) {
			Errores.semantico(a.getLinea(),
				"Tipos incompatibles: " + textoTipo(destino) + " vs " + textoTipo(fuente));
		}
	}

	/* --- read ----------------------------------------------------------- */

	private void analizarLeer(NodoLeer l) {
		String nombre = l.getIdentificador();
		RegistroSimbolo r = tabla.buscar(nombre);

		if (r == null) {
			reportarNoDeclarada(nombre, l.getLinea());
			if (l.esIndexada()) {
				verificar(l.getIndice());
			}
			return;
		}

		l.setRegistro(r);

		if (esRutina(r)) {
			Errores.semantico(l.getLinea(), "'" + nombre + "' no es una variable");
			if (l.esIndexada()) {
				verificar(l.getIndice());
			}
			return;
		}

		if (l.esIndexada()) {
			if (r.getCategoria() != Categoria.ARREGLO) {
				Errores.semantico(l.getLinea(), "'" + nombre + "' no es un arreglo");
				verificar(l.getIndice());
				return;
			}
			verificarIndice(l.getIndice(), l.getLinea());
		} else {
			if (r.getCategoria() == Categoria.ARREGLO) {
				Errores.semantico(l.getLinea(), "'" + nombre + "' es un arreglo y debe indexarse");
				return;
			}
		}

		/* La instruccion IN de la TM lee enteros: el destino (o el tipo base
		 * del arreglo) tiene que ser int. */
		if (r.getTipo() != Tipo.ENTERO) {
			Errores.semantico(l.getLinea(), "read solo acepta variables de tipo int, '"
				+ nombre + "' es " + textoTipo(r.getTipo()));
		}
	}

	/* --- write ---------------------------------------------------------- */

	private void analizarEscribir(NodoEscribir w) {
		NodoBase e = w.getExpresion();
		Tipo t = verificar(e);

		if (t == Tipo.ERROR) {
			return;   /* regla de oro */
		}

		if (t == Tipo.CADENA) {
			/* Una cadena solo es escribible si es un literal: en esta version
			 * una variable de tipo string no llega a tener valor. */
			if (!(e instanceof NodoString)) {
				Errores.semantico(w.getLinea(),
					"write no puede escribir un valor de tipo " + textoTipo(t));
			}
			return;
		}

		if (t != Tipo.ENTERO && t != Tipo.BOOLEANO) {
			Errores.semantico(w.getLinea(),
				"write no puede escribir un valor de tipo " + textoTipo(t));
		}
	}

	/* --- if / repeat ----------------------------------------------------- */

	private void analizarIf(NodoIf s) {
		verificarCondicion(s.getPrueba(), s.getLinea());
		recorrer(s.getParteThen());
		if (s.getParteElse() != null) {
			recorrer(s.getParteElse());
		}
	}

	private void analizarRepeat(NodoRepeat s) {
		recorrer(s.getCuerpo());
		verificarCondicion(s.getPrueba(), s.getLinea());
	}

	/* La condicion de un if o de un until debe ser booleana.
	 * Se reporta en la linea de la propia condicion cuando se conoce (en un
	 * repeat el until puede estar muchas lineas debajo del repeat). */
	private void verificarCondicion(NodoBase prueba, int lineaSentencia) {
		Tipo t = verificar(prueba);
		if (t == Tipo.ERROR || t == Tipo.BOOLEANO) {
			return;   /* regla de oro para ERROR */
		}
		int linea = (prueba != null && prueba.getLinea() >= 0)
				? prueba.getLinea() : lineaSentencia;
		Errores.semantico(linea,
			"La condicion debe ser de tipo bool, se encontro " + textoTipo(t));
	}

	/* --- return ----------------------------------------------------------- */

	private void analizarRetorno(NodoRetorno r) {
		NodoBase e = r.getExpresion();

		if (funcionActual == null) {
			Errores.semantico(r.getLinea(), "return fuera de una funcion o procedimiento");
			if (e != null) {
				verificar(e);
			}
			return;
		}

		String nombre = funcionActual.getIdentificador();

		if (funcionActual.getCategoria() == Categoria.PROCEDIMIENTO) {
			vioRetorno = true;
			if (e != null) {
				Errores.semantico(r.getLinea(),
					"return con valor dentro del procedimiento '" + nombre + "'");
				verificar(e);
			}
			return;
		}

		/* Estamos dentro de una funcion */
		vioRetorno = true;   /* se marca aunque el return este mal, para no
		                      * reportar ademas "debe retornar un valor" */

		Tipo esperado = funcionActual.getTipo();

		if (e == null) {
			Errores.semantico(r.getLinea(), "La funcion '" + nombre
				+ "' debe retornar un valor de tipo " + textoTipo(esperado));
			return;
		}

		Tipo obtenido = verificar(e);
		if (obtenido == Tipo.ERROR) {
			return;   /* regla de oro */
		}
		if (obtenido != esperado) {
			Errores.semantico(r.getLinea(),
				"Tipos incompatibles: " + textoTipo(esperado) + " vs " + textoTipo(obtenido));
		}
	}

	/* ==================================================================
	 * EXPRESIONES -- verificar() devuelve el tipo sintetizado
	 * ================================================================== */

	private Tipo verificar(NodoBase n) {
		if (n == null) {
			return Tipo.ERROR;
		}

		Tipo t;

		if (n instanceof NodoValor) {
			t = Tipo.ENTERO;
		} else if (n instanceof NodoBooleano) {
			t = Tipo.BOOLEANO;
		} else if (n instanceof NodoString) {
			t = Tipo.CADENA;
		} else if (n instanceof NodoIdentificador) {
			t = verificarIdentificador((NodoIdentificador) n);
		} else if (n instanceof NodoAccesoArreglo) {
			t = verificarAccesoArreglo((NodoAccesoArreglo) n);
		} else if (n instanceof NodoOperacion) {
			t = verificarOperacion((NodoOperacion) n);
		} else if (n instanceof NodoLlamada) {
			t = verificarLlamada((NodoLlamada) n, false);
		} else {
			t = Tipo.ERROR;   /* nodo no esperado en posicion de expresion */
		}

		n.setTipo(t);
		return t;
	}

	private Tipo verificarIdentificador(NodoIdentificador id) {
		String nombre = id.getNombre();
		RegistroSimbolo r = tabla.buscar(nombre);

		if (r == null) {
			reportarNoDeclarada(nombre, id.getLinea());
			return Tipo.ERROR;
		}

		id.setRegistro(r);

		if (r.getCategoria() == Categoria.ARREGLO) {
			Errores.semantico(id.getLinea(), "'" + nombre + "' es un arreglo y debe indexarse");
			return Tipo.ERROR;
		}
		if (r.getCategoria() == Categoria.PROCEDIMIENTO) {
			Errores.semantico(id.getLinea(),
				"'" + nombre + "' es un procedimiento y no retorna un valor");
			return Tipo.ERROR;
		}
		if (r.getCategoria() == Categoria.FUNCION) {
			Errores.semantico(id.getLinea(), "'" + nombre + "' no es una variable");
			return Tipo.ERROR;
		}

		return r.getTipo();
	}

	private Tipo verificarAccesoArreglo(NodoAccesoArreglo a) {
		String nombre = a.getNombre();
		RegistroSimbolo r = tabla.buscar(nombre);

		if (r == null) {
			reportarNoDeclarada(nombre, a.getLinea());
			verificar(a.getIndice());
			return Tipo.ERROR;
		}

		a.setRegistro(r);

		if (r.getCategoria() != Categoria.ARREGLO) {
			Errores.semantico(a.getLinea(), "'" + nombre + "' no es un arreglo");
			verificar(a.getIndice());
			return Tipo.ERROR;
		}

		verificarIndice(a.getIndice(), a.getLinea());
		return r.getTipo();   /* el resultado es el tipo base del arreglo */
	}

	/* El indice de un arreglo siempre debe ser int. Se reporta aqui y una
	 * sola vez; devolver ERROR no corta el analisis porque el tipo del
	 * acceso lo determina el arreglo, no el indice. */
	private void verificarIndice(NodoBase indice, int linea) {
		Tipo t = verificar(indice);
		if (t == Tipo.ERROR || t == Tipo.ENTERO) {
			return;   /* regla de oro para ERROR */
		}
		Errores.semantico(linea,
			"El indice de un arreglo debe ser de tipo int, se encontro " + textoTipo(t));
	}

	private Tipo verificarOperacion(NodoOperacion op) {
		tipoOp clase = op.getOperacion();

		/* "no" es el unico unario: el operando viaja en opIzquierdo y
		 * opDerecho queda null. */
		if (clase == tipoOp.no) {
			Tipo ti = verificar(op.getOpIzquierdo());
			if (ti == Tipo.ERROR) {
				return Tipo.ERROR;
			}
			if (ti != Tipo.BOOLEANO) {
				Errores.semantico(op.getLinea(),
					"Operacion logica requiere operandos bool, se encontro " + textoTipo(ti));
				return Tipo.ERROR;
			}
			return Tipo.BOOLEANO;
		}

		/* Los dos operandos se verifican SIEMPRE, aunque el primero falle:
		 * asi un error independiente del segundo tambien se reporta. */
		Tipo ti = verificar(op.getOpIzquierdo());
		Tipo td = verificar(op.getOpDerecho());

		if (ti == Tipo.ERROR || td == Tipo.ERROR) {
			return Tipo.ERROR;   /* regla de oro: no se emite un error nuevo */
		}

		if (clase == tipoOp.mas || clase == tipoOp.menos
				|| clase == tipoOp.por || clase == tipoOp.entre) {
			if (ti != Tipo.ENTERO || td != Tipo.ENTERO) {
				Tipo culpable = (ti != Tipo.ENTERO) ? ti : td;
				Errores.semantico(op.getLinea(),
					"Operacion aritmetica requiere operandos int, se encontro "
					+ textoTipo(culpable));
				return Tipo.ERROR;
			}
			return Tipo.ENTERO;
		}

		if (clase == tipoOp.menor || clase == tipoOp.igual) {
			/* Solo se comparan valores del MISMO tipo, y solo int o bool:
			 * las cadenas no son comparables en esta version. */
			boolean comparable = (ti == td)
					&& (ti == Tipo.ENTERO || ti == Tipo.BOOLEANO);
			if (!comparable) {
				Errores.semantico(op.getLinea(), "No se pueden comparar valores de tipo "
					+ textoTipo(ti) + " y " + textoTipo(td));
				return Tipo.ERROR;
			}
			return Tipo.BOOLEANO;
		}

		if (clase == tipoOp.y || clase == tipoOp.o) {
			if (ti != Tipo.BOOLEANO || td != Tipo.BOOLEANO) {
				Tipo culpable = (ti != Tipo.BOOLEANO) ? ti : td;
				Errores.semantico(op.getLinea(),
					"Operacion logica requiere operandos bool, se encontro "
					+ textoTipo(culpable));
				return Tipo.ERROR;
			}
			return Tipo.BOOLEANO;
		}

		return Tipo.ERROR;   /* operador desconocido: no deberia ocurrir */
	}

	/* --- Llamadas --------------------------------------------------------
	 * comoSentencia == true  -> se permite descartar el valor de retorno
	 * comoSentencia == false -> la llamada esta dentro de una expresion y
	 *                           tiene que ser una FUNCION
	 */
	private Tipo verificarLlamada(NodoLlamada ll, boolean comoSentencia) {
		String nombre = ll.getNombre();
		RegistroSimbolo r = tabla.buscar(nombre);

		if (r == null || !esRutina(r)) {
			Errores.semantico(ll.getLinea(),
				"'" + nombre + "' no es una funcion ni un procedimiento");
			verificarArgumentosSueltos(ll);
			ll.setTipo(Tipo.ERROR);
			return Tipo.ERROR;
		}

		ll.setRegistro(r);

		if (!comoSentencia && r.getCategoria() == Categoria.PROCEDIMIENTO) {
			Errores.semantico(ll.getLinea(),
				"'" + nombre + "' es un procedimiento y no retorna un valor");
			verificarArgumentosSueltos(ll);
			ll.setTipo(Tipo.ERROR);
			return Tipo.ERROR;
		}

		List<RegistroSimbolo> formales = r.getParametros();
		List<NodoBase> argumentos = ll.getArgumentos();

		if (formales.size() != argumentos.size()) {
			Errores.semantico(ll.getLinea(), "La funcion '" + nombre + "' espera "
				+ formales.size() + " argumentos, se recibieron " + argumentos.size());
			/* Los argumentos se verifican igual, para no esconder errores
			 * propios de ellos, pero ya no se comparan contra los formales. */
			verificarArgumentosSueltos(ll);
			ll.setTipo(Tipo.ERROR);
			return Tipo.ERROR;
		}

		for (int i = 0; i < formales.size(); i++) {
			RegistroSimbolo formal = formales.get(i);
			NodoBase arg = argumentos.get(i);

			if (formal.getCategoria() == Categoria.ARREGLO) {
				verificarArgumentoArreglo(arg, formal, nombre, i + 1, ll.getLinea());
				continue;
			}

			Tipo ta = verificar(arg);
			if (ta == Tipo.ERROR) {
				continue;   /* regla de oro */
			}
			if (ta != formal.getTipo()) {
				Errores.semantico(ll.getLinea(), "Argumento " + (i + 1) + " de '" + nombre
					+ "': se esperaba " + textoTipo(formal.getTipo())
					+ ", se encontro " + textoTipo(ta));
			}
		}

		Tipo resultado = (r.getCategoria() == Categoria.FUNCION) ? r.getTipo() : Tipo.VACIO;
		ll.setTipo(resultado);
		return resultado;
	}

	/* Un parametro declarado "int V[]" se pasa por referencia: el argumento
	 * tiene que ser el NOMBRE de un arreglo, sin indexar. Ese es el unico
	 * contexto donde un arreglo sin indice es legal, asi que aqui NO se
	 * delega en verificar() (que exigiria indexarlo). */
	private void verificarArgumentoArreglo(NodoBase arg, RegistroSimbolo formal,
			String nombreRutina, int posicion, int linea) {

		if (arg instanceof NodoIdentificador) {
			NodoIdentificador id = (NodoIdentificador) arg;
			RegistroSimbolo ra = tabla.buscar(id.getNombre());
			if (ra == null) {
				reportarNoDeclarada(id.getNombre(), id.getLinea());
				arg.setTipo(Tipo.ERROR);
				return;
			}
			if (ra.getCategoria() == Categoria.ARREGLO) {
				id.setRegistro(ra);
				id.setTipo(ra.getTipo());
				if (ra.getTipo() != formal.getTipo()) {
					Errores.semantico(linea, "Argumento " + posicion + " de '" + nombreRutina
						+ "': se esperaba " + textoTipo(formal.getTipo())
						+ "[], se encontro " + textoTipo(ra.getTipo()) + "[]");
				}
				return;
			}
		}

		/* No es un arreglo: se verifica normalmente y se reporta el desajuste */
		Tipo ta = verificar(arg);
		if (ta == Tipo.ERROR) {
			return;   /* regla de oro */
		}
		Errores.semantico(linea, "Argumento " + posicion + " de '" + nombreRutina
			+ "': se esperaba " + textoTipo(formal.getTipo())
			+ "[], se encontro " + textoTipo(ta));
	}

	/* Verifica los argumentos sin compararlos con ninguna firma. Se usa
	 * cuando la llamada ya fallo (rutina inexistente o aridad incorrecta):
	 * conviene seguir revisandolos para detectar errores propios de ellos,
	 * pero sin encadenar un segundo error por cada uno. */
	private void verificarArgumentosSueltos(NodoLlamada ll) {
		List<NodoBase> argumentos = ll.getArgumentos();
		for (int i = 0; i < argumentos.size(); i++) {
			verificar(argumentos.get(i));
		}
	}

	/* ==================================================================
	 * Utilidades
	 * ================================================================== */

	/* Emite "Variable 'X' no declarada" SOLO la primera vez que se ve ese
	 * nombre EN EL AMBITO ACTUAL. Las veces siguientes calla: el
	 * programador ya sabe que le falta declararla y una sola variable
	 * ausente usada diez veces no debe pintar diez marcadores de error en
	 * el editor del IDE. El conjunto se reinicia en cada rutina, de modo
	 * que el mismo nombre ausente en dos funciones distintas si produce
	 * un mensaje en cada una. */
	private void reportarNoDeclarada(String nombre, int linea) {
		if (noDeclaradosReportados.contains(nombre)) {
			return;
		}
		noDeclaradosReportados.add(nombre);
		Errores.semantico(linea, "Variable '" + nombre + "' no declarada");
	}

	/* Emite la redeclaracion en la linea de la declaracion POSTERIOR.
	 *
	 * Hace falta la comparacion porque la pasada 1 registra las firmas de
	 * las funciones antes de procesar las variables globales: con
	 * "int f;" en la linea 1 y "function int f(...)" en la linea 2 el
	 * choque se detecta al insertar la variable (linea 1), pero la
	 * declaracion culpable es la de la linea 2. Se reporta la mayor de
	 * las dos, que es siempre la segunda declaracion en el fuente. */
	private void reportarRedeclaracion(String nombre, int lineaNodo) {
		RegistroSimbolo previo = tabla.buscarEnAmbitoActual(nombre);
		int linea = lineaNodo;
		if (previo != null && previo.getNumLinea() > linea) {
			linea = previo.getNumLinea();
		}
		Errores.semantico(linea,
			"La variable '" + nombre + "' ya fue declarada en este ambito");
	}

	private boolean esRutina(RegistroSimbolo r) {
		return r != null && (r.getCategoria() == Categoria.FUNCION
				|| r.getCategoria() == Categoria.PROCEDIMIENTO);
	}

	/* "int" / "bool" / "string" / "void" / "error", tolerando un tipo null */
	private String textoTipo(Tipo t) {
		return (t == null) ? "error" : t.comoTexto();
	}
}
