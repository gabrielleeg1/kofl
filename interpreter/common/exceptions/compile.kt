package com.lorenzoog.kofl.interpreter.exceptions

import com.lorenzoog.kofl.frontend.KoflException
import com.lorenzoog.kofl.interpreter.typing.KoflType

sealed class KoflCompileException(message: String) : KoflException("compile", message) {
  class UnresolvedVar(name: String) : KoflCompileException("variable $name not found")
  class UnresolvedParameter(index: Int) : KoflCompileException("unresolved parameter $index")
  class MissingReturn : KoflCompileException("missing return function body")
  class AlreadyResolvedVar(name: String) : KoflCompileException("already resolved var $name")
  class InvalidType(value: Any) : KoflCompileException("invalid kofl type in $value")
  class UnexpectedType(current: Any, expected: Any) : KoflCompileException("excepted $expected but got $current")
  class ClassMissingName(definition: KoflType.Class) : KoflCompileException("$definition missing name")
}
