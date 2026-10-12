package com.inodra.movesui.debug

import com.inodra.movesui.toolchain.MoveSettings
import com.inodra.movesui.toolchain.SuiToolchainLocator
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.application.PathManager
import org.eclipse.lsp4j.debug.*
import org.eclipse.lsp4j.debug.launch.DSPLauncher
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import org.eclipse.lsp4j.debug.services.IDebugProtocolServer
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/** Locates Node.js and the bundled DAP server, and owns the adapter process + JSON-RPC connection. */
class MoveTraceAdapter private constructor(private val process: Process, val server: IDebugProtocolServer, private val listening: Future<Void>) {

    fun <T> request(timeoutSec: Long = 30, call: (IDebugProtocolServer) -> CompletableFuture<T>): T = call(server).get(timeoutSec, TimeUnit.SECONDS)

    fun close() {
        runCatching { server.disconnect(DisconnectArguments()).get(2, TimeUnit.SECONDS) }
        listening.cancel(true)
        process.destroy()
        if (!process.waitFor(2, TimeUnit.SECONDS)) process.destroyForcibly()
    }

    val isAlive get() = process.isAlive

    companion object {
        fun findNode(): Path? {
            MoveSettings.getInstance().state.nodePath?.takeIf { it.isNotBlank() }?.let { p -> Paths.get(p).takeIf { Files.isExecutable(it) }?.let { return it } }
            SuiToolchainLocator.findExecutable("node")?.let { return it }
            val home = Paths.get(System.getProperty("user.home"))
            val nvm = home.resolve(".nvm/versions/node")
            if (nvm.isDirectory()) {
                Files.list(nvm).use { s -> s.filter { it.isDirectory() }.sorted(compareByDescending { it.name }).map { it.resolve("bin/node") }.filter { Files.isExecutable(it) }.findFirst().orElse(null) }?.let { return it }
            }
            listOf("/opt/homebrew/bin/node", "/usr/local/bin/node").map(Paths::get).firstOrNull { Files.isExecutable(it) }?.let { return it }
            return null
        }

        /** The bundled `server.js`, extracted once per bundle content into the IDE system directory. */
        fun serverScript(): Path {
            val bytes = MoveTraceAdapter::class.java.getResourceAsStream("/trace-adapter/server.js")?.use { it.readBytes() } ?: error("Bundled trace adapter is missing")
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }.take(16)
            val target = Paths.get(PathManager.getSystemPath(), "move-sui", "trace-adapter-$digest", "server.js")
            if (!Files.exists(target)) {
                Files.createDirectories(target.parent)
                Files.write(target, bytes)
            }
            return target
        }

        fun start(client: IDebugProtocolClient, workDir: File): MoveTraceAdapter {
            val node = findNode() ?: throw com.intellij.execution.ExecutionException("Node.js was not found. Trace debugging runs Mysten's debug adapter with Node; install it or set its path in Settings | Languages & Frameworks | Move for Sui.")
            val cmd = GeneralCommandLine(node.toString(), "--nolazy", serverScript().toString())
                .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.CONSOLE)
                .withWorkingDirectory(workDir.toPath())
            val process = cmd.createProcess()
            val launcher = DSPLauncher.createClientLauncher(client, process.inputStream, process.outputStream)
            val listening = launcher.startListening()
            return MoveTraceAdapter(process, launcher.remoteProxy, listening)
        }
    }
}
