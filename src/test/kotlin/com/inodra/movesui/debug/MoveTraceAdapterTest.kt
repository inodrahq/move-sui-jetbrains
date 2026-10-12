package com.inodra.movesui.debug

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.eclipse.lsp4j.debug.*
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Generates a real trace for the counter fixture and replays it through the bundled adapter. Skipped without Node.js or sui. */
class MoveTraceAdapterTest : BasePlatformTestCase() {
    fun `test launch stop step and variables`() {
        val node = MoveTraceAdapter.findNode()
        val sui = com.inodra.movesui.toolchain.SuiToolchainLocator.findSui()
        if (node == null || sui == null) {
            System.err.println("skipping: node=$node sui=$sui")
            return
        }
        // A pristine copy of the counter package; traces embed source hashes, so they are generated here.
        val pkg = java.nio.file.Files.createTempDirectory("move-trace-test").toRealPath().toFile()
        com.intellij.openapi.util.io.FileUtil.copyDir(File("src/test/testData/counter-package"), pkg)
        val out = com.intellij.execution.process.CapturingProcessHandler(sui.commandLine("move", "test", "--trace", "--disassemble", workDir = pkg.toPath())).runProcess(300_000)
        val trace = File(pkg, "traces/counter__counter_tests__test_increment.json.zst")
        assertTrue("trace generation failed: ${out.stderr}", trace.isFile)
        val stopped = java.util.concurrent.LinkedBlockingQueue<StoppedEventArguments>()
        val initialized = CountDownLatch(1)
        val client = object : IDebugProtocolClient {
            override fun initialized() = initialized.countDown()
            override fun stopped(args: StoppedEventArguments) { stopped.add(args) }
        }
        val adapter = MoveTraceAdapter.start(client, pkg)
        try {
            val caps = adapter.request { it.initialize(InitializeRequestArguments().apply { clientID = "test"; adapterID = "move-debug"; linesStartAt1 = true; columnsStartAt1 = true; pathFormat = InitializeRequestArgumentsPathFormat.PATH }) }
            assertNotNull(caps)
            adapter.request(60) { it.launch(mapOf("source" to File(pkg, "tests/counter_tests.move").absolutePath, "traceInfo" to "counter::counter_tests::test_increment", "stopOnEntry" to true, "logLevel" to "none")) }
            assertTrue("no initialized event", initialized.await(30, TimeUnit.SECONDS))
            // Breakpoints are validated against the loaded trace, so they must follow `launch`.
            val bps = adapter.request { it.setBreakpoints(SetBreakpointsArguments().apply { source = Source().apply { path = File(pkg, "sources/counter.move").absolutePath }; breakpoints = arrayOf(SourceBreakpoint().apply { line = 25 }) }) }
            assertTrue("breakpoint not verified", bps.breakpoints.single().isVerified)
            adapter.request { it.configurationDone(ConfigurationDoneArguments()) }
            val first = stopped.poll(30, TimeUnit.SECONDS) ?: fail("no stopped event") as Nothing
            val threadId = first.threadId ?: 1
            val frames = adapter.request { it.stackTrace(StackTraceArguments().apply { this.threadId = threadId }) }.stackFrames
            assertTrue("no frames", frames.isNotEmpty())
            assertTrue("top frame should be the test: ${frames[0].name}", frames[0].name.contains("test_increment"))
            assertTrue(frames[0].source.path.endsWith("counter_tests.move"))

            // Continue to the breakpoint inside increment().
            adapter.request { it.continue_(ContinueArguments().apply { this.threadId = threadId }) }
            val atBp = stopped.poll(30, TimeUnit.SECONDS) ?: fail("no stopped event at breakpoint") as Nothing
            // The adapter reports "step" for breakpoint stops; the frame position is the real check.
            assertNotNull(atBp.reason)
            val bpFrames = adapter.request { it.stackTrace(StackTraceArguments().apply { this.threadId = threadId }) }.stackFrames
            assertTrue(bpFrames[0].name.contains("increment"))
            assertEquals(25, bpFrames[0].line)
            val scopes = adapter.request { it.scopes(ScopesArguments().apply { frameId = bpFrames[0].id }) }.scopes
            val vars = scopes.flatMap { s -> adapter.request { it.variables(VariablesArguments().apply { variablesReference = s.variablesReference }) }.variables.toList() }
            assertTrue("expected `by` and `counter` locals, got ${vars.map { it.name }}", vars.any { it.name == "by" } && vars.any { it.name == "counter" })
            assertEquals("5", vars.first { it.name == "by" }.value)

            adapter.request { it.next(NextArguments().apply { this.threadId = threadId }) }
            assertNotNull(stopped.poll(30, TimeUnit.SECONDS))
        } finally {
            adapter.close()
        }
    }
}
