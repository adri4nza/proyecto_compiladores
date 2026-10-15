package ve.edu.unet.nodosAST;

public class NodoBase {
	/*Esta clase fue creada para tener un origen comun de todas las clases...
	 * 
	 * ESTE MODELO DE AST PUEDE SER MEJORADO DE MUCHAS FORMAS, POR SIMPLIFICACION Y
	 * DIDACTICA EN CLASE, SE LLEVARA A CABO DE FORMA MUY SIMPLE Y POCO FLEXIBLE
	 * */
	
	private NodoBase HermanoDerecha;

	public NodoBase(NodoBase hermanoDerecha) {
		super();
		HermanoDerecha = hermanoDerecha;
	}

	public NodoBase() {
		super();
		HermanoDerecha=null;
	}

	public NodoBase getHermanoDerecha() {
		return HermanoDerecha;
	}

	public void setHermanoDerecha(NodoBase hermanoDerecha) {
		HermanoDerecha = hermanoDerecha;
	}
	
	public boolean TieneHermano() {
		return (HermanoDerecha!=null);
	}

	/* Linea del fuente donde aparece este nodo. La fija el parser en sus
	 * acciones semanticas. Sirve para reportar errores (Contrato B del equipo). */
	private int linea = -1;
	public int  getLinea()      { return linea; }
	public void setLinea(int l) { linea = l; }

	/* Anotaciones que rellena AnalizadorSemantico y consume el generador (M2) */
	private Tipo tipo = Tipo.ERROR;                 // tipo sintetizado de la expresion
	private ve.edu.unet.RegistroSimbolo registro;   // simbolo ya resuelto
	public Tipo getTipo()           { return tipo; }
	public void setTipo(Tipo t)     { tipo = t; }
	public ve.edu.unet.RegistroSimbolo getRegistro()       { return registro; }
	public void setRegistro(ve.edu.unet.RegistroSimbolo r) { registro = r; }

}
