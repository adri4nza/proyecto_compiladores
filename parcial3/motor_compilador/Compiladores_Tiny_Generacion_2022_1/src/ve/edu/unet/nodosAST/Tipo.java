package ve.edu.unet.nodosAST;

/* Tipos de dato del lenguaje Tiny++.
 * VACIO = los procedimientos, que no retornan valor.
 * ERROR = tipo centinela: cuando una subexpresion ya fallo se propaga
 *         ERROR hacia arriba y NO se vuelve a reportar, para evitar
 *         que un solo error del programador genere una cascada de mensajes.
 */
public enum Tipo {
    ENTERO, BOOLEANO, CADENA, VACIO, ERROR;

    /* Nombre tal como lo escribe el programador, para los mensajes de error */
    public String comoTexto() {
        switch (this) {
            case ENTERO:   return "int";
            case BOOLEANO: return "bool";
            case CADENA:   return "string";
            case VACIO:    return "void";
            default:       return "error";
        }
    }
}
