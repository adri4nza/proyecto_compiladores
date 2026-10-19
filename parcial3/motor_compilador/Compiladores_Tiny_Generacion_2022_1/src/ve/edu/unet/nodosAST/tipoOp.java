package ve.edu.unet.nodosAST;

public enum tipoOp {
	/* menor, igual, mas, menos, por, entre: operadores aritmeticos/relacionales,
	 * siempre binarios (usan opIzquierdo y opDerecho de NodoOperacion).
	 *
	 * y, o: operadores logicos binarios (AND, OR de Tiny++). Usan opIzquierdo
	 * y opDerecho igual que los anteriores.
	 *
	 * no: operador logico unario (NOT de Tiny++). Reutiliza NodoOperacion
	 * dejando opDerecho == null y usando solo opIzquierdo.
	 */
	menor,igual,mas,menos,por,entre,y,o,no
}
