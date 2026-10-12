package com.inodra.movesui.ide.completion

import com.inodra.movesui.ide.structure.iconFor
import com.inodra.movesui.lang.MoveLanguage
import com.inodra.movesui.lang.psi.*
import com.inodra.movesui.lang.psi.ext.*
import com.inodra.movesui.lang.stubs.index.MvModuleIndex
import com.inodra.movesui.lsp.MoveLspIntegrationProvider
import com.inodra.movesui.project.MoveNamedAddresses
import com.inodra.movesui.resolve.MvModuleResolver
import com.inodra.movesui.resolve.MvPathResolver
import com.inodra.movesui.resolve.MvScopes
import com.inodra.movesui.resolve.Namespace
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.icons.AllIcons
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.parentOfType
import com.intellij.util.ProcessingContext

/**
 * Keyword, attribute, ability, struct-field and `use`-path completion is always native. Symbol completion
 * comes from the analyzer when it runs; the native scope-based fallback only kicks in without a running server.
 */
class MoveCompletionContributor : CompletionContributor() {
    init {
        extend(CompletionType.BASIC, psiElement().withLanguage(MoveLanguage), MoveCompletionProvider())
    }
}

private class MoveCompletionProvider : CompletionProvider<CompletionParameters>() {
    override fun addCompletions(parameters: CompletionParameters, context: ProcessingContext, result: CompletionResultSet) {
        val position = parameters.position
        val parent = position.parent

        when {
            position.parentOfType<MvAttr>() != null -> ATTRIBUTES.forEach { result.addElement(LookupElementBuilder.create(it).bold()) }
            parent is MvAbility || position.prevSiblingSkippingWs()?.text == "has" || parent?.parent is MvAbilityBound || parent is MvAbilityList ->
                ABILITIES.forEach { result.addElement(LookupElementBuilder.create(it).bold()) }
            parent is MvStructLitField -> completeFields(parent, (parent.parent?.parent as? MvStructLitExpr)?.path, parent.parent, result, pattern = false)
            parent is MvStructPatField -> completeFields(parent, (parent.parent?.parent as? MvStructPat)?.path, parent.parent, result, pattern = true)
            parent is MvPathSegment -> completePath(parent, result)
            parent is MvUseSpeckName -> completeUseSpeck(parent.parent as MvUseSpeck, parameters, result)
            parent is MvMethodCallExpr -> {}
            else -> completeKeywords(position, result)
        }
    }

    /** Fields of the struct (or enum variant) a literal / pattern names; the ones not written yet come first. */
    private fun completeFields(current: PsiElement, path: MvPath?, list: PsiElement?, result: CompletionResultSet, pattern: Boolean) {
        val fields = MvPathResolver.fieldsOf(path)
        if (fields.isEmpty()) return
        val present = when (list) {
            is MvStructLitFieldList -> list.structLitFieldList.filter { it !== current }.mapNotNull { it.identifier?.text }
            is MvStructPatFieldList -> list.structPatFieldList.filter { it !== current }.mapNotNull { it.identifier?.text }
            else -> emptyList()
        }.toSet()
        fields.forEach { field ->
            val name = field.name ?: return@forEach
            if (name in present) return@forEach
            val element = LookupElementBuilder.create(field, name).withIcon(iconFor(field)).withTypeText(field.type?.text).bold()
                .withInsertHandler { ctx, _ ->
                    if (pattern) return@withInsertHandler
                    val tail = ctx.document.charsSequence.subSequence(ctx.tailOffset, minOf(ctx.tailOffset + 2, ctx.document.textLength)).toString()
                    if (!tail.trimStart().startsWith(":")) ctx.document.insertString(ctx.tailOffset, ": ")
                    ctx.editor.caretModel.moveToOffset(ctx.tailOffset)
                }
            result.addElement(PrioritizedLookupElement.withPriority(element, 100.0))
        }
        result.stopHere()
    }

    /** `use <addr>::<module>::{<items>}`: addresses at the root, then modules, then module items. */
    private fun completeUseSpeck(speck: MvUseSpeck, parameters: CompletionParameters, result: CompletionResultSet) {
        val parentSpeck = (speck.parent as? MvUseSpeck) ?: ((speck.parent as? MvUseGroup)?.parent as? MvUseSpeck)
        val file = parameters.originalFile.virtualFile
        if (parentSpeck == null) {
            if (file != null) {
                MoveNamedAddresses.forFile(speck.project, file).forEach { e ->
                    result.addElement(LookupElementBuilder.create(e.name).withIcon(AllIcons.Nodes.Package).withTypeText(e.origin, true))
                }
            }
            return
        }
        val owners = MvModuleResolver.resolveUseSpeck(parentSpeck)
        if (owners.isNotEmpty()) {
            owners.forEach { owner ->
                when (owner) {
                    is MvModule -> {
                        result.addElement(LookupElementBuilder.create("Self").bold().withTypeText("module ${owner.name}", true))
                        owner.items.filterIsInstance<MvNamedElement>().filter { it !is MvUseAlias }.forEach { result.addElement(lookup(it)) }
                    }
                    is MvEnum -> owner.variants.forEach { result.addElement(lookup(it)) }
                }
            }
            return
        }
        // Parent is an address: offer every module declared under it.
        val grand = (parentSpeck.parent as? MvUseSpeck) ?: ((parentSpeck.parent as? MvUseGroup)?.parent as? MvUseSpeck)
        if (grand != null) return
        completeModulesOfAddress(parentSpeck.useSpeckName.text, speck.project, result)
    }

    private fun completeModulesOfAddress(address: String, project: com.intellij.openapi.project.Project, result: CompletionResultSet) {
        val wanted = MvModuleResolver.canonicalAddress(address)
        StubIndex.getInstance().getAllKeys(MvModuleIndex.KEY, project).forEach { name ->
            MvModuleResolver.findModules(project, address, name)
                .filter { m -> m.addressName?.let { MvModuleResolver.canonicalAddress(it) } == wanted }
                .forEach { m -> result.addElement(lookup(m)) }
        }
    }

    private fun completePath(segment: MvPathSegment, result: CompletionResultSet) {
        val path = segment.parent as? MvPath ?: return
        val segments = path.segments
        val index = segments.indexOf(segment)
        if (index > 0) {
            val owners = MvPathResolver.resolve(path, index - 1)
            if (owners.isEmpty() && index == 1) {
                completeModulesOfAddress(segments[0].referenceName ?: return, segment.project, result)
                return
            }
            owners.flatMap { owner ->
                when (owner) {
                    is MvModule -> owner.items.filterIsInstance<MvNamedElement>()
                    is MvEnum -> owner.variants
                    else -> emptyList()
                }
            }.forEach { result.addElement(lookup(it)) }
            return
        }

        val ns = MvPathResolver.namespaceOf(path)
        val seen = HashSet<String>()
        val analyzer = analyzerRunning(segment)
        // Locals, parameters and type parameters are always native: the analyzer does not offer them.
        // Module-level names are left to the analyzer while it runs (it also handles auto-import).
        MoveScopeNames.collect(path, ns).forEach { e ->
            val name = (e as? MvNamedElement)?.name ?: return@forEach
            val local = e is MvBindingPat || e is MvFunctionParam || e is MvTypeParam || e is MvStructPatField
            if (analyzer && !local) return@forEach
            if (seen.add(name)) result.addElement(if (local) PrioritizedLookupElement.withPriority(lookup(e), 200.0) else lookup(e))
        }
        if (!analyzer) BUILTIN_TYPES.forEach { if (seen.add(it)) result.addElement(LookupElementBuilder.create(it).bold()) }
        if (path.parent is MvPathType) TYPE_KEYWORDS.forEach { result.addElement(LookupElementBuilder.create(it).bold()) }
        else EXPR_KEYWORDS.forEach { result.addElement(LookupElementBuilder.create(it).bold()) }
    }

    private fun completeKeywords(position: PsiElement, result: CompletionResultSet) {
        val inBlock = position.parentOfType<MvCodeBlock>() != null
        val inModule = position.parentOfType<MvModule>() != null
        val words = when {
            inBlock -> EXPR_KEYWORDS + STMT_KEYWORDS
            inModule -> ITEM_KEYWORDS
            else -> TOP_KEYWORDS
        }
        words.forEach { result.addElement(LookupElementBuilder.create(it).bold().withInsertHandler { ctx, _ -> if (it !in NO_SPACE) ctx.document.insertString(ctx.tailOffset, " ") }) }
    }

    private fun analyzerRunning(e: PsiElement): Boolean =
        MoveLspIntegrationProvider.clients(e.project).any { it.state == LspServerState.Running }

    private fun lookup(e: PsiElement): LookupElement {
        val name = (e as MvNamedElement).name ?: ""
        var b = LookupElementBuilder.createWithIcon(e).withIcon(iconFor(e))
        when (e) {
            is MvFunction -> b = b.withTailText("(" + e.params.joinToString(", ") { "${it.name}: ${it.type?.text}" } + ")")
                .withTypeText(e.returnType?.type?.text ?: "()")
                .withInsertHandler { ctx, _ ->
                    if (ctx.completionChar != '(' && !ctx.document.text.startsWith("(", ctx.tailOffset)) {
                        val call = if (e.isMacro) "!()" else "()"
                        ctx.document.insertString(ctx.tailOffset, call)
                        ctx.editor.caretModel.moveToOffset(ctx.tailOffset - 1)
                    }
                }
            is MvStruct -> b = b.withTypeText("struct").withTailText(e.typeParamList?.text ?: "")
            is MvEnum -> b = b.withTypeText("enum")
            is MvConst -> b = b.withTypeText(e.type?.text)
            is MvModule -> b = b.withTypeText("module").withTailText(" " + (e.addressName ?: ""), true)
            is MvNamedField -> b = b.withTypeText(e.type?.text)
            is MvFunctionParam -> b = b.withTypeText(e.type?.text)
            is MvEnumVariant -> b = b.withTypeText(e.ancestorStrict<MvEnum>()?.name)
        }
        return b.withLookupString(name)
    }

    private fun PsiElement.prevSiblingSkippingWs(): PsiElement? {
        var p = prevSibling
        while (p is PsiWhiteSpace) p = p.prevSibling
        return p
    }

    companion object {
        val ATTRIBUTES = listOf("test", "test_only", "expected_failure", "allow", "error", "syntax", "ext", "deprecated", "random_test", "mode", "lint_allow", "abort_code", "arithmetic_error", "out_of_gas", "vector_error", "location")
        val TOP_KEYWORDS = listOf("module", "address", "script")
        val ITEM_KEYWORDS = listOf("public", "public(package)", "entry", "fun", "struct", "enum", "const", "use", "macro", "native", "has")
        val STMT_KEYWORDS = listOf("let", "mut", "use")
        val EXPR_KEYWORDS = listOf("if", "else", "while", "loop", "match", "return", "abort", "break", "continue", "true", "false", "copy", "move", "vector", "as")
        val TYPE_KEYWORDS = listOf("mut")
        val NO_SPACE = setOf("true", "false", "vector", "public(package)")
    }
}

/** Declarations visible from a position, innermost first. Shared by native completion and analyzer ranking. */
object MoveScopeNames {
    fun collect(from: PsiElement, ns: Namespace): List<PsiElement> {
        val out = mutableListOf<PsiElement>()
        var e: PsiElement? = from
        while (e != null) {
            when (e) {
                is MvCodeBlock -> e.stmtList.forEach { s -> s.letStmt?.let { out += MvScopes.bindingsOf(it.pat) } }
                is MvLambdaExpr -> e.lambdaParamList.lambdaParamList.forEach { out += MvScopes.bindingsOf(it.pat) }
                is MvMatchArm -> out += MvScopes.bindingsOf(e.pat)
                is MvFunction -> { out += e.params; e.typeParamList?.typeParamList?.let { out += it } }
                is MvStruct -> e.typeParamList?.typeParamList?.let { out += it }
                is MvEnum -> e.typeParamList?.typeParamList?.let { out += it }
                is MvModule -> {
                    out += e.items.filterIsInstance<MvNamedElement>()
                    MvModuleResolver.imports(e).forEach { imp -> imp.target().firstOrNull()?.let { out += it } }
                }
            }
            e = e.parent
        }
        return out.filter { MvScopes.matches(it, ns) }
    }

    /** Names of locals, parameters and type parameters (not module items) visible at [position]. */
    fun localNamesInScope(position: PsiElement): Set<String> =
        collect(position, Namespace.ANY).filter { it is MvBindingPat || it is MvFunctionParam || it is MvTypeParam || it is MvStructPatField }
            .mapNotNullTo(HashSet()) { (it as? MvNamedElement)?.name }

    /** Names of locals, parameters, type parameters and module items visible at [position]. */
    fun namesInScope(position: PsiElement): Set<String> =
        collect(position, Namespace.ANY).mapNotNullTo(HashSet()) { (it as? MvNamedElement)?.name }
}
