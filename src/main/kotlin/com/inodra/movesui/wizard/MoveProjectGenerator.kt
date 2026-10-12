package com.inodra.movesui.wizard

import com.inodra.movesui.MoveIcons
import com.inodra.movesui.MoveNotifications
import com.inodra.movesui.project.MovePackageService
import com.inodra.movesui.toolchain.MoveSettings
import com.inodra.movesui.toolchain.SuiToolchain
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.facet.ui.ValidationResult
import com.intellij.ide.util.projectWizard.AbstractNewProjectStep
import com.intellij.ide.util.projectWizard.CustomStepProjectGenerator
import com.intellij.ide.util.projectWizard.ProjectSettingsStepBase
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.DirectoryProjectGeneratorBase
import com.intellij.platform.GeneratorPeerImpl
import com.intellij.platform.ProjectGeneratorPeer
import com.intellij.ui.dsl.builder.*
import java.io.File
import javax.swing.Icon
import javax.swing.JComponent

data class MoveProjectSettings(var edition: String = "2024", var addTests: Boolean = true)

/** "New Project → Sui Move": runs `sui move new`, optionally adds a tests/ skeleton. Shown in RustRover, WebStorm, PyCharm… */
class MoveProjectGenerator : DirectoryProjectGeneratorBase<MoveProjectSettings>(), CustomStepProjectGenerator<MoveProjectSettings> {
    override fun getName() = "Sui Move"
    override fun getLogo(): Icon = MoveIcons.SUI
    override fun getDescription() = "A Move package for Sui, created with `sui move new`."

    override fun createPeer(): ProjectGeneratorPeer<MoveProjectSettings> = MoveGeneratorPeer()

    override fun createStep(projectGenerator: com.intellij.platform.DirectoryProjectGenerator<MoveProjectSettings>, callback: AbstractNewProjectStep.AbstractCallback<MoveProjectSettings>) =
        ProjectSettingsStepBase(projectGenerator, callback)

    override fun validate(baseDirPath: String): ValidationResult =
        if (MoveSettings.getInstance().toolchain == null) ValidationResult("Sui CLI not found. Install it with `suiup install sui` or set the path in Settings | Languages & Frameworks | Move for Sui.")
        else ValidationResult.OK

    override fun generateProject(project: Project, baseDir: VirtualFile, settings: MoveProjectSettings, module: Module) {
        val toolchain = MoveSettings.getInstance().toolchain ?: return
        ApplicationManager.getApplication().executeOnPooledThread {
            val error = scaffold(toolchain, File(baseDir.path), settings)
            VfsUtil.markDirtyAndRefresh(false, true, true, baseDir)
            if (error != null) MoveNotifications.error(project, "sui move new failed", error)
            MovePackageService.getInstance(project).invalidate()
            ApplicationManager.getApplication().invokeLater({
                baseDir.findFileByRelativePath("sources/${packageName(baseDir.name)}.move")?.let { com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(it, true) }
            }, project.disposed)
        }
    }

    companion object {
        fun packageName(dirName: String) = dirName.lowercase().replace(Regex("[^a-z0-9_]"), "_").trimStart('_').ifEmpty { "package" }

        /** Runs `sui move new <name>` into [dir]; returns an error message or null. */
        fun scaffold(toolchain: SuiToolchain, dir: File, settings: MoveProjectSettings): String? {
            dir.mkdirs()
            val name = packageName(dir.name)
            // `sui move new` refuses non-empty dirs only when Move.toml exists; create into the directory itself.
            val out = CapturingProcessHandler(toolchain.commandLine("move", "new", name, "--path", dir.absolutePath, workDir = dir.parentFile?.toPath())).runProcess(60_000)
            if (out.exitCode != 0) return out.stderr.ifBlank { out.stdout }
            val manifest = File(dir, "Move.toml")
            if (manifest.isFile && settings.edition != "2024") {
                manifest.writeText(manifest.readText().replace(Regex("edition\\s*=\\s*\"[^\"]*\""), "edition = \"${settings.edition}\""))
            }
            if (!settings.addTests) File(dir, "tests").deleteRecursively()
            return null
        }
    }
}

class MoveGeneratorPeer : GeneratorPeerImpl<MoveProjectSettings>() {
    private val settings = MoveProjectSettings()
    private var panel: DialogPanel? = null

    override fun getSettings(): MoveProjectSettings { panel?.apply(); return settings }

    override fun getComponent(myLocationField: com.intellij.openapi.ui.TextFieldWithBrowseButton, checkValid: Runnable): JComponent = panel {
        row("Edition:") {
            comboBox(listOf("2024", "2024.beta", "legacy")).bindItem({ settings.edition }, { settings.edition = it ?: "2024" })
        }
        row { checkBox("Create tests/ directory with a sample test").bindSelected({ settings.addTests }, { settings.addTests = it }) }
        row {
            val tc = MoveSettings.getInstance().toolchain
            comment(if (tc == null) "Sui CLI not found; the package will not be created." else "Uses ${tc.suiPath}")
        }
    }.also { panel = it }

    override fun validate(): com.intellij.openapi.ui.ValidationInfo? =
        if (MoveSettings.getInstance().toolchain == null) com.intellij.openapi.ui.ValidationInfo("Sui CLI not found") else null
}
