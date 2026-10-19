package ve.edu.unet.nodosAST;

/* Literal de cadena de Tiny++: "hola mundo". */
public class NodoString extends NodoBase {
	private String valor;

	public NodoString(String valor) {
		super();
		this.valor = valor;
	}

	public NodoString() {
		super();
		this.valor = "";
	}

	public String getValor() {
		return valor;
	}

	public void setValor(String valor) {
		this.valor = valor;
	}

}
