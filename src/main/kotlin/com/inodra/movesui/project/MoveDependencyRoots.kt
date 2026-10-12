package com.inodra.movesui.project

import com.inodra.movesui.toolchain.MoveSettings
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * Locates source directories of a package's dependencies, including the implicit Sui framework packages.
 * Order of preference: `build/<pkg>/sources/dependencies` (exact, produced by `sui move build`), then the
 * local `~/.move` git cache (framework matched against the toolchain commit), then `local = ".."` paths.
 */
object MoveDependencyRoots {
    private val home: Path get() = Paths.get(System.getProperty("user.home"))
    private val moveCache: Path get() = home.resolve(".move/git")

    val FRAMEWORK_PACKAGES = listOf("move-stdlib", "sui-framework", "sui-system", "bridge", "deepbook")

    /** Named-address → package directory alias table for framework packages. */
    val FRAMEWORK_ADDRESSES = mapOf(
        "std" to "0x1", "sui" to "0x2", "sui_system" to "0x3", "bridge" to "0xb", "deepbook" to "0xdee9",
    )

    fun dependencySourceRoots(pkg: MovePackage): List<VirtualFile> {
        val roots = linkedSetOf<VirtualFile>()
        val fs = LocalFileSystem.getInstance()

        // 1. Build output: sources/dependencies/<Dep>/*.move
        pkg.buildDir?.children?.forEach { built ->
            built.findFileByRelativePath("sources/dependencies")?.children?.forEach { dep -> if (dep.isDirectory) roots += dep }
        }

        // 2. Move.lock pins (git deps and MVR names resolved by the last build/update-deps) in the ~/.move cache.
        pkg.root.findChild("Move.lock")?.let { lock ->
            val text = runCatching { String(lock.contentsToByteArray(), Charsets.UTF_8) }.getOrNull()
            if (text != null) {
                for (dep in MoveLock.parse(text).packagesFor()) {
                    if (dep.isRoot) continue
                    val dir = when {
                        dep.git != null && dep.rev != null -> MoveLock.gitCacheDir(dep.git, dep.rev)?.resolve(dep.subdir ?: "")
                        dep.local != null -> pkg.root.toNioPath().resolve(dep.local).normalize()
                        else -> null
                    } ?: continue
                    val sources = dir.resolve("sources")
                    if (sources.isDirectory()) fs.findFileByNioFile(sources)?.let { roots += it }
                }
            }
        }

        // 3. Local path dependencies.
        for (dep in pkg.manifest.dependencies + pkg.manifest.devDependencies) {
            val local = dep.local ?: continue
            val dir = pkg.root.findFileByRelativePath(local) ?: fs.findFileByNioFile(pkg.root.toNioPath().resolve(local).normalize())
            dir?.findChild("sources")?.let { roots += it }
        }

        // 4. Framework from the git cache when nothing above provided it.
        if (roots.none { it.name == "Sui" || it.name == "sui-framework" || it.path.contains("sui-framework/packages") }) {
            frameworkPackagesDir()?.let { fwDir ->
                FRAMEWORK_PACKAGES.forEach { name ->
                    val sources = fwDir.resolve(name).resolve("sources")
                    if (sources.isDirectory()) fs.findFileByNioFile(sources)?.let { roots += it }
                }
            }
        }
        return roots.toList()
    }

    /** `~/.move/git/<sui checkout>/crates/sui-framework/packages`, preferring the checkout matching the toolchain commit. */
    fun frameworkPackagesDir(): Path? {
        if (!moveCache.isDirectory()) return null
        val commit = MoveSettings.getInstance().toolchain?.version?.commit
        val checkouts = Files.list(moveCache).use { stream ->
            stream.filter { it.name.startsWith("https___github_com_MystenLabs_sui_git_") }.toList()
        }
        val candidates = checkouts.map { it.resolve("crates/sui-framework/packages") }.filter { it.isDirectory() }
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { commit != null && it.parent.parent.parent.name.endsWith(commit) || (commit != null && it.parent.parent.parent.name.contains(commit)) }
            ?: candidates.maxByOrNull { Files.getLastModifiedTime(it).toMillis() }
    }
}
