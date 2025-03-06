package com.github.woniu9524.codeaskjb.listeners

import com.github.woniu9524.codeaskjb.toolWindow.CodeAskToolWindowFactory
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager

/**
 * 文件编辑器监听器
 * 监听文件打开和选择事件，并相应更新代码解释面板
 */
class CodeAskFileEditorListener : FileEditorManagerListener {

    /**
     * 当文件被打开时调用
     */
    override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
        updateCodeAskPanel(source, file)
    }

    /**
     * 当选中的文件改变时调用
     */
    override fun selectionChanged(event: FileEditorManagerEvent) {
        val file = event.newFile ?: return
        updateCodeAskPanel(event.manager, file)
    }

    /**
     * 更新代码解释面板
     * 
     * @param manager 文件编辑器管理器
     * @param file 当前选中的文件
     */
    private fun updateCodeAskPanel(manager: FileEditorManager, file: VirtualFile) {
        ApplicationManager.getApplication().invokeLater {
            val project = manager.project
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("CodeAsk") ?: return@invokeLater
            
            // 如果工具窗口已经打开，更新内容
            if (toolWindow.isVisible) {
                val contentManager = toolWindow.contentManager
                contentManager.contents.forEach { content ->
                    val component = content.component
                    if (component is CodeAskToolWindowFactory.CodeAskPanel) {
                        component.updateForFile(file)
                    }
                }
            }
        }
    }
} 