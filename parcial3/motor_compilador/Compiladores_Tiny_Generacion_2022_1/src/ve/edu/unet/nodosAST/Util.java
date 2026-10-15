package ve.edu.unet.nodosAST;

public class Util {

	static int sangria = 0;

	//Imprimo en modo texto con sangrias el AST
	public static void imprimirAST(NodoBase raiz){
		  sangria+=2;
		  while (raiz != null) {
		    printSpaces();
		    if (raiz instanceof  NodoIf)
		    	System.err.println("If (linea "+raiz.getLinea()+")");
		    else if (raiz instanceof  NodoRepeat)
		    	System.err.println("Repeat (linea "+raiz.getLinea()+")");

		    else if (raiz instanceof  NodoAsignacion)
		    	System.err.println("Asignacion a: "+((NodoAsignacion)raiz).getIdentificador()+(((NodoAsignacion)raiz).esIndexada()?"[indexado]":"")+" (linea "+raiz.getLinea()+")");

		    else if (raiz instanceof  NodoLeer)
		    	System.err.println("Lectura: "+((NodoLeer)raiz).getIdentificador()+(((NodoLeer)raiz).esIndexada()?"[indexado]":"")+" (linea "+raiz.getLinea()+")");

		    else if (raiz instanceof  NodoEscribir)
		    	System.err.println("Escribir (linea "+raiz.getLinea()+")");

		    /* IMPORTANTE: NodoDeclaracionArreglo extends NodoDeclaracion, asi que
		     * "instanceof NodoDeclaracion" tambien da true para un arreglo. Por
		     * eso el chequeo de NodoDeclaracionArreglo va SIEMPRE antes que el
		     * de NodoDeclaracion en esta cadena de else-if. */
		    else if (raiz instanceof  NodoDeclaracionArreglo)
		    	System.err.println("Declaracion arreglo: "+((NodoDeclaracionArreglo)raiz).getTipoDeclarado().comoTexto()+" "+((NodoDeclaracionArreglo)raiz).getNombre()+"["+((NodoDeclaracionArreglo)raiz).getTamano()+"] (linea "+raiz.getLinea()+")");

		    else if (raiz instanceof  NodoDeclaracion)
		    	System.err.println("Declaracion: "+((NodoDeclaracion)raiz).getTipoDeclarado().comoTexto()+" "+((NodoDeclaracion)raiz).getNombre()+" (linea "+raiz.getLinea()+")");

		    else if (raiz instanceof  NodoAccesoArreglo)
		    	System.err.println("Acceso arreglo: "+((NodoAccesoArreglo)raiz).getNombre()+" (linea "+raiz.getLinea()+")");

		    else if (raiz instanceof  NodoFuncion)
		    	System.err.println("Funcion: "+((NodoFuncion)raiz).getTipoRetorno().comoTexto()+" "+((NodoFuncion)raiz).getNombre()+"("+((NodoFuncion)raiz).getParametros().size()+" parametros) (linea "+raiz.getLinea()+")");

		    else if (raiz instanceof  NodoProcedimiento)
		    	System.err.println("Procedimiento: "+((NodoProcedimiento)raiz).getNombre()+"("+((NodoProcedimiento)raiz).getParametros().size()+" parametros) (linea "+raiz.getLinea()+")");

		    else if (raiz instanceof  NodoLlamada)
		    	System.err.println("Llamada a: "+((NodoLlamada)raiz).getNombre()+" con "+((NodoLlamada)raiz).getArgumentos().size()+" argumentos (linea "+raiz.getLinea()+")");

		    else if (raiz instanceof  NodoRetorno)
		    	System.err.println("Retorno (linea "+raiz.getLinea()+")");

		    else if (raiz instanceof NodoOperacion
		    		|| raiz instanceof NodoValor
		    		|| raiz instanceof NodoIdentificador
		    		|| raiz instanceof NodoBooleano
		    		|| raiz instanceof NodoString )
		    	imprimirNodo(raiz);
		    else System.err.println("Tipo de nodo desconocido");;

		    /* Hago el recorrido recursivo */
		    if (raiz instanceof  NodoIf){
		    	printSpaces();
		    	System.err.println("**Prueba IF**");
		    	imprimirAST(((NodoIf)raiz).getPrueba());
		    	printSpaces();
		    	System.err.println("**Then IF**");
		    	imprimirAST(((NodoIf)raiz).getParteThen());
		    	if(((NodoIf)raiz).getParteElse()!=null){
		    		printSpaces();
		    		System.err.println("**Else IF**");
		    		imprimirAST(((NodoIf)raiz).getParteElse());
		    	}
		    }
		    else if (raiz instanceof  NodoRepeat){
		    	printSpaces();
		    	System.err.println("**Cuerpo REPEAT**");
		    	imprimirAST(((NodoRepeat)raiz).getCuerpo());
		    	printSpaces();
		    	System.err.println("**Prueba REPEAT**");
		    	imprimirAST(((NodoRepeat)raiz).getPrueba());
		    }
		    else if (raiz instanceof  NodoAsignacion){
		    	if(((NodoAsignacion)raiz).esIndexada()){
		    		printSpaces();
		    		System.err.println("**Indice ASIGNACION**");
		    		imprimirAST(((NodoAsignacion)raiz).getIndice());
		    	}
		    	imprimirAST(((NodoAsignacion)raiz).getExpresion());
		    }
		    else if (raiz instanceof  NodoLeer){
		    	if(((NodoLeer)raiz).esIndexada()){
		    		printSpaces();
		    		System.err.println("**Indice LEER**");
		    		imprimirAST(((NodoLeer)raiz).getIndice());
		    	}
		    }
		    else if (raiz instanceof  NodoEscribir)
		    	imprimirAST(((NodoEscribir)raiz).getExpresion());
		    else if (raiz instanceof NodoOperacion){
		    	printSpaces();
		    	System.err.println("**Expr Izquierda Operacion**");
		    	imprimirAST(((NodoOperacion)raiz).getOpIzquierdo());
		    	printSpaces();
		    	System.err.println("**Expr Derecha Operacion**");
		    	imprimirAST(((NodoOperacion)raiz).getOpDerecho());
		    }
		    else if (raiz instanceof NodoAccesoArreglo){
		    	printSpaces();
		    	System.err.println("**Indice**");
		    	imprimirAST(((NodoAccesoArreglo)raiz).getIndice());
		    }
		    else if (raiz instanceof NodoFuncion){
		    	printSpaces();
		    	System.err.println("**Parametros FUNCION**");
		    	imprimirParametros(((NodoFuncion)raiz).getParametros());
		    	printSpaces();
		    	System.err.println("**Cuerpo FUNCION**");
		    	imprimirAST(((NodoFuncion)raiz).getCuerpo());
		    }
		    else if (raiz instanceof NodoProcedimiento){
		    	printSpaces();
		    	System.err.println("**Parametros PROCEDIMIENTO**");
		    	imprimirParametros(((NodoProcedimiento)raiz).getParametros());
		    	printSpaces();
		    	System.err.println("**Cuerpo PROCEDIMIENTO**");
		    	imprimirAST(((NodoProcedimiento)raiz).getCuerpo());
		    }
		    else if (raiz instanceof NodoLlamada){
		    	java.util.List<NodoBase> argumentos = ((NodoLlamada)raiz).getArgumentos();
		    	for (int i=0; i<argumentos.size(); i++){
		    		printSpaces();
		    		System.err.println("**Argumento "+i+"**");
		    		imprimirAST(argumentos.get(i));
		    	}
		    }
		    else if (raiz instanceof NodoRetorno){
		    	if(((NodoRetorno)raiz).getExpresion()!=null){
		    		printSpaces();
		    		System.err.println("**Expresion RETORNO**");
		    		imprimirAST(((NodoRetorno)raiz).getExpresion());
		    	}
		    }
		    raiz = raiz.getHermanoDerecha();
		  }
		  sangria-=2;
		}

/* Imprime espacios con sangria */
static void printSpaces()
{ int i;
  for (i=0;i<sangria;i++)
	  System.err.print(" ");
}

/* Imprime la lista de parametros de una funcion/procedimiento.
 * IMPORTANTE: NodoDeclaracionArreglo extends NodoDeclaracion, asi que hay
 * que chequear "instanceof NodoDeclaracionArreglo" ANTES que NodoDeclaracion,
 * igual que en imprimirAST, para no degradar un parametro arreglo a simple. */
static void imprimirParametros(java.util.List<NodoDeclaracion> parametros)
{
	for (int i=0; i<parametros.size(); i++){
		NodoDeclaracion p = parametros.get(i);
		printSpaces();
		if (p instanceof NodoDeclaracionArreglo){
			NodoDeclaracionArreglo pa = (NodoDeclaracionArreglo)p;
			System.err.println("    param: "+pa.getTipoDeclarado().comoTexto()+" "+pa.getNombre()+"[] (arreglo, tamano="+pa.getTamano()+")");
		}
		else
			System.err.println("    param: "+p.getTipoDeclarado().comoTexto()+" "+p.getNombre());
	}
}

/* Imprime informacion de los nodos */
static void imprimirNodo( NodoBase raiz )
{
	if(	raiz instanceof NodoRepeat
		||	raiz instanceof NodoLeer
		||	raiz instanceof NodoEscribir  ){
		System.err.println("palabra reservada: "+ raiz.getClass().getName()+" (linea "+raiz.getLinea()+")");
	}

	if(	raiz instanceof NodoAsignacion )
		System.err.println(":= (linea "+raiz.getLinea()+")");

	if(	raiz instanceof NodoOperacion ){
		tipoOp sel=((NodoOperacion) raiz).getOperacion();
		if(sel==tipoOp.menor)
			System.err.println("< (linea "+raiz.getLinea()+")");
		if(sel==tipoOp.igual)
			System.err.println("= (linea "+raiz.getLinea()+")");
		if(sel==tipoOp.mas)
			System.err.println("+ (linea "+raiz.getLinea()+")");
		if(sel==tipoOp.menos)
			System.err.println("- (linea "+raiz.getLinea()+")");
		if(sel==tipoOp.por)
			System.err.println("* (linea "+raiz.getLinea()+")");
		if(sel==tipoOp.entre)
			System.err.println("/ (linea "+raiz.getLinea()+")");
		if(sel==tipoOp.y)
			System.err.println("y (linea "+raiz.getLinea()+")");
		if(sel==tipoOp.o)
			System.err.println("o (linea "+raiz.getLinea()+")");
		if(sel==tipoOp.no)
			System.err.println("no (linea "+raiz.getLinea()+")");
	}

	if(	raiz instanceof NodoValor ){
		System.err.println("NUM, val= "+ ((NodoValor)raiz).getValor()+" (linea "+raiz.getLinea()+")");
	}

	if(	raiz instanceof NodoIdentificador ){
		System.err.println("ID, nombre= "+ ((NodoIdentificador)raiz).getNombre()+" (linea "+raiz.getLinea()+")");
	}

	if(	raiz instanceof NodoBooleano ){
		System.err.println("BOOL, val= "+ ((NodoBooleano)raiz).getValor()+" (linea "+raiz.getLinea()+")");
	}

	if(	raiz instanceof NodoString ){
		System.err.println("STRING, val= \""+ ((NodoString)raiz).getValor()+"\" (linea "+raiz.getLinea()+")");
	}

}


}
