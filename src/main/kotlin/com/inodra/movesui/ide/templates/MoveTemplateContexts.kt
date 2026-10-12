package com.inodra.movesui.ide.templates

import com.inodra.movesui.lang.MoveFile
import com.inodra.movesui.lang.psi.MvCodeBlock
import com.inodra.movesui.lang.psi.MvFunction
import com.inodra.movesui.lang.psi.MvModule
import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parentOfType

sealed class MoveTemplateContextType(name: String) : TemplateContextType(name) {
    override fun isInContext(context: TemplateActionContext): Boolean {
        val file = context.file as? MoveFile ?: return false
        val element = file.findElementAt(context.startOffset)?.let { it.takeIf { e -> e.textRange.startOffset <= context.startOffset } ?: it }
        return isInContext(element)
    }

    protected abstract fun isInContext(element: PsiElement?): Boolean

    class File : MoveTemplateContextType("Move") {
        override fun isInContext(element: PsiElement?) = true
    }

    class Module : MoveTemplateContextType("Module") {
        override fun isInContext(element: PsiElement?): Boolean {
            if (element == null) return false
            if (element.parentOfType<MvCodeBlock>() != null) return false
            return element.parentOfType<MvModule>() != null || element.parentOfType<MvFunction>() == null
        }
    }

    class Block : MoveTemplateContextType("Block") {
        override fun isInContext(element: PsiElement?) = element?.parentOfType<MvCodeBlock>() != null
    }
}
