package com.inodra.movesui.lang.psi.ext

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

interface MvElement : PsiElement

abstract class MvElementImpl(node: ASTNode) : ASTWrapperPsiElement(node), MvElement {
    override fun toString(): String = "${javaClass.simpleName.removeSuffix("Impl")}(${node.elementType})"
}
