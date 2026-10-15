package ve.edu.unet.nodosAST;

public class NodoAsignacion extends NodoBase {
	private String identificador;
	private NodoBase expresion;

	/* Indice del destino cuando la asignacion es sobre un arreglo.
	 * x := e            -> indice == null (destino es una variable simple)
	 * A[i] := e         -> indice contiene la expresion del indice (ej: i*2+1)
	 */
	private NodoBase indice;

	public NodoAsignacion(String identificador) {
		super();
		this.identificador = identificador;
		this.expresion = null;
		this.indice = null;
	}

	public NodoAsignacion(String identificador, NodoBase expresion) {
		super();
		this.identificador = identificador;
		this.expresion = expresion;
		this.indice = null;
	}

	/* Asignacion a un destino indexado: A[indice] := expresion */
	public NodoAsignacion(String identificador, NodoBase indice, NodoBase expresion) {
		super();
		this.identificador = identificador;
		this.indice = indice;
		this.expresion = expresion;
	}

	public String getIdentificador() {
		return identificador;
	}

	public void setIdentificador(String identificador) {
		this.identificador = identificador;
	}

	public NodoBase getExpresion() {
		return expresion;
	}

	public void setExpresion(NodoBase expresion) {
		this.expresion = expresion;
	}

	public NodoBase getIndice() {
		return indice;
	}

	public void setIndice(NodoBase indice) {
		this.indice = indice;
	}

	/* true si el destino de la asignacion es un arreglo indexado (A[i] := e) */
	public boolean esIndexada() {
		return indice != null;
	}

}
