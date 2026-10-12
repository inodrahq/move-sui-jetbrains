package com.inodra.movesui.lang.parser

import com.inodra.movesui.lang.psi.MoveTokenSets
import com.inodra.movesui.lang.psi.MoveTypes.*
import com.intellij.lang.PsiBuilder
import com.intellij.lang.parser.GeneratedParserUtilBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

@Suppress("UNUSED_PARAMETER")
object MoveParserUtil : GeneratedParserUtilBase() {

    private fun PsiBuilder.hasWhitespaceBefore(): Boolean {
        val prev = rawLookup(-1) ?: return false
        return prev == TokenType.WHITE_SPACE || MoveTokenSets.COMMENTS.contains(prev)
    }

    private fun PsiBuilder.isAdjacent(offsetAfter: Int): Boolean =
        rawLookup(offsetAfter) != TokenType.WHITE_SPACE && rawTokenTypeStart(offsetAfter) == rawTokenTypeStart(offsetAfter - 1) + 1

    /** Succeeds (without consuming) when the current token directly follows the previous one. */
    @JvmStatic
    fun noWhitespaceBefore(b: PsiBuilder, level: Int): Boolean = !b.hasWhitespaceBefore()

    /** `>` `>` with no whitespace in between, consumed as a shift-right operator. */
    @JvmStatic
    fun gtGt(b: PsiBuilder, level: Int): Boolean = adjacentPair(b, GT, GT)

    /** `>` `=` with no whitespace in between, consumed as a greater-or-equal operator. */
    @JvmStatic
    fun gtEq(b: PsiBuilder, level: Int): Boolean = adjacentPair(b, GT, EQ)

    /** A lone `>`: not the first half of an adjacent `>>` or `>=`. */
    @JvmStatic
    fun gtSingle(b: PsiBuilder, level: Int): Boolean {
        if (b.tokenType != GT) return false
        val next = b.rawLookup(1)
        if ((next == GT || next == EQ) && b.rawTokenTypeStart(1) == b.rawTokenTypeStart(0) + 1) return false
        b.advanceLexer()
        return true
    }

    private fun adjacentPair(b: PsiBuilder, first: IElementType, second: IElementType): Boolean {
        if (b.tokenType != first || b.rawLookup(1) != second) return false
        if (b.rawTokenTypeStart(1) != b.rawTokenTypeStart(0) + 1) return false
        val m = b.mark()
        b.advanceLexer()
        b.advanceLexer()
        m.collapse(if (second == GT) GT_GT else GT_EQ)
        return true
    }

    /** The `vector` identifier when it starts a vector literal: `vector[...]` or `vector<T>[...]`. */
    @JvmStatic
    fun vectorIdent(b: PsiBuilder, level: Int): Boolean {
        if (b.tokenType != IDENTIFIER || b.tokenText != "vector") return false
        val next = b.lookAhead(1)
        if (next != LBRACK && next != LT) return false
        b.advanceLexer()
        return true
    }

    /** Block-like expressions (`if`, `loop`, `{}` …) may end a statement without a semicolon. */
    @JvmStatic
    fun afterBlockLike(b: PsiBuilder, level: Int): Boolean {
        var i = -1
        while (true) {
            val t = b.rawLookup(i) ?: return false
            if (t == TokenType.WHITE_SPACE || MoveTokenSets.COMMENTS.contains(t)) { i--; continue }
            return t == RBRACE
        }
    }

    /** Legacy `spec` blocks are not parsed; skip to the end of the balanced `{ }` (or `;`). */
    @JvmStatic
    fun skipSpecBlock(b: PsiBuilder, level: Int): Boolean {
        var depth = 0
        while (!b.eof()) {
            when (b.tokenType) {
                LBRACE -> depth++
                RBRACE -> {
                    depth--
                    if (depth <= 0) { b.advanceLexer(); return true }
                }
                SEMICOLON -> if (depth == 0) { b.advanceLexer(); return true }
            }
            b.advanceLexer()
        }
        return true
    }
}
