package com.inodra.movesui.project

import com.inodra.movesui.lang.psi.ext.addressName
import com.inodra.movesui.lang.stubs.index.MvModuleIndex
import com.inodra.movesui.resolve.MvModuleResolver
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.stubs.StubIndex

/** Named addresses a file can use as the first segment of a `use` path. */
object MoveNamedAddresses {
    /** Always present on Sui. */
    val IMPLICIT = listOf("std", "sui")

    data class Entry(val name: String, val origin: String)

    fun forFile(project: Project, file: VirtualFile): List<Entry> {
        val pkg = MovePackage.forFile(file)
        val entries = linkedMapOf<String, Entry>()
        fun add(name: String, origin: String) { if (name.isNotBlank()) entries.putIfAbsent(name, Entry(name, origin)) }
        IMPLICIT.forEach { add(it, "implicit") }
        if (pkg != null) {
            pkg.manifest.name?.let { add(it.lowercase().replace('-', '_'), "this package") }
            pkg.manifest.addresses.keys.forEach { add(it, "Move.toml [addresses]") }
            (pkg.manifest.dependencies + pkg.manifest.devDependencies).forEach { add(it.name, "Move.toml dependency" + (it.mvr?.let { m -> " $m" } ?: "")) }
            pkg.root.findChild("Move.lock")?.let { lock ->
                val text = runCatching { String(lock.contentsToByteArray(), Charsets.UTF_8) }.getOrNull() ?: return@let
                MoveLock.parse(text).packagesFor().firstOrNull { it.isRoot }?.deps?.forEach { (alias, pkgName) -> add(alias, "Move.lock → $pkgName") }
            }
        }
        MoveDependencyRoots.FRAMEWORK_ADDRESSES.keys.forEach { add(it, "Sui framework") }
        return entries.values.toList()
    }

    fun isKnown(project: Project, file: VirtualFile, name: String): Boolean {
        if (name.startsWith("0x")) return true
        if (forFile(project, file).any { it.name == name }) return true
        // A module declared under that address anywhere in project or libraries.
        val wanted = MvModuleResolver.canonicalAddress(name)
        return StubIndex.getInstance().getAllKeys(MvModuleIndex.KEY, project).any { moduleName ->
            MvModuleIndex.find(project, moduleName, MvModuleResolver.searchScope(project)).any { m -> m.addressName?.let { MvModuleResolver.canonicalAddress(it) } == wanted }
        }
    }
}
