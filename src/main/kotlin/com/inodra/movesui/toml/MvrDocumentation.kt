package com.inodra.movesui.toml

import com.inodra.movesui.MoveIcons
import com.inodra.movesui.mvr.MvrClient
import com.intellij.lang.documentation.DocumentationMarkup
import com.intellij.model.Pointer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.backend.documentation.DocumentationResult
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.DocumentationTargetProvider
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.psi.PsiFile
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.util.parentOfType
import org.toml.lang.psi.TomlLiteral

/** Hover / Quick Documentation on an `@org/pkg` string in Move.toml shows what the Move Registry knows about it. */
class MvrDocumentationTargetProvider : DocumentationTargetProvider {
    override fun documentationTargets(file: PsiFile, offset: Int): List<DocumentationTarget> {
        if (!MoveToml.isMoveToml(file)) return emptyList()
        val literal = file.findElementAt(offset)?.parentOfType<TomlLiteral>(withSelf = true) ?: return emptyList()
        val name = MoveToml.literalText(literal)
        if (!MvrClient.NAME_REGEX.matches(name)) return emptyList()
        return listOf(MvrDocumentationTarget(literal, name))
    }
}

class MvrDocumentationTarget(private val literal: TomlLiteral, private val name: String) : DocumentationTarget {
    override fun createPointer(): Pointer<out DocumentationTarget> {
        val ptr = SmartPointerManager.createPointer(literal)
        val n = name
        return Pointer { ptr.element?.let { MvrDocumentationTarget(it, n) } }
    }

    override fun computePresentation(): TargetPresentation = TargetPresentation.builder(name).icon(MoveIcons.SUI).presentation()

    override fun computeDocumentation(): DocumentationResult = DocumentationResult.asyncDocumentation {
        val pkg = MvrClient.details(name.substringBeforeLast('/', name).let { if (name.count { c -> c == '/' } == 2) it else name })
            ?: return@asyncDocumentation DocumentationResult.documentation("${DocumentationMarkup.DEFINITION_START}$name${DocumentationMarkup.DEFINITION_END}${DocumentationMarkup.CONTENT_START}Not found on the Move Registry (mainnet).${DocumentationMarkup.CONTENT_END}")
        val html = buildString {
            append(DocumentationMarkup.DEFINITION_START).append(StringUtil.escapeXmlEntities(pkg.name)).append(DocumentationMarkup.DEFINITION_END)
            pkg.description?.let { append(DocumentationMarkup.CONTENT_START).append(StringUtil.escapeXmlEntities(it)).append(DocumentationMarkup.CONTENT_END) }
            append(DocumentationMarkup.SECTIONS_START)
            fun section(title: String, value: String?) {
                if (value.isNullOrBlank()) return
                append(DocumentationMarkup.SECTION_HEADER_START).append(title).append(DocumentationMarkup.SECTION_SEPARATOR).append(value).append(DocumentationMarkup.SECTION_END)
            }
            section("Package:", pkg.packageAddress?.let { "<code>$it</code>" })
            section("Version:", pkg.version?.toString())
            section("Homepage:", pkg.homepage?.let { "<a href=\"$it\">$it</a>" })
            section("Source:", pkg.repository?.let { r -> "<a href=\"$r\">$r</a>" + (pkg.path?.let { " ($it" + (pkg.tag?.let { t -> " @ $t" } ?: "") + ")" } ?: "") })
            append(DocumentationMarkup.SECTIONS_END)
        }
        DocumentationResult.documentation(html)
    }
}
