package com.inodra.movesui.run

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.OpenFileHyperlinkInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File

/** Links `./sources/x.move:12:5` (as printed by the Move compiler) to the file inside the package directory. */
class MoveRelativePathFilter(private val project: Project, private val baseDir: String) : Filter {
    override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
        val m = PATTERN.find(line) ?: return null
        val rel = m.groupValues[1]
        val file = LocalFileSystem.getInstance().findFileByIoFile(File(baseDir, rel)) ?: return null
        val lineNo = m.groupValues[2].toInt() - 1
        val col = m.groupValues[3].toIntOrNull()?.minus(1) ?: 0
        val start = entireLength - line.length + m.range.first
        return Filter.Result(start, start + m.value.length, OpenFileHyperlinkInfo(project, file, lineNo, col))
    }

    companion object {
        private val PATTERN = Regex("""(\./[\w./-]+\.move):(\d+)(?::(\d+))?""")
    }
}
