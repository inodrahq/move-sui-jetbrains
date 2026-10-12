package com.inodra.movesui.project

import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory

/** A `[pinned.<env>.<Name>]` entry of `Move.lock`: where the build resolved a dependency (MVR names included). */
data class MoveLockPackage(val name: String, val environment: String, val git: String?, val subdir: String?, val rev: String?, val local: String?, val isRoot: Boolean, val deps: Map<String, String>)

/** The parts of `Move.lock` (format version 4) the plugin uses to find dependency sources. */
data class MoveLock(val packages: List<MoveLockPackage>) {
    /** Resolved packages for one environment (the first one found when [environment] is null). */
    fun packagesFor(environment: String? = null): List<MoveLockPackage> {
        val env = environment ?: packages.firstOrNull { it.isRoot }?.environment ?: packages.firstOrNull()?.environment ?: return emptyList()
        return packages.filter { it.environment == env }
    }

    companion object {
        private val HEADER = Regex("""^\[pinned\.([^.\]]+)\.(.+)]$""")
        private val SOURCE_FIELD = Regex("""(\w+)\s*=\s*"([^"]*)"""")

        fun parse(text: String): MoveLock {
            val result = mutableListOf<MoveLockPackage>()
            var current: MutableMap<String, String>? = null
            var header: Pair<String, String>? = null
            fun flush() {
                val h = header ?: return
                val c = current ?: return
                val source = c["source"] ?: ""
                val fields = SOURCE_FIELD.findAll(source).associate { it.groupValues[1] to it.groupValues[2] }
                val deps = SOURCE_FIELD.findAll(c["deps"] ?: "").associate { it.groupValues[1] to it.groupValues[2] }
                result += MoveLockPackage(h.second.trim('"'), h.first, fields["git"], fields["subdir"], fields["rev"], fields["local"], source.contains("root = true"), deps)
            }
            for (raw in text.lines()) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                if (line.startsWith("[")) {
                    flush()
                    val m = HEADER.matchEntire(line)
                    header = m?.let { it.groupValues[1] to it.groupValues[2] }
                    current = if (m != null) mutableMapOf() else null
                    continue
                }
                val eq = line.indexOf('=')
                if (eq > 0) current?.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim())
            }
            flush()
            return MoveLock(result)
        }

        /** `~/.move/git/https___github_com_MystenLabs_sui_git_<rev>` – how the Move package system caches git dependencies. */
        fun gitCacheDir(git: String, rev: String, cacheRoot: Path = Paths.get(System.getProperty("user.home"), ".move", "git")): Path? {
            val mangled = git.replace(Regex("[^A-Za-z0-9]"), "_")
            val exact = cacheRoot.resolve("${mangled}_$rev")
            if (exact.isDirectory()) return exact
            // The lock may pin a branch name; fall back to any checkout of the same repository.
            return runCatching { java.nio.file.Files.list(cacheRoot).use { s -> s.filter { it.fileName.toString().startsWith("${mangled}_") && it.isDirectory() }.max(compareBy { java.nio.file.Files.getLastModifiedTime(it) }).orElse(null) } }.getOrNull()
        }
    }
}
