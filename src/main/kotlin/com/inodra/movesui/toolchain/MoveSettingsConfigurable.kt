package com.inodra.movesui.toolchain

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.*
import java.nio.file.Path

class MoveSettingsConfigurable : BoundConfigurable("Move for Sui") {
    private val settings get() = MoveSettings.getInstance()
    private var fingerprintBefore = ""
    private val versionLabel = JBLabel()

    override fun createPanel(): DialogPanel {
        fingerprintBefore = settings.analyzerFingerprint()
        val state = settings.state
        return panel {
            group("Sui Toolchain") {
                row("Sui executable:") {
                    textFieldWithBrowseButton(FileChooserDescriptorFactory.singleFile().withTitle("Select Sui Executable"))
                        .bindText({ state.suiPath ?: "" }, { state.suiPath = it.ifBlank { null } })
                        .align(AlignX.FILL)
                        .comment("Leave empty to auto-detect from PATH, ~/.local/bin, ~/.sui/bin, ~/.cargo/bin or Homebrew. The analyzer, builds and tests all use this binary; sui ${SuiToolchain.MIN_SUPPORTED}+ is supported.")
                        .applyToComponent { textField.document.addDocumentListener(object : javax.swing.event.DocumentListener {
                            override fun insertUpdate(e: javax.swing.event.DocumentEvent) = refreshVersion(text)
                            override fun removeUpdate(e: javax.swing.event.DocumentEvent) = refreshVersion(text)
                            override fun changedUpdate(e: javax.swing.event.DocumentEvent) = refreshVersion(text)
                        }) }
                }
                row("Detected version:") { cell(versionLabel) }
                row {
                    button("Install or Update via suiup") { SuiupInstaller.installSui(null) { refreshVersion(state.suiPath ?: "") } }
                        .comment(SuiToolchainLocator.findSuiup()?.let { "Uses $it" } ?: "suiup was not found on PATH")
                }
            }
            group("Move Analyzer") {
                row("Analyzer executable:") {
                    textFieldWithBrowseButton(FileChooserDescriptorFactory.singleFile().withTitle("Select move-analyzer Executable"))
                        .bindText({ state.analyzerPath ?: "" }, { state.analyzerPath = it.ifBlank { null } })
                        .align(AlignX.FILL)
                        .comment("Leave empty to use <code>sui analyzer</code> from the toolchain above.")
                }
                row("Lint level:") {
                    comboBox(LintLevel.entries, textListCellRenderer { it?.label })
                        .bindItem({ state.lintLevel }, { state.lintLevel = it ?: LintLevel.DEFAULT })
                }
                row { checkBox("Add missing imports when completing").bindSelected({ state.autoImports }, { state.autoImports = it }) }
                row { checkBox("Show type inlay hints").bindSelected({ state.inlayHintsType }, { state.inlayHintsType = it }) }
                row { checkBox("Show parameter name inlay hints").bindSelected({ state.inlayHintsParam }, { state.inlayHintsParam = it }) }
            }
            group("Trace Debugging") {
                row("Node.js executable:") {
                    textFieldWithBrowseButton(FileChooserDescriptorFactory.singleFile().withTitle("Select Node.js Executable"))
                        .bindText({ state.nodePath ?: "" }, { state.nodePath = it.ifBlank { null } })
                        .align(AlignX.FILL)
                        .comment("Runs Mysten's Move trace debug adapter. Leave empty to auto-detect (PATH, nvm, Homebrew).")
                }
            }
            group("Build") {
                row {
                    checkBox("Run `sui move build` on save and show compiler diagnostics")
                        .bindSelected({ state.compileOnSave }, { state.compileOnSave = it })
                        .comment("Usually unnecessary: the analyzer already reports errors as you type.")
                }
            }
        }.also { refreshVersion(state.suiPath ?: "") }
    }

    override fun apply() {
        super.apply()
        val after = settings.analyzerFingerprint()
        if (after != fingerprintBefore) {
            fingerprintBefore = after
            settings.notifyChanged()
        }
    }

    private fun refreshVersion(path: String) {
        versionLabel.text = "Checking…"
        ApplicationManager.getApplication().executeOnPooledThread {
            val toolchain = SuiToolchain.fromPath(path) ?: SuiToolchainLocator.findSui()
            val text = when {
                toolchain == null -> "sui not found"
                !toolchain.isSupported -> "sui ${toolchain.version}  (${toolchain.suiPath})  — older than the supported ${SuiToolchain.MIN_SUPPORTED}; update with suiup"
                else -> toolchain.version?.let { "sui $it  (${toolchain.suiPath})" } ?: "${toolchain.suiPath} did not report a version"
            }
            ApplicationManager.getApplication().invokeLater({ versionLabel.text = text }, ModalityState.any())
        }
    }
}
