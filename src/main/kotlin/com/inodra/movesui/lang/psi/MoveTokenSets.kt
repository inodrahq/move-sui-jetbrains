package com.inodra.movesui.lang.psi

import com.inodra.movesui.lang.psi.MoveTypes.*
import com.intellij.psi.tree.TokenSet

object MoveTokenSets {
    @JvmField val COMMENTS = TokenSet.create(LINE_COMMENT, BLOCK_COMMENT, DOC_COMMENT)
    @JvmField val DOC_COMMENTS = TokenSet.create(DOC_COMMENT)
    @JvmField val STRINGS = TokenSet.create(BYTE_STRING_LITERAL, HEX_STRING_LITERAL, STRING_LITERAL)
    @JvmField val LITERALS = TokenSet.create(INTEGER_LITERAL, BYTE_STRING_LITERAL, HEX_STRING_LITERAL, STRING_LITERAL, TRUE_KW, FALSE_KW)

    @JvmField val RESERVED_KEYWORDS = TokenSet.create(
        ABORT_KW, ACQUIRES_KW, AS_KW, BREAK_KW, CONST_KW, CONTINUE_KW, COPY_KW, ELSE_KW, FALSE_KW, FUN_KW,
        FRIEND_KW, IF_KW, LET_KW, LOOP_KW, MODULE_KW, MOVE_KW, NATIVE_KW, PUBLIC_KW, RETURN_KW, STRUCT_KW,
        TRUE_KW, USE_KW, WHILE_KW, MUT_KW, ENUM_KW, MATCH_KW, TYPE_KW, FOR_KW,
    )
    @JvmField val CONTEXTUAL_KEYWORDS = TokenSet.create(
        ADDRESS_KW, ENTRY_KW, HAS_KW, MACRO_KW, PACKAGE_KW, PHANTOM_KW, SCRIPT_KW, SPEC_KW,
    )
    @JvmField val KEYWORDS = TokenSet.orSet(RESERVED_KEYWORDS, CONTEXTUAL_KEYWORDS)

    /** Tokens that can act as a name (identifier or contextual keyword). */
    @JvmField val IDENTIFIERS = TokenSet.orSet(TokenSet.create(IDENTIFIER, SYNTAX_IDENTIFIER), CONTEXTUAL_KEYWORDS)

    @JvmField val OPERATORS = TokenSet.create(
        EQ, EQ_EQ, NOT_EQ, LT, GT, LT_EQ, LT_LT, AND_AND, OR_OR, AND, OR, XOR, EXCL, PLUS, MINUS, MUL, DIV, PERCENT,
        FAT_ARROW, ARROW, IMPLIES, IFF, DOT_DOT,
    )
    @JvmField val BRACES = TokenSet.create(LBRACE, RBRACE)
    @JvmField val BRACKETS = TokenSet.create(LBRACK, RBRACK)
    @JvmField val PARENS = TokenSet.create(LPAREN, RPAREN)
}
