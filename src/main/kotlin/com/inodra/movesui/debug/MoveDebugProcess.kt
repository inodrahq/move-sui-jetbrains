package com.inodra.movesui.debug

import com.inodra.movesui.MoveIcons
import com.inodra.movesui.lang.MoveFileType
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.*
import org.eclipse.lsp4j.debug.*
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Drives Mysten's trace adapter over DAP and presents it through the platform debugger UI. */
class MoveDebugProcess(session: XDebugSession, private val sourceFile: File, private val traceInfo: String, private val packageDir: File) : XDebugProcess(session) {
    private val log = logger<MoveDebugProcess>()
    private val breakpoints = ConcurrentHashMap<String, MutableSet<XLineBreakpoint<*>>>() // path → breakpoints
    private val breakpointHandler = object : XBreakpointHandler<XLineBreakpoint<XBreakpointProperties<*>>>(MoveLineBreakpointType::class.java) {
        override fun registerBreakpoint(bp: XLineBreakpoint<XBreakpointProperties<*>>) {
            val path = bp.sourcePosition?.file?.path ?: return
            breakpoints.getOrPut(path) { ConcurrentHashMap.newKeySet() }.add(bp)
            if (initialized.get()) sendBreakpoints(path)
        }
        override fun unregisterBreakpoint(bp: XLineBreakpoint<XBreakpointProperties<*>>, temporary: Boolean) {
            val path = bp.sourcePosition?.file?.path ?: return
            breakpoints[path]?.remove(bp)
            if (initialized.get()) sendBreakpoints(path)
        }
    }
    private val initialized = AtomicBoolean(false)
    private var threadId = 1
    @Volatile private var adapter: MoveTraceAdapter? = null

    private val client = object : IDebugProtocolClient {
        // The adapter only validates breakpoints against a loaded trace, so they are sent after `launch` completes (see sessionInitialized).
        override fun initialized() {}

        override fun stopped(args: StoppedEventArguments) {
            args.threadId?.let { threadId = it }
            ApplicationManager.getApplication().executeOnPooledThread {
                val frames = runCatching { fetchFrames() }.getOrElse { log.warn("stackTrace failed", it); emptyList() }
                val context = MoveSuspendContext(frames)
                val reason = args.reason
                if (reason == StoppedEventArgumentsReason.BREAKPOINT) {
                    val top = frames.firstOrNull()
                    val bp = top?.position?.let { pos -> breakpoints[pos.file.path]?.firstOrNull { it.line == pos.line } }
                    if (bp != null) { session.breakpointReached(bp, null, context); return@executeOnPooledThread }
                }
                session.positionReached(context)
            }
        }

        override fun terminated(args: TerminatedEventArguments?) { session.stop() }
        override fun exited(args: ExitedEventArguments?) { session.stop() }

        override fun output(args: OutputEventArguments) {
            val type = if (args.category == OutputEventArgumentsCategory.STDERR) ConsoleViewContentType.ERROR_OUTPUT else ConsoleViewContentType.NORMAL_OUTPUT
            (session.consoleView as? ConsoleView)?.print(args.output, type)
        }
    }

    override fun getEditorsProvider(): XDebuggerEditorsProvider = MoveDebuggerEditorsProvider
    override fun getBreakpointHandlers(): Array<XBreakpointHandler<*>> = arrayOf(breakpointHandler)

    override fun sessionInitialized() {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val a = MoveTraceAdapter.start(client, packageDir)
                adapter = a
                a.request { s ->
                    s.initialize(InitializeRequestArguments().apply {
                        clientID = "intellij"; clientName = "Move for Sui"; adapterID = "move-debug"
                        linesStartAt1 = true; columnsStartAt1 = true; pathFormat = InitializeRequestArgumentsPathFormat.PATH
                        supportsVariableType = true
                    })
                }
                a.request(120) { s ->
                    s.launch(mapOf("source" to sourceFile.canonicalPath, "traceInfo" to traceInfo, "stopOnEntry" to true, "logLevel" to "none"))
                }
                initialized.set(true)
                breakpoints.keys.forEach(::sendBreakpoints)
                runCatching { a.request { it.configurationDone(ConfigurationDoneArguments()) } }.onFailure { log.warn("configurationDone failed", it) }
            } catch (e: Exception) {
                log.warn("Trace debugger failed to start", e)
                (session.consoleView as? ConsoleView)?.print("Trace debugger failed to start: ${e.message}\n", ConsoleViewContentType.ERROR_OUTPUT)
                session.stop()
            }
        }
    }

    private fun sendBreakpoints(path: String) {
        val a = adapter ?: return
        val lines = breakpoints[path].orEmpty().map { SourceBreakpoint().apply { line = it.line + 1 } }.toTypedArray()
        // The adapter keys files by their canonical path (symlinks such as /var → /private/var resolved).
        val canonical = runCatching { File(path).canonicalPath }.getOrDefault(path)
        runCatching {
            a.request { it.setBreakpoints(SetBreakpointsArguments().apply { source = Source().apply { this.path = canonical; name = File(path).name }; this.breakpoints = lines }) }
        }.onFailure { log.warn("setBreakpoints failed", it) }
    }

    private fun fetchFrames(): List<MoveStackFrame> {
        val a = adapter ?: return emptyList()
        val response = a.request { it.stackTrace(StackTraceArguments().apply { threadId = this@MoveDebugProcess.threadId }) }
        return response.stackFrames.map { MoveStackFrame(session.project, a, it) }
    }

    private fun step(call: (org.eclipse.lsp4j.debug.services.IDebugProtocolServer) -> java.util.concurrent.CompletableFuture<*>) {
        val a = adapter ?: return
        ApplicationManager.getApplication().executeOnPooledThread { runCatching { a.request(call = { call(it) }) }.onFailure { log.warn("step failed", it) } }
    }

    override fun startStepOver(context: XSuspendContext?) = step { it.next(NextArguments().apply { threadId = this@MoveDebugProcess.threadId }) }
    override fun startStepInto(context: XSuspendContext?) = step { it.stepIn(StepInArguments().apply { threadId = this@MoveDebugProcess.threadId }) }
    override fun startStepOut(context: XSuspendContext?) = step { it.stepOut(StepOutArguments().apply { threadId = this@MoveDebugProcess.threadId }) }
    override fun resume(context: XSuspendContext?) = step { it.continue_(ContinueArguments().apply { threadId = this@MoveDebugProcess.threadId }) }
    override fun runToPosition(position: XSourcePosition, context: XSuspendContext?) = resume(context)

    override fun stop() {
        val a = adapter ?: return
        adapter = null
        ApplicationManager.getApplication().executeOnPooledThread { a.close() }
    }

    override fun getCurrentStateMessage(): String = if (session.isStopped) "Trace finished" else "Replaying trace $traceInfo"
}

class MoveSuspendContext(private val frames: List<MoveStackFrame>) : XSuspendContext() {
    private val stack = object : XExecutionStack("Move trace", MoveIcons.SUI) {
        override fun getTopFrame(): XStackFrame? = frames.firstOrNull()
        override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer) {
            container.addStackFrames(frames.drop(firstFrameIndex), true)
        }
    }
    override fun getActiveExecutionStack(): XExecutionStack = stack
    override fun getExecutionStacks(): Array<XExecutionStack> = arrayOf(stack)
}

class MoveStackFrame(private val project: Project, private val adapter: MoveTraceAdapter, private val frame: StackFrame) : XStackFrame() {
    val position: XSourcePosition? by lazy {
        val path = frame.source?.path ?: return@lazy null
        if (!path.endsWith(".move")) return@lazy null
        val vf = LocalFileSystem.getInstance().findFileByPath(path) ?: return@lazy null
        XDebuggerUtil.getInstance().createPosition(vf, (frame.line - 1).coerceAtLeast(0))
    }

    override fun getSourcePosition(): XSourcePosition? = position
    override fun getEqualityObject(): Any = frame.id

    override fun customizePresentation(component: com.intellij.ui.ColoredTextContainer) {
        component.append(frame.name, com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES)
        frame.source?.name?.let { component.append("  $it:${frame.line}", com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES) }
        component.setIcon(com.intellij.icons.AllIcons.Debugger.Frame)
    }

    override fun computeChildren(node: XCompositeNode) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val list = XValueChildrenList()
            runCatching {
                val scopes = adapter.request { it.scopes(ScopesArguments().apply { frameId = frame.id }) }.scopes
                for (scope in scopes) {
                    val vars = adapter.request { it.variables(VariablesArguments().apply { variablesReference = scope.variablesReference }) }.variables
                    vars.forEach { list.add(MoveValue(adapter, it)) }
                }
            }.onFailure { node.setErrorMessage("Cannot read variables: ${it.message}") }
            node.addChildren(list, true)
        }
    }
}

class MoveValue(private val adapter: MoveTraceAdapter, private val variable: Variable) : XNamedValue(variable.name) {
    override fun computePresentation(node: XValueNode, place: XValuePlace) {
        val hasChildren = (variable.variablesReference ?: 0) > 0
        val icon = if (hasChildren) com.intellij.icons.AllIcons.Debugger.Value else com.intellij.icons.AllIcons.Debugger.Db_primitive
        node.setPresentation(icon, variable.type, variable.value ?: "", hasChildren)
    }

    override fun computeChildren(node: XCompositeNode) {
        val ref = variable.variablesReference ?: 0
        if (ref <= 0) { node.addChildren(XValueChildrenList.EMPTY, true); return }
        ApplicationManager.getApplication().executeOnPooledThread {
            val list = XValueChildrenList()
            runCatching { adapter.request { it.variables(VariablesArguments().apply { variablesReference = ref }) }.variables.forEach { list.add(MoveValue(adapter, it)) } }
                .onFailure { node.setErrorMessage(it.message ?: "error") }
            node.addChildren(list, true)
        }
    }
}

object MoveDebuggerEditorsProvider : XDebuggerEditorsProvider() {
    override fun getFileType(): FileType = MoveFileType
    override fun createDocument(project: Project, expression: com.intellij.xdebugger.XExpression, sourcePosition: XSourcePosition?, mode: EvaluationMode): Document =
        EditorFactory.getInstance().createDocument(expression.expression)
}
