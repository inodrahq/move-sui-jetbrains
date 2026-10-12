package com.inodra.movesui.project

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.AsyncFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import java.util.concurrent.atomic.AtomicReference

/** Discovers Move packages in the project (directories with `Move.toml`) and their dependency roots. */
@Service(Service.Level.PROJECT)
class MovePackageService(private val project: Project) {
    private val packagesCache = AtomicReference<List<MovePackage>?>(null)

    fun packages(): List<MovePackage> = packagesCache.get() ?: discover().also { packagesCache.set(it) }

    fun packageFor(file: VirtualFile): MovePackage? = packages().firstOrNull { VfsUtilCore.isAncestor(it.root, file, false) } ?: MovePackage.forFile(file)

    fun dependencyRoots(): List<VirtualFile> = packages().flatMap { MoveDependencyRoots.dependencySourceRoots(it) }.distinct()

    fun invalidate() {
        packagesCache.set(null)
        ApplicationManager.getApplication().invokeLater({
            if (project.isDisposed) return@invokeLater
            WriteAction.run<RuntimeException> {
                ProjectRootManagerEx.getInstanceEx(project).makeRootsChange(EmptyRunnable.INSTANCE, com.intellij.openapi.project.RootsChangeRescanningInfo.TOTAL_RESCAN)
            }
        }, project.disposed)
    }

    // Must not touch ProjectFileIndex: this runs while roots are being computed (AdditionalLibraryRootsProvider).
    private fun discover(): List<MovePackage> {
        val result = mutableListOf<MovePackage>()
        val base = project.basePath?.let { LocalFileSystem.getInstance().findFileByPath(it) } ?: return result
        VfsUtilCore.visitChildrenRecursively(base, object : VirtualFileVisitor<Unit>(limit(8)) {
            override fun visitFile(file: VirtualFile): Boolean {
                if (file.isDirectory) {
                    if (file.name in SKIP_DIRS || file.name.startsWith(".")) return false
                    val manifest = file.findChild(MovePackage.MANIFEST)
                    if (manifest != null) result += MovePackage(file, MoveManifest.parse(manifest))
                }
                return true
            }
        })
        return result
    }

    companion object {
        private val SKIP_DIRS = setOf("build", "node_modules", "target", ".git", ".idea", ".gradle")
        fun getInstance(project: Project): MovePackageService = project.getService(MovePackageService::class.java)
    }
}

/** Re-scans packages whenever a `Move.toml` changes. */
class MoveManifestChangeListener : AsyncFileListener {
    override fun prepareChange(events: MutableList<out VFileEvent>): AsyncFileListener.ChangeApplier? {
        if (events.none { it.path.endsWith("/" + MovePackage.MANIFEST) || it.path.contains("/build/") && it.path.endsWith(".move") }) return null
        return object : AsyncFileListener.ChangeApplier {
            override fun afterVfsChange() {
                com.intellij.openapi.project.ProjectManager.getInstance().openProjects.forEach { p ->
                    if (!p.isDisposed) MovePackageService.getInstance(p).invalidate()
                }
            }
        }
    }
}
