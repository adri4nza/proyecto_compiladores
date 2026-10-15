package ve.edu.unet.nodosAST;

/* Declaracion de un arreglo de tamano fijo: "int A[10];"
 *
 * Hereda de NodoDeclaracion para que un parametro de tipo arreglo quepa en
 * una List<NodoDeclaracion>. CONSECUENCIA: al discriminar con instanceof,
 * SIEMPRE chequear NodoDeclaracionArreglo ANTES que NodoDeclaracion, o los
 * arreglos se clasificaran como declaraciones simples.
 */
public class NodoDeclaracionArreglo extends NodoDeclaracion {
	private int tamano;

	public NodoDeclaracionArreglo(Tipo tipo, String nombre, int tamano) {
		super(tipo, nombre);
		this.tamano = tamano;
	}

	public NodoDeclaracionArreglo() {
		super();
		this.tamano = 0;
	}

	public int getTamano() {
		return tamano;
	}

	public void setTamano(int tamano) {
		this.tamano = tamano;
	}

}
