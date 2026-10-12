package com.inodra.movesui.lang.psi

import com.inodra.movesui.lang.MoveFile
import com.inodra.movesui.lang.MoveFileType
import com.inodra.movesui.lang.psi.ext.descendantOfType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory

class MvPsiFactory(private val project: Project) {
    fun createFile(text: String): MoveFile =
        PsiFileFactory.getInstance(project).createFileFromText("dummy.move", MoveFileType, text) as MoveFile

    fun createIdentifier(name: String): PsiElement =
        createFunction("fun $name() {}").nameIdentifier ?: error("Invalid identifier: $name")

    fun createLabelIdentifier(name: String): PsiElement =
        createFunction("fun f() { '$name: loop {} }").descendantOfType<MvLabel>()?.quoteIdentifier
            ?: error("Invalid label: $name")

    fun createFunction(text: String): MvFunction =
        createFile("module m::m;\n$text").descendantOfType() ?: error("Failed to create function from: $text")

    fun createUseStmt(path: String): MvUseStmt =
        createFile("module m::m;\nuse $path;").descendantOfType() ?: error("Failed to create use: $path")
}
