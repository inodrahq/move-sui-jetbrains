package com.inodra.movesui.ide.usages

import com.inodra.movesui.lang.lexer.MoveLexer
import com.inodra.movesui.lang.psi.*
import com.inodra.movesui.lang.psi.ext.MvNamedElement
import com.inodra.movesui.lang.psi.ext.qualifiedName
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet

class MoveFindUsagesProvider : FindUsagesProvider {
    override fun getWordsScanner(): WordsScanner = DefaultWordsScanner(
        MoveLexer(),
        TokenSet.orSet(MoveTokenSets.IDENTIFIERS, TokenSet.create(MoveTypes.QUOTE_IDENTIFIER)),
        MoveTokenSets.COMMENTS,
        MoveTokenSets.LITERALS,
    )

    override fun canFindUsagesFor(element: PsiElement) = element is MvNamedElement
    override fun getHelpId(element: PsiElement): String? = null

    override fun getType(element: PsiElement): String = when (element) {
        is MvModule -> "module"
        is MvFunction -> "function"
        is MvStruct -> "struct"
        is MvEnum -> "enum"
        is MvEnumVariant -> "enum variant"
        is MvConst -> "constant"
        is MvNamedField -> "field"
        is MvTypeParam -> "type parameter"
        is MvFunctionParam -> "parameter"
        is MvBindingPat, is MvStructPatField -> "variable"
        is MvLabel -> "label"
        is MvUseAlias -> "import alias"
        else -> "element"
    }

    override fun getDescriptiveName(element: PsiElement): String = when (element) {
        is MvModule -> element.qualifiedName
        is MvNamedElement -> element.name ?: ""
        else -> ""
    }

    override fun getNodeText(element: PsiElement, useFullName: Boolean) = getDescriptiveName(element)
}
