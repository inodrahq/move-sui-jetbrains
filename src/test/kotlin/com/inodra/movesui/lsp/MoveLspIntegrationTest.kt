package com.inodra.movesui.lsp

import com.inodra.movesui.toolchain.SuiToolchainLocator
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.platform.lsp.api.LspClientManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.Position
import java.io.File

/** Starts `sui analyzer` through the plugin against the counter fixture. Skipped when `sui` is not installed. */
class MoveLspIntegrationTest : HeavyPlatformTestCase() {

    fun `test analyzer starts and answers hover`() {
        if (SuiToolchainLocator.findSui() == null) {
            System.err.println("sui not installed; skipping LSP integration test")
            return
        }
        val base = File(project.basePath!!)
        FileUtil.copyDir(File("src/test/testData/counter-package"), base)
        File(base, ".idea").deleteRecursively()
        val root = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(base)!!
        VfsUtil.markDirtyAndRefresh(false, true, true, root)
        val file = root.findFileByRelativePath("sources/counter.move")!!

        FileEditorManager.getInstance(project).openFile(file, true)
        val manager = LspClientManager.getInstance(project)
        manager.startClientsIfNeeded(MoveLspIntegrationProvider::class.java)
        // Headless editors may not fire fileOpened; start the client explicitly as the provider would.
        val descriptor = MoveLspClientDescriptor.create(project) ?: error("no descriptor")
        manager.ensureClientStarted(MoveLspIntegrationProvider::class.java, descriptor)

        val deadline = System.currentTimeMillis() + 90_000
        var running = false
        while (System.currentTimeMillis() < deadline) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            val clients = manager.getClients(MoveLspIntegrationProvider::class.java)
            if (clients.any { it.state == LspServerState.Running }) { running = true; break }
            if (clients.any { it.state == LspServerState.ShutdownUnexpectedly }) fail("analyzer shut down unexpectedly")
            Thread.sleep(200)
        }
        assertTrue("analyzer did not reach Running state", running)

        val client = manager.getClients(MoveLspIntegrationProvider::class.java).first { it.state == LspServerState.Running }
        // Headless editors do not notify the server; open the document the way an editor would.
        val uri = client.getDocumentIdentifier(file).uri
        client.sendNotification { it.textDocumentService.didOpen(org.eclipse.lsp4j.DidOpenTextDocumentParams(org.eclipse.lsp4j.TextDocumentItem(uri, "move", 1, String(file.contentsToByteArray())))) }
        // Give the analyzer time to symbolicate the package, then hover over `Counter` in `increment(counter: &mut Counter, ...)`.
        var hover: org.eclipse.lsp4j.Hover? = null
        val hoverDeadline = System.currentTimeMillis() + 60_000
        while (hover == null && System.currentTimeMillis() < hoverDeadline) {
            PlatformTestUtil.dispatchAllEventsInIdeEventQueue()
            hover = ApplicationManager.getApplication().executeOnPooledThread<org.eclipse.lsp4j.Hover?> {
                client.sendRequestSync(10_000) { it.textDocumentService.hover(HoverParams(client.getDocumentIdentifier(file), Position(23, 40))) }
            }.get()
            if (hover == null) Thread.sleep(1000)
        }
        assertNotNull("no hover response from analyzer", hover)
        val text = hover!!.contents.let { if (it.isRight) it.right.value else it.left.joinToString { e -> if (e.isLeft) e.left else e.right.value } }
        assertTrue("unexpected hover: $text", text.contains("Counter"))
    }
}
