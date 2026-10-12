package com.inodra.movesui.lang.lexer;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.TokenType;

import static com.inodra.movesui.lang.psi.MoveTypes.*;

%%

%{
  private int commentDepth = 0;
  private boolean commentIsDoc = false;

  public _MoveLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _MoveLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

%state IN_BLOCK_COMMENT

WHITE_SPACE=[ \t\n\x0B\f\r]+

LINE_DOC_COMMENT="///"[^/][^\r\n]* | "///"
LINE_COMMENT="//"[^\r\n]*

IDENT=[a-zA-Z_][a-zA-Z0-9_]*
SYNTAX_IDENT="$"{IDENT}
QUOTE_IDENT="'"{IDENT}
RESTRICTED_IDENT="`"[^`\r\n]*"`"

DEC=[0-9][0-9_]*
HEX=0[xX][0-9a-fA-F_]+
SUFFIX=u8|u16|u32|u64|u128|u256
NUMBER=({HEX}|{DEC}){SUFFIX}?

STRING_BODY=([^\"\\\r\n]|\\.)*
BYTE_STRING="b\"" {STRING_BODY} \"?
HEX_STRING="x\"" {STRING_BODY} \"?
STRING=\" {STRING_BODY} \"?

%%

<YYINITIAL> {
  {WHITE_SPACE}          { return TokenType.WHITE_SPACE; }

  "/**/"                 { return BLOCK_COMMENT; }
  "/**"                  { commentDepth = 1; commentIsDoc = true; yybegin(IN_BLOCK_COMMENT); }
  "/*"                   { commentDepth = 1; commentIsDoc = false; yybegin(IN_BLOCK_COMMENT); }
  "////"[^\r\n]*         { return LINE_COMMENT; }
  {LINE_DOC_COMMENT}     { return DOC_COMMENT; }
  {LINE_COMMENT}         { return LINE_COMMENT; }

  "abort"                { return ABORT_KW; }
  "acquires"             { return ACQUIRES_KW; }
  "as"                   { return AS_KW; }
  "break"                { return BREAK_KW; }
  "const"                { return CONST_KW; }
  "continue"             { return CONTINUE_KW; }
  "copy"                 { return COPY_KW; }
  "else"                 { return ELSE_KW; }
  "false"                { return FALSE_KW; }
  "fun"                  { return FUN_KW; }
  "friend"               { return FRIEND_KW; }
  "if"                   { return IF_KW; }
  "let"                  { return LET_KW; }
  "loop"                 { return LOOP_KW; }
  "module"               { return MODULE_KW; }
  "move"                 { return MOVE_KW; }
  "native"               { return NATIVE_KW; }
  "public"               { return PUBLIC_KW; }
  "return"               { return RETURN_KW; }
  "struct"               { return STRUCT_KW; }
  "true"                 { return TRUE_KW; }
  "use"                  { return USE_KW; }
  "while"                { return WHILE_KW; }
  "mut"                  { return MUT_KW; }
  "enum"                 { return ENUM_KW; }
  "match"                { return MATCH_KW; }
  "type"                 { return TYPE_KW; }
  "for"                  { return FOR_KW; }

  // Contextual keywords: the grammar also accepts these as identifiers.
  "address"              { return ADDRESS_KW; }
  "entry"                { return ENTRY_KW; }
  "has"                  { return HAS_KW; }
  "macro"                { return MACRO_KW; }
  "package"              { return PACKAGE_KW; }
  "phantom"              { return PHANTOM_KW; }
  "script"               { return SCRIPT_KW; }
  "spec"                 { return SPEC_KW; }

  {BYTE_STRING}          { return BYTE_STRING_LITERAL; }
  {HEX_STRING}           { return HEX_STRING_LITERAL; }
  {STRING}               { return STRING_LITERAL; }
  {NUMBER}               { return INTEGER_LITERAL; }

  "_"                    { return UNDERSCORE; }
  {SYNTAX_IDENT}         { return SYNTAX_IDENTIFIER; }
  {QUOTE_IDENT}          { return QUOTE_IDENTIFIER; }
  {RESTRICTED_IDENT}     { return IDENTIFIER; }
  {IDENT}                { return IDENTIFIER; }

  "<==>"                 { return IFF; }
  "==>"                  { return IMPLIES; }
  "=="                   { return EQ_EQ; }
  "!="                   { return NOT_EQ; }
  "<="                   { return LT_EQ; }
  "=>"                   { return FAT_ARROW; }
  "->"                   { return ARROW; }
  "&&"                   { return AND_AND; }
  "||"                   { return OR_OR; }
  "<<"                   { return LT_LT; }
  "::"                   { return COLON_COLON; }
  ".."                   { return DOT_DOT; }

  "!"                    { return EXCL; }
  "%"                    { return PERCENT; }
  "&"                    { return AND; }
  "("                    { return LPAREN; }
  ")"                    { return RPAREN; }
  "["                    { return LBRACK; }
  "]"                    { return RBRACK; }
  "*"                    { return MUL; }
  "+"                    { return PLUS; }
  ","                    { return COMMA; }
  "-"                    { return MINUS; }
  "."                    { return DOT; }
  "/"                    { return DIV; }
  ":"                    { return COLON; }
  ";"                    { return SEMICOLON; }
  "<"                    { return LT; }
  "="                    { return EQ; }
  ">"                    { return GT; }
  "^"                    { return XOR; }
  "{"                    { return LBRACE; }
  "|"                    { return OR; }
  "}"                    { return RBRACE; }
  "#"                    { return HASH; }
  "@"                    { return AT; }
  "$"                    { return DOLLAR; }
}

<IN_BLOCK_COMMENT> {
  "/*"                   { commentDepth++; }
  "*/"                   {
                           commentDepth--;
                           if (commentDepth == 0) {
                             yybegin(YYINITIAL);
                             return commentIsDoc ? DOC_COMMENT : BLOCK_COMMENT;
                           }
                         }
  <<EOF>>                { yybegin(YYINITIAL); return commentIsDoc ? DOC_COMMENT : BLOCK_COMMENT; }
  [^*/]+                 { }
  [*/]                   { }
}

[^]                      { return TokenType.BAD_CHARACTER; }
