package com.inodra.movesui.toml

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.*
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet
import com.intellij.util.ProcessingContext
import org.toml.lang.psi.TomlLiteral

/** `local = "../other"` in Move.toml navigates to the dependency directory. */
class MoveTomlReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(psiElement(TomlLiteral::class.java), object : PsiReferenceProvider() {
            override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                val literal = element as TomlLiteral
                if (!MoveToml.isMoveToml(literal.containingFile)) return PsiReference.EMPTY_ARRAY
                val kv = MoveToml.keyValueOf(literal) ?: return PsiReference.EMPTY_ARRAY
                val key = MoveToml.keyName(kv)
                if (key != "local" && key != "subdir") return PsiReference.EMPTY_ARRAY
                if (key == "subdir") return PsiReference.EMPTY_ARRAY
                val text = literal.text
                if (text.length < 2) return PsiReference.EMPTY_ARRAY
                val set = object : FileReferenceSet(MoveToml.literalText(literal), literal, 1, this, true, true) {
                    override fun isAbsolutePathReference() = super.isAbsolutePathReference()
                }
                return set.allReferences.map { it as PsiReference }.toTypedArray()
            }
        })
    }
}
