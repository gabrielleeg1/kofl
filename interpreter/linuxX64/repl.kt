@file:OptIn(ExperimentalUnsignedTypes::class)
package com.lorenzoog.kofl.interpreter

import kotlinx.cinterop.*
import platform.posix.*

// save copy of original termios to apply again to terminal
// when the user quit the program
val orig_termios = nativeHeap.alloc<termios>()

@OptIn(ExperimentalUnsignedTypes::class)
internal fun MemScope.enterCBreakMode() {
  // will trigger [quitRawMode] when program exits
  atexit(staticCFunction(::quitRawMode))

  val raw = alloc<termios>()

  // put all [STDIN_FILENO] attrs into [raw]
  tcgetattr(STDIN_FILENO, raw.ptr)

  raw.reinterpret<termios>().apply {
    c_iflag = c_iflag and ICRNL.toUInt().inv()
    c_lflag = c_lflag and (
      ECHO.toUInt()
        or ICANON.toUInt()
        or IEXTEN.toUInt()
        or ISIG.toUInt()
      ).inv()
  }

  tcsetattr(STDIN_FILENO, TCSAFLUSH, raw.ptr)
}

internal fun quitRawMode() {
  tcsetattr(STDIN_FILENO, TCSAFLUSH, orig_termios.ptr)
}

internal fun clearScreen() {
  if (getenv("TERM") != null) {
    // clear screen if it is in a terminal
    system("clear")
  }
}

// TODO: handle arrow keys
@OptIn(ExperimentalUnsignedTypes::class)
internal fun repl(): Unit = memScoped {
  clearScreen()
//  enterCBreakMode()

  println("KOFL's repl. Type :quit to exit the program. Enjoy it 😃")
  println()

  while (true) {
    print("kofl>")

    when (val line = readLine().orEmpty()) {
      ":quit" -> exit(0)
      ":clear" -> clearScreen()
      else -> try {
        eval(line) // interpret and run the provided code in the line
      } catch (error: KoflError) {
        error.report() // just report error to dont crash program
      }
    }

    println()
  }
}

fun die(msg: String) {
  printerr(msg + ENTER_CHAR)
  exit(1)
}