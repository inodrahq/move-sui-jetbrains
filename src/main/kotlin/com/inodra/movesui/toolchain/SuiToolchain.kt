package com.inodra.movesui.toolchain

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.EnvironmentUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.isExecutable
import kotlin.io.path.isRegularFile

/** A `sui` CLI installation. */
data class SuiToolchain(val suiPath: Path) {
    val version: SuiVersion? get() = versionCache.getOrPut(suiPath) { probeVersion() ?: SuiVersion.UNKNOWN }.takeIf { it != SuiVersion.UNKNOWN }

    fun commandLine(vararg args: String, workDir: Path? = null): GeneralCommandLine =
        GeneralCommandLine(suiPath.toString(), *args)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
            .withEnvironment("PATH", pathWithToolchainDirs())
            .also { if (workDir != null) it.withWorkingDirectory(workDir) }

    fun analyzerCommandLine(workDir: Path?): GeneralCommandLine = commandLine("analyzer", workDir = workDir)

    private fun probeVersion(): SuiVersion? = runCatching {
        val output = CapturingProcessHandler(commandLine("--version")).runProcess(10_000)
        if (output.exitCode != 0) return null
        SuiVersion.parse(output.stdout.trim())
    }.getOrNull()

    private fun pathWithToolchainDirs(): String {
        val existing = EnvironmentUtil.getValue("PATH") ?: System.getenv("PATH") ?: ""
        val extra = SuiToolchainLocator.knownBinDirs().map { it.toString() }
        return (listOf(suiPath.parent.toString()) + extra + existing.split(java.io.File.pathSeparator)).distinct().joinToString(java.io.File.pathSeparator)
    }

    /** Below this the analyzer lacks settings support and implicit framework dependencies; features degrade. */
    val isSupported: Boolean get() = version?.let { it >= MIN_SUPPORTED } ?: true

    companion object {
        val MIN_SUPPORTED = SuiVersion(1, 45, 0, null)
        private val versionCache = ConcurrentHashMap<Path, SuiVersion>()
        fun invalidateCaches() = versionCache.clear()

        fun fromPath(path: String?): SuiToolchain? {
            val p = path?.takeIf { it.isNotBlank() }?.let { Paths.get(it) } ?: return null
            return if (p.isRegularFile() && p.isExecutable()) SuiToolchain(p) else null
        }
    }
}

data class SuiVersion(val major: Int, val minor: Int, val patch: Int, val commit: String?) : Comparable<SuiVersion> {
    override fun compareTo(other: SuiVersion) = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
    override fun toString() = "$major.$minor.$patch" + (commit?.let { "-$it" } ?: "")

    companion object {
        val UNKNOWN = SuiVersion(0, 0, 0, null)
        private val REGEX = Regex("""(\d+)\.(\d+)\.(\d+)(?:-([0-9a-f]+))?""")

        /** Parses `sui 1.80.1-671ba71e69c7`. */
        fun parse(text: String): SuiVersion? = REGEX.find(text)?.let { m ->
            SuiVersion(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), m.groupValues[4].ifEmpty { null })
        }
    }
}

object SuiToolchainLocator {
    private val home: Path get() = Paths.get(System.getProperty("user.home"))

    fun knownBinDirs(): List<Path> = listOfNotNull(
        home.resolve(".local/bin"),
        home.resolve(".sui/bin"),
        home.resolve(".cargo/bin"),
        Paths.get("/opt/homebrew/bin").takeIf { SystemInfo.isMac },
        Paths.get("/usr/local/bin").takeUnless { SystemInfo.isWindows },
    )

    private fun exe(name: String) = if (SystemInfo.isWindows) "$name.exe" else name

    fun findExecutable(name: String): Path? {
        val dirs = (EnvironmentUtil.getValue("PATH") ?: System.getenv("PATH") ?: "")
            .split(java.io.File.pathSeparator).filter { it.isNotBlank() }.map { Paths.get(it) } + knownBinDirs()
        return dirs.asSequence().map { it.resolve(exe(name)) }.firstOrNull { Files.isRegularFile(it) && Files.isExecutable(it) }
    }

    fun findSui(): SuiToolchain? = findExecutable("sui")?.let { SuiToolchain(it) }
    fun findSuiup(): Path? = findExecutable("suiup")
    fun findMoveAnalyzer(): Path? = findExecutable("move-analyzer")
}
