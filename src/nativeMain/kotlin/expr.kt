sealed class Expr {
  data class Assign(val name: Token, val value: Expr) : Expr()
  data class Binary(val left: Expr, val op: Token, val right: Expr) : Expr()
  data class Logical(val left: Expr, val op: Token, val right: Expr) : Expr()
  data class Grouping(val expr: Expr) : Expr()
  data class Literal(val value: Any) : Expr()
  data class Unary(val op: Token, val right: Expr) : Expr()
  data class Var(val name: Token) : Expr()
  data class Call(val calle: Expr, val paren: Token, val arguments: List<Expr>) : Expr()
  data class Func(val name: Token, val arguments: List<Token>, val body: List<Stmt>) : Expr()

  // TODO: change to List<Stmt> to Stmt.Block
  data class IfExpr(val condition: Expr, val thenBranch: List<Stmt>, val elseBranch: List<Stmt>?) : Expr()
}
