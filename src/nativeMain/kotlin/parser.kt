class Parser(private val tokens: List<Token>) {
  private val isAtEnd get() = peek().type == TokenType.Eof

  private var current = 0

  fun parse(): List<Stmt> {
    val stmts = mutableListOf<Stmt>()

    while (!isAtEnd) {
      stmts += declaration() ?: continue
    }

    return stmts
  }

  private fun synchronize(): Boolean {
    advance()

    while (!isAtEnd) {
      if (peek().type == TokenType.Semicolon) return true

      when (peek().type) {
        TokenType.Struct, TokenType.Func,
        TokenType.Val, TokenType.If, TokenType.Else,
        TokenType.Return, TokenType.Var -> return true
        else -> {
          // do nothing
        }
      }

      advance()
    }

    return false
  }

  // stmts
  private fun declaration(): Stmt? = try {
    when {
      match(TokenType.Val) -> valDeclaration()
      match(TokenType.Var) -> varDeclaration()
      match(TokenType.While) -> whileStatement()
      match(TokenType.LeftBrace) -> Stmt.Block(block())

      else -> statement()
    }
  } catch (error: ParseError) {
    // panic mode
    if (!synchronize())
      error.report()

    null
  }

  private fun block(): MutableList<Stmt> {
    val stmts = mutableListOf<Stmt>()

    while (!check(TokenType.RightBrace) && !isAtEnd) {
      stmts += declaration() ?: continue
    }

    consume(TokenType.RightBrace) ?: throw ParseError(peek(), "expecting finish block")

    return stmts
  }

  // TODO: remove
  private fun initializer(): Expr {
    if (!match(TokenType.Equal))
      throw ParseError(peek(), "expecting a declaration initializer")

    val initializer = expression()

    consume(TokenType.Semicolon)
      ?: throw ParseError(peek(), "expecting a semicolon after a declaration")

    return initializer
  }

  private fun valDeclaration(): Stmt {
    val name = consume(TokenType.Identifier)
      ?: throw ParseError(peek(), "expecting a declaration name")

    return Stmt.ValDecl(name, initializer())
  }

  private fun varDeclaration(): Stmt {
    val name = consume(TokenType.Identifier)
      ?: throw ParseError(peek(), "expecting a declaration name")

    return Stmt.VarDecl(name, initializer())
  }

  private fun statement(): Stmt {
    return exprStatement()
  }

  private fun whileStatement(): Stmt {
    val condition = expression()

    if (!match(TokenType.LeftBrace))
      throw ParseError(peek(), "Missing start of while block")

    return Stmt.WhileStmt(condition, block())
  }

  private fun exprStatement(): Stmt {
    val expr = expression()

    consume(TokenType.Semicolon) ?: throw ParseError(peek(), "Missing end of line")

    return Stmt.ExprStmt(expr)
  }

  // expressions
  @OptIn(ExperimentalStdlibApi::class)
  private fun expression(): Expr {
    if (match(TokenType.If)) return ifExpr()
    if (match(TokenType.Func)) return funcExpr()

    val expr = or()

    if (match(TokenType.Equal)) {
      val equals = previous()
      val value = assignment()

      if (expr is Expr.Var) {
        val (name) = expr

        return Expr.Assign(name, value)
      }

      // we report the error but don't throw
      // to enter in panic mode and synchronize
      ParseError(equals, "Invalid right-associative assignment")
        .report()
    }

    return expr
  }

  private fun ifExpr(): Expr {
    val condition = expression()

    if (!match(TokenType.LeftBrace))
      throw ParseError(peek(), "Missing start of if block")

    val mainBranch = block()
    val elseBranch = if (match(TokenType.Else))
      if (match(TokenType.LeftBrace))
        block()
      else throw ParseError(peek(), "Missing start of else block")
    else null

    return Expr.IfExpr(condition, mainBranch, elseBranch)
  }

  @OptIn(ExperimentalStdlibApi::class)
  private fun funcExpr(): Expr {
    val name = consume(TokenType.Identifier)
      ?: throw ParseError(peek(), "Expect func name")

    consume(TokenType.LeftParen)
      ?: throw ParseError(peek(), "Missing start of parameters")

    val parameters = buildList {
      if (!check(TokenType.RightParen))
        do
          if (size >= 32) // the limit is really 32 'cause the this is passed as a arg
            ParseError(peek(), "Can't have more than 32 arguments in a function")
              .report()
          else add(consume(TokenType.Identifier) ?: throw ParseError(peek(), "Missing param name"))
        while (match(TokenType.Comma))
    }

    consume(TokenType.RightParen) ?: throw ParseError(peek(), "Missing finish arguments decl")
    consume(TokenType.LeftBrace) ?: throw ParseError(peek(), "Missing start of func block")

    val body = block()

    return Expr.Func(name, parameters, body)
  }

  private fun assignment(): Expr {
    return or()
  }

  private fun or(): Expr {
    var expr = and()

    while (match(TokenType.Or)) {
      val op = previous()
      val right = equality()

      expr = Expr.Logical(expr, op, right)
    }

    return expr
  }

  private fun and(): Expr {
    var expr = equality()

    while (match(TokenType.And)) {
      val op = previous()
      val right = equality()

      expr = Expr.Logical(expr, op, right)
    }

    return expr
  }

  private fun equality(): Expr {
    var expr = comparison()

    while (match(TokenType.BangEqual, TokenType.EqualEqual)) {
      val op = previous()
      val right = comparison()

      expr = Expr.Binary(expr, op, right)
    }

    return expr
  }

  private fun comparison(): Expr {
    var expr = term()

    while (match(TokenType.GreaterEqual, TokenType.Greater, TokenType.Less, TokenType.LessEqual)) {
      val op = previous()
      val right = term()

      expr = Expr.Binary(expr, op, right)
    }

    return expr
  }

  private fun term(): Expr {
    var expr = factor()

    while (match(TokenType.Minus, TokenType.Plus, TokenType.Bang)) {
      val op = previous()
      val right = factor()

      expr = Expr.Binary(expr, op, right)
    }

    return expr
  }

  private fun factor(): Expr {
    var expr = unary()

    while (match(TokenType.Slash, TokenType.Star)) {
      val op = previous()
      val right = unary()

      expr = Expr.Binary(expr, op, right)
    }

    return expr
  }

  private fun unary(): Expr {
    if (match(TokenType.Bang, TokenType.Minus, TokenType.Plus)) {
      val op = previous()
      val right = unary()

      return Expr.Unary(op, right)
    }

    return call()
  }

  private fun call(): Expr {
    var expr = primary()

    while (true) when {
      match(TokenType.LeftParen) -> expr = finishCall(expr)
      else -> break
    }

    return expr
  }

  @OptIn(ExperimentalStdlibApi::class)
  private fun finishCall(callee: Expr): Expr {
    val arguments = buildList {
      if (!check(TokenType.RightParen)) {
        do
          if (size >= 32) // the limit is really 32 'cause the this is passed as a arg
            ParseError(peek(), "Can't have more than 32 arguments in a function")
              .report()
          else add(expression())
        while (match(TokenType.Comma))
      }
    }

    val paren = consume(TokenType.RightParen)
      ?: throw ParseError(peek(), "Missing finish call")

    return Expr.Call(callee, paren, arguments)
  }

  private fun primary(): Expr = when {
    match(TokenType.False) -> Expr.Literal(false)
    match(TokenType.True) -> Expr.Literal(true)

    match(TokenType.Number, TokenType.String) -> Expr.Literal(previous().let {
      it.literal ?: ""
    }) // TODO: fixme

    match(TokenType.LeftParen) -> Expr.Grouping(expression().also {
      consume(TokenType.RightParen) ?: throw ParseError(peek(), "Unfinished grouping")
    })

    match(TokenType.Identifier) -> Expr.Var(previous())

    else -> throw ParseError(peek())
  }

  // utils
  private fun consume(type: TokenType): Token? {
    if (check(type)) return advance()

    return null
  }

  private fun match(vararg types: TokenType): Boolean {
    for (type in types) {
      if (check(type)) {
        advance()
        return true
      }
    }

    return false
  }

  private fun check(type: TokenType): Boolean {
    if (isAtEnd) return false

    return peek().type == type
  }

  private fun advance(): Token {
    if (!isAtEnd) current++

    return previous()
  }

  private fun peek(): Token {
    return tokens[current]
  }

  private fun previous(): Token {
    return tokens[current - 1]
  }
}

fun debug(msg: String) {
  println("[debug] $msg")
}