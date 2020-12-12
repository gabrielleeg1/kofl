@file:OptIn(ExperimentalUnsignedTypes::class)

package com.lorenzoog.kofl.vm

import com.lorenzoog.kofl.frontend.Expr
import com.lorenzoog.kofl.frontend.Stmt
import com.lorenzoog.kofl.frontend.TokenType
import com.lorenzoog.kofl.interpreter.internal.*
import kotlinx.cinterop.*
import platform.posix.UINT8_MAX

open class CompilationException(message: String) : RuntimeException(message)

class Compiler : Expr.Visitor<Unit>, Stmt.Visitor<Unit> {
  private val heap = MemScope()

  // chunk index
  private var ci = 0
  private var chunks = arrayOf(chunk_create(0, 0))

  fun compile(stmts: List<Stmt>): Array<CPointer<chunk_t>?> {
    visitStmts(stmts)
    endCompiler()

    return chunks
  }

  fun compile(exprs: List<Expr>): Array<CPointer<chunk_t>?> {
    visitExprs(exprs)
    endCompiler()

    return chunks
  }

  override fun visitAssignExpr(expr: Expr.Assign) {
    TODO("Not yet implemented")
  }

  override fun visitBinaryExpr(expr: Expr.Binary) {
    visitExpr(expr.left)
    visitExpr(expr.right)

    when (expr.op.type) {
      TokenType.Plus -> emit(opcode.OP_SUM, expr.line) // TODO: compile OpCode.Concat when have typechecking
      TokenType.Minus -> emit(opcode.OP_SUB, expr.line)
      TokenType.Slash -> emit(opcode.OP_DIV, expr.line)
      TokenType.Star -> emit(opcode.OP_MULT, expr.line)
      else -> Unit
    }
  }

  override fun visitLogicalExpr(expr: Expr.Logical) {
    TODO("Not yet implemented")
  }

  override fun visitGroupingExpr(expr: Expr.Grouping) {
    visitExpr(expr.expr)
  }

  override fun visitLiteralExpr(expr: Expr.Literal) {
    when (val value = expr.value) {
      is Int -> emit(opcode.OP_CONST, makeConst(value), expr.line)
      is Number -> emit(opcode.OP_CONST, makeConst(value.toDouble()), expr.line)
      is Boolean -> emit(opcode.OP_CONST, makeConst(value), expr.line)
      is String -> emit(opcode.OP_CONST, makeConst(value), expr.line)
    }
  }

  override fun visitUnaryExpr(expr: Expr.Unary) {
    visitExpr(expr.right)

    when (expr.op.type) {
      TokenType.Minus -> emit(opcode.OP_NEGATE, expr.line)
      TokenType.Bang -> emit(opcode.OP_NOT, expr.line)
      else -> {
      }
    }
  }

  override fun visitVarExpr(expr: Expr.Var) {
    emit(opcode.OP_CONST, makeConst(expr.name.lexeme), expr.line)
    emit(opcode.OP_ACCESS_GLOBAL, expr.line)
  }

  override fun visitCallExpr(expr: Expr.Call) {
    TODO("Not yet implemented")
  }

  override fun visitGetExpr(expr: Expr.Get) {
    TODO("Not yet implemented")
  }

  override fun visitSetExpr(expr: Expr.Set) {
    TODO("Not yet implemented")
  }

  override fun visitFuncExpr(expr: Expr.CommonFunc) {
    TODO("Not yet implemented")
  }

  override fun visitThisExpr(expr: Expr.ThisExpr) {
    TODO("Not yet implemented")
  }

  override fun visitExtensionFuncExpr(expr: Expr.ExtensionFunc) {
    TODO("Not yet implemented")
  }

  override fun visitAnonymousFuncExpr(expr: Expr.AnonymousFunc) {
    TODO("Not yet implemented")
  }

  override fun visitNativeFuncExpr(expr: Expr.NativeFunc) {
    TODO("Not yet implemented")
  }

  override fun visitIfExpr(expr: Expr.IfExpr) {
    TODO("Not yet implemented")
  }

  override fun visitExprStmt(stmt: Stmt.ExprStmt) {
    visitExpr(stmt.expr)
  }

  override fun visitBlockStmt(stmt: Stmt.Block) {
    TODO("Not yet implemented")
  }

  override fun visitWhileStmt(stmt: Stmt.WhileStmt) {
    TODO("Not yet implemented")
  }

  override fun visitReturnStmt(stmt: Stmt.ReturnStmt) {
    TODO("Not yet implemented")
  }

  override fun visitValDeclStmt(stmt: Stmt.ValDecl) {
    emit(opcode.OP_CONCAT, makeConst(stmt.name.lexeme), stmt.line)
    visitExpr(stmt.value)
    emit(opcode.OP_STORE_GLOBAL, stmt.line)
  }

  override fun visitVarDeclStmt(stmt: Stmt.VarDecl) {
    TODO("Not yet implemented")
  }

  override fun visitClassTypeStmt(stmt: Stmt.Type.Class) {
    TODO("Not yet implemented")
  }

  private fun makeConst(value: Boolean): UInt = makeConst(cValue {
    bool_ = value
  })

  private fun makeConst(value: Double): UInt = makeConst(cValue {
    double_ = value
  })

  private fun makeConst(value: Int): UInt = makeConst(cValue {
    int_ = value
  })

  private fun makeConst(value: String): UInt = makeConst(cValue {
    string_ = value.cstr.placeTo(heap)
  })

  private fun makeConst(value: CValue<value>): UInt {
    val const = chunk_write_const(chunk(), value)

    if (const > UINT8_MAX) {
      error("TOO LONG CONST") // TODO: make a error
    }

    return const.toUInt()
  }

  private fun chunk(): CPointer<chunk_t>? {
    return chunks[ci]
  }

  private fun endCompiler() {
    emit(opcode.OP_RETURN, -1)
  }

  private fun emit(op: opcode, line: Int) {
    chunk_write(chunk(), op.value, line)
  }

  private fun emit(op: UInt, line: Int) {
    chunk_write(chunk(), op, line)
  }

  private fun emit(op: opcode, value: UInt, line: Int) {
    emit(op.value, line)
    emit(value, line)
  }
}