package ve.edu.unet;

import java_cup.runtime.*;
import java.io.Reader;

%%
/* Habilitar la compatibilidad con el interfaz CUP para el generador sintactico*/
%cup
/* Llamar Lexico a la clase que contiene el analizador lexico */
%class Lexico

/*-- DECLARACIONES --*/
%{
	/* OJO: el campo sf DEBE estar tipado con la clase concreta
	 * DefaultSymbolFactory y no con la interfaz SymbolFactory. La interfaz
	 * NO declara los overloads newSymbol(nombre, id, int left, int right)
	 * ni newSymbol(nombre, id, int left, int right, Object valor), que son
	 * los que hacen falta para que cada token viaje con su numero de linea.
	 * Si se tipa con la interfaz, el lexer generado no compila. */
	public Lexico(Reader r, DefaultSymbolFactory sf){
		this(r);
		this.sf = sf;
		debug = false;
	}

	private DefaultSymbolFactory sf;
	private boolean debug;

	/* La linea la provee yyline (directiva %line de JFlex), base 0 -> +1.
	 * JFlex la lleva por su cuenta, incluso dentro de los comentarios
	 * multilinea, asi que no hace falta ningun contador manual. */
	private int ultimaLinea = 1;
	private int linea() { ultimaLinea = yyline + 1; return ultimaLinea; }

	/* Linea que se le pone al token EOF. NO se usa linea() aqui: al terminar
	 * el archivo yyline ya apunta a la linea SIGUIENTE a la ultima (por el
	 * salto de linea final), y el IDE no puede marcar una linea que no
	 * existe. Se reporta la linea del ultimo token real. */
	private int lineaFin() { return ultimaLinea; }
%}
%eofval{
	return sf.newSymbol("EOF", sym.EOF, lineaFin(), lineaFin());
%eofval}

/* Acceso a la columna y fila actual de analisis CUP */
%line
%column


digito			= [0-9]
numero			= {digito}+
letra			= [a-zA-Z]
identificador	= {letra}({letra}|{digito}|_)*
cadena			= \"[^\"\n]*\"
/* El \r suelto hace falta: la regla "." de JFlex NO cubre los terminadores de
 * linea, asi que un fuente con finales de linea estilo Mac clasico reventaba
 * con java.lang.Error "could not match input" y rompia el contrato de errores. */
nuevalinea		= \n | \n\r | \r\n | \r
espacio			= [ \t]+
%%

/* --- Palabras reservadas del Tiny original ---
 * Van ANTES de {identificador}: JFlex usa el match mas largo y, cuando dos
 * reglas empatan en longitud, gana la que aparece primero en el archivo. */
"if"			{ return sf.newSymbol("IF", sym.IF, linea(), linea()); }
"then"			{ return sf.newSymbol("THEN", sym.THEN, linea(), linea()); }
"else"			{ return sf.newSymbol("ELSE", sym.ELSE, linea(), linea()); }
"end"			{ return sf.newSymbol("END", sym.END, linea(), linea()); }
"repeat"		{ return sf.newSymbol("REPEAT", sym.REPEAT, linea(), linea()); }
"until"			{ return sf.newSymbol("UNTIL", sym.UNTIL, linea(), linea()); }
"read"			{ return sf.newSymbol("READ", sym.READ, linea(), linea()); }
"write"			{ return sf.newSymbol("WRITE", sym.WRITE, linea(), linea()); }

/* --- Palabras reservadas anadidas por Tiny++ --- */
"int"			{ return sf.newSymbol("INT", sym.INT, linea(), linea()); }
"bool"			{ return sf.newSymbol("BOOL", sym.BOOL, linea(), linea()); }
"string"		{ return sf.newSymbol("STRING", sym.STRING, linea(), linea()); }
"true"			{ return sf.newSymbol("TRUE", sym.TRUE, linea(), linea()); }
"false"			{ return sf.newSymbol("FALSE", sym.FALSE, linea(), linea()); }
"function"		{ return sf.newSymbol("FUNCTION", sym.FUNCTION, linea(), linea()); }
"procedure"		{ return sf.newSymbol("PROCEDURE", sym.PROCEDURE, linea(), linea()); }
"return"		{ return sf.newSymbol("RETURN", sym.RETURN, linea(), linea()); }
"and"			{ return sf.newSymbol("AND", sym.AND, linea(), linea()); }
"or"			{ return sf.newSymbol("OR", sym.OR, linea(), linea()); }
"not"			{ return sf.newSymbol("NOT", sym.NOT, linea(), linea()); }

/* --- Operadores y signos de puntuacion --- */
":="			{ return sf.newSymbol("ASSIGN", sym.ASSIGN, linea(), linea()); }
"="				{ return sf.newSymbol("EQ", sym.EQ, linea(), linea()); }
"<"				{ return sf.newSymbol("LT", sym.LT, linea(), linea()); }
"+"				{ return sf.newSymbol("PLUS", sym.PLUS, linea(), linea()); }
"-"				{ return sf.newSymbol("MINUS", sym.MINUS, linea(), linea()); }
"*"				{ return sf.newSymbol("TIMES", sym.TIMES, linea(), linea()); }
"/"				{ return sf.newSymbol("OVER", sym.OVER, linea(), linea()); }
"("				{ return sf.newSymbol("LPAREN", sym.LPAREN, linea(), linea()); }
")"				{ return sf.newSymbol("RPAREN", sym.RPAREN, linea(), linea()); }
"["				{ return sf.newSymbol("LBRACKET", sym.LBRACKET, linea(), linea()); }
"]"				{ return sf.newSymbol("RBRACKET", sym.RBRACKET, linea(), linea()); }
","				{ return sf.newSymbol("COMMA", sym.COMMA, linea(), linea()); }
";"				{ return sf.newSymbol("SEMI", sym.SEMI, linea(), linea()); }

/* --- Literales --- */
{numero}		{ /* Se valida el rango AQUI, que es donde se conoce la linea exacta.
				   * Sin esto, el Integer.parseInt de las acciones del parser lanzaba
				   * una NumberFormatException sin capturar: el compilador moria con
				   * un stack trace crudo en stderr y eso rompe el contrato JSON de
				   * errores. Se reporta y se entrega "0" para poder seguir analizando. */
				  String n = yytext();
				  try {
					Integer.parseInt(n);
				  } catch (NumberFormatException e) {
					Errores.lexico(linea(),
						"Literal entero fuera del rango representable: " + n);
					n = "0";
				  }
				  return sf.newSymbol("NUM", sym.NUM, linea(), linea(), n); }
{cadena}		{ /* se entrega sin las comillas */
				  String t = yytext();
				  return sf.newSymbol("CADENA", sym.CADENA, linea(), linea(),
							t.substring(1, t.length()-1)); }
{identificador}	{ return sf.newSymbol("ID", sym.ID, linea(), linea(),
							new String(yytext())); }

/* --- Elementos que no producen token --- */
{nuevalinea}	{ /* yyline ya lleva la cuenta de las lineas */ }
{espacio}		{ /* salto espacios en blanco */ }
"{"[^}]+"}"		{ /* salto comentarios */ }

/* --- Cualquier otra cosa es un caracter ilegal --- */
.				{ Errores.lexico(linea(), "Caracter ilegal: " + yytext()); }
