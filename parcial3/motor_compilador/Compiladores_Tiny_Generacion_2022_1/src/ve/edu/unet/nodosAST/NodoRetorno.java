package ve.edu.unet.nodosAST;

/* Sentencia return de Tiny++. La expresion puede ser null cuando es
 * un "return;" sin valor (procedimientos). */
public class NodoRetorno extends NodoBase {
	private NodoBase expresion;

	public NodoRetorno(NodoBase expresion) {
		super();
		this.expresion = expresion;
	}

	public NodoRetorno() {
		super();
		this.expresion = null;
	}

	public NodoBase getExpresion() {
		return expresion;
	}

	public void setExpresion(NodoBase expresion) {
		this.expresion = expresion;
	}

}
