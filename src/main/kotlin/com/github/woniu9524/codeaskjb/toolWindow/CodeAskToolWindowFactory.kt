package com.github.woniu9524.codeaskjb.toolWindow

import com.github.woniu9524.codeaskjb.MyBundle
import com.github.woniu9524.codeaskjb.model.FileData
import com.github.woniu9524.codeaskjb.services.CodeAskDataService
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.tabs.TabInfo
import com.intellij.ui.tabs.impl.JBTabsImpl
import java.awt.BorderLayout
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.*

/**
 * 代码解释工具窗口工厂
 */
class CodeAskToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentPanel = CodeAskPanel(project)
        val content = ContentFactory.getInstance().createContent(contentPanel, "", false)
        toolWindow.contentManager.addContent(content)
        
        // 初始化当前选中的文件
        val selectedFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        if (selectedFile != null) {
            contentPanel.updateForFile(selectedFile)
        }
    }

    /**
     * 代码解释面板
     */
    class CodeAskPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {
        private val tabs = JBTabsImpl(project)
        private val dataService = CodeAskDataService.getInstance(project)
        private val noFilePanel = createNoFilePanel()
        private val noExplanationPanel = createNoExplanationPanel()
        private var currentFile: VirtualFile? = null
        private var currentPlugins: List<Pair<String, FileData>> = emptyList()
        
        init {
            setContent(JPanel(BorderLayout()).apply {
                add(noFilePanel, BorderLayout.CENTER)
            })
            
            // 刷新按钮
            val actionGroup = DefaultActionGroup().apply {
                add(object : AnAction("刷新") {
                    override fun actionPerformed(e: AnActionEvent) {
                        refreshData()
                    }
                })
            }
            
            val actionToolbar = ActionManager.getInstance().createActionToolbar(
                ActionPlaces.TOOLBAR,
                actionGroup,
                true
            )
            
            // 设置工具栏
            setToolbar(actionToolbar.component)
        }
        
        /**
         * 为指定文件更新面板内容
         */
        fun updateForFile(file: VirtualFile) {
            currentFile = file
            refreshData()
        }
        
        /**
         * 刷新数据
         */
        private fun refreshData() {
            val file = currentFile ?: run {
                setContent(noFilePanel)
                return
            }
            
            dataService.loadData()
            currentPlugins = dataService.getFileExplanation(file.path)
            
            if (currentPlugins.isEmpty()) {
                setContent(noExplanationPanel)
                return
            }
            
            updateTabs()
        }
        
        /**
         * 更新标签页
         */
        private fun updateTabs() {
            tabs.removeAllTabs()
            
            var firstTab: TabInfo? = null
            
            currentPlugins.forEach { (pluginName, fileData) ->
                val tabInfo = TabInfo(createExplanationPanel(fileData))
                tabInfo.text = pluginName
                tabs.addTab(tabInfo)
                if (firstTab == null) {
                    firstTab = tabInfo
                }
            }
            
            if (tabs.tabCount > 0 && firstTab != null) {
                tabs.select(firstTab!!, true)
            }
            
            setContent(tabs.component)
        }
        
        /**
         * 创建解释面板
         */
        private fun createExplanationPanel(fileData: FileData): JComponent {
            val panel = JPanel(BorderLayout())
            
            // 文件名标签
            val fileNameLabel = JBLabel("当前文件: ${fileData.filename}")
            fileNameLabel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            panel.add(fileNameLabel, BorderLayout.NORTH)
            
            // 解释内容
            val explanationText = JTextPane().apply {
                contentType = "text/html"
                text = formatExplanationAsHtml(fileData.result)
                isEditable = false
                caretPosition = 0
            }
            
            val scrollPane = JBScrollPane(explanationText)
            panel.add(scrollPane, BorderLayout.CENTER)
            
            // 底部按钮
            val buttonPanel = JPanel()
            val copyButton = JButton("复制解释").apply {
                addActionListener {
                    val selection = StringSelection(fileData.result)
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                }
            }
            
            buttonPanel.add(copyButton)
            panel.add(buttonPanel, BorderLayout.SOUTH)
            
            return panel
        }
        
        /**
         * 将解释文本转换为HTML
         */
        private fun formatExplanationAsHtml(text: String): String {
            val htmlText = text
                .replace("\n", "<br/>")
                .replace("```(.+?)```".toRegex(RegexOption.DOT_MATCHES_ALL), "<pre><code>\$1</code></pre>")
                .replace("`([^`]+)`".toRegex(), "<code>\$1</code>")
                
            return """
                <html>
                <body style='font-family: sans-serif; font-size: 12px;'>
                $htmlText
                </body>
                </html>
            """.trimIndent()
        }
        
        /**
         * 创建无文件面板
         */
        private fun createNoFilePanel(): JComponent {
            val panel = JPanel(BorderLayout())
            val label = JBLabel("请打开一个文件以查看代码解释")
            label.horizontalAlignment = SwingConstants.CENTER
            panel.add(label, BorderLayout.CENTER)
            return panel
        }
        
        /**
         * 创建无解释面板
         */
        private fun createNoExplanationPanel(): JComponent {
            val panel = JPanel(BorderLayout())
            val label = JBLabel("当前文件没有可用的代码解释")
            label.horizontalAlignment = SwingConstants.CENTER
            panel.add(label, BorderLayout.CENTER)
            return panel
        }
    }
} 