package ve.edu.unet.nodosAST;

/* Literal booleano de Tiny++: true o false. */
public class NodoBooleano extends NodoBase {
	private boolean valor;

	public NodoBooleano(boolean valor) {
		super();
		this.valor = valor;
	}

	public NodoBooleano() {
		super();
		this.valor = false;
	}

	public boolean getValor() {
		return valor;
	}

	public void setValor(boolean valor) {
		this.valor = valor;
	}

}
