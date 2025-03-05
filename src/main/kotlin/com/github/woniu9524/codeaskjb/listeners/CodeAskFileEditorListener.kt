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
 * 用于监听当前打开的文件，并更新代码解释显示
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
        val file = event.newFile ?: run {
            return
        }
        updateCodeAskPanel(event.manager, file)
    }

    /**
     * 更新代码解释面板
     */
    private fun updateCodeAskPanel(manager: FileEditorManager, file: VirtualFile) {
        ApplicationManager.getApplication().invokeLater {
            val project = manager.project
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("CodeAsk") ?: run {
                return@invokeLater
            }
            
            // 如果工具窗口已经打开，更新内容
            if (toolWindow.isVisible) {
                val contentManager = toolWindow.contentManager
                contentManager.contents.forEach { content ->
                    // 获取内容视图中的组件
                    val component = content.component
                    if (component is CodeAskToolWindowFactory.CodeAskPanel) {
                        component.updateForFile(file)
                    }
                }
            }
        }
    }
} 