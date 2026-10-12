package com.inodra.movesui.project

import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

/** A Move package rooted at a directory containing `Move.toml`. */
data class MovePackage(val root: VirtualFile, val manifest: MoveManifest) {
    val name: String get() = manifest.name ?: root.name
    val sourcesDir: VirtualFile? get() = root.findChild("sources")
    val testsDir: VirtualFile? get() = root.findChild("tests")
    val buildDir: VirtualFile? get() = root.findChild("build")

    /** Named addresses visible to this package: `[addresses]` plus the package name itself. */
    val namedAddresses: Map<String, String>
        get() = manifest.addresses + (manifest.name?.let { mapOf(it to "_") } ?: emptyMap())

    companion object {
        const val MANIFEST = "Move.toml"

        fun forFile(file: VirtualFile): MovePackage? {
            var dir = if (file.isDirectory) file else file.parent
            while (dir != null) {
                val manifest = dir.findChild(MANIFEST)
                if (manifest != null) return MovePackage(dir, MoveManifest.parse(manifest))
                dir = dir.parent
            }
            return null
        }
    }
}

data class MoveDependency(val name: String, val local: String?, val git: String?, val rev: String?, val subdir: String?, val mvr: String?)

/**
 * The parts of `Move.toml` the plugin cares about. Parsed with a tiny TOML reader so the plugin
 * does not depend on the TOML plugin being present.
 */
data class MoveManifest(
    val name: String?,
    val edition: String?,
    val addresses: Map<String, String>,
    val dependencies: List<MoveDependency>,
    val devDependencies: List<MoveDependency>,
) {
    companion object {
        fun parse(file: VirtualFile): MoveManifest = runCatching { parse(String(file.contentsToByteArray(), Charsets.UTF_8)) }
            .getOrDefault(MoveManifest(null, null, emptyMap(), emptyList(), emptyList()))

        fun parse(text: String): MoveManifest {
            var section = ""
            var name: String? = null
            var edition: String? = null
            val addresses = linkedMapOf<String, String>()
            val deps = mutableListOf<MoveDependency>()
            val devDeps = mutableListOf<MoveDependency>()
            for (raw in text.lines()) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                if (line.startsWith("[")) {
                    section = line.trim('[', ']').trim()
                    // `[dependencies.Foo]` table form
                    if (section.startsWith("dependencies.") || section.startsWith("dev-dependencies.")) {
                        val isDev = section.startsWith("dev-")
                        val depName = section.substringAfter("dependencies.").trim('"')
                        (if (isDev) devDeps else deps) += MoveDependency(depName, null, null, null, null, null)
                        section = if (isDev) "dev-dependencies.table" else "dependencies.table"
                    }
                    continue
                }
                val eq = line.indexOf('=')
                if (eq < 0) continue
                val key = line.substring(0, eq).trim().trim('"')
                val value = line.substring(eq + 1).trim()
                when (section) {
                    "package" -> when (key) {
                        "name" -> name = unquote(value)
                        "edition" -> edition = unquote(value)
                    }
                    "addresses", "dev-addresses" -> addresses[key] = unquote(value)
                    "dependencies" -> deps += parseInlineDep(key, value)
                    "dev-dependencies" -> devDeps += parseInlineDep(key, value)
                    "dependencies.table" -> deps.lastOrNull()?.let { deps[deps.lastIndex] = withField(it, key, unquote(value)) }
                    "dev-dependencies.table" -> devDeps.lastOrNull()?.let { devDeps[devDeps.lastIndex] = withField(it, key, unquote(value)) }
                }
            }
            return MoveManifest(name, edition, addresses, deps, devDeps)
        }

        private fun unquote(v: String) = v.trim().removeSurrounding("\"").removeSurrounding("'")

        private fun parseInlineDep(name: String, value: String): MoveDependency {
            var dep = MoveDependency(name, null, null, null, null, null)
            val inner = value.trim().removePrefix("{").removeSuffix("}")
            // split on commas outside quotes
            val parts = mutableListOf<String>(); val sb = StringBuilder(); var inQ = false
            for (c in inner) { if (c == '"') inQ = !inQ; if (c == ',' && !inQ) { parts += sb.toString(); sb.clear() } else sb.append(c) }
            parts += sb.toString()
            for (part in parts) {
                val eq = part.indexOf('=')
                if (eq < 0) continue
                dep = withField(dep, part.substring(0, eq).trim(), unquote(part.substring(eq + 1)))
            }
            return dep
        }

        private fun withField(dep: MoveDependency, key: String, value: String) = when (key) {
            "local" -> dep.copy(local = value)
            "git" -> dep.copy(git = value)
            "rev" -> dep.copy(rev = value)
            "subdir" -> dep.copy(subdir = value)
            "r.mvr" -> dep.copy(mvr = value)
            else -> dep
        }
    }
}
