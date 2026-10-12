package com.inodra.movesui.resolve

import com.inodra.movesui.lang.MoveFile
import com.inodra.movesui.lang.psi.*
import com.inodra.movesui.lang.psi.ext.*
import com.inodra.movesui.lang.stubs.index.MvModuleIndex
import com.inodra.movesui.project.MoveDependencyRoots
import com.inodra.movesui.project.MovePackageService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker

/** A name made visible in a module by a `use` item (or an implicit import). */
data class ImportEntry(val name: String, val target: () -> List<PsiElement>, val source: PsiElement?)

object MvModuleResolver {
    private val NUMERIC_ALIASES: Map<String, String> = MoveDependencyRoots.FRAMEWORK_ADDRESSES.entries.associate { (k, v) -> v to k }

    /** Sui Move 2024 implicit imports, as `address::module::item?` triples. */
    private val IMPLICIT_IMPORTS = listOf(
        Triple("std", "vector", null),
        Triple("std", "option", null), Triple("std", "option", "Option"),
        Triple("sui", "object", null), Triple("sui", "object", "ID"), Triple("sui", "object", "UID"),
        Triple("sui", "transfer", null),
        Triple("sui", "tx_context", null), Triple("sui", "tx_context", "TxContext"),
    )

    fun searchScope(project: Project): GlobalSearchScope = GlobalSearchScope.allScope(project)

    /** Modules named [name] whose declared address matches [address] (named or numeric, `null` = any). */
    fun findModules(project: Project, address: String?, name: String): List<MvModule> {
        val all = MvModuleIndex.find(project, name, searchScope(project)).toList()
        if (address == null || all.size <= 1) return all
        val wanted = canonicalAddress(address)
        val matched = all.filter { canonicalAddress(it.addressName ?: "") == wanted }
        return matched.ifEmpty { all }
    }

    /** Named-address aliases map to one canonical form: `0x2` and `sui` compare equal. */
    fun canonicalAddress(a: String): String {
        val lower = a.lowercase()
        NUMERIC_ALIASES[normalizeHex(lower)]?.let { return it }
        return lower
    }

    private fun normalizeHex(a: String): String {
        if (!a.startsWith("0x")) return a
        val digits = a.removePrefix("0x").trimStart('0')
        return "0x" + digits.ifEmpty { "0" }
    }

    fun isLikelyAddress(name: String, project: Project): Boolean =
        name.startsWith("0x") || name in MoveDependencyRoots.FRAMEWORK_ADDRESSES ||
            MovePackageService.getInstance(project).packages().any { name in it.namedAddresses }

    /** All `use` imports of a module (including block-level legacy `use`s are handled by scopes). */
    fun imports(module: MvModule): List<ImportEntry> = CachedValuesManager.getCachedValue(module) {
        CachedValueProvider.Result.create(computeImports(module), PsiModificationTracker.MODIFICATION_COUNT)
    }

    private fun computeImports(module: MvModule): List<ImportEntry> {
        val entries = mutableListOf<ImportEntry>()
        module.useStmts.forEach { entries += importsOf(it) }
        val project = module.project
        for ((addr, mod, item) in IMPLICIT_IMPORTS) {
            val name = item ?: mod
            entries += ImportEntry(name, { resolveQualified(project, addr, mod, item) }, null)
        }
        // `Self` names the current module.
        entries += ImportEntry("Self", { listOf(module) }, null)
        return entries
    }

    fun importsOf(use: MvUseStmt): List<ImportEntry> {
        val root = use.useSpeck ?: return emptyList()
        val result = mutableListOf<ImportEntry>()
        collectLeaves(root, emptyList(), result)
        return result
    }

    private fun collectLeaves(speck: MvUseSpeck, prefix: List<String>, out: MutableList<ImportEntry>) {
        val name = speck.useSpeckName.text
        val path = prefix + name
        val group = speck.useGroup
        val child = speck.useSpeck
        when {
            group != null -> group.useSpeckList.forEach { collectLeaves(it, path, out) }
            child != null -> collectLeaves(child, path, out)
            else -> {
                val alias = speck.useAlias?.nameIdentifier?.text
                if (alias == "_") return
                val visible = alias ?: if (name == "Self") path.getOrNull(path.size - 2) ?: return else name
                out += ImportEntry(visible, { resolveUsePath(speck.project, path) }, speck)
            }
        }
    }

    /** Resolves a full use path like `[sui, coin, Coin]` or `[sui, coin, Self]`. */
    fun resolveUsePath(project: Project, path: List<String>): List<PsiElement> {
        if (path.isEmpty()) return emptyList()
        val segs = if (path.last() == "Self") path.dropLast(1) else path
        return when (segs.size) {
            1 -> emptyList()
            2 -> findModules(project, segs[0], segs[1])
            3 -> resolveQualified(project, segs[0], segs[1], segs[2])
            else -> {
                // address::module::Enum::Variant
                val enums = resolveQualified(project, segs[0], segs[1], segs[2]).filterIsInstance<MvEnum>()
                enums.flatMap { e -> e.variants.filter { it.name == segs[3] } }
            }
        }
    }

    fun resolveQualified(project: Project, address: String, moduleName: String, item: String?): List<PsiElement> {
        val modules = findModules(project, address, moduleName)
        if (item == null) return modules
        return modules.flatMap { m -> m.itemsNamed(item) }
    }

    /** Resolves a `use` speck segment itself (for navigation from inside a `use`). */
    fun resolveUseSpeck(speck: MvUseSpeck): List<PsiElement> {
        val path = mutableListOf<String>()
        var cur: MvUseSpeck? = speck
        while (cur != null) {
            path.add(0, cur.useSpeckName.text)
            cur = cur.parent.let { p -> (p as? MvUseSpeck) ?: (p as? MvUseGroup)?.parent as? MvUseSpeck }
        }
        if (path.size == 1) return emptyList() // an address
        return resolveUsePath(speck.project, path)
    }
}

fun MvModule.itemsNamed(name: String): List<PsiElement> =
    items.filter { it is MvNamedElement && it.name == name }

/** Items of a module, from stubs when the file is not loaded. */
val MvModule.stubItems: List<PsiElement>
    get() {
        val stub = (this as? com.intellij.extapi.psi.StubBasedPsiElementBase<*>)?.greenStub
        if (stub != null) return stub.childrenStubs.map { it.psi }
        return items
    }
