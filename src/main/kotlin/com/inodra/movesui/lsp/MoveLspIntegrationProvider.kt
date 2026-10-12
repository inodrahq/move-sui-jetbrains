package com.inodra.movesui.lsp

import com.inodra.movesui.MoveIcons
import com.inodra.movesui.MoveNotifications
import com.inodra.movesui.toolchain.MoveSettings
import com.inodra.movesui.toolchain.MoveSettingsConfigurable
import com.inodra.movesui.toolchain.SuiToolchain
import com.inodra.movesui.toolchain.SuiToolchainLocator
import com.inodra.movesui.toolchain.SuiupInstaller
import com.intellij.notification.NotificationAction
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspClient
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspIntegrationProvider
import com.intellij.platform.lsp.api.lsWidget.LspClientWidgetItem
import java.util.concurrent.ConcurrentHashMap

class MoveLspIntegrationProvider : LspIntegrationProvider {
    override fun fileOpened(project: Project, file: VirtualFile, clientStarter: LspIntegrationProvider.LspClientStarter) {
        if (file.extension != "move") return
        val descriptor = MoveLspClientDescriptor.create(project)
        if (descriptor == null) {
            LOG.info("No Sui toolchain found; analyzer not started for ${file.path}")
            notifyMissingToolchainOnce(project)
            return
        }
        LOG.info("Starting analyzer for ${file.path}: ${descriptor.createCommandLine().commandLineString}")
        warnIfOutdatedOnce(project)
        clientStarter.ensureClientStarted(descriptor)
    }

    override fun createWidgetItem(lspClient: LspClient, currentFile: VirtualFile?): LspClientWidgetItem =
        LspClientWidgetItem(lspClient, currentFile, MoveIcons.SUI, MoveSettingsConfigurable::class.java)

    private fun warnIfOutdatedOnce(project: Project) {
        val toolchain = MoveSettings.getInstance().toolchain ?: return
        val version = toolchain.version ?: return
        if (toolchain.isSupported || !notified.add("outdated:" + project.locationHash)) return
        val actions = mutableListOf<NotificationAction>()
        if (SuiToolchainLocator.findSuiup() != null) actions += NotificationAction.createSimple("Update via suiup") { SuiupInstaller.installSui(project) { restart(project) } }
        actions += NotificationAction.createSimple("Configure…") { ShowSettingsUtil.getInstance().showSettingsDialog(project, MoveSettingsConfigurable::class.java) }
        MoveNotifications.warn(
            project, "Sui CLI $version is older than supported",
            "Move for Sui is tested with sui ${SuiToolchain.MIN_SUPPORTED} or newer. Errors, completion and inlay hints come from " +
                "<code>sui analyzer</code>, so older versions may miss features or misbehave.",
            *actions.toTypedArray(),
        )
    }

    private fun notifyMissingToolchainOnce(project: Project) {
        if (!notified.add(project.locationHash)) return
        val actions = mutableListOf<NotificationAction>(
            NotificationAction.createSimple("Configure…") {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, MoveSettingsConfigurable::class.java)
            },
        )
        if (SuiToolchainLocator.findSuiup() != null) {
            actions += NotificationAction.createSimple("Install via suiup") {
                SuiupInstaller.installSui(project) { restart(project) }
            }
        }
        MoveNotifications.warn(
            project, "Sui CLI not found",
            "Code intelligence for Move needs the <code>sui</code> binary (it provides the language server). " +
                "Install it with <code>suiup install sui</code> or set its path in Settings | Languages &amp; Frameworks | Move for Sui.",
            *actions.toTypedArray(),
        )
    }

    companion object {
        private val LOG = com.intellij.openapi.diagnostic.logger<MoveLspIntegrationProvider>()
        private val notified = ConcurrentHashMap.newKeySet<String>()

        fun restart(project: Project) {
            notified.remove(project.locationHash)
            LspClientManager.getInstance(project).stopAndRestartClientsIfNeeded(MoveLspIntegrationProvider::class.java)
        }

        fun clients(project: Project): Collection<LspClient> =
            LspClientManager.getInstance(project).getClients(MoveLspIntegrationProvider::class.java)
    }
}

/** Restarts analyzers in every open project when analyzer-related settings change. */
class MoveLspSettingsListener : MoveSettings.Listener {
    override fun settingsChanged(settings: MoveSettings) {
        com.intellij.openapi.project.ProjectManager.getInstance().openProjects.forEach { MoveLspIntegrationProvider.restart(it) }
    }
}
