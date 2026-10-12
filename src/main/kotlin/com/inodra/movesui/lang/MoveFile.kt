package com.inodra.movesui.lang

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider

class MoveFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, MoveLanguage) {
    override fun getFileType() = MoveFileType
    override fun toString() = "Move file"
}
