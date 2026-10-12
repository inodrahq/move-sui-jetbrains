package com.inodra.movesui.project

import com.inodra.movesui.MoveIcons
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/** Exposes dependency and framework sources as an external library so they are indexed and navigable. */
class MoveLibraryRootsProvider : AdditionalLibraryRootsProvider() {
    override fun getAdditionalProjectLibraries(project: Project): Collection<SyntheticLibrary> {
        val roots = MovePackageService.getInstance(project).dependencyRoots()
        if (roots.isEmpty()) return emptyList()
        return listOf(MoveSyntheticLibrary(roots))
    }

    override fun getRootsToWatch(project: Project): Collection<VirtualFile> =
        MovePackageService.getInstance(project).dependencyRoots()
}

class MoveSyntheticLibrary(private val roots: List<VirtualFile>) : SyntheticLibrary(), ItemPresentation {
    override fun getSourceRoots(): Collection<VirtualFile> = roots
    override fun getPresentableText() = "Move Dependencies"
    override fun getIcon(unused: Boolean): Icon = MoveIcons.SUI
    override fun equals(other: Any?) = other is MoveSyntheticLibrary && other.roots == roots
    override fun hashCode() = roots.hashCode()
}
