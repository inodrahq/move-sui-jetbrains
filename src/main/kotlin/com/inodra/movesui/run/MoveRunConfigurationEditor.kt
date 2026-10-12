package com.inodra.movesui.run

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent

class MoveRunConfigurationEditor(private val project: Project) : SettingsEditor<MoveRunConfiguration>() {
    private var panel: DialogPanel? = null
    private var command = MoveCommand.TEST
    private var packagePath = ""
    private var testFilter = ""
    private var extraArgs = ""
    private var coverage = false
    private var lint = false
    private var warningsAsErrors = false
    private var trace = false
    private var gasLimit = ""
    private var seed = ""

    override fun createEditor(): JComponent = panel {
        row("Command:") {
            comboBox(MoveCommand.entries, com.intellij.ui.dsl.listCellRenderer.textListCellRenderer { it?.display })
                .bindItem({ command }, { command = it ?: MoveCommand.TEST })
        }
        row("Package directory:") {
            textFieldWithBrowseButton(FileChooserDescriptorFactory.singleDir().withTitle("Select Move Package Directory"), project)
                .bindText(::packagePath).align(AlignX.FILL)
                .comment("Directory containing Move.toml; defaults to the project root.")
        }
        row("Test filter:") { textField().bindText(::testFilter).align(AlignX.FILL).comment("Only tests whose fully qualified name contains this string run (sui move test &lt;filter&gt;).") }
        row("Additional arguments:") { expandableTextField().bindText(::extraArgs).align(AlignX.FILL) }
        row("Gas limit:") { textField().bindText(::gasLimit).columns(12) }
        row("Seed:") { textField().bindText(::seed).columns(12) }
        row {
            checkBox("Collect coverage").bindSelected(::coverage)
            checkBox("Generate traces").bindSelected(::trace)
            checkBox("Run extra lints").bindSelected(::lint)
            checkBox("Warnings are errors").bindSelected(::warningsAsErrors)
        }
    }.also { panel = it }

    override fun resetEditorFrom(c: MoveRunConfiguration) {
        command = c.command; packagePath = c.packagePath; testFilter = c.testFilter; extraArgs = c.extraArgs
        coverage = c.options.coverage; lint = c.options.lint; warningsAsErrors = c.options.warningsAsErrors; trace = c.options.trace
        gasLimit = c.options.gasLimit ?: ""; seed = c.options.seed ?: ""
        panel?.reset()
    }

    override fun applyEditorTo(c: MoveRunConfiguration) {
        panel?.apply()
        c.command = command; c.packagePath = packagePath; c.testFilter = testFilter; c.extraArgs = extraArgs
        c.options.coverage = coverage; c.options.lint = lint; c.options.warningsAsErrors = warningsAsErrors; c.options.trace = trace
        c.options.gasLimit = gasLimit; c.options.seed = seed
    }
}
