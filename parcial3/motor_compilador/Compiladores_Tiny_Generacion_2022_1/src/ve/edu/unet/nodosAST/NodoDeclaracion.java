package ve.edu.unet.nodosAST;

/* Declaracion simple de una variable: "int x;" o un parametro de funcion/procedimiento. */
public class NodoDeclaracion extends NodoBase {
	/* Tipo escrito por el programador en la declaracion. Distinto del tipo
	 * sintetizado de NodoBase (getTipo), que lo rellena el analisis semantico. */
	private Tipo tipoDeclarado;
	private String nombre;

	public NodoDeclaracion(Tipo tipo, String nombre) {
		super();
		this.tipoDeclarado = tipo;
		this.nombre = nombre;
	}

	public NodoDeclaracion() {
		super();
		this.tipoDeclarado = Tipo.ERROR;
		this.nombre = "";
	}

	public Tipo getTipoDeclarado() {
		return tipoDeclarado;
	}

	public void setTipoDeclarado(Tipo tipo) {
		this.tipoDeclarado = tipo;
	}

	public String getNombre() {
		return nombre;
	}

	public void setNombre(String nombre) {
		this.nombre = nombre;
	}

}
