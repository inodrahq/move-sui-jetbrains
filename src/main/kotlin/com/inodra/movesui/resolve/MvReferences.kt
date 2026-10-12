package com.inodra.movesui.resolve

import com.inodra.movesui.lang.psi.*
import com.inodra.movesui.lang.psi.ext.*
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.ResolveResult
import com.intellij.psi.impl.source.resolve.ResolveCache

abstract class MvReferenceBase<T : MvElement>(element: T, rangeInElement: TextRange) : PsiPolyVariantReferenceBase<T>(element, rangeInElement) {
    abstract fun resolveInner(): List<PsiElement>

    override fun multiResolve(incompleteCode: Boolean): Array<ResolveResult> =
        ResolveCache.getInstance(element.project).resolveWithCaching(this, RESOLVER, false, incompleteCode)

    override fun resolve(): PsiElement? = multiResolve(false).singleOrNull()?.element

    override fun handleElementRename(newElementName: String): PsiElement {
        val nameElement = element.node.findChildByType(NAME_TOKENS)?.psi
            ?: element.node.findChildByType(MoveTypes.QUOTE_IDENTIFIER)?.psi
        if (nameElement != null) {
            val factory = MvPsiFactory(element.project)
            nameElement.replace(if (nameElement.node.elementType == MoveTypes.QUOTE_IDENTIFIER) factory.createLabelIdentifier(newElementName) else factory.createIdentifier(newElementName))
        }
        return element
    }

    override fun getVariants(): Array<Any> = emptyArray()

    companion object {
        private val RESOLVER = ResolveCache.PolyVariantResolver<MvReferenceBase<*>> { ref, _ ->
            ref.resolveInner().map { PsiElementResolveResult(it) }.toTypedArray()
        }
    }
}

class MvPathSegmentReference(segment: MvPathSegment) : MvReferenceBase<MvPathSegment>(segment, segment.nameElement!!.textRangeInParent) {
    override fun resolveInner() = MvPathResolver.resolveSegment(element)
}

class MvMethodCallReference(call: MvMethodCallExpr) : MvReferenceBase<MvMethodCallExpr>(call, call.identifier!!.textRangeInParent) {
    override fun resolveInner() = MvPathResolver.resolveMethod(element)
}

class MvStructLitFieldReference(field: MvStructLitField) : MvReferenceBase<MvStructLitField>(field, field.identifier!!.textRangeInParent) {
    override fun resolveInner(): List<PsiElement> {
        val lit = element.parent.parent as? MvStructLitExpr ?: return emptyList()
        val name = element.identifier?.text
        return MvPathResolver.fieldsOf(lit.path).filter { it.name == name }
    }
}

class MvStructPatFieldReference(field: MvStructPatField) : MvReferenceBase<MvStructPatField>(field, field.identifier!!.textRangeInParent) {
    override fun resolveInner(): List<PsiElement> {
        val pat = element.parent.parent as? MvStructPat ?: return emptyList()
        return MvPathResolver.fieldsOf(pat.path).filter { it.name == element.name }
    }
}

class MvLabelReference(label: MvLabel) : MvReferenceBase<MvLabel>(label, TextRange(0, label.textLength)) {
    override fun resolveInner() = MvPathResolver.resolveLabelUsage(element)
}

class MvUseSpeckReference(speck: MvUseSpeck) : MvReferenceBase<MvUseSpeck>(speck, speck.useSpeckName.textRangeInParent) {
    override fun resolveInner() = MvModuleResolver.resolveUseSpeck(element)
}
