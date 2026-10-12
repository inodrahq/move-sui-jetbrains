package com.inodra.movesui.debug

import com.inodra.movesui.lang.MoveFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpointType

class MoveLineBreakpointType : XLineBreakpointType<XBreakpointProperties<*>>("move-line-breakpoint", "Move Line Breakpoints") {
    override fun canPutAt(file: VirtualFile, line: Int, project: Project) = file.fileType == MoveFileType
    override fun createBreakpointProperties(file: VirtualFile, line: Int): XBreakpointProperties<*>? = null
}
