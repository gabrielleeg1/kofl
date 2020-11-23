package com.lorenzoog.kofl.interpreter

private const val MAX_ARGS = 32  // the limit is really 31 'cause the this is passed as a arg
private const val MAX_ARGS_ERROR_MESSAGE = "can't have more than $MAX_ARGS arguments in a function"
private const val INVALID_RIGHT_ASSOCIATIVE_ERROR_MESSAGE = "invalid right-associative assignment"

class Parser(private val tokens: List<Token>, private val repl: Boolean = false) {
  private val isAtEnd get() = peek().type == TokenType.Eof
  private var current = 0

  fun parse(): List<Stmt> {
    val stmts = mutableListOf<Stmt>()

    while (!isAtEnd) {
      stmts += declaration() ?: continue
    }

    return stmts
  }

  private fun synchronize() {
    advance()

    while (!isAtEnd) {
      when (peek().type) { // TODO: report useless char
        TokenType.Struct, TokenType.Func,
        TokenType.Val, TokenType.If, TokenType.Else,
        TokenType.Return, TokenType.Var,
        TokenType.Semicolon -> return
        else -> {
          // do nothing
        }
      }

      advance()
    }
  }

  // stmts
  enum class ScopeType { Global, Func }

  private fun declaration(): Stmt? = try {
    when {
      match(TokenType.Val) -> valDeclaration()
      match(TokenType.Var) -> varDeclaration()
      match(TokenType.Typedef) -> typeDeclaration()
      match(TokenType.LeftBrace) -> Stmt.Block(block())
      match(TokenType.Func) -> Stmt.ExprStmt(funcExpr(FuncType.Func))

      else -> if (repl) statement() else throw error(expecting("declaration"))
    }
  } catch (error: ParseError) {
    // panic mode
    synchronize()

    null
  }

  private fun block(scopeType: ScopeType = ScopeType.Global): MutableList<Stmt> {
    val stmts = mutableListOf<Stmt>()

    while (!check(TokenType.RightBrace) && !isAtEnd) {
      stmts += statement(scopeType)
    }

    consume(TokenType.RightBrace) ?: throw error(expecting(end("block")))

    return stmts
  }

  private fun initializer(): Expr {
    if (!match(TokenType.Equal))
      throw error(expecting("initializer"))

    val initializer = expression()

    consume(TokenType.Semicolon) ?: throw error(expecting(TokenType.Semicolon))

    return initializer
  }

  @OptIn(ExperimentalStdlibApi::class)
  private fun typeDeclaration(): Stmt {
    consume(TokenType.Struct) ?: throw error(expecting(TokenType.Struct))

    val name = consume(TokenType.Identifier) ?: throw error(expecting("struct name"))

    val fields: List<Token> = when {
      match(TokenType.Semicolon) -> listOf()
      match(TokenType.LeftParen) -> buildList {
        if (!check(TokenType.RightParen))
          do {
            if (size >= MAX_ARGS) {
              error(MAX_ARGS_ERROR_MESSAGE).report()
            } else {
              add(consume(TokenType.Identifier) ?: throw error(expecting("parameter's name")))
            }
          } while (match(TokenType.Comma))

        consume(TokenType.RightParen) ?: throw error(expecting(end("fields")))
      }
      else -> throw error(expecting(TokenType.Semicolon))
    }

    return Stmt.TypeDef.Struct(name, fields)
  }

  private fun valDeclaration(): Stmt {
    val name = consume(TokenType.Identifier)
      ?: throw error(expecting("declaration name"))

    return Stmt.ValDecl(name, initializer())
  }

  private fun varDeclaration(): Stmt {
    val name = consume(TokenType.Identifier)
      ?: throw error(expecting("declaration name"))

    return Stmt.VarDecl(name, initializer())
  }

  private fun statement(scopeType: ScopeType = ScopeType.Global): Stmt {
    return when {
      match(TokenType.Return) -> when (scopeType) {
        ScopeType.Global -> throw error(notExpecting(TokenType.Return), token = previous())
        ScopeType.Func -> returnStatement()
      }
      match(TokenType.Val) -> valDeclaration()
      match(TokenType.Var) -> varDeclaration()
      match(TokenType.Typedef) -> typeDeclaration()
      match(TokenType.While) -> whileStatement()
      match(TokenType.LeftBrace) -> Stmt.Block(block())
      match(TokenType.If) -> Stmt.ExprStmt(ifExpr(IfType.If))
      match(TokenType.Func) -> Stmt.ExprStmt(funcExpr(FuncType.Func))

      else -> exprStatement()
    }
  }

  private fun returnStatement(): Stmt {
    val expression = if (!check(TokenType.Semicolon)) {
      expression()
    } else Expr.Literal(Unit) // returns unit if hasn't value

    consume(TokenType.Semicolon) ?: throw error(expecting(TokenType.Semicolon))

    return Stmt.ReturnStmt(expression)
  }

  private fun whileStatement(): Stmt {
    val condition = expression()

    if (!match(TokenType.LeftBrace))
      throw error(expecting(start("while body")))

    return Stmt.WhileStmt(condition, block())
  }

  private fun exprStatement(): Stmt {
    val expr = expression()

    consume(TokenType.Semicolon) ?: throw error(expecting(TokenType.Semicolon))

    return Stmt.ExprStmt(expr)
  }

  // expressions
  @OptIn(ExperimentalStdlibApi::class)
  private fun expression(): Expr {
    if (match(TokenType.If)) return ifExpr(IfType.Anonymous)
    if (match(TokenType.Func)) return funcExpr(FuncType.Anonymous)

    val expr = or()

    if (match(TokenType.Equal)) {
      val equals = previous()
      val value = assignment()

      if (expr is Expr.Var) {
        val (name) = expr

        return Expr.Assign(name, value)
      } else if (expr is Expr.Get) {
        return Expr.Set(expr.receiver, expr.name, value)
      }

      // we report the error but don't throw
      // to enter in panic mode and synchronize
      error(INVALID_RIGHT_ASSOCIATIVE_ERROR_MESSAGE, token = equals)
        .report()
    }

    return expr
  }

  private enum class IfType { Anonymous, If }

  private fun ifExpr(type: IfType): Expr {
    val condition = expression()

    if (!match(TokenType.LeftBrace))
      throw error(expecting(start("if body")))

    val mainBranch = block()
    val elseBranch = if (match(TokenType.Else))
      if (match(TokenType.LeftBrace))
        block()
      else throw error(expecting(start("else body")))
    else null

    if (type == IfType.Anonymous && elseBranch == null) {
      throw error(expecting("else body on local if"))
    }

    return Expr.IfExpr(condition, mainBranch, elseBranch)
  }

  private enum class FuncType { Anonymous, Func }

  @OptIn(ExperimentalStdlibApi::class)
  private fun funcExpr(type: FuncType, modifiers: List<TokenType> = emptyList()): Expr {
    fun Token?.orThrow() = this ?: throw error(expecting("function's name"))

    val name = consume(TokenType.Identifier)

    if (match(TokenType.Identifier)) {
      return extensionFuncExpr(name.orThrow(), type, modifiers)
    } else {
      consume(TokenType.LeftParen) ?: throw error(expecting(start("arguments")))
    }


    val parameters = parameters()
    val body = if (TokenType.External !in modifiers) funcBody(type) else {
      return Expr.NativeFunc(name.orThrow(), parameters)
        .also {
          requireSemicolon()
        }
    }

    return when (type) {
      FuncType.Anonymous -> Expr.AnonymousFunc(parameters, body)
      FuncType.Func -> Expr.Func(name.orThrow(), parameters, body)
    }
  }

  private fun extensionFuncExpr(receiver: Token, type: FuncType, modifiers: List<TokenType> = emptyList()): Expr {
    val name = previous()

    consume(TokenType.LeftParen) ?: throw error(expecting(start("arguments")))

    val parameters = parameters()
    val body = if (TokenType.External !in modifiers) funcBody(type) else {
      return Expr.NativeFunc(name, parameters).also {
        requireSemicolon()
      }
    }

    return Expr.ExtensionFunc(receiver, name, parameters, body)
  }

  private fun funcBody(type: FuncType): List<Stmt> {
    return when {
      consume(TokenType.LeftBrace) != null -> block(ScopeType.Func)
      consume(TokenType.Equal) != null -> listOf(Stmt.ReturnStmt(expression())).also {
        if (type == FuncType.Func) requireSemicolon()
      }
      else -> throw error(expecting(start("function's body")))
    }
  }

  @OptIn(ExperimentalStdlibApi::class)
  private fun parameters(): List<Token> {
    val parameters = buildList {
      if (!check(TokenType.RightParen))
        do {
          if (size >= MAX_ARGS) {
            error(MAX_ARGS_ERROR_MESSAGE).report()
          } else {
            add(consume(TokenType.Identifier) ?: throw error(expecting("parameter's name")))
          }
        } while (match(TokenType.Comma))
    }

    consume(TokenType.RightParen) ?: throw error(expecting(end("arguments")))

    return parameters
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

    while (true) expr = when {
      match(TokenType.LeftParen) -> finishCall(expr)
      match(TokenType.Dot) -> {
        val name = consume(TokenType.Identifier)
          ?: throw error(expecting("identifier after ${TokenType.Dot}"))

        Expr.Get(expr, name)
      }
      else -> break
    }

    return expr
  }

  @OptIn(ExperimentalStdlibApi::class)
  private fun finishCall(callee: Expr): Expr {
    val arguments = buildList {
      if (!check(TokenType.RightParen)) {
        do {
          if (size >= MAX_ARGS) {
            error(MAX_ARGS_ERROR_MESSAGE).report()
          } else {
            add(expression())
          }
        } while (match(TokenType.Comma))
      }
    }

    consume(TokenType.RightParen) ?: throw error(expecting(TokenType.RightParen))

    return Expr.Call(callee, arguments)
  }

  private fun primary(): Expr = when {
    match(TokenType.False) -> Expr.Literal(false)
    match(TokenType.True) -> Expr.Literal(true)

    match(TokenType.This) -> Expr.ThisExpr(previous())

    match(TokenType.Double, TokenType.String, TokenType.Int) -> Expr.Literal(previous().let {
      it.literal ?: ""
    }) // TODO: fixme

    match(TokenType.LeftParen) -> Expr.Grouping(expression().also {
      consume(TokenType.RightParen) ?: throw error(expecting(TokenType.RightParen))
    })

    match(TokenType.Identifier) -> Expr.Var(previous())

    else -> throw error()
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

  private fun requireSemicolon(): Token = consume(TokenType.Semicolon)
    ?: throw error(expecting(TokenType.Semicolon))

  private fun error(message: String = "", token: Token = peek()) = ParseError(token, message)

  private fun expecting(type: Any) = "expecting $type"
  private fun notExpecting(type: Any) = "not expecting $type"
  private fun start(type: Any) = "start of $type"
  private fun end(type: Any) = "end of $type"
}
