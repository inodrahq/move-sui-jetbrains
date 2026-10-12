package com.inodra.movesui.wizard

import com.inodra.movesui.MoveIcons
import com.inodra.movesui.MoveNotifications
import com.inodra.movesui.toolchain.MoveSettings
import com.intellij.ide.wizard.AbstractNewProjectWizardStep
import com.intellij.ide.wizard.GeneratorNewProjectWizard
import com.intellij.ide.wizard.GeneratorNewProjectWizardBuilderAdapter
import com.intellij.ide.wizard.NewProjectWizardBaseStep
import com.intellij.ide.wizard.NewProjectWizardStep
import com.intellij.ide.wizard.RootNewProjectWizardStep
import com.intellij.ide.wizard.NewProjectWizardChainStep.Companion.nextStep
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import java.io.File
import javax.swing.Icon

/** IntelliJ IDEA's unified New Project dialog entry. */
class MoveNewProjectWizard : GeneratorNewProjectWizard {
    override val id = "com.inodra.movesui.newProject"
    override val name = "Sui Move"
    override val icon: Icon = MoveIcons.SUI
    override val ordinal = 900

    override fun createStep(context: com.intellij.ide.util.projectWizard.WizardContext): NewProjectWizardStep =
        RootNewProjectWizardStep(context).nextStep(::NewProjectWizardBaseStep).nextStep(::MoveStep)

    class MoveStep(parent: NewProjectWizardBaseStep) : AbstractNewProjectWizardStep(parent) {
        private val settings = MoveProjectSettings()
        private val base = parent

        override fun setupUI(builder: Panel) {
            with(builder) {
                row("Edition:") { comboBox(listOf("2024", "2024.beta", "legacy")).bindItem({ settings.edition }, { settings.edition = it ?: "2024" }) }
                row { checkBox("Create tests/ directory with a sample test").bindSelected({ settings.addTests }, { settings.addTests = it }) }
                row {
                    val tc = MoveSettings.getInstance().toolchain
                    comment(if (tc == null) "Sui CLI not found; configure it in Settings | Languages &amp; Frameworks | Move for Sui." else "Uses ${tc.suiPath}")
                }
            }
        }

        override fun setupProject(project: Project) {
            val toolchain = MoveSettings.getInstance().toolchain ?: run { MoveNotifications.error(project, "Sui CLI not found", "The package could not be created."); return }
            val dir = File(base.path, base.name)
            ApplicationManager.getApplication().executeOnPooledThread {
                val error = MoveProjectGenerator.scaffold(toolchain, dir, settings)
                LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dir)?.let { VfsUtil.markDirtyAndRefresh(false, true, true, it) }
                if (error != null) MoveNotifications.error(project, "sui move new failed", error)
            }
        }
    }
}

class MoveModuleBuilder : GeneratorNewProjectWizardBuilderAdapter(MoveNewProjectWizard())
