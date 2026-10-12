package com.inodra.movesui.lang.psi.ext

import com.inodra.movesui.lang.psi.*
import com.inodra.movesui.lang.stubs.MvEnumVariantStub
import com.inodra.movesui.resolve.*
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.util.parentOfType

abstract class MvPathSegmentMixin(node: ASTNode) : MvElementImpl(node) {
    override fun getReference(): PsiReference? {
        val seg = this as MvPathSegment
        if (seg.nameElement == null || seg.integerLiteral != null) return null
        return MvPathSegmentReference(seg)
    }
}

abstract class MvMethodCallExprMixin(node: ASTNode) : MvElementImpl(node) {
    override fun getReference(): PsiReference? = (this as MvMethodCallExpr).identifier?.let { MvMethodCallReference(this) }
}

abstract class MvStructLitFieldMixin(node: ASTNode) : MvElementImpl(node) {
    override fun getReference(): PsiReference? = (this as MvStructLitField).identifier?.let { MvStructLitFieldReference(this) }
}

/** `S { x }` shorthand both binds `x` and refers to field `x`; `S { x: pat }` only refers to the field. */
abstract class MvStructPatFieldMixin(node: ASTNode) : MvNamedElementImpl(node) {
    override fun getReference(): PsiReference? = (this as MvStructPatField).identifier?.let { MvStructPatFieldReference(this) }
    override fun getUseScope(): SearchScope = localScope(this)
}

abstract class MvUseSpeckMixin(node: ASTNode) : MvElementImpl(node) {
    override fun getReference(): PsiReference = MvUseSpeckReference(this as MvUseSpeck)
}

abstract class MvLabelMixin(node: ASTNode) : MvNamedElementImpl(node) {
    override fun getNameIdentifier(): PsiElement? = findChildByType(MoveTypes.QUOTE_IDENTIFIER)
    override fun getName(): String? = nameIdentifier?.text?.removePrefix("'")
    override fun setName(name: String): PsiElement {
        nameIdentifier?.replace(MvPsiFactory(project).createLabelIdentifier(name))
        return this
    }
    override fun getReference(): PsiReference? = if (parent is MvLabeledExpr) null else MvLabelReference(this as MvLabel)
    override fun getUseScope(): SearchScope = localScope(this)
}

abstract class MvLocalNamedElementImpl(node: ASTNode) : MvNamedElementImpl(node) {
    override fun getUseScope(): SearchScope = localScope(this)
}

internal fun localScope(e: PsiElement): SearchScope {
    val owner = e.parentOfType<MvFunction>() ?: e.parentOfType<MvModule>() ?: e.containingFile
    return LocalSearchScope(owner)
}
