package com.inodra.movesui.toolchain

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.messages.Topic
import java.nio.file.Path

class MoveSettingsState : BaseState() {
    /** Explicit `sui` executable; empty means auto-detect. */
    var suiPath by string()
    /** Explicit standalone `move-analyzer`; empty means `sui analyzer`. */
    var analyzerPath by string()
    var lintLevel by enum(LintLevel.DEFAULT)
    var autoImports by property(true)
    var inlayHintsType by property(true)
    var inlayHintsParam by property(true)
    var compileOnSave by property(false)
    var nodePath by string()
}

enum class LintLevel(val lspValue: String, val label: String) {
    NONE("none", "None"), DEFAULT("default", "Default"), ALL("all", "All");
}

@Service(Service.Level.APP)
@State(name = "MoveSuiSettings", storages = [Storage("move-sui.xml")])
class MoveSettings : SimplePersistentStateComponent<MoveSettingsState>(MoveSettingsState()) {

    val toolchain: SuiToolchain?
        get() = SuiToolchain.fromPath(state.suiPath) ?: SuiToolchainLocator.findSui()

    val analyzerExecutable: Path? get() = state.analyzerPath?.takeIf { it.isNotBlank() }?.let(Path::of)

    fun analyzerInitializationOptions(): Map<String, Any> = mapOf(
        "lintLevel" to state.lintLevel.lspValue,
        "autoImports" to state.autoImports,
        "inlayHintsType" to state.inlayHintsType,
        "inlayHintsParam" to state.inlayHintsParam,
    )

    /** Snapshot of the values the language server is started with; a change means it must restart. */
    fun analyzerFingerprint(): String = listOf(state.suiPath, state.analyzerPath, analyzerInitializationOptions()).toString()

    fun notifyChanged() {
        SuiToolchain.invalidateCaches()
        ApplicationManager.getApplication().messageBus.syncPublisher(TOPIC).settingsChanged(this)
    }

    fun interface Listener {
        fun settingsChanged(settings: MoveSettings)
    }

    companion object {
        @JvmField val TOPIC = Topic.create("Move for Sui settings", Listener::class.java)
        fun getInstance(): MoveSettings = ApplicationManager.getApplication().getService(MoveSettings::class.java)
    }
}
