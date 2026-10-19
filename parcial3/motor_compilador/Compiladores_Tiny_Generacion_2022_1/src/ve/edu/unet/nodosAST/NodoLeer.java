package ve.edu.unet.nodosAST;

public class NodoLeer extends NodoBase {
	private String id;

	/* Indice del destino cuando se lee hacia un arreglo.
	 * read x        -> indice == null (destino es una variable simple)
	 * read A[i]     -> indice contiene la expresion del indice
	 */
	private NodoBase indice;

	public NodoLeer(String identificador) {
		super();
		this.id = identificador;
		this.indice = null;
	}

	/* Lectura hacia un destino indexado: read A[indice] */
	public NodoLeer(String identificador, NodoBase indice) {
		super();
		this.id = identificador;
		this.indice = indice;
	}

	public NodoLeer() {
		super();
		id="";
		this.indice = null;
	}

	public String getIdentificador() {
		return id;
	}

	public void setExpresion(String identificador) {
		this.id = identificador;
	}

	public NodoBase getIndice() {
		return indice;
	}

	public void setIndice(NodoBase indice) {
		this.indice = indice;
	}

	/* true si el destino de la lectura es un arreglo indexado (read A[i]) */
	public boolean esIndexada() {
		return indice != null;
	}

}
