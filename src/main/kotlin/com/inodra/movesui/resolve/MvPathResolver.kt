package com.inodra.movesui.resolve

import com.inodra.movesui.lang.psi.*
import com.inodra.movesui.lang.psi.ext.*
import com.inodra.movesui.lang.stubs.index.MvNamedElementIndex
import com.intellij.psi.PsiElement

object MvPathResolver {
    fun namespaceOf(path: MvPath): Namespace = when (path.parent) {
        is MvPathType -> Namespace.TYPE
        is MvPathExpr, is MvCallExpr, is MvMacroCallExpr, is MvStructLitExpr, is MvAttrValue -> Namespace.VALUE
        is MvPathPat, is MvStructPat, is MvTupleStructPat -> Namespace.VALUE
        is MvUseFun, is MvUseFunTarget, is MvFriendDecl, is MvAcquiresList -> Namespace.ANY
        else -> Namespace.ANY
    }

    fun resolveSegment(segment: MvPathSegment): List<PsiElement> {
        val path = segment.parent as? MvPath ?: return emptyList()
        val segments = path.segments
        val index = segments.indexOf(segment)
        if (index < 0) return emptyList()
        return resolve(path, index)
    }

    fun resolve(path: MvPath, index: Int): List<PsiElement> {
        val segments = path.segments
        val name = segments[index].referenceName ?: return emptyList()
        val project = path.project
        val isLast = index == segments.lastIndex
        val ns = if (isLast) namespaceOf(path) else Namespace.ANY

        if (index == 0) {
            if (segments[0].syntaxIdentifier != null) {
                // `$x` / `$T` inside a macro body refer to the macro's parameters.
                return MvScopes.lookup(path, name, Namespace.ANY).filter { it is MvFunctionParam || it is MvTypeParam }
            }
            val lexical = MvScopes.lookup(path, name, if (segments.size > 1) Namespace.ANY else ns)
            if (lexical.isNotEmpty()) return lexical
            if (segments.size > 1 && segments.size <= 2) {
                // `addr::module` where addr is a named/numeric address: the address itself has no declaration.
                return emptyList()
            }
            return emptyList()
        }

        val prev = resolve(path, index - 1)
        if (prev.isEmpty()) {
            // First segment may be an address: `sui::coin` or `0x2::coin::Coin`.
            if (index == 1) {
                val address = segments[0].referenceName ?: return emptyList()
                return MvModuleResolver.findModules(project, address, name)
            }
            return emptyList()
        }
        return prev.flatMap { members(it, name, ns) }.distinct()
    }

    fun members(owner: PsiElement, name: String, ns: Namespace): List<PsiElement> = when (owner) {
        is MvModule -> owner.itemsNamed(name).filter { MvScopes.matches(it, ns) }
        is MvEnum -> owner.variants.filter { it.name == name }
        is MvStruct -> emptyList()
        else -> emptyList()
    }

    /** Method call `recv.name(...)`: functions with that name in the current module, imports, then everywhere. */
    fun resolveMethod(call: MvMethodCallExpr): List<PsiElement> {
        val name = call.identifier?.text ?: return emptyList()
        val module = call.ancestorStrict<MvModule>()
        val local = module?.let { m ->
            m.itemsNamed(name).filterIsInstance<MvFunction>() +
                MvModuleResolver.imports(m).flatMap { imp -> imp.target().filterIsInstance<MvModule>() }.flatMap { it.itemsNamed(name) }.filterIsInstance<MvFunction>()
        } ?: emptyList()
        if (local.isNotEmpty()) return local.distinct()
        return MvNamedElementIndex.find(call.project, name, MvModuleResolver.searchScope(call.project))
            .filterIsInstance<MvFunction>().filter { it.params.isNotEmpty() }
    }

    /** Fields of the struct (or enum variant) that a literal / pattern path names. */
    fun fieldsOf(path: MvPath?): List<MvNamedField> {
        val target = path?.lastSegment?.let { resolveSegment(it) }?.firstOrNull() ?: return emptyList()
        return when (target) {
            is MvStruct -> target.fields
            is MvEnumVariant -> target.namedFieldList?.namedFieldList ?: emptyList()
            else -> emptyList()
        }
    }

    fun resolveLabelUsage(label: MvLabel): List<PsiElement> {
        val name = label.name ?: return emptyList()
        var e: PsiElement? = label.parent
        while (e != null) {
            if (e is MvLabeledExpr && e.label !== label && e.label.name == name) return listOf(e.label)
            if (e is MvFunction || e is MvLambdaExpr) return emptyList()
            e = e.parent
        }
        return emptyList()
    }
}
