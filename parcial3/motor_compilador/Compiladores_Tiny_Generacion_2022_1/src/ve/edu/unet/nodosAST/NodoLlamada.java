package ve.edu.unet.nodosAST;

/* Llamada a una funcion o procedimiento: f(a, b+1, c). */
public class NodoLlamada extends NodoBase {
	private String nombre;
	private java.util.List<NodoBase> argumentos;

	public NodoLlamada(String nombre, java.util.List<NodoBase> argumentos) {
		super();
		this.nombre = nombre;
		this.argumentos = (argumentos != null) ? argumentos : new java.util.ArrayList<NodoBase>();
	}

	public NodoLlamada() {
		super();
		this.nombre = "";
		this.argumentos = new java.util.ArrayList<NodoBase>();
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public java.util.List<NodoBase> getArgumentos() {
		return argumentos;
	}

	public void setArgumentos(java.util.List<NodoBase> argumentos) {
		this.argumentos = (argumentos != null) ? argumentos : new java.util.ArrayList<NodoBase>();
	}

}
