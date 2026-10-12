package com.inodra.movesui.ide.editor

import com.inodra.movesui.lang.psi.MoveTypes.*
import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

class MoveBraceMatcher : PairedBraceMatcher {
    override fun getPairs() = PAIRS
    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?) = true
    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int) = openingBraceOffset

    companion object {
        private val PAIRS = arrayOf(
            BracePair(LBRACE, RBRACE, true),
            BracePair(LPAREN, RPAREN, false),
            BracePair(LBRACK, RBRACK, false),
            BracePair(LT, GT, false),
        )
    }
}
