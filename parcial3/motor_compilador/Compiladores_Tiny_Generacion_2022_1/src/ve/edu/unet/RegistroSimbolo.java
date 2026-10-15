package ve.edu.unet;

import java.util.ArrayList;
import java.util.List;

import ve.edu.unet.nodosAST.Categoria;
import ve.edu.unet.nodosAST.Tipo;

/* RegistroSimbolo -- una entrada de la tabla de simbolos.
 *
 * En el Tiny original solo guardaba nombre, linea y direccion de memoria.
 * Tiny++ necesita ademas tipo, categoria, tamano (arreglos), nivel de
 * ambito, desplazamiento dentro del marco de activacion y la firma de
 * parametros de funciones y procedimientos.
 *
 * COMPATIBILIDAD: el constructor de 3 argumentos y los getters originales
 * se conservan intactos porque Generador.java (modulo 2) los usa.
 */
public class RegistroSimbolo {
	private String identificador;
	private int NumLinea;
	private int DireccionMemoria;

	/* Campos de Tiny++ */
	private Tipo tipo;                 /* tipo del dato, o tipo de retorno si es FUNCION */
	private Categoria categoria;       /* VARIABLE / ARREGLO / FUNCION / PROCEDIMIENTO / PARAMETRO */
	private int tamano;                /* numero de elementos si es ARREGLO; 1 en cualquier otro caso */
	private int nivel;                 /* 0 = ambito global; >0 = dentro de funcion o procedimiento */
	private int desplazamiento;        /* indice dentro del marco de activacion, desde 0 */
	private List<RegistroSimbolo> parametros;  /* la firma, si es FUNCION o PROCEDIMIENTO */

	/* Constructor original de 3 argumentos.
	 * Delega en el constructor completo con los valores por defecto del
	 * Tiny original, para que el comportamiento del codigo base no cambie.
	 */
	public RegistroSimbolo(String identificador, int numLinea,
			int direccionMemoria) {
		this(identificador, numLinea, Tipo.ENTERO, Categoria.VARIABLE, 1, 0);
		DireccionMemoria = direccionMemoria;
	}

	/* Constructor completo de Tiny++ */
	public RegistroSimbolo(String identificador, int numLinea, Tipo tipo,
			Categoria categoria, int tamano, int nivel) {
		super();
		this.identificador = identificador;
		NumLinea = numLinea;
		DireccionMemoria = 0;
		this.tipo = tipo;
		this.categoria = categoria;
		this.tamano = tamano;
		this.nivel = nivel;
		this.desplazamiento = 0;
		this.parametros = new ArrayList<RegistroSimbolo>();  /* nunca null */
	}

	public String getIdentificador() {
		return identificador;
	}

	public int getNumLinea() {
		return NumLinea;
	}

	public void setNumLinea(int numLinea) {
		NumLinea = numLinea;
	}

	public int getDireccionMemoria() {
		return DireccionMemoria;
	}

	public void setDireccionMemoria(int direccionMemoria) {
		DireccionMemoria = direccionMemoria;
	}

	public Tipo getTipo() {
		return tipo;
	}

	public void setTipo(Tipo tipo) {
		this.tipo = tipo;
	}

	public Categoria getCategoria() {
		return categoria;
	}

	public void setCategoria(Categoria categoria) {
		this.categoria = categoria;
	}

	public int getTamano() {
		return tamano;
	}

	public void setTamano(int tamano) {
		this.tamano = tamano;
	}

	public int getNivel() {
		return nivel;
	}

	public void setNivel(int nivel) {
		this.nivel = nivel;
	}

	public int getDesplazamiento() {
		return desplazamiento;
	}

	public void setDesplazamiento(int desplazamiento) {
		this.desplazamiento = desplazamiento;
	}

	public List<RegistroSimbolo> getParametros() {
		return parametros;
	}

	public void setParametros(List<RegistroSimbolo> parametros) {
		this.parametros = (parametros == null)
				? new ArrayList<RegistroSimbolo>() : parametros;
	}

	public void agregarParametro(RegistroSimbolo p) {
		parametros.add(p);
	}

	public int getCantidadParametros() {
		return parametros.size();
	}

	/* Lo consulta el modulo 2 para decidir si el acceso es absoluto
	 * (variable global) o relativo al marco de activacion (local). */
	public boolean esGlobal() {
		return nivel == 0;
	}

	/* Salida de depuracion, usada por TablaSimbolos.ImprimirClaves() */
	public String toString() {
		return identificador
			+ " [tipo=" + (tipo == null ? "null" : tipo.comoTexto())
			+ ", categoria=" + categoria
			+ ", nivel=" + nivel
			+ ", dir=" + DireccionMemoria
			+ ", desp=" + desplazamiento
			+ ", tamano=" + tamano
			+ ", linea=" + NumLinea + "]";
	}
}
