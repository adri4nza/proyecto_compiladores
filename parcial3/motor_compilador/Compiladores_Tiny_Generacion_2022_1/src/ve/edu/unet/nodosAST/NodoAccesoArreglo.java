package ve.edu.unet.nodosAST;

/* Acceso indexado a un arreglo: A[i*2+1]. El indice es una expresion completa. */
public class NodoAccesoArreglo extends NodoBase {
	private String nombre;
	private NodoBase indice;

	public NodoAccesoArreglo(String nombre, NodoBase indice) {
		super();
		this.nombre = nombre;
		this.indice = indice;
	}

	public NodoAccesoArreglo() {
		super();
		this.nombre = "";
		this.indice = null;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public NodoBase getIndice() {
		return indice;
	}

	public void setIndice(NodoBase indice) {
		this.indice = indice;
	}

}
