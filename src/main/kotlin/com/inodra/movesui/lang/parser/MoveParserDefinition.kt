package com.inodra.movesui.lang.parser

import com.inodra.movesui.lang.MoveFile
import com.inodra.movesui.lang.lexer.MoveLexer
import com.inodra.movesui.lang.psi.MoveTokenSets
import com.inodra.movesui.lang.psi.MoveTypes
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.inodra.movesui.lang.stubs.MoveFileStub

class MoveParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?) = MoveLexer()
    override fun createParser(project: Project?) = MoveParser()
    override fun getFileNodeType() = MoveFileStub.Type
    override fun getCommentTokens() = MoveTokenSets.COMMENTS
    override fun getStringLiteralElements() = MoveTokenSets.STRINGS
    override fun createElement(node: ASTNode) = MoveTypes.Factory.createElement(node)
    override fun createFile(viewProvider: FileViewProvider) = MoveFile(viewProvider)
}
