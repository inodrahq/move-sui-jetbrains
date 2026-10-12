package com.inodra.movesui.ide.annotator

import com.inodra.movesui.ide.highlight.MoveColor
import com.inodra.movesui.lang.psi.*
import com.inodra.movesui.lang.psi.ext.*
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType

/** Syntax-driven semantic colors; no name resolution involved. */
class MoveHighlightingAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val color = colorFor(element) ?: return
        val range = rangeFor(element) ?: return
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION).range(range).textAttributes(color.textAttributesKey).create()
    }

    private fun rangeFor(e: PsiElement) = when (e) {
        is MvAttr -> e.textRange
        is MvNamedElement -> e.nameIdentifier?.textRange
        is MvPathSegment -> e.nameElement?.textRange
        is MvMethodCallExpr -> e.identifier?.textRange
        is MvFieldExpr -> (e.identifier ?: e.integerLiteral)?.textRange
        is MvStructLitField -> e.identifier?.textRange
        else -> e.textRange
    }

    private fun colorFor(e: PsiElement): MoveColor? = when (e) {
        is MvAttr -> MoveColor.ATTRIBUTE
        is MvModule -> MoveColor.MODULE
        is MvFunction -> if (e.isMacro) MoveColor.MACRO else MoveColor.FUNCTION
        is MvStruct -> MoveColor.STRUCT
        is MvEnum -> MoveColor.ENUM
        is MvEnumVariant -> MoveColor.ENUM_VARIANT
        is MvConst -> MoveColor.CONSTANT
        is MvNamedField -> MoveColor.FIELD
        is MvTypeParam -> MoveColor.TYPE_PARAMETER
        is MvFunctionParam -> if (e.syntaxIdentifier != null) MoveColor.MACRO_PARAMETER else MoveColor.PARAMETER
        is MvBindingPat -> MoveColor.VARIABLE
        is MvStructLitField -> MoveColor.FIELD
        is MvFieldExpr -> MoveColor.FIELD
        is MvMethodCallExpr -> if (e.excl != null) MoveColor.MACRO_CALL else MoveColor.METHOD_CALL
        is MvAbility -> MoveColor.ABILITY
        is MvPathSegment -> pathSegmentColor(e)
        else -> leafColor(e)
    }

    private fun pathSegmentColor(seg: MvPathSegment): MoveColor? {
        val name = seg.referenceName ?: return null
        val path = seg.parent as? MvPath ?: return null
        val isLast = path.lastSegment === seg
        val owner = path.parent
        return when {
            name == "Self" -> MoveColor.SELF_PARAMETER
            seg.syntaxIdentifier != null -> MoveColor.MACRO_PARAMETER
            isLast && name in BUILTIN_TYPES && path.segments.size == 1 && (owner is MvPathType || owner is MvVectorLitExpr) -> MoveColor.BUILTIN_TYPE
            isLast && owner is MvMacroCallExpr -> MoveColor.MACRO_CALL
            isLast && owner is MvCallExpr -> MoveColor.FUNCTION_CALL
            isLast && (owner is MvPathType || owner is MvStructLitExpr || owner is MvStructPat || owner is MvTupleStructPat) && name.first().isUpperCase() -> MoveColor.STRUCT
            isLast && owner is MvPathExpr && name.first().isUpperCase() && name.length > 1 && name.substring(1).any { it.isLowerCase() } -> MoveColor.CONSTANT
            !isLast -> MoveColor.MODULE
            else -> null
        }
    }

    private fun leafColor(e: PsiElement): MoveColor? {
        val type = e.elementType ?: return null
        if (!MoveTokenSets.CONTEXTUAL_KEYWORDS.contains(type)) return null
        val parent = e.parent
        val isKeyword = when (parent) {
            is MvFunctionModifier, is MvVisibility, is MvAbilityList, is MvTypeParam, is MvAddressBlock, is MvScript, is MvSpecBlock -> true
            else -> false
        }
        return if (isKeyword) MoveColor.KEYWORD else null
    }
}
