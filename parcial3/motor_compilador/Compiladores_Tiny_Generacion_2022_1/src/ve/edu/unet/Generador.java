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
	
	public static void setTablaSimbolos(TablaSimbolos tabla){
		tablaSimbolos = tabla;
	}
	
	/* Contrato D: stdout lleva SOLO el codigo .tm. Nada de cabeceras
	 * decorativas: TM.C (readInstructions) rechaza toda linea que no sea
	 * vacia, comentario '*' o instruccion 'N:' con "Bad location". */
	public static void generarCodigoObjeto(NodoBase raiz){
		generarPreludioEstandar();
		reservarCadenas(raiz);
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
		if(n.esIndexada()){
			/* A[i] := e. El valor queda en la pila temporal mientras se
			 * calcula el indice, que puede usar AC a su antojo */
			UtGen.emitirRM("ST", UtGen.AC, desplazamientoTmp--, UtGen.MP, "asignacion: push en la pila tmp el valor a asignar");
			generarDesplazamientoIndice(n.getIndice());
			UtGen.emitirRM("LD", UtGen.AC1, ++desplazamientoTmp, UtGen.MP, "asignacion: pop del valor a asignar en AC1");
			UtGen.emitirRM("ST", UtGen.AC1, direccion, UtGen.AC, "asignacion: almaceno el valor en "+n.getIdentificador()+"[indice]");
		}else{
			/* Ahora almaceno el valor resultante */
			UtGen.emitirRM("ST", UtGen.AC, direccion, UtGen.GP, "asignacion: almaceno el valor para el id "+n.getIdentificador());
		}
		if(UtGen.debug)	UtGen.emitirComentario("<- asignacion");
	}
	
	private static void generarLeer(NodoBase nodo){
		NodoLeer n = (NodoLeer)nodo;
		int direccion;
		if(UtGen.debug)	UtGen.emitirComentario("-> leer");
		direccion = direccionDe(n, n.getIdentificador());
		if(n.esIndexada()){
			/* read A[i]: primero el indice (ocupa AC), luego IN en AC1 */
			generarDesplazamientoIndice(n.getIndice());
			UtGen.emitirRO("IN", UtGen.AC1, 0, 0, "leer: lee un valor entero ");
			UtGen.emitirRM("ST", UtGen.AC1, direccion, UtGen.AC, "leer: almaceno el valor entero leido en "+n.getIdentificador()+"[indice]");
		}else{
			UtGen.emitirRO("IN", UtGen.AC, 0, 0, "leer: lee un valor entero ");
			UtGen.emitirRM("ST", UtGen.AC, direccion, UtGen.GP, "leer: almaceno el valor entero leido en el id "+n.getIdentificador());
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
		UtGen.emitirRM("LD", UtGen.AC, direccion, UtGen.GP, "cargar valor de identificador: "+n.getNombre());
		if(UtGen.debug)	UtGen.emitirComentario("-> identificador");
	}

	/* Direccion base de un simbolo. Primero el RegistroSimbolo que anoto el
	 * semantico: resuelve el sombreado de ambitos y sigue valido aunque el
	 * ambito ya se haya cerrado. getDireccion(String) solo ve lo que siga
	 * abierto en la pila, y queda como respaldo. */
	private static int direccionDe(NodoBase nodo, String nombre){
		RegistroSimbolo r = nodo.getRegistro();
		if(r != null)
			return r.getDireccionMemoria();
		return tablaSimbolos.getDireccion(nombre);
	}

	/* Deja en AC el desplazamiento absoluto de un elemento: indice + GP.
	 * La celda es entonces base(AC), con la base del arreglo como
	 * desplazamiento constante de la instruccion LD/ST.
	 * Sin chequeo de limites: la TM solo detecta DMEM_ERR fuera de dMem. */
	private static void generarDesplazamientoIndice(NodoBase indice){
		generar(indice);
		UtGen.emitirRO("ADD", UtGen.AC, UtGen.AC, UtGen.GP, "arreglo: AC = indice + GP");
	}

	/* A[i] dentro de una expresion: AC = dMem[base + i + GP] */
	private static void generarAccesoArreglo(NodoBase nodo){
		NodoAccesoArreglo n = (NodoAccesoArreglo)nodo;
		int base = direccionDe(n, n.getNombre());
		if(UtGen.debug)	UtGen.emitirComentario("-> acceso arreglo "+n.getNombre());
		generarDesplazamientoIndice(n.getIndice());
		UtGen.emitirRM("LD", UtGen.AC, base, UtGen.AC, "arreglo: cargo "+n.getNombre()+"[indice]");
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
