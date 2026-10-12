package com.inodra.movesui.ide.docs

import com.inodra.movesui.ide.structure.presentableText
import com.inodra.movesui.lang.psi.*
import com.inodra.movesui.lang.psi.ext.*
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.elementType

class MoveDocumentationTargetProvider : PsiDocumentationTargetProvider {
    override fun documentationTarget(element: PsiElement, originalElement: PsiElement?): DocumentationTarget? {
        if (element !is MvNamedElement || element.containingFile !is com.inodra.movesui.lang.MoveFile) return null
        return MoveDocumentationTarget(element)
    }
}

class MoveDocumentationTarget(private val element: MvNamedElement) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> {
        val ptr = SmartPointerManager.createPointer(element)
        return Pointer { ptr.element?.let { MoveDocumentationTarget(it) } }
    }

    override fun computePresentation(): TargetPresentation =
        TargetPresentation.builder(presentableText(element)).icon(com.inodra.movesui.ide.structure.iconFor(element)).presentation()

    override fun computeDocumentation(): DocumentationResult? {
        val signature = signatureOf(element)
        val docs = docCommentOf(element)
        val html = buildString {
            append(DocumentationMarkup.DEFINITION_START).append(StringUtil.escapeXmlEntities(signature)).append(DocumentationMarkup.DEFINITION_END)
            if (docs.isNotBlank()) {
                append(DocumentationMarkup.CONTENT_START).append(renderDocText(docs)).append(DocumentationMarkup.CONTENT_END)
            }
            element.ancestorStrict<MvModule>()?.let { m ->
                append(DocumentationMarkup.SECTIONS_START)
                append(DocumentationMarkup.SECTION_HEADER_START).append("Module:").append(DocumentationMarkup.SECTION_SEPARATOR)
                append(StringUtil.escapeXmlEntities(m.qualifiedName)).append(DocumentationMarkup.SECTION_END)
                append(DocumentationMarkup.SECTIONS_END)
            }
        }
        return DocumentationResult.documentation(html)
    }
}

fun signatureOf(e: PsiElement): String = when (e) {
    is MvFunction -> {
        val header = e.text.substringBefore("{").substringBeforeLast(";").trim()
        header.lines().joinToString("\n") { it.trimEnd() }.let { if (e.attrs.isNotEmpty()) it.substringAfter("]", it).trimStart() else it }.trim()
    }
    is MvStruct -> buildString {
        e.visibility?.let { append(it.text).append(' ') }
        append("struct ").append(e.name).append(e.typeParamList?.text ?: "")
        val abilities = e.abilities.joinToString(", ") { it.text }
        if (abilities.isNotEmpty()) append(" has ").append(abilities)
        e.namedFieldList?.let { append(" { ").append(e.fields.joinToString(", ") { f -> "${f.name}: ${f.type?.text}" }).append(" }") }
        e.positionalFieldList?.let { append(it.text) }
    }
    is MvEnum -> "enum ${e.name}${e.typeParamList?.text ?: ""}" + e.abilities.joinToString(", ") { it.text }.let { if (it.isEmpty()) "" else " has $it" }
    is MvEnumVariant -> (e.ancestorStrict<MvEnum>()?.name?.let { "$it::" } ?: "") + e.name + (e.positionalFieldList?.text ?: e.namedFieldList?.let { " { ... }" } ?: "")
    is MvConst -> "const ${e.name}: ${e.type?.text} = ${e.expr?.text}"
    is MvNamedField -> "${e.name}: ${e.type?.text}"
    is MvModule -> "module ${e.qualifiedName}"
    is MvFunctionParam -> e.text
    is MvBindingPat -> "let ${e.text}" + ((e.parent as? MvLetStmt)?.typeAscription?.text ?: "")
    is MvTypeParam -> e.text
    else -> presentableText(e)
}

/** Text of the `///` / `/** */` comments directly above an item, with delimiters stripped. */
fun docCommentOf(e: PsiElement): String {
    val lines = mutableListOf<String>()
    var prev = e.prevSibling
    // Doc comments sit before attributes inside the item or before the item itself.
    val firstChildComments = generateSequence(e.firstChild) { it.nextSibling }.takeWhile { it is PsiComment || it is PsiWhiteSpace }.filterIsInstance<PsiComment>().toList()
    val sourceComments = mutableListOf<PsiComment>()
    while (prev != null) {
        when {
            prev is PsiWhiteSpace -> if (prev.text.count { it == '\n' } > 1) break
            prev is PsiComment && prev.elementType == MoveTypes.DOC_COMMENT -> sourceComments += prev
            else -> break
        }
        prev = prev.prevSibling
    }
    val owner = e.parent?.takeIf { it is MvModuleItem }
    if (sourceComments.isEmpty() && owner != null) {
        var p = owner.prevSibling
        while (p != null) {
            when {
                p is PsiWhiteSpace -> if (p.text.count { it == '\n' } > 1) break
                p is PsiComment && p.elementType == MoveTypes.DOC_COMMENT -> sourceComments += p
                else -> break
            }
            p = p.prevSibling
        }
    }
    (sourceComments.asReversed() + firstChildComments).forEach { c ->
        val t = c.text
        lines += when {
            t.startsWith("///") -> t.removePrefix("///").trim()
            t.startsWith("/**") -> t.removePrefix("/**").removeSuffix("*/").lines().joinToString("\n") { it.trim().removePrefix("*").trim() }
            else -> return@forEach
        }
    }
    return lines.joinToString("\n").trim()
}

private fun renderDocText(text: String): String {
    val sb = StringBuilder()
    var inCode = false
    for (line in text.lines()) {
        if (line.trim().startsWith("```")) {
            sb.append(if (inCode) "</pre>" else "<pre>"); inCode = !inCode; continue
        }
        val escaped = StringUtil.escapeXmlEntities(line).replace(Regex("`([^`]+)`"), "<code>$1</code>")
        if (inCode) sb.append(escaped).append('\n') else if (line.isBlank()) sb.append("<p>") else sb.append(escaped).append(' ')
    }
    if (inCode) sb.append("</pre>")
    return sb.toString()
}
