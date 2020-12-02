package com.lorenzoog.kofl.interpreter.runtime

import com.lorenzoog.kofl.interpreter.backend.*
import com.lorenzoog.kofl.interpreter.exceptions.KoflCompileException
import com.lorenzoog.kofl.interpreter.typing.KoflType

class ClassBuilder internal constructor(private val definition: KoflType.Class) {
  private val constructors = mutableListOf<KoflObject.Callable>()
  private val fields = mutableMapOf<String, Value>()
  private val functions = mutableMapOf<String, List<KoflObject.Callable>>()

  fun function(descriptor: CallableDescriptor, function: KoflObject.Callable) {
  }

  fun constructor(function: KoflObject.Callable) {
    constructors += function
  }

  fun constructor(parameters: Map<String, KoflType>, function: KoflNativeCallable) {
    val descriptor = NativeFunctionDescriptor(
      name = definition.name ?: "<no name provided>",
      parameters = parameters,
      returnType = definition,
      nativeCall = "${definition.name}.<init>"
    )

    constructors += KoflObject.Callable.LocalNativeFunction(function, descriptor)
  }

  fun build(): KoflObject.Class.KoflClass = KoflObject.Class.KoflClass(definition, constructors, functions)
}

class SingletonBuilder internal constructor() {

}

fun Environment.createClass(
  definition: KoflType.Class,
  builder: ClassBuilder.() -> Unit = {}
): KoflObject.Class.KoflClass {
  val name = definition.name ?: throw KoflCompileException.ClassMissingName(definition)
  val koflClass = ClassBuilder(definition).apply(builder).build()

  koflClass.constructors.forEachIndexed { index, constructor ->
    declareFunction("$name-$index", constructor)
  }

  return koflClass
}

fun Environment.createSingleton(definition: KoflType.Class, builder: ClassBuilder.() -> Unit = {}) {

}
