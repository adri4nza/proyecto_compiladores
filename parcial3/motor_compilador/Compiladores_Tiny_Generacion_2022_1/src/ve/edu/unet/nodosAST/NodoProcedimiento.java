package ve.edu.unet.nodosAST;

/* Definicion de un procedimiento de Tiny++: no retorna valor, tiene nombre,
 * lista de parametros y un cuerpo (lista de sentencias enlazadas). */
public class NodoProcedimiento extends NodoBase {
	private String nombre;
	private java.util.List<NodoDeclaracion> parametros;
	private NodoBase cuerpo;

	public NodoProcedimiento(String nombre,
			java.util.List<NodoDeclaracion> parametros, NodoBase cuerpo) {
		super();
		this.nombre = nombre;
		this.parametros = (parametros != null) ? parametros : new java.util.ArrayList<NodoDeclaracion>();
		this.cuerpo = cuerpo;
	}

	public NodoProcedimiento() {
		super();
		this.nombre = "";
		this.parametros = new java.util.ArrayList<NodoDeclaracion>();
		this.cuerpo = null;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

	public java.util.List<NodoDeclaracion> getParametros() {
		return parametros;
	}

	public void setParametros(java.util.List<NodoDeclaracion> parametros) {
		this.parametros = (parametros != null) ? parametros : new java.util.ArrayList<NodoDeclaracion>();
	}

	public NodoBase getCuerpo() {
		return cuerpo;
	}

	public void setCuerpo(NodoBase cuerpo) {
		this.cuerpo = cuerpo;
	}

}
