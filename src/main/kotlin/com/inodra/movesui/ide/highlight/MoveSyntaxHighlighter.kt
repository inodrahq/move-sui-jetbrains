package com.inodra.movesui.ide.highlight

import com.inodra.movesui.lang.lexer.MoveLexer
import com.inodra.movesui.lang.psi.MoveTokenSets
import com.inodra.movesui.lang.psi.MoveTypes.*
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class MoveSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = MoveLexer()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> =
        pack(colorFor(tokenType)?.textAttributesKey)

    private fun colorFor(t: IElementType?): MoveColor? = when (t) {
        null -> null
        in MoveTokenSets.RESERVED_KEYWORDS -> MoveColor.KEYWORD
        IDENTIFIER -> MoveColor.IDENTIFIER
        SYNTAX_IDENTIFIER -> MoveColor.MACRO_PARAMETER
        QUOTE_IDENTIFIER -> MoveColor.LABEL
        INTEGER_LITERAL -> MoveColor.NUMBER
        in MoveTokenSets.STRINGS -> MoveColor.STRING
        LINE_COMMENT -> MoveColor.LINE_COMMENT
        BLOCK_COMMENT -> MoveColor.BLOCK_COMMENT
        DOC_COMMENT -> MoveColor.DOC_COMMENT
        in MoveTokenSets.BRACES -> MoveColor.BRACES
        in MoveTokenSets.BRACKETS -> MoveColor.BRACKETS
        in MoveTokenSets.PARENS -> MoveColor.PARENTHESES
        SEMICOLON -> MoveColor.SEMICOLON
        COMMA -> MoveColor.COMMA
        DOT, COLON_COLON -> MoveColor.DOT
        in MoveTokenSets.OPERATORS -> MoveColor.OPERATORS
        HASH -> MoveColor.ATTRIBUTE
        AT -> MoveColor.ADDRESS
        TokenType.BAD_CHARACTER -> MoveColor.BAD_CHARACTER
        else -> null
    }
}

class MoveSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?) = MoveSyntaxHighlighter()
}
