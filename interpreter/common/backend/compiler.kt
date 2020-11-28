package com.lorenzoog.kofl.interpreter.backend

import com.lorenzoog.kofl.frontend.Expr
import com.lorenzoog.kofl.frontend.Stack
import com.lorenzoog.kofl.frontend.Stmt
import com.lorenzoog.kofl.frontend.Token
import com.lorenzoog.kofl.interpreter.exceptions.KoflCompileTimeException
import com.lorenzoog.kofl.interpreter.typing.KoflType
import com.lorenzoog.kofl.interpreter.typing.TypeContainer
import com.lorenzoog.kofl.interpreter.typing.TypeValidator

class Compiler(private val container: Stack<TypeContainer>) : Expr.Visitor<Descriptor>, Stmt.Visitor<Descriptor> {
  private val emitter = Emitter()
  private val validator = TypeValidator(container)

  override fun visitStmts(stmts: List<Stmt>): List<Descriptor> {
    return emitter.compiled()
  }

  override fun visitAssignExpr(expr: Expr.Assign): Descriptor {
    val type = validator.visitAssignExpr(expr)
    val name = expr.name.lexeme
    val value = visitExpr(expr.value)

    return emitter.emit(AssignDescriptor(name, value, type))
  }

  override fun visitBinaryExpr(expr: Expr.Binary): Descriptor {
    val type = validator.visitBinaryExpr(expr)
    val op = expr.op.type
    val left = visitExpr(expr.left)
    val right = visitExpr(expr.right)

    return emitter.emit(BinaryDescriptor(left, op, right, type))
  }

  override fun visitLogicalExpr(expr: Expr.Logical): Descriptor {
    val type = validator.visitLogicalExpr(expr)
    val op = expr.op.type
    val left = visitExpr(expr.left)
    val right = visitExpr(expr.right)

    return emitter.emit(LogicalDescriptor(left, op, right, type))
  }

  override fun visitGroupingExpr(expr: Expr.Grouping): Descriptor {
    return visitExpr(expr.expr)
  }

  override fun visitLiteralExpr(expr: Expr.Literal): Descriptor {
    val type = validator.visitLiteralExpr(expr)

    return emitter.emit(ConstDescriptor(expr.value, type))
  }

  override fun visitUnaryExpr(expr: Expr.Unary): Descriptor {
    val type = validator.visitUnaryExpr(expr)
    val op = expr.op.type
    val right = visitExpr(expr.right)

    return emitter.emit(UnaryDescriptor(op, right, type))
  }

  override fun visitVarExpr(expr: Expr.Var): Descriptor {
    return emitter.emit(GlobalVarDescriptor(expr.name.lexeme))
  }

  override fun visitCallExpr(expr: Expr.Call): Descriptor {
    val type = validator.visitCallExpr(expr)
    val callee = visitExpr(expr.calle)
    val arguments =
      expr.arguments.mapKeys { (name) -> name?.lexeme.toString() } // todo find the called method and get parameters' names
        .mapValues { (_, value) ->
          visitExpr(value)
        }

    return emitter.emit(CallDescriptor(callee, arguments, type))
  }

  override fun visitGetExpr(expr: Expr.Get): Descriptor {
    val receiver = visitExpr(expr.receiver)
    val name = expr.name.lexeme

    return emitter.emit(GetDescriptor(receiver, name))
  }

  override fun visitSetExpr(expr: Expr.Set): Descriptor {
    val receiver = visitExpr(expr.receiver)
    val value = visitExpr(expr.value)
    val name = expr.name.lexeme

    return emitter.emit(SetDescriptor(receiver, name, value))
  }

  override fun visitThisExpr(expr: Expr.ThisExpr): Descriptor {
    return emitter.emit(ThisDescriptor())
  }

  override fun visitIfExpr(expr: Expr.IfExpr): Descriptor {
    val type = validator.visitIfExpr(expr)
    val condition = visitExpr(expr.condition)
    val then = visitStmts(expr.thenBranch)
    val orElse = visitStmts(expr.elseBranch ?: emptyList())

    return IfDescriptor(condition, then, orElse, type)
  }

  override fun visitFuncExpr(expr: Expr.CommonFunc): Descriptor {
    val name = expr.name.lexeme
    val parameters = typedParameters(expr.parameters)
    val returnType = typedReturn(expr.returnType)

    return FunctionDescriptor(name, parameters, returnType, scoped {
      visitStmts(expr.body)
    })
  }

  override fun visitExtensionFuncExpr(expr: Expr.ExtensionFunc): Descriptor {
    val name = expr.name.lexeme
    val parameters = mapOf("this" to findType(expr.receiver.lexeme)) + typedParameters(expr.parameters)
    val returnType = typedReturn(expr.returnType)

    return FunctionDescriptor(name, parameters, returnType, scoped {
      visitStmts(expr.body)
    })
  }

  override fun visitAnonymousFuncExpr(expr: Expr.AnonymousFunc): Descriptor {
    val parameters = typedParameters(expr.parameters)
    val returnType = typedReturn(expr.returnType)

    return LocalFunctionDescriptor(parameters, returnType, scoped {
      visitStmts(expr.body)
    })
  }

  override fun visitNativeFuncExpr(expr: Expr.NativeFunc): Descriptor {
    val name = expr.name.lexeme
    val parameters = typedParameters(expr.parameters)
    val returnType = typedReturn(expr.returnType)

    return NativeFunctionDescriptor(name, parameters, returnType, name)
  }

  override fun visitExprStmt(stmt: Stmt.ExprStmt): Descriptor {
    return visitExpr(stmt.expr)
  }

  override fun visitBlockStmt(stmt: Stmt.Block): Descriptor {
    return BlockDescriptor(scoped {
      visitStmts(stmt.body)
    })
  }

  override fun visitWhileStmt(stmt: Stmt.WhileStmt): Descriptor {
    val condition = visitExpr(stmt.condition)

    return WhileDescriptor(condition, scoped {
      visitStmts(stmt.body)
    })
  }

  override fun visitReturnStmt(stmt: Stmt.ReturnStmt): Descriptor {
    val type = validator.visitReturnStmt(stmt)
    val value = visitExpr(stmt.expr)

    return ReturnDescriptor(value, type)
  }

  override fun visitValDeclStmt(stmt: Stmt.ValDecl): Descriptor {
    val type = validator.visitValDeclStmt(stmt)
    val name = stmt.name.lexeme
    val value = visitExpr(stmt.value)

    return ValDescriptor(name, value, type)
  }

  override fun visitVarDeclStmt(stmt: Stmt.VarDecl): Descriptor {
    val type = validator.visitVarDeclStmt(stmt)
    val name = stmt.name.lexeme
    val value = visitExpr(stmt.value)

    return VarDescriptor(name, value, type)
  }

  override fun visitStructTypedefStmt(stmt: Stmt.Type.Class): Descriptor {
    val name = stmt.name.lexeme
    val inherits = emptyList<KoflType>()
    val parameters = typedParameters(stmt.parameters)

    return ClassDescriptor(name, inherits, parameters)
  }

  private inline fun typedReturn(name: Token?): KoflType {
    return name?.lexeme
      ?.let { typeName -> findType(typeName) }
      ?: findType("Unit")
  }

  private inline fun typedParameters(parameters: Map<Token, Token>): Map<String, KoflType> {
    return parameters.mapKeys { (name) -> name.lexeme }.mapValues { (_, typeName) ->
      findType(typeName.lexeme)
    }
  }

  private inline fun findType(name: String): KoflType {
    return container.peek().lookupType(name) ?: throw KoflCompileTimeException.UnresolvedVar(name)
  }

  private inline fun <R> scoped(body: () -> R): R {
    container.push(TypeContainer(container.peek()))
    val value = body()
    container.pop()

    return value
  }
}