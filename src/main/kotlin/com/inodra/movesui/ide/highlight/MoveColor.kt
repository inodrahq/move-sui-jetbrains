package com.inodra.movesui.ide.highlight

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors as Default
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.options.colors.AttributesDescriptor

enum class MoveColor(humanName: String, default: TextAttributesKey?) {
    KEYWORD("Keyword", Default.KEYWORD),
    IDENTIFIER("Identifier", Default.IDENTIFIER),
    NUMBER("Literals//Number", Default.NUMBER),
    STRING("Literals//String", Default.STRING),
    VALID_ESCAPE("Literals//Valid escape", Default.VALID_STRING_ESCAPE),
    ADDRESS("Literals//Address", Default.CONSTANT),

    LINE_COMMENT("Comments//Line comment", Default.LINE_COMMENT),
    BLOCK_COMMENT("Comments//Block comment", Default.BLOCK_COMMENT),
    DOC_COMMENT("Comments//Doc comment", Default.DOC_COMMENT),

    BRACES("Braces and operators//Braces", Default.BRACES),
    BRACKETS("Braces and operators//Brackets", Default.BRACKETS),
    PARENTHESES("Braces and operators//Parentheses", Default.PARENTHESES),
    OPERATORS("Braces and operators//Operators", Default.OPERATION_SIGN),
    SEMICOLON("Braces and operators//Semicolon", Default.SEMICOLON),
    COMMA("Braces and operators//Comma", Default.COMMA),
    DOT("Braces and operators//Dot", Default.DOT),

    ATTRIBUTE("Attributes//Attribute", Default.METADATA),
    MODULE("Declarations//Module", Default.CLASS_NAME),
    FUNCTION("Declarations//Function", Default.FUNCTION_DECLARATION),
    MACRO("Declarations//Macro", Default.FUNCTION_DECLARATION),
    STRUCT("Declarations//Struct", Default.CLASS_NAME),
    ENUM("Declarations//Enum", Default.CLASS_NAME),
    ENUM_VARIANT("Declarations//Enum variant", Default.STATIC_FIELD),
    CONSTANT("Declarations//Constant", Default.CONSTANT),
    FIELD("Declarations//Field", Default.INSTANCE_FIELD),
    TYPE_PARAMETER("Declarations//Type parameter", Default.PARAMETER),
    PARAMETER("Declarations//Parameter", Default.PARAMETER),
    VARIABLE("Declarations//Local variable", Default.LOCAL_VARIABLE),
    LABEL("Declarations//Label", Default.LABEL),

    FUNCTION_CALL("References//Function call", Default.FUNCTION_CALL),
    MACRO_CALL("References//Macro call", Default.FUNCTION_CALL),
    METHOD_CALL("References//Method call", Default.FUNCTION_CALL),
    BUILTIN_TYPE("References//Builtin type", Default.KEYWORD),
    ABILITY("References//Ability", Default.KEYWORD),
    SELF_PARAMETER("References//Self", Default.KEYWORD),
    MACRO_PARAMETER("References//Macro parameter (\$x)", Default.PARAMETER),

    BAD_CHARACTER("Bad character", HighlighterColors.BAD_CHARACTER),
    ;

    val textAttributesKey: TextAttributesKey = TextAttributesKey.createTextAttributesKey("com.inodra.movesui.$name", default)
    val attributesDescriptor = AttributesDescriptor(humanName, textAttributesKey)
}
