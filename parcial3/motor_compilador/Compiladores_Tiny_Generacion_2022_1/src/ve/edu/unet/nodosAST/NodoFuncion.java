package ve.edu.unet.nodosAST;

/* Definicion de una funcion de Tiny++: tiene tipo de retorno, nombre,
 * lista de parametros y un cuerpo (lista de sentencias enlazadas). */
public class NodoFuncion extends NodoBase {
	private Tipo tipoRetorno;
	private String nombre;
	private java.util.List<NodoDeclaracion> parametros;
	private NodoBase cuerpo;

	public NodoFuncion(Tipo tipoRetorno, String nombre,
			java.util.List<NodoDeclaracion> parametros, NodoBase cuerpo) {
		super();
		this.tipoRetorno = tipoRetorno;
		this.nombre = nombre;
		this.parametros = (parametros != null) ? parametros : new java.util.ArrayList<NodoDeclaracion>();
		this.cuerpo = cuerpo;
	}

	public NodoFuncion() {
		super();
		this.tipoRetorno = Tipo.ERROR;
		this.nombre = "";
		this.parametros = new java.util.ArrayList<NodoDeclaracion>();
		this.cuerpo = null;
	}

	public Tipo getTipoRetorno() {
		return tipoRetorno;
	}

	public void setTipoRetorno(Tipo tipoRetorno) {
		this.tipoRetorno = tipoRetorno;
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
