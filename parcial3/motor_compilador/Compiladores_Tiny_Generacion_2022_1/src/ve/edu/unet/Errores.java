package ve.edu.unet;

/* Errores.java -- Unica salida de errores del compilador Tiny++.
 *
 * PARA QUE SIRVE
 * Centraliza el reporte de TODOS los errores del compilador (lexicos,
 * sintacticos y semanticos) en un solo punto, y lleva la cuenta de
 * cuantos hubo de cada clase.
 *
 * QUIEN LA CONSUME
 * La consola del IDE (modulo 4). El IDE lanza el compilador como proceso
 * externo y lee su stderr linea por linea: cada linea es un objeto JSON
 * independiente que el IDE parsea para pintar el marcador de error en el
 * editor, en la linea indicada.
 *
 * CONTRATO B (INMUTABLE)
 * - Una linea por error, por stderr (NUNCA por stdout: el stdout lleva
 *   solamente el codigo .tm generado, ese es el Contrato D).
 * - JSON valido con exactamente tres claves, ni una mas:
 *       { "tipo": "...", "linea": N, "mensaje": "..." }
 * - "tipo" es uno de: lexico, sintactico, semantico.
 *
 * El modulo 2 (generacion de codigo) usa hayErroresPrevios() para no
 * intentar generar codigo si el analisis lexico o sintactico ya fallo.
 */
public class Errores {
    private static int totalLexicos = 0;
    private static int totalSintacticos = 0;
    private static int totalSemanticos = 0;

    /* Escapa el mensaje para que el JSON sea valido (Contrato B) */
    private static String escapar(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ")
                .replace("\r", " ")
                .replace("\t", " ");
    }

    private static void emitir(String tipo, int linea, String mensaje) {
        System.err.println("{ \"tipo\": \"" + tipo + "\", \"linea\": " + linea
                         + ", \"mensaje\": \"" + escapar(mensaje) + "\" }");
    }

    public static void lexico(int linea, String mensaje) {
        totalLexicos++;     emitir("lexico", linea, mensaje);
    }
    public static void sintactico(int linea, String mensaje) {
        totalSintacticos++; emitir("sintactico", linea, mensaje);
    }
    public static void semantico(int linea, String mensaje) {
        totalSemanticos++;  emitir("semantico", linea, mensaje);
    }

    public static int getTotal()       { return totalLexicos + totalSintacticos + totalSemanticos; }
    public static boolean hayErrores() { return getTotal() > 0; }
    public static boolean hayErroresPrevios() { return totalLexicos + totalSintacticos > 0; }
}
