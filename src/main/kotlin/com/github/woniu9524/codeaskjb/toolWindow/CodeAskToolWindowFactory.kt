package com.github.woniu9524.codeaskjb.toolWindow

import com.github.woniu9524.codeaskjb.MyBundle
import com.github.woniu9524.codeaskjb.model.FileData
import com.github.woniu9524.codeaskjb.services.CodeAskDataService
import com.github.woniu9524.codeaskjb.utils.MarkdownRenderer
import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.ScrollType
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
import java.awt.FlowLayout
import java.awt.Insets
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener
import javax.swing.text.html.HTML
import javax.swing.text.html.HTMLDocument

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

            // 解释内容
            val explanationText = JTextPane().apply {
                contentType = "text/html"

                // 添加一点边距
                margin = Insets(10, 10, 10, 10)

                // 设置渲染后的HTML内容
                text = MarkdownRenderer.renderMarkdown(fileData.result)
                isEditable = false
                caretPosition = 0

                // 开启超链接支持
                addHyperlinkListener(object : HyperlinkListener {
                    override fun hyperlinkUpdate(e: HyperlinkEvent) {
                        if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                            val url = e.url?.toString() ?: e.description

                            // 检查是否是内联代码链接
                            if (url.startsWith("#code-search")) {
                                try {
                                    // 从URL查询字符串中提取code参数
                                    var processedCode: String? = null
                                    
                                    if (url.contains("?code=")) {
                                        // 从URL查询参数中获取代码
                                        val codeParam = url.substringAfter("?code=")
                                        processedCode = java.net.URLDecoder.decode(codeParam, "UTF-8")
                                    }
                                    
                                    // 如果找到了代码，执行搜索
                                    if (processedCode != null && processedCode.isNotEmpty()) {
                                        searchInCurrentEditor(processedCode)
                                    }
                                } catch (ex: Exception) {
                                    // 忽略异常
                                }
                                return
                            }
                            
                            // 处理其他超链接
                            try {
                                java.awt.Desktop.getDesktop().browse(e.url.toURI())
                            } catch (ex: Exception) {
                                // 忽略异常
                            }
                        }
                    }
                })
            }

            val scrollPane = JBScrollPane(explanationText).apply {
                // 让滚动更平滑
                verticalScrollBar.unitIncrement = 16
                horizontalScrollBar.unitIncrement = 16

                // 设置边框
                border = BorderFactory.createEmptyBorder()
            }

            panel.add(scrollPane, BorderLayout.CENTER)

            return panel
        }
        
        /**
         * 从HTML元素中获取自定义属性
         */
        private fun getCustomAttributesFromElement(element: javax.swing.text.html.HTMLDocument.RunElement): Map<String, String> {
            val result = mutableMapOf<String, String>()
            
            try {
                val attributes = element.attributes
                val attributeNames = attributes.attributeNames
                
                while (attributeNames.hasMoreElements()) {
                    val name = attributeNames.nextElement()
                    if (name.toString().startsWith("data-")) {
                        val value = attributes.getAttribute(name)?.toString() ?: ""
                        result[name.toString()] = value
                    }
                }
            } catch (e: Exception) {
                // 忽略异常
            }
            
            return result
        }
        
        /**
         * 在当前编辑器中搜索并定位到匹配项
         */
        private fun searchInCurrentEditor(searchText: String) {
            try {
                // 确保搜索文本不为空
                if (searchText.isBlank()) {
                    return
                }
                
                // 获取当前编辑器
                val fileEditorManager = FileEditorManager.getInstance(project)
                val editor = fileEditorManager.selectedTextEditor ?: return
                
                // 创建查找模型
                val findManager = FindManager.getInstance(project)
                val findModel = FindModel().apply {
                    stringToFind = searchText
                    isCaseSensitive = true
                    isWholeWordsOnly = false
                    isRegularExpressions = false
                    isGlobal = true
                }
                
                // 执行搜索
                val document = editor.document
                val startOffset = editor.caretModel.offset
                
                val result = findManager.findString(document.charsSequence, startOffset, findModel, editor.virtualFile)
                
                if (result.isStringFound) {
                    // 将编辑器定位到找到的位置
                    editor.caretModel.moveToOffset(result.startOffset)
                    editor.selectionModel.setSelection(result.startOffset, result.endOffset)
                    editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                    
                    // 确保编辑器获得焦点
                    fileEditorManager.openFile(editor.virtualFile, true)
                    editor.contentComponent.requestFocus()
                } else {
                    // 如果从当前位置到文件末尾没找到，尝试从文件开头重新搜索
                    val resultFromStart = findManager.findString(document.charsSequence, 0, findModel, editor.virtualFile)
                    
                    if (resultFromStart.isStringFound) {
                        // 将编辑器定位到找到的位置
                        editor.caretModel.moveToOffset(resultFromStart.startOffset)
                        editor.selectionModel.setSelection(resultFromStart.startOffset, resultFromStart.endOffset)
                        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
                        
                        // 确保编辑器获得焦点
                        fileEditorManager.openFile(editor.virtualFile, true)
                        editor.contentComponent.requestFocus()
                    }
                    // 找不到匹配项时不做任何响应
                }
            } catch (ex: Exception) {
                // 忽略异常
            }
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