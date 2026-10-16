package ve.edu.unet;

import ve.edu.unet.nodosAST.*;

import java.util.*;

/* TablaSimbolos -- pila de ambitos con alcance lexico para Tiny++.
 *
 * POR QUE UNA PILA Y NO UN SOLO HashMap
 * El enunciado exige alcance lexico: las variables declaradas dentro de una
 * funcion son locales a ella, y solo las del cuerpo principal son globales.
 * Un unico HashMap plano no puede expresar eso, porque dos variables
 * llamadas "x" en funciones distintas colisionarian entre si.
 *
 * El ambito global (nivel 0) se abre en el constructor y nunca se cierra.
 * El campo "tabla" queda como alias del HashMap de ese ambito global para
 * preservar el comportamiento del codigo base del Tiny original.
 */
public class TablaSimbolos {

	/* Un ambito = nombre + sus simbolos + el contador de desplazamientos locales */
	private static class Ambito {
		String nombre;
		HashMap<String, RegistroSimbolo> simbolos = new HashMap<String, RegistroSimbolo>();
		int siguienteDesplazamiento = 0;
		Ambito(String n) { nombre = n; }
	}

	private ArrayList<Ambito> pila      = new ArrayList<Ambito>();  // el ultimo es el ambito actual
	private ArrayList<Ambito> historial = new ArrayList<Ambito>();  // todos los ambitos, para -debug
	private int direccion = 0;                       // contador de direcciones GLOBALES
	private HashMap<String, RegistroSimbolo> tabla;  // alias del HashMap del ambito global

	public TablaSimbolos() {
		super();
		/* CRITICO: direccion arranca en 0, igual que el codigo base.
		 * El preludio que emite UtGen limpia dMem[0] y la primera variable
		 * global vive justo ahi; arrancar en 1 desalinearia todo el codigo
		 * que genera el modulo 2. */
		direccion = 0;
		abrirAmbito("global");
		tabla = pila.get(0).simbolos;
	}

	/* ------------------------------------------------------------------
	 * Manejo de la pila de ambitos
	 * ------------------------------------------------------------------ */

	/* Hace push de un ambito nuevo. Se agrega tambien al historial, que
	 * nunca se vacia, para poder imprimir la tabla completa al final. */
	public void abrirAmbito(String nombre) {
		Ambito a = new Ambito(nombre);
		pila.add(a);
		historial.add(a);
	}

	/* Hace pop del ambito actual.
	 *
	 * OJO: NO borra el Ambito del historial, y eso es intencional. Los
	 * RegistroSimbolo que viven en el ambito cerrado siguen referenciados
	 * desde los nodos del AST que el analizador semantico anoto, de modo
	 * que el modulo 2 puede generar codigo mucho despues de que todos los
	 * ambitos se cerraron. Si se descartaran aqui, el generador se quedaria
	 * sin la informacion de nivel/desplazamiento de las locales.
	 *
	 * Protege el ambito global: si solo queda uno, no hace nada. */
	public void cerrarAmbito() {
		if (pila.size() <= 1) {
			return;
		}
		pila.remove(pila.size() - 1);
	}

	/* 0 = global; >0 = dentro de una funcion o procedimiento */
	public int nivelActual() {
		return pila.size() - 1;
	}

	private Ambito ambitoActual() {
		return pila.get(pila.size() - 1);
	}

	public String nombreAmbitoActual() {
		return ambitoActual().nombre;
	}

	/* ------------------------------------------------------------------
	 * Insercion y busqueda
	 * ------------------------------------------------------------------ */

	/* Inserta el registro en el ambito actual y le asigna su posicion de
	 * memoria. Devuelve false si el nombre YA EXISTE en ESE MISMO ambito
	 * (redeclaracion: el que llama debe reportar el error via Errores);
	 * true si lo inserto. */
	public boolean insertar(RegistroSimbolo r) {
		if (r == null) {
			return false;
		}
		Ambito actual = ambitoActual();
		if (actual.simbolos.containsKey(r.getIdentificador())) {
			return false;
		}

		r.setNivel(nivelActual());

		int ocupa = r.getTamano();
		if (ocupa < 1) {
			ocupa = 1;
		}

		/* Las funciones y procedimientos no son datos: no consumen celdas
		 * de memoria de datos, asi que no mueven ningun contador. */
		boolean esRutina = (r.getCategoria() == Categoria.FUNCION
				|| r.getCategoria() == Categoria.PROCEDIMIENTO);

		if (!esRutina) {
			if (nivelActual() == 0) {
				r.setDireccionMemoria(direccion);
				direccion += ocupa;   /* un arreglo de 10 reserva 10 celdas */
			} else {
				r.setDesplazamiento(actual.siguienteDesplazamiento);
				actual.siguienteDesplazamiento += ocupa;
			}
		}

		actual.simbolos.put(r.getIdentificador(), r);
		return true;
	}

	/* Recorre la pila DEL TOPE HACIA LA BASE y devuelve la PRIMERA
	 * coincidencia, o null si no esta en ningun ambito visible.
	 *
	 * Esto es exactamente lo que implementa el sombreado lexico: si una
	 * funcion declara una variable local "x" y tambien existe una global
	 * "x", dentro de la funcion se encuentra primero la local y la global
	 * queda tapada. Al cerrar el ambito, la global vuelve a ser visible. */
	public RegistroSimbolo buscar(String nombre) {
		for (int i = pila.size() - 1; i >= 0; i--) {
			RegistroSimbolo r = pila.get(i).simbolos.get(nombre);
			if (r != null) {
				return r;
			}
		}
		return null;
	}

	/* Busca solo en el tope de la pila. Sirve para detectar redeclaraciones
	 * sin confundirlas con el sombreado legitimo de una global. */
	public RegistroSimbolo buscarEnAmbitoActual(String nombre) {
		return ambitoActual().simbolos.get(nombre);
	}

	/* Busca solo en la base de la pila (el ambito global). */
	public RegistroSimbolo buscarGlobal(String nombre) {
		return pila.get(0).simbolos.get(nombre);
	}

	/* Proxima direccion global libre. La usa el modulo 2 si necesita
	 * reservar temporales mas alla de las variables del programa. */
	public int getProximaDireccionGlobal() {
		return direccion;
	}

	/* ------------------------------------------------------------------
	 * Metodos del codigo base: misma firma, adaptados a la pila
	 * ------------------------------------------------------------------ */

	/* CARGADOR DEL TINY ORIGINAL -- OBSOLETO EN TINY++.
	 *
	 * Recorre el AST e inserta un simbolo cada vez que ENCUENTRA un
	 * identificador, no cuando se lo DECLARA: en el Tiny original no
	 * existian las declaraciones, toda variable nacia al usarse por
	 * primera vez.
	 *
	 * Con Tiny++ esto queda obsoleto, porque el analisis semantico es
	 * quien puebla la tabla a partir de las declaraciones reales, con su
	 * tipo, categoria, tamano y ambito. Se conserva unicamente para el
	 * modo -permisivo (programas Tiny clasicos sin declaraciones) y por
	 * compatibilidad hacia atras con el codigo del equipo.
	 */
	public void cargarTabla(NodoBase raiz){
		while (raiz != null) {
	    if (raiz instanceof NodoIdentificador){
	    	InsertarSimbolo(((NodoIdentificador)raiz).getNombre(),-1);
	    	//TODO: Anadir el numero de linea y localidad de memoria correcta
	    }

	    /* Hago el recorrido recursivo */
	    if (raiz instanceof  NodoIf){
	    	cargarTabla(((NodoIf)raiz).getPrueba());
	    	cargarTabla(((NodoIf)raiz).getParteThen());
	    	if(((NodoIf)raiz).getParteElse()!=null){
	    		cargarTabla(((NodoIf)raiz).getParteElse());
	    	}
	    }
	    else if (raiz instanceof  NodoRepeat){
	    	cargarTabla(((NodoRepeat)raiz).getCuerpo());
	    	cargarTabla(((NodoRepeat)raiz).getPrueba());
	    }
	    else if (raiz instanceof  NodoAsignacion)
	    	cargarTabla(((NodoAsignacion)raiz).getExpresion());
	    else if (raiz instanceof  NodoEscribir)
	    	cargarTabla(((NodoEscribir)raiz).getExpresion());
	    else if (raiz instanceof NodoOperacion){
	    	cargarTabla(((NodoOperacion)raiz).getOpIzquierdo());
	    	cargarTabla(((NodoOperacion)raiz).getOpDerecho());
	    }
	    raiz = raiz.getHermanoDerecha();
	  }
	}

	/* true es nuevo no existe se insertara, false ya existe NO se vuelve a insertar.
	 * Construye un simbolo VARIABLE/ENTERO y lo inserta en el ambito actual. */
	public boolean InsertarSimbolo(String identificador, int numLinea){
		RegistroSimbolo simbolo = new RegistroSimbolo(identificador, numLinea,
				Tipo.ENTERO, Categoria.VARIABLE, 1, nivelActual());
		return insertar(simbolo);
	}

	public RegistroSimbolo BuscarSimbolo(String identificador){
		return buscar(identificador);
	}

	/* Volcado de depuracion de TODOS los ambitos (modo -debug).
	 * Imprime por System.err, NUNCA por la salida estandar: el stdout lleva
	 * exclusivamente el codigo .tm generado (Contrato D). */
	public void ImprimirClaves(){
		System.err.println("*** Tabla de Simbolos ***");
		for (int i = 0; i < historial.size(); i++) {
			Ambito a = historial.get(i);
			System.err.println("-- Ambito \"" + a.nombre + "\" --");
			if (a.simbolos.isEmpty()) {
				System.err.println("   (vacio)");
				continue;
			}
			ArrayList<String> claves = new ArrayList<String>(a.simbolos.keySet());
			Collections.sort(claves);
			for (int j = 0; j < claves.size(); j++) {
				RegistroSimbolo r = a.simbolos.get(claves.get(j));
				String posicion = r.esGlobal()
						? ("direccion=" + r.getDireccionMemoria())
						: ("desplazamiento=" + r.getDesplazamiento());
				System.err.println("   " + r.getIdentificador()
						+ " | tipo=" + (r.getTipo() == null ? "null" : r.getTipo().comoTexto())
						+ " | categoria=" + r.getCategoria()
						+ " | nivel=" + r.getNivel()
						+ " | " + posicion
						+ " | tamano=" + r.getTamano()
						+ " | linea=" + r.getNumLinea());
			}
		}
	}

	public int getDireccion(String Clave){
		/* Antes esto reventaba con NullPointerException si el simbolo no
		 * existia. Ahora devuelve -1, que el modulo 2 puede detectar. */
		RegistroSimbolo simbolo = buscar(Clave);
		if (simbolo == null) {
			return -1;
		}
		return simbolo.getDireccionMemoria();
	}

	/*
	 * TODO:
	 * 1. Crear lista con las lineas de codigo donde la variable es usada.
	 * */
}
