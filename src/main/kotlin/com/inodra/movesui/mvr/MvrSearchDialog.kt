package com.inodra.movesui.mvr

import com.inodra.movesui.MoveNotifications
import com.inodra.movesui.project.MovePackage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import javax.swing.DefaultListModel
import javax.swing.JComponent

object MvrDependencies {
    /** Appends `<depName> = { r.mvr = "<mvr name>" }` to the package's `[dependencies]`; the key is the named address used in `use` paths. */
    fun add(project: Project, pkg: MovePackage, mvr: MvrPackage, depName: String = mvr.pkg.replace('-', '_')) {
        val manifest = pkg.root.findChild(MovePackage.MANIFEST) ?: return
        val document = FileDocumentManager.getInstance().getDocument(manifest) ?: return
        val line = "$depName = { r.mvr = \"${mvr.name}\" }"
        WriteCommandAction.runWriteCommandAction(project, "Add MVR Dependency", null, {
            val text = document.text
            val idx = text.indexOf("[dependencies]")
            if (idx >= 0) {
                val insertAt = text.indexOf('\n', idx).let { if (it < 0) text.length else it + 1 }
                document.insertString(insertAt, "$line\n")
            } else {
                document.insertString(text.length, (if (text.endsWith("\n") || text.isEmpty()) "" else "\n") + "\n[dependencies]\n$line\n")
            }
            FileDocumentManager.getInstance().saveDocument(document)
        })
        MoveNotifications.info(project, "Dependency added", "<code>$line</code> was added to Move.toml. Run <b>Tools | Sui Move | Update Dependencies</b> to fetch it.")
    }
}

class MvrSearchDialog(project: Project, initialQuery: String = "") : DialogWrapper(project) {
    private val model = DefaultListModel<MvrPackage>()
    private val list = JBList(model).apply {
        cellRenderer = com.intellij.ui.SimpleListCellRenderer.create { label, value, _ ->
            label.text = value.name + (value.description?.let { "  —  " + it.take(70) } ?: "")
        }
    }
    private val search = SearchTextField()
    var selected: MvrPackage? = null
        private set

    init {
        title = "Add Move Registry Dependency"
        setOKButtonText("Add")
        search.addDocumentListener(object : javax.swing.event.DocumentListener {
            override fun insertUpdate(e: javax.swing.event.DocumentEvent) = refresh()
            override fun removeUpdate(e: javax.swing.event.DocumentEvent) = refresh()
            override fun changedUpdate(e: javax.swing.event.DocumentEvent) = refresh()
        })
        init()
        search.text = initialQuery
        refresh()
    }

    private fun refresh() {
        val q = search.text.trim().removePrefix("@")
        ApplicationManager.getApplication().executeOnPooledThread {
            val results = MvrClient.search(q)
            ApplicationManager.getApplication().invokeLater({
                model.clear(); results.forEach(model::addElement); if (results.isNotEmpty()) list.selectedIndex = 0
            }, com.intellij.openapi.application.ModalityState.any())
        }
    }

    override fun createCenterPanel(): JComponent = panel {
        row { cell(search).align(AlignX.FILL) }
        row { scrollCell(list).align(AlignX.FILL).applyToComponent { preferredSize = JBUI.size(520, 300) } }
    }

    override fun getPreferredFocusedComponent(): JComponent = search

    override fun doOKAction() {
        selected = list.selectedValue
        super.doOKAction()
    }
}
