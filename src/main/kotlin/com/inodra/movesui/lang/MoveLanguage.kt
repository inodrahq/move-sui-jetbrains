package com.inodra.movesui.lang

import com.intellij.lang.Language

object MoveLanguage : Language("Move", "text/x-move") {
    private fun readResolve(): Any = MoveLanguage
    override fun getDisplayName() = "Move"
    override fun isCaseSensitive() = true
}
