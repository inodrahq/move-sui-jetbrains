package com.inodra.movesui.ide

import com.inodra.movesui.ide.structure.iconFor
import com.inodra.movesui.lang.MoveLanguage
import com.inodra.movesui.lang.psi.*
import com.inodra.movesui.lang.psi.ext.MvNamedElement
import com.inodra.movesui.lang.psi.ext.qualifiedName
import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import javax.swing.Icon

class MoveBreadcrumbsProvider : BreadcrumbsProvider {
    override fun getLanguages(): Array<Language> = arrayOf(MoveLanguage)

    override fun acceptElement(e: PsiElement) =
        e is MvModule || e is MvFunction || e is MvStruct || e is MvEnum || e is MvEnumVariant || e is MvConst

    override fun getElementInfo(e: PsiElement): String = when (e) {
        is MvModule -> e.qualifiedName
        is MvFunction -> (e.name ?: "?") + "()"
        is MvNamedElement -> e.name ?: "?"
        else -> e.text
    }

    override fun getElementIcon(e: PsiElement): Icon? = iconFor(e)
}
