package com.inodra.movesui.lang.psi.ext

import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType

inline fun <reified T : PsiElement> PsiElement.descendantOfType(): T? = PsiTreeUtil.findChildOfType(this, T::class.java)
inline fun <reified T : PsiElement> PsiElement.descendantsOfType(): Collection<T> = PsiTreeUtil.findChildrenOfType(this, T::class.java)
inline fun <reified T : PsiElement> PsiElement.childrenOfType(): List<T> = PsiTreeUtil.getChildrenOfTypeAsList(this, T::class.java)
inline fun <reified T : PsiElement> PsiElement.ancestorOrSelf(): T? = parentOfType<T>(withSelf = true)
inline fun <reified T : PsiElement> PsiElement.ancestorStrict(): T? = parentOfType<T>(withSelf = false)

val PsiElement.elementTypeOrNull get() = elementType
