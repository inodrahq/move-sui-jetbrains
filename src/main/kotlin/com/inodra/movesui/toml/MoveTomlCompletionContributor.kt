package com.inodra.movesui.toml

import com.inodra.movesui.MoveIcons
import com.inodra.movesui.mvr.MvrClient
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.util.ProcessingContext
import org.toml.lang.psi.*

class MoveTomlCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, psiElement().inFile(psiElement(TomlFile::class.java)), object : CompletionProvider<CompletionParameters>() {
            override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
                if (!MoveToml.isMoveToml(parameters.originalFile)) return
                val position = parameters.position
                val section = MoveToml.sectionOf(position)
                val kv = MoveToml.keyValueOf(position)
                val inKey = position.parent is TomlKeySegment && kv != null && kv.key.textRange.contains(position.textRange.startOffset)
                val inlineTable = position.parent?.parent?.parent as? TomlInlineTable ?: kv?.parent as? TomlInlineTable

                when {
                    position.parent is TomlKeySegment && position.parent.parent?.parent is TomlTableHeader ->
                        MoveToml.SECTIONS.forEach { result.addElement(LookupElementBuilder.create(it).bold()) }
                    inKey && inlineTable != null && MoveToml.isDependencySection(section) ->
                        MoveToml.DEPENDENCY_KEYS.forEach { result.addElement(LookupElementBuilder.create(it).withInsertHandler(keyInsert)) }
                    inKey && section == "package" ->
                        MoveToml.PACKAGE_KEYS.forEach { result.addElement(LookupElementBuilder.create(it).withInsertHandler(keyInsert)) }
                    inKey && section?.startsWith("dependencies.") == true || inKey && section?.startsWith("dev-dependencies.") == true ->
                        MoveToml.DEPENDENCY_KEYS.forEach { result.addElement(LookupElementBuilder.create(it).withInsertHandler(keyInsert)) }
                    kv != null && !inKey -> completeValue(kv, section, parameters, result)
                }
            }
        })
    }

    private fun completeValue(kv: TomlKeyValue, section: String?, parameters: CompletionParameters, result: CompletionResultSet) {
        val key = MoveToml.keyName(kv)
        when {
            section == "package" && key == "edition" -> MoveToml.EDITIONS.forEach { result.addElement(LookupElementBuilder.create("\"$it\"").withPresentableText(it)) }
            section == "package" && key == "flavor" -> result.addElement(LookupElementBuilder.create("\"sui\"").withPresentableText("sui"))
            key == "r.mvr" || key == "mvr" -> {
                val prefix = parameters.position.text.substringBefore(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED).trim('"', '\'')
                val rs = result.withPrefixMatcher(prefix)
                MvrClient.search(prefix.removePrefix("@").substringAfter('/', prefix.removePrefix("@")).ifBlank { prefix.removePrefix("@") }).forEach { p ->
                    rs.addElement(LookupElementBuilder.create("\"${p.name}\"").withPresentableText(p.name).withIcon(MoveIcons.SUI).withTypeText(p.description?.take(60), true))
                }
                rs.restartCompletionOnAnyPrefixChange()
            }
        }
    }

    private val keyInsert = InsertHandler<com.intellij.codeInsight.lookup.LookupElement> { ctx, item ->
        val tail = ctx.document.charsSequence.subSequence(ctx.tailOffset, minOf(ctx.tailOffset + 3, ctx.document.textLength)).toString()
        if (!tail.trimStart().startsWith("=")) {
            ctx.document.insertString(ctx.tailOffset, " = \"\"")
            ctx.editor.caretModel.moveToOffset(ctx.tailOffset - 1)
        }
    }
}
