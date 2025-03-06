package com.github.woniu9524.codeaskjb.toolWindow

import com.github.woniu9524.codeaskjb.utils.CodeAskBrowser
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * 代码解释工具窗口工厂
 * 负责创建和管理代码解释工具窗口
 */
class CodeAskToolWindowFactory : ToolWindowFactory {
    companion object {
        private val LOG = Logger.getInstance(CodeAskToolWindowFactory::class.java)
    }
    
    /**
     * 创建工具窗口内容
     */
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentPanel = CodeAskPanel(project)
        val content = ContentFactory.getInstance().createContent(contentPanel, "", false)
        toolWindow.contentManager.addContent(content)
        
        // 初始化当前选中的文件
        val selectedFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        if (selectedFile != null) {
            contentPanel.updateForFile(selectedFile)
        }
        
        // 注册文件选择监听器
        project.messageBus.connect().subscribe(
            FileEditorManagerListener.FILE_EDITOR_MANAGER,
            object : FileEditorManagerListener {
                override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                    contentPanel.updateForFile(file)
                }
                
                override fun selectionChanged(event: FileEditorManagerEvent) {
                    event.newFile?.let { contentPanel.updateForFile(it) }
                }
            }
        )
    }
    
    /**
     * 代码解释面板 - 使用JCEF浏览器实现
     */
    class CodeAskPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {
        private val browser = CodeAskBrowser(project)
        private var currentFile: VirtualFile? = null
        
        init {
            // 设置面板内容为浏览器组件
            setContent(browser.component)
        }
        
        /**
         * 为指定文件更新面板内容
         */
        fun updateForFile(file: VirtualFile) {
            if (currentFile?.path == file.path) {
                return // 避免重复更新
            }
            
            currentFile = file
            refreshData()
        }
        
        /**
         * 刷新数据
         */
        private fun refreshData() {
            val file = currentFile ?: return
            
            try {
                // 通过浏览器实例刷新文件解释
                browser.refreshExplanation(file.path)
            } catch (e: Exception) {
                LOG.error("Failed to refresh file explanation", e)
            }
        }
    }
} 