package ve.edu.unet;

import ve.edu.unet.nodosAST.*;

public class Generador {
	/* Ilustracion de la disposicion de la memoria en
	 * este ambiente de ejecucion para el lenguaje Tiny
	 *
	 * |t1	|<- mp (Maxima posicion de memoria de la TM
	 * |t1	|<- desplazamientoTmp (tope actual)
	 * |free|
	 * |free|
	 * |...	|
	 * |x	|
	 * |y	|<- gp
	 * 
	 * */
	
	
	
	/* desplazamientoTmp es una variable inicializada en 0
	 * y empleada como el desplazamiento de la siguiente localidad
	 * temporal disponible desde la parte superior o tope de la memoria
	 * (la que apunta el registro MP).
	 * 
	 * - Se decrementa (desplazamientoTmp--) despues de cada almacenamiento y
	 * 
	 * - Se incrementa (desplazamientoTmp++) despues de cada eliminacion/carga en 
	 *   otra variable de un valor de la pila.
	 * 
	 * Pudiendose ver como el apuntador hacia el tope de la pila temporal
	 * y las llamadas a la funcion emitirRM corresponden a una inserccion 
	 * y extraccion de esta pila
	 */
	private static int desplazamientoTmp = 0;
	private static TablaSimbolos tablaSimbolos = null;

	/* Literales string reservados en memoria de datos: literal -> direccion
	 * de su celda de largo. Layout de cada uno: [largo][c1]..[cn], con cada
	 * caracter como su codigo ASCII. Van justo despues de las globales, asi
	 * que no chocan con ellas ni con la pila temporal que baja desde MP.
	 * Un literal repetido reutiliza la misma reserva. */
	private static java.util.LinkedHashMap<String, Integer> cadenas =
			new java.util.LinkedHashMap<String, Integer>();

	/* Activation Record (marco) de una funcion o procedimiento. Crece hacia
	 * abajo desde FP, igual que la pila temporal:
	 *
	 * |dir. de retorno |<- FP + 0
	 * |FP anterior     |<- FP - 1
	 * |MP anterior     |<- FP - 2
	 * |param/local d=0 |<- FP - 3
	 * |param/local d=1 |<- FP - 4
	 * |...             |
	 * |                |<- MP del llamado (sus temporales empiezan aqui)
	 *
	 * Cada parametro o local ocupa su 'desplazamiento' (lo asigna
	 * TablaSimbolos desde 0). Un arreglo local de n celdas con
	 * desplazamiento d ocupa FP-3-d .. FP-3-d-(n-1), con el elemento 0 en
	 * la direccion mas baja para indexarlo igual que a los globales.
	 * El valor de retorno viaja en AC. */
	private static final int CABECERA_MARCO = 3;
	private static java.util.HashMap<String, Integer> tamanoMarco =
			new java.util.HashMap<String, Integer>();
	private static java.util.HashMap<String, Integer> entradaRutina =
			new java.util.HashMap<String, Integer>();
	/* Llamadas emitidas antes que la rutina (llamadas hacia adelante):
	 * hueco reservado a la espera de conocer la direccion de entrada */
	private static java.util.HashMap<String, java.util.List<Integer>> saltosPendientes =
			new java.util.HashMap<String, java.util.List<Integer>>();
	/* Parametros "int V[]": su celda del marco guarda la DIRECCION absoluta
	 * del elemento 0 del arreglo del llamador (paso por referencia). Se
	 * reconocen por identidad del RegistroSimbolo, porque un arreglo local
	 * "int A[1]" tiene la misma categoria y tamano. */
	private static java.util.Set<RegistroSimbolo> referencias =
			java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<RegistroSimbolo, Boolean>());
	
	public static void setTablaSimbolos(TablaSimbolos tabla){
		tablaSimbolos = tabla;
	}
	
	/* Contrato D: stdout lleva SOLO el codigo .tm. Nada de cabeceras
	 * decorativas: TM.C (readInstructions) rechaza toda linea que no sea
	 * vacia, comentario '*' o instruccion 'N:' con "Bad location". */
	public static void generarCodigoObjeto(NodoBase raiz){
		generarPreludioEstandar();
		reservarCadenas(raiz);
		tamanoMarco.clear();
		entradaRutina.clear();
		saltosPendientes.clear();
		referencias.clear();
		calcularMarcos(raiz);
		generar(raiz);
		/*Genero el codigo de finalizacion de ejecucion del codigo*/   
		UtGen.emitirComentario("Fin de la ejecucion.");
		UtGen.emitirRO("HALT", 0, 0, 0, "");
	}
	
	//Funcion principal de generacion de codigo
	//prerequisito: Fijar la tabla de simbolos antes de generar el codigo objeto 
	private static void generar(NodoBase nodo){
	if(tablaSimbolos!=null){
		if (nodo instanceof  NodoIf){
			generarIf(nodo);
		}else if (nodo instanceof  NodoRepeat){
			generarRepeat(nodo);
		}else if (nodo instanceof  NodoAsignacion){
			generarAsignacion(nodo);
		}else if (nodo instanceof  NodoLeer){
			generarLeer(nodo);
		}else if (nodo instanceof  NodoEscribir){
			generarEscribir(nodo);
		}else if (nodo instanceof NodoValor){
			generarValor(nodo);
		}else if (nodo instanceof NodoIdentificador){
			generarIdentificador(nodo);
		}else if (nodo instanceof NodoOperacion){
			generarOperacion(nodo);
		}else if (nodo instanceof NodoBooleano){
			generarBooleano(nodo);
		}else if (nodo instanceof NodoAccesoArreglo){
			generarAccesoArreglo(nodo);
		}else if (nodo instanceof NodoFuncion){
			NodoFuncion f = (NodoFuncion)nodo;
			generarRutina(f.getNombre(), f.getCuerpo());
		}else if (nodo instanceof NodoProcedimiento){
			NodoProcedimiento p = (NodoProcedimiento)nodo;
			generarRutina(p.getNombre(), p.getCuerpo());
		}else if (nodo instanceof NodoLlamada){
			generarLlamada((NodoLlamada)nodo);
		}else if (nodo instanceof NodoRetorno){
			generarRetorno((NodoRetorno)nodo);
		}else if (nodo instanceof NodoDeclaracionArreglo){
			/* Debe ir ANTES que NodoDeclaracion: hereda de ella */
			generarDeclaracion(nodo);
		}else if (nodo instanceof NodoDeclaracion){
			generarDeclaracion(nodo);
		}else{
			/* Como comentario y no como println: una linea sin '*' en stdout
			 * romperia el .tm (Contrato D) */
			UtGen.emitirComentario("BUG: Tipo de nodo a generar desconocido");
		}
		/*Si el hijo de extrema izquierda tiene hermano a la derecha lo genero tambien*/
		if(nodo.TieneHermano())
			generar(nodo.getHermanoDerecha());
	}else
		System.out.println("¡¡¡ERROR: por favor fije la tabla de simbolos a usar antes de generar codigo objeto!!!");
}

	private static void generarIf(NodoBase nodo){
    	NodoIf n = (NodoIf)nodo;
		int localidadSaltoElse,localidadSaltoEnd,localidadActual;
		if(UtGen.debug)	UtGen.emitirComentario("-> if");
		/*Genero el codigo para la parte de prueba del IF*/
		generar(n.getPrueba());
		localidadSaltoElse = UtGen.emitirSalto(1);
		UtGen.emitirComentario("If: el salto hacia el else debe estar aqui");
		/*Genero la parte THEN*/
		generar(n.getParteThen());
		/* El hueco del salto al final solo se reserva si hay ELSE: sin ELSE
		 * nadie lo rellena y la TM, que inicializa iMem con HALT, detendria
		 * el programa al terminar el THEN */
		localidadSaltoEnd = -1;
		if(n.getParteElse()!=null){
			localidadSaltoEnd = UtGen.emitirSalto(1);
			UtGen.emitirComentario("If: el salto hacia el final debe estar aqui");
		}
		localidadActual = UtGen.emitirSalto(0);
		UtGen.cargarRespaldo(localidadSaltoElse);
		UtGen.emitirRM_Abs("JEQ", UtGen.AC, localidadActual, "if: jmp hacia else");
		UtGen.restaurarRespaldo();
		/*Genero la parte ELSE*/
		if(n.getParteElse()!=null){
			generar(n.getParteElse());
			localidadActual = UtGen.emitirSalto(0);
			UtGen.cargarRespaldo(localidadSaltoEnd);
			UtGen.emitirRM_Abs("LDA", UtGen.PC, localidadActual, "if: jmp hacia el final");
			UtGen.restaurarRespaldo();
    	}
		
		if(UtGen.debug)	UtGen.emitirComentario("<- if");
	}
	
	private static void generarRepeat(NodoBase nodo){
    	NodoRepeat n = (NodoRepeat)nodo;
		int localidadSaltoInicio;
		if(UtGen.debug)	UtGen.emitirComentario("-> repeat");
			localidadSaltoInicio = UtGen.emitirSalto(0);
			UtGen.emitirComentario("repeat: el salto hacia el final (luego del cuerpo) del repeat debe estar aqui");
			/* Genero el cuerpo del repeat */
			generar(n.getCuerpo());
			/* Genero el codigo de la prueba del repeat */
			generar(n.getPrueba());
			UtGen.emitirRM_Abs("JEQ", UtGen.AC, localidadSaltoInicio, "repeat: jmp hacia el inicio del cuerpo");
		if(UtGen.debug)	UtGen.emitirComentario("<- repeat");
	}		
	
	private static void generarAsignacion(NodoBase nodo){
		NodoAsignacion n = (NodoAsignacion)nodo;
		int direccion;
		if(UtGen.debug)	UtGen.emitirComentario("-> asignacion");		
		/* Genero el codigo para la expresion a la derecha de la asignacion */
		generar(n.getExpresion());
		direccion = direccionDe(n, n.getIdentificador());
		int base = registroBase(n);
		if(n.esIndexada()){
			/* A[i] := e. El valor queda en la pila temporal mientras se
			 * calcula el indice, que puede usar AC a su antojo */
			UtGen.emitirRM("ST", UtGen.AC, desplazamientoTmp--, UtGen.MP, "asignacion: push en la pila tmp el valor a asignar");
			int desp = generarDireccionElemento(n.getIndice(), n, n.getIdentificador());
			UtGen.emitirRM("LD", UtGen.AC1, ++desplazamientoTmp, UtGen.MP, "asignacion: pop del valor a asignar en AC1");
			UtGen.emitirRM("ST", UtGen.AC1, desp, UtGen.AC, "asignacion: almaceno el valor en "+n.getIdentificador()+"[indice]");
		}else{
			/* Ahora almaceno el valor resultante */
			UtGen.emitirRM("ST", UtGen.AC, direccion, base, "asignacion: almaceno el valor para el id "+n.getIdentificador());
		}
		if(UtGen.debug)	UtGen.emitirComentario("<- asignacion");
	}
	
	private static void generarLeer(NodoBase nodo){
		NodoLeer n = (NodoLeer)nodo;
		int direccion;
		if(UtGen.debug)	UtGen.emitirComentario("-> leer");
		direccion = direccionDe(n, n.getIdentificador());
		int base = registroBase(n);
		if(n.esIndexada()){
			/* read A[i]: primero el indice (ocupa AC), luego IN en AC1 */
			int desp = generarDireccionElemento(n.getIndice(), n, n.getIdentificador());
			UtGen.emitirRO("IN", UtGen.AC1, 0, 0, "leer: lee un valor entero ");
			UtGen.emitirRM("ST", UtGen.AC1, desp, UtGen.AC, "leer: almaceno el valor entero leido en "+n.getIdentificador()+"[indice]");
		}else{
			UtGen.emitirRO("IN", UtGen.AC, 0, 0, "leer: lee un valor entero ");
			UtGen.emitirRM("ST", UtGen.AC, direccion, base, "leer: almaceno el valor entero leido en el id "+n.getIdentificador());
		}
		if(UtGen.debug)	UtGen.emitirComentario("<- leer");
	}
	
	private static void generarEscribir(NodoBase nodo){
		NodoEscribir n = (NodoEscribir)nodo;
		if(UtGen.debug)	UtGen.emitirComentario("-> escribir");
		if(n.getExpresion() instanceof NodoString){
			generarEscribirCadena((NodoString)n.getExpresion());
			if(UtGen.debug)	UtGen.emitirComentario("<- escribir");
			return;
		}
		/* Genero el codigo de la expresion que va a ser escrita en pantalla */
		generar(n.getExpresion());
		/* Ahora genero la salida */
		UtGen.emitirRO("OUT", UtGen.AC, 0, 0, "escribir: genero la salida de la expresion");
		if(UtGen.debug)	UtGen.emitirComentario("<- escribir");
	}
	
	private static void generarValor(NodoBase nodo){
    	NodoValor n = (NodoValor)nodo;
    	if(UtGen.debug)	UtGen.emitirComentario("-> constante");
    	UtGen.emitirRM("LDC", UtGen.AC, n.getValor(), 0, "cargar constante: "+n.getValor());
    	if(UtGen.debug)	UtGen.emitirComentario("<- constante");
	}
	
	private static void generarIdentificador(NodoBase nodo){
		NodoIdentificador n = (NodoIdentificador)nodo;
		int direccion;
		if(UtGen.debug)	UtGen.emitirComentario("-> identificador");
		direccion = direccionDe(n, n.getNombre());
		UtGen.emitirRM("LD", UtGen.AC, direccion, registroBase(n), "cargar valor de identificador: "+n.getNombre());
		if(UtGen.debug)	UtGen.emitirComentario("-> identificador");
	}

	/* Direccion base de un simbolo. Primero el RegistroSimbolo que anoto el
	 * semantico: resuelve el sombreado de ambitos y sigue valido aunque el
	 * ambito ya se haya cerrado. getDireccion(String) solo ve lo que siga
	 * abierto en la pila, y queda como respaldo. */
	private static int direccionDe(NodoBase nodo, String nombre){
		RegistroSimbolo r = nodo.getRegistro();
		if(r != null){
			if(r.esGlobal())
				return r.getDireccionMemoria();
			/* Local o parametro: desplazamiento (negativo) respecto de FP */
			int tamano = Math.max(r.getTamano(), 1);
			return -(CABECERA_MARCO + r.getDesplazamiento() + tamano - 1);
		}
		return tablaSimbolos.getDireccion(nombre);
	}

	/* Registro base de la direccion que devuelve direccionDe: GP para los
	 * globales, FP para locales y parametros (viven en el marco). */
	private static int registroBase(NodoBase nodo){
		RegistroSimbolo r = nodo.getRegistro();
		if(r != null && !r.esGlobal())
			return UtGen.FP;
		return UtGen.GP;
	}

	/* Prepara el acceso al elemento arreglo[indice]: deja un valor en AC y
	 * devuelve el desplazamiento d tal que la celda es d(AC).
	 *  - arreglo propio (global o local): AC = indice + GP/FP, d = direccion
	 *    del arreglo
	 *  - parametro "int V[]": AC = indice + direccion guardada en el marco,
	 *    d = 0
	 * Pisa AC1. Sin chequeo de limites: la TM solo detecta DMEM_ERR fuera
	 * de dMem. */
	private static int generarDireccionElemento(NodoBase indice, NodoBase arreglo, String nombre){
		generar(indice);
		int direccion = direccionDe(arreglo, nombre);
		if(esReferencia(arreglo)){
			UtGen.emitirRM("LD", UtGen.AC1, direccion, UtGen.FP, "arreglo por referencia: AC1 = direccion de "+nombre+"[0]");
			UtGen.emitirRO("ADD", UtGen.AC, UtGen.AC, UtGen.AC1, "arreglo: AC = indice + direccion base");
			return 0;
		}
		UtGen.emitirRO("ADD", UtGen.AC, UtGen.AC, registroBase(arreglo), "arreglo: AC = indice + registro base");
		return direccion;
	}

	private static boolean esReferencia(NodoBase nodo){
		RegistroSimbolo r = nodo.getRegistro();
		return r != null && referencias.contains(r);
	}

	/* Argumento para un parametro "int V[]": AC = direccion absoluta del
	 * elemento 0. Si el argumento ya es una referencia, se reenvia tal cual. */
	private static void generarReferencia(NodoBase argumento){
		NodoIdentificador id = (NodoIdentificador)argumento;
		int direccion = direccionDe(id, id.getNombre());
		if(esReferencia(id))
			UtGen.emitirRM("LD", UtGen.AC, direccion, UtGen.FP, "llamada: reenvio la referencia "+id.getNombre());
		else
			UtGen.emitirRM("LDA", UtGen.AC, direccion, registroBase(id), "llamada: AC = direccion de "+id.getNombre()+"[0]");
	}

	/* A[i] dentro de una expresion: AC = dMem[dir + i + base] */
	private static void generarAccesoArreglo(NodoBase nodo){
		NodoAccesoArreglo n = (NodoAccesoArreglo)nodo;
		if(UtGen.debug)	UtGen.emitirComentario("-> acceso arreglo "+n.getNombre());
		int desp = generarDireccionElemento(n.getIndice(), n, n.getNombre());
		UtGen.emitirRM("LD", UtGen.AC, desp, UtGen.AC, "arreglo: cargo "+n.getNombre()+"[indice]");
		if(UtGen.debug)	UtGen.emitirComentario("<- acceso arreglo "+n.getNombre());
	}

	/* Recorre las sentencias buscando write "literal" (el semantico solo
	 * admite cadenas ahi) y reserva cada literal distinto en dMem, emitiendo
	 * las instrucciones que lo inicializan al arrancar el programa. */
	private static void reservarCadenas(NodoBase raiz){
		cadenas.clear();
		recolectarCadenas(raiz);
		int direccion = tablaSimbolos.getProximaDireccionGlobal();
		for(String literal : cadenas.keySet()){
			cadenas.put(literal, direccion);
			UtGen.emitirComentario("cadena \""+literal+"\" en dMem["+direccion+"]");
			UtGen.emitirRM("LDC", UtGen.AC, literal.length(), 0, "cadena: largo "+literal.length());
			UtGen.emitirRM("ST", UtGen.AC, direccion, UtGen.GP, "cadena: guardo el largo");
			for(int i = 0; i < literal.length(); i++){
				UtGen.emitirRM("LDC", UtGen.AC, literal.charAt(i), 0, "cadena: caracter "+literal.charAt(i));
				UtGen.emitirRM("ST", UtGen.AC, direccion + 1 + i, UtGen.GP, "cadena: guardo el caracter");
			}
			direccion += literal.length() + 1;
		}
	}

	private static void recolectarCadenas(NodoBase nodo){
		for(NodoBase n = nodo; n != null; n = n.getHermanoDerecha()){
			if(n instanceof NodoEscribir){
				NodoBase e = ((NodoEscribir)n).getExpresion();
				if(e instanceof NodoString)
					cadenas.put(((NodoString)e).getValor(), -1);
			}else if(n instanceof NodoIf){
				recolectarCadenas(((NodoIf)n).getParteThen());
				recolectarCadenas(((NodoIf)n).getParteElse());
			}else if(n instanceof NodoRepeat){
				recolectarCadenas(((NodoRepeat)n).getCuerpo());
			}else if(n instanceof NodoFuncion){
				recolectarCadenas(((NodoFuncion)n).getCuerpo());
			}else if(n instanceof NodoProcedimiento){
				recolectarCadenas(((NodoProcedimiento)n).getCuerpo());
			}
		}
	}

	/* write "literal": un OUTC por caracter, leyendo la cadena de su reserva.
	 * OUTC es una instruccion NUEVA de la TM (formato RO, como OUT): imprime
	 * reg[r] como el caracter de ese codigo ASCII, sin salto de linea.
	 * La deben implementar M3 (simulador) y M4 (consola del IDE); ver
	 * parcial3/modulo_2/INTEGRACION_M2.md. */
	private static void generarEscribirCadena(NodoString n){
		int base = cadenas.get(n.getValor());
		String literal = n.getValor();
		for(int i = 0; i < literal.length(); i++){
			UtGen.emitirRM("LD", UtGen.AC, base + 1 + i, UtGen.GP, "escribir: cargo el caracter "+literal.charAt(i));
			UtGen.emitirRO("OUTC", UtGen.AC, 0, 0, "escribir: salida del caracter");
		}
	}

	/* Tamano del marco de cada rutina: cabecera + las celdas que ocupan sus
	 * parametros y locales. Se calcula antes de generar porque una llamada
	 * puede aparecer antes que la rutina (llamada hacia adelante). */
	private static void calcularMarcos(NodoBase nodo){
		for(NodoBase n = nodo; n != null; n = n.getHermanoDerecha()){
			if(n instanceof NodoFuncion){
				NodoFuncion f = (NodoFuncion)n;
				registrarMarco(f.getNombre(), f.getParametros(), f.getCuerpo());
				calcularMarcos(f.getCuerpo());
			}else if(n instanceof NodoProcedimiento){
				NodoProcedimiento p = (NodoProcedimiento)n;
				registrarMarco(p.getNombre(), p.getParametros(), p.getCuerpo());
				calcularMarcos(p.getCuerpo());
			}else if(n instanceof NodoIf){
				calcularMarcos(((NodoIf)n).getParteThen());
				calcularMarcos(((NodoIf)n).getParteElse());
			}else if(n instanceof NodoRepeat){
				calcularMarcos(((NodoRepeat)n).getCuerpo());
			}
		}
	}

	private static void registrarMarco(String nombre, java.util.List<NodoDeclaracion> parametros, NodoBase cuerpo){
		int celdas = 0;
		if(parametros != null)
			for(NodoDeclaracion d : parametros){
				celdas = Math.max(celdas, finDeCeldas(d));
				if(d instanceof NodoDeclaracionArreglo && d.getRegistro() != null)
					referencias.add(d.getRegistro());
			}
		celdas = Math.max(celdas, celdasLocales(cuerpo));
		tamanoMarco.put(nombre, CABECERA_MARCO + celdas);
	}

	/* Mayor (desplazamiento + tamano) entre las declaraciones del cuerpo,
	 * sin entrar en rutinas anidadas: esas tienen su propio marco. */
	private static int celdasLocales(NodoBase nodo){
		int celdas = 0;
		for(NodoBase n = nodo; n != null; n = n.getHermanoDerecha()){
			if(n instanceof NodoDeclaracion){
				celdas = Math.max(celdas, finDeCeldas((NodoDeclaracion)n));
			}else if(n instanceof NodoIf){
				celdas = Math.max(celdas, celdasLocales(((NodoIf)n).getParteThen()));
				celdas = Math.max(celdas, celdasLocales(((NodoIf)n).getParteElse()));
			}else if(n instanceof NodoRepeat){
				celdas = Math.max(celdas, celdasLocales(((NodoRepeat)n).getCuerpo()));
			}
		}
		return celdas;
	}

	private static int finDeCeldas(NodoDeclaracion d){
		RegistroSimbolo r = d.getRegistro();
		if(r == null || r.esGlobal())
			return 0;
		return r.getDesplazamiento() + Math.max(r.getTamano(), 1);
	}

	/* Cuerpo de una funcion o procedimiento. El flujo principal salta por
	 * encima: la rutina solo se ejecuta cuando alguien la llama. */
	private static void generarRutina(String nombre, NodoBase cuerpo){
		if(UtGen.debug)	UtGen.emitirComentario("-> rutina "+nombre+" (marco de "+tamanoMarco.get(nombre)+" celdas)");
		int saltoSobreRutina = UtGen.emitirSalto(1);
		int entrada = UtGen.emitirSalto(0);
		entradaRutina.put(nombre, entrada);
		/* Completo las llamadas hacia adelante que esperaban esta entrada */
		java.util.List<Integer> pendientes = saltosPendientes.remove(nombre);
		if(pendientes != null){
			for(int salto : pendientes){
				UtGen.cargarRespaldo(salto);
				UtGen.emitirRM_Abs("LDA", UtGen.PC, entrada, "llamada: salto a "+nombre);
			}
			UtGen.restaurarRespaldo();
		}
		/* Los temporales del llamado empiezan en su propio MP */
		int tmpPrevio = desplazamientoTmp;
		desplazamientoTmp = 0;
		if(cuerpo != null)
			generar(cuerpo);
		/* Fin del cuerpo sin return (procedimientos): retorno implicito */
		generarEpilogo(nombre);
		desplazamientoTmp = tmpPrevio;
		int fin = UtGen.emitirSalto(0);
		UtGen.cargarRespaldo(saltoSobreRutina);
		UtGen.emitirRM_Abs("LDA", UtGen.PC, fin, "salto sobre el cuerpo de "+nombre);
		UtGen.restaurarRespaldo();
		if(UtGen.debug)	UtGen.emitirComentario("<- rutina "+nombre);
	}

	/* Deshace el marco y vuelve al llamador. AC no se toca: lleva el valor
	 * de retorno de las funciones. */
	private static void generarEpilogo(String nombre){
		UtGen.emitirRM("LD", UtGen.AC1, 0, UtGen.FP, "retorno: AC1 = direccion de retorno de "+nombre);
		UtGen.emitirRM("LD", UtGen.MP, -2, UtGen.FP, "retorno: restauro MP del llamador");
		UtGen.emitirRM("LD", UtGen.FP, -1, UtGen.FP, "retorno: restauro FP del llamador");
		UtGen.emitirRM("LDA", UtGen.PC, 0, UtGen.AC1, "retorno: salto de vuelta al llamador");
	}

	private static void generarRetorno(NodoRetorno n){
		if(n.getExpresion() != null)
			generar(n.getExpresion());   /* valor de retorno en AC */
		generarEpilogo("return");
	}

	/* Llamada a funcion o procedimiento. El marco se arma debajo de los
	 * temporales vivos del llamador (desplazamientoTmp), asi que una
	 * llamada dentro de una expresion no pisa operandos ya apilados:
	 *   1. reservo el marco moviendo desplazamientoTmp; los temporales que
	 *      usen los argumentos quedan por debajo del marco
	 *   2. evaluo cada argumento y lo guardo en la celda de su parametro
	 *   3. guardo FP y MP del llamador, fijo FP y MP del llamado
	 *   4. guardo la direccion de retorno y salto a la rutina */
	private static void generarLlamada(NodoLlamada n){
		String nombre = n.getNombre();
		RegistroSimbolo firma = n.getRegistro();
		int tamano = tamanoMarco.get(nombre);
		int tope = desplazamientoTmp;          /* FP del llamado = MP + tope */
		if(UtGen.debug)	UtGen.emitirComentario("-> llamada "+nombre);
		desplazamientoTmp -= tamano;

		java.util.List<NodoBase> argumentos = n.getArgumentos();
		for(int i = 0; i < argumentos.size(); i++){
			RegistroSimbolo formal = firma.getParametros().get(i);
			if(referencias.contains(formal))
				generarReferencia(argumentos.get(i));
			else
				generar(argumentos.get(i));
			UtGen.emitirRM("ST", UtGen.AC, tope - CABECERA_MARCO - formal.getDesplazamiento(), UtGen.MP,
					"llamada: argumento "+(i+1)+" -> "+formal.getIdentificador());
		}

		UtGen.emitirRM("ST", UtGen.FP, tope - 1, UtGen.MP, "llamada: guardo FP del llamador");
		UtGen.emitirRM("ST", UtGen.MP, tope - 2, UtGen.MP, "llamada: guardo MP del llamador");
		UtGen.emitirRM("LDA", UtGen.FP, tope, UtGen.MP, "llamada: FP apunta al nuevo marco");
		UtGen.emitirRM("LDA", UtGen.MP, tope - tamano, UtGen.MP, "llamada: MP debajo del marco");
		UtGen.emitirRM("LDA", UtGen.AC1, 2, UtGen.PC, "llamada: AC1 = direccion de retorno");
		UtGen.emitirRM("ST", UtGen.AC1, 0, UtGen.FP, "llamada: guardo la direccion de retorno");
		Integer entrada = entradaRutina.get(nombre);
		if(entrada != null){
			UtGen.emitirRM_Abs("LDA", UtGen.PC, entrada, "llamada: salto a "+nombre);
		}else{
			int salto = UtGen.emitirSalto(1);
			UtGen.emitirComentario("llamada: salto a "+nombre+" (se completa al generar la rutina)");
			if(!saltosPendientes.containsKey(nombre))
				saltosPendientes.put(nombre, new java.util.ArrayList<Integer>());
			saltosPendientes.get(nombre).add(salto);
		}
		desplazamientoTmp = tope;
		if(UtGen.debug)	UtGen.emitirComentario("<- llamada "+nombre);
	}

	/* Las declaraciones no emiten instrucciones: la direccion ya la reservo
	 * TablaSimbolos al analizar (globales desde dMem[0], igual que el Tiny
	 * base) y la TM arranca con toda dMem en 0. */
	private static void generarDeclaracion(NodoBase nodo){
		NodoDeclaracion n = (NodoDeclaracion)nodo;
		if(UtGen.debug)	UtGen.emitirComentario("declaracion: " + n.getNombre());
	}

	/* Un bool en la TM es un entero: true = 1, false = 0. Todas las
	 * operaciones logicas y relacionales mantienen ese invariante. */
	private static void generarBooleano(NodoBase nodo){
		NodoBooleano n = (NodoBooleano)nodo;
		int valor = n.getValor() ? 1 : 0;
		if(UtGen.debug)	UtGen.emitirComentario("-> booleano");
		UtGen.emitirRM("LDC", UtGen.AC, valor, 0, "cargar booleano: "+n.getValor());
		if(UtGen.debug)	UtGen.emitirComentario("<- booleano");
	}

	/* 'not' es unario: el parser deja opDerecho == null a proposito, asi que
	 * no se usa la pila temporal. Con el operando en {0,1}: AC = 1 - AC. */
	private static void generarNegacion(NodoOperacion n){
		if(UtGen.debug)	UtGen.emitirComentario("-> Operacion: no");
		generar(n.getOpIzquierdo());
		UtGen.emitirRM("LDC", UtGen.AC1, 1, 0, "op: not, cargo 1 en AC1");
		UtGen.emitirRO("SUB", UtGen.AC, UtGen.AC1, UtGen.AC, "op: not (AC = 1 - AC)");
		if(UtGen.debug)	UtGen.emitirComentario("<- Operacion: no");
	}

	private static void generarOperacion(NodoBase nodo){
		NodoOperacion n = (NodoOperacion) nodo;
		if(n.getOperacion() == tipoOp.no){
			generarNegacion(n);
			return;
		}
		if(UtGen.debug)	UtGen.emitirComentario("-> Operacion: " + n.getOperacion());
		/* Genero la expresion izquierda de la operacion */
		generar(n.getOpIzquierdo());
		/* Almaceno en la pseudo pila de valor temporales el valor de la operacion izquierda */
		UtGen.emitirRM("ST", UtGen.AC, desplazamientoTmp--, UtGen.MP, "op: push en la pila tmp el resultado expresion izquierda");
		/* Genero la expresion derecha de la operacion */
		generar(n.getOpDerecho());
		/* Ahora cargo/saco de la pila el valor izquierdo */
		UtGen.emitirRM("LD", UtGen.AC1, ++desplazamientoTmp, UtGen.MP, "op: pop o cargo de la pila el valor izquierdo en AC1");
		switch(n.getOperacion()){
			case	mas:	UtGen.emitirRO("ADD", UtGen.AC, UtGen.AC1, UtGen.AC, "op: +");		
							break;
			case	menos:	UtGen.emitirRO("SUB", UtGen.AC, UtGen.AC1, UtGen.AC, "op: -");
							break;
			case	por:	UtGen.emitirRO("MUL", UtGen.AC, UtGen.AC1, UtGen.AC, "op: *");
							break;
			case	entre:	UtGen.emitirRO("DIV", UtGen.AC, UtGen.AC1, UtGen.AC, "op: /");
							break;		
			case	menor:	UtGen.emitirRO("SUB", UtGen.AC, UtGen.AC1, UtGen.AC, "op: <");
							UtGen.emitirRM("JLT", UtGen.AC, 2, UtGen.PC, "voy dos instrucciones mas alla if verdadero (AC<0)");
							UtGen.emitirRM("LDC", UtGen.AC, 0, UtGen.AC, "caso de falso (AC=0)");
							UtGen.emitirRM("LDA", UtGen.PC, 1, UtGen.PC, "Salto incodicional a direccion: PC+1 (es falso evito colocarlo verdadero)");
							UtGen.emitirRM("LDC", UtGen.AC, 1, UtGen.AC, "caso de verdadero (AC=1)");
							break;
			case	igual:	UtGen.emitirRO("SUB", UtGen.AC, UtGen.AC1, UtGen.AC, "op: ==");
							UtGen.emitirRM("JEQ", UtGen.AC, 2, UtGen.PC, "voy dos instrucciones mas alla if verdadero (AC==0)");
							UtGen.emitirRM("LDC", UtGen.AC, 0, UtGen.AC, "caso de falso (AC=0)");
							UtGen.emitirRM("LDA", UtGen.PC, 1, UtGen.PC, "Salto incodicional a direccion: PC+1 (es falso evito colocarlo verdadero)");
							UtGen.emitirRM("LDC", UtGen.AC, 1, UtGen.AC, "caso de verdadero (AC=1)");
							break;	
			/* and/or sin cortocircuito: ambos operandos ya estan evaluados.
			 * Con operandos en {0,1}, and = producto. */
			case	y:		UtGen.emitirRO("MUL", UtGen.AC, UtGen.AC1, UtGen.AC, "op: and");
							break;
			/* or = suma normalizada a {0,1}: si AC == 0 salta el LDC */
			case	o:		UtGen.emitirRO("ADD", UtGen.AC, UtGen.AC1, UtGen.AC, "op: or");
							UtGen.emitirRM("JEQ", UtGen.AC, 1, UtGen.PC, "or: si la suma es 0 el resultado es falso");
							UtGen.emitirRM("LDC", UtGen.AC, 1, 0, "or: normalizo verdadero a 1");
							break;
			default:
							UtGen.emitirComentario("BUG: tipo de operacion desconocida");
		}
		if(UtGen.debug)	UtGen.emitirComentario("<- Operacion: " + n.getOperacion());
	}
	
	//TODO: enviar preludio a archivo de salida, obtener antes su nombre
	private static void generarPreludioEstandar(){
		UtGen.emitirComentario("Compilacion TINY para el codigo objeto TM");
		UtGen.emitirComentario("Archivo: "+ "NOMBRE_ARREGLAR");
		/*Genero inicializaciones del preludio estandar*/
		/*Todos los registros en tiny comienzan en cero*/
		UtGen.emitirComentario("Preludio estandar:");
		UtGen.emitirRM("LD", UtGen.MP, 0, UtGen.AC, "cargar la maxima direccion desde la localidad 0");
		UtGen.emitirRM("ST", UtGen.AC, 0, UtGen.AC, "limpio el registro de la localidad 0");
	}

}
