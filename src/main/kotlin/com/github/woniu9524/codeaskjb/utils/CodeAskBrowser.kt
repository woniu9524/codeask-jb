package com.github.woniu9524.codeaskjb.utils

import com.github.woniu9524.codeaskjb.services.CodeAskDataService
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.intellij.find.FindManager
import com.intellij.find.FindModel
import com.intellij.find.FindResult
import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.ui.jcef.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.cef.CefApp
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.callback.CefCallback
import org.cef.callback.CefSchemeHandlerFactory
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefResourceHandler
import org.cef.misc.IntRef
import org.cef.misc.StringRef
import org.cef.network.CefRequest
import org.cef.network.CefResponse
import java.awt.Color
import java.io.ByteArrayInputStream
import java.io.InputStream
import javax.swing.JComponent


/**
 * CodeAsk浏览器包装类
 * 负责管理JCEF浏览器实例和通信机制
 */
class CodeAskBrowser(private val project: Project) {
    companion object {
        private val LOG = Logger.getInstance(CodeAskBrowser::class.java)
        private const val SCHEME_NAME = "codeask"
        private const val DOMAIN_NAME = "app"

        // 启用开发者工具
        init {
            try {
                // 设置JS查询池大小和启用开发者工具
                System.setProperty("JBCefClient.Properties.JS_QUERY_POOL_SIZE", "100")
                System.setProperty("ide.browser.jcef.contextMenu.devTools.enabled", "true")
                System.setProperty("ide.browser.jcef.devtools.enabled", "true")
                LOG.info("JCEF developer tools enabled")
            } catch (e: Exception) {
                LOG.error("Failed to enable JCEF developer tools", e)
            }
        }

        /**
         * 注册自定义Schema处理器
         */
        fun registerCustomSchemeHandler() {
            try {
                CefApp.getInstance().registerSchemeHandlerFactory(
                    "http",
                    DOMAIN_NAME,
                    CodeAskSchemeHandlerFactory()
                )
                LOG.info("CodeAsk scheme handler registered successfully")
            } catch (e: Exception) {
                LOG.error("Failed to register custom scheme handler", e)
            }
        }
    }

    // 创建协程作用域
    private val coroutineScope = CoroutineScope(Dispatchers.Main)

    // JCEF浏览器实例
    private val browser = JBCefBrowser()

    // JS通信通道 - 延迟初始化
    private var jsChannel: JBCefJSQuery? = null

    // 获取浏览器组件
    val component: JComponent
        get() = browser.component

    init {
        try {
            // 设置JS查询池大小
            browser.jbCefClient.setProperty(JBCefClient.Properties.JS_QUERY_POOL_SIZE, 100)

            // 确保注册了自定义Schema处理器
            registerCustomSchemeHandler()

            // 设置页面加载完成后的处理
            browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
                override fun onLoadingStateChange(
                    browser: CefBrowser?,
                    isLoading: Boolean,
                    canGoBack: Boolean,
                    canGoForward: Boolean
                ) {
                    if (!isLoading) {
                        try {
                            // 延迟初始化，确保浏览器已完全加载
                            ApplicationManager.getApplication().invokeLater({
                                initJsChannel()
                                injectJavascriptInterface()
                                sendThemeInfo()

                                // 加载当前文件
                                val currentFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
                                if (currentFile != null) {
                                    LOG.info("初始化加载文件: ${currentFile.path}")
                                    refreshExplanation(currentFile.path)
                                }
                            }, ModalityState.any())
                        } catch (e: Exception) {
                            LOG.error("Error during browser initialization", e)
                        }
                    }
                }
            }, browser.cefBrowser)

            // 监听IDE主题变更
            ApplicationManager.getApplication().messageBus.connect().subscribe(
                LafManagerListener.TOPIC,
                LafManagerListener {
                    ApplicationManager.getApplication().invokeLater {
                        sendThemeInfo()
                    }
                }
            )

            // 加载主页面
            val url = "http://$DOMAIN_NAME/index.html"
            LOG.info("Loading URL: $url")
            browser.loadURL(url)
        } catch (e: Exception) {
            LOG.error("Error initializing CodeAskBrowser", e)
        }
    }

    /**
     * 初始化JS通道
     */
    private fun initJsChannel() {
        try {
            if (jsChannel == null) {
                LOG.info("Initializing JS channel")
                jsChannel = JBCefJSQuery.create((browser as JBCefBrowserBase?)!!)
                setupJavaScriptBridge()
                LOG.info("JS channel initialized")
            }
        } catch (e: Exception) {
            LOG.error("Failed to initialize JS channel", e)
        }
    }

    /**
     * 注入JavaScript接口
     */
    private fun injectJavascriptInterface() {
        try {
            // 确保JS通道已初始化
            if (jsChannel == null) {
                LOG.warn("JS channel not initialized, skipping injectJavascriptInterface")
                return
            }

            // 创建JavaScript桥接代码
            val script = """
                // 确保只定义一次sendToIde函数
                if (!window.sendToIde) {
                    console.log("Defining sendToIde function...");
                    window.sendToIde = function(type, data) {
                        if (!type || typeof type !== 'string') {
                            console.error("sendToIde: type must be a string");
                            return;
                        }
                        if (!data || typeof data !== 'object') {
                            console.error("sendToIde: data must be an object");
                            return;
                        }
                        
                        try {
                            console.log("Sending message to IDE:", type, data);
                            const message = JSON.stringify({type: type, data: data});
                            ${jsChannel!!.inject("message")};
                            return true;
                        } catch (error) {
                            console.error("Error sending message to IDE:", error);
                            return false;
                        }
                    };
                    
                    // 发送初始化成功消息
                    setTimeout(function() {
                        window.sendToIde("bridge_initialized", { timestamp: new Date().getTime() });
                    }, 500);
                }
                
                // 确保receiveFromIde函数存在
                if (!window.receiveFromIde) {
                    console.log("Defining receiveFromIde function...");
                    window.receiveFromIde = function(message) {
                        console.log("Received message from IDE:", message);
                        if (window.sendToIde && message && message.type === "bridge_ready") {
                            window.sendToIde("ready", { timestamp: new Date().getTime() });
                        }
                    };
                }
                
                // 触发初始化
                if (typeof window.init === 'function') {
                    window.init();
                }
            """.trimIndent()

            // 执行脚本
            coroutineScope.launch {
                try {
                    browser.executeJavaScript(script)
                } catch (e: Exception) {
                    LOG.error("Error executing JavaScript script", e)
                }
            }

            // 发送一个测试消息
            sendToWeb("bridge_ready", mapOf("timestamp" to System.currentTimeMillis()))
        } catch (e: Exception) {
            LOG.error("Error injecting JavaScript interface", e)
        }
    }

    /**
     * 向Web页面发送消息
     */
    fun sendToWeb(type: String, data: Any?) {
        try {
            // 确保JS通道已初始化
            if (jsChannel == null) return

            val jsonData = Gson().toJson(mapOf("type" to type, "data" to data))
            val script = """
                if (window.receiveFromIde) {
                    try {
                        window.receiveFromIde($jsonData);
                    } catch (error) {
                        console.error("Error in receiveFromIde:", error);
                    }
                }
            """.trimIndent()

            // 使用新的API执行脚本
            coroutineScope.launch {
                try {
                    browser.executeJavaScript(script)
                } catch (e: Exception) {
                    LOG.error("Error executing JavaScript", e)
                }
            }
        } catch (e: Exception) {
            LOG.error("Error sending message to web", e)
        }
    }

    /**
     * 检测是否为暗色主题
     */
    private fun isDarkTheme(): Boolean {
        return try {
            val scheme = EditorColorsManager.getInstance().globalScheme
            val bgColor: Color = scheme.getDefaultBackground()
            val hsb: FloatArray = Color.RGBtoHSB(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), null)
            val brightness = hsb[2] // 亮度值（0.0-1.0）
            brightness < 0.5 // 阈值可根据需求调整
        } catch (e: Exception) {
            false // 默认使用亮色主题
        }
    }

    /**
     * 发送当前主题信息
     */
    private fun sendThemeInfo() {
        // 检测当前IDE主题是否为暗色
        val isDarkTheme = isDarkTheme()
        
        // 安全地获取主题信息
        val themeName = try {
            // 使用更安全的方法获取主题信息
            val colorScheme = EditorColorsManager.getInstance().globalScheme
            colorScheme.name ?: "Unknown Theme"
        } catch (e: Exception) {
            LOG.warn("无法获取主题名称：${e.message}")
            "Unknown Theme"
        }

        LOG.info("当前IDE主题: $themeName, 是否为暗色: $isDarkTheme")

        sendToWeb(
            "themeChanged", mapOf(
                "isDark" to isDarkTheme,
                "themeName" to themeName
            )
        )
    }

    /**
     * 刷新文件解释
     */
    fun refreshExplanation(filePath: String) {
        try {
            LOG.info("刷新文件解释: $filePath")

            // 确保JS通道已初始化
            if (jsChannel == null) return

            val dataService = CodeAskDataService.getInstance(project)
            dataService.loadData()

            val fileExplanations = dataService.getFileExplanation(filePath)

            // 检查解释数据是否为空
            if (fileExplanations == null || fileExplanations.isEmpty()) {
                LOG.warn("文件 $filePath 没有可用的解释")
                sendToWeb(
                    "fileExplanation", mapOf(
                        "filePath" to filePath,
                        "explanations" to emptyList<Any>()
                    )
                )
                return
            }

            // 根据数据类型采取不同的处理方式
            val processedExplanations = when (fileExplanations) {
                is Map<*, *> -> {
                    // 如果是Map，转换为列表格式
                    fileExplanations.entries.map { entry ->
                        mapOf(
                            "first" to (entry.key?.toString() ?: ""),
                            "second" to (entry.value ?: "")
                        )
                    }
                }

                is List<*> -> {
                    // 已经是列表，直接使用
                    fileExplanations
                }

                else -> {
                    // 未知类型，转换为字符串作为单个项
                    listOf(
                        mapOf(
                            "first" to "代码解释",
                            "second" to fileExplanations.toString()
                        )
                    )
                }
            }

            // 发送处理后的解释数据到Web页面
            sendToWeb(
                "fileExplanation", mapOf(
                    "filePath" to filePath,
                    "explanations" to processedExplanations
                )
            )

            LOG.info("已发送文件解释数据到Web页面")
        } catch (e: Exception) {
            LOG.error("刷新文件解释时发生错误", e)
            // 发送错误信息到Web页面
            sendToWeb(
                "error", mapOf(
                    "message" to "加载文件解释失败: ${e.message}"
                )
            )
        }
    }

    /**
     * 在当前编辑器中搜索代码
     */
    private fun searchInCurrentEditor(searchText: String) {
        try {
            // 确保搜索文本不为空
            if (searchText.isBlank() || jsChannel == null) {
                sendToWeb(
                    "searchResult", mapOf(
                        "success" to false,
                        "message" to "搜索文本为空"
                    )
                )
                return
            }

            ApplicationManager.getApplication().invokeLater {
                try {
                    // 获取当前编辑器
                    val fileEditorManager = FileEditorManager.getInstance(project)
                    val editor = fileEditorManager.selectedTextEditor

                    if (editor == null) {
                        sendToWeb(
                            "searchResult", mapOf(
                                "success" to false,
                                "message" to "没有活动的编辑器"
                            )
                        )
                        return@invokeLater
                    }

                    // 创建查找模型
                    val findManager = FindManager.getInstance(project)
                    val findModel = FindModel().apply {
                        stringToFind = searchText
                        isCaseSensitive = true
                        isWholeWordsOnly = true  // 修改为全词匹配
                        isRegularExpressions = false
                        isGlobal = true
                    }

                    // 执行搜索
                    val document = editor.document
                    val startOffset = editor.caretModel.offset

                    val result = findManager.findString(
                        document.charsSequence,
                        startOffset,
                        findModel,
                        editor.virtualFile
                    )

                    if (result.isStringFound) {
                        handleSearchSuccess(editor, fileEditorManager, result)
                    } else {
                        // 从文件开头重新搜索
                        val resultFromStart = findManager.findString(
                            document.charsSequence,
                            0,
                            findModel,
                            editor.virtualFile
                        )

                        if (resultFromStart.isStringFound) {
                            handleSearchSuccess(editor, fileEditorManager, resultFromStart)
                        } else {
                            // 如果全词匹配失败，尝试部分匹配
                            findModel.isWholeWordsOnly = false
                            val partialResult = findManager.findString(
                                document.charsSequence,
                                0,
                                findModel,
                                editor.virtualFile
                            )

                            if (partialResult.isStringFound) {
                                handleSearchSuccess(editor, fileEditorManager, partialResult)
                            } else {
                                sendToWeb(
                                    "searchResult", mapOf(
                                        "success" to false,
                                        "message" to "未找到匹配项"
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    LOG.error("Error during code search", e)
                    sendToWeb(
                        "searchResult", mapOf(
                            "success" to false,
                            "message" to "搜索过程中发生错误: ${e.message}"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            LOG.error("Error initiating code search", e)
        }
    }

    // 新增处理搜索成功的辅助函数
    private fun handleSearchSuccess(
        editor: Editor,
        fileEditorManager: FileEditorManager,
        result: FindResult
    ) {
        // 将编辑器定位到找到的位置
        editor.caretModel.moveToOffset(result.startOffset)
        editor.selectionModel.setSelection(result.startOffset, result.endOffset)
        editor.scrollingModel.scrollToCaret(ScrollType.CENTER)

        // 确保编辑器获得焦点
        fileEditorManager.openFile(editor.virtualFile, true)

        sendToWeb(
            "searchResult", mapOf(
                "success" to true,
                "message" to "找到匹配项"
            )
        )
    }

    /**
     * 设置JavaScript桥接
     */
    private fun setupJavaScriptBridge() {
        // 确保JS通道已初始化
        val channel = jsChannel ?: return

        channel.addHandler { message: String? ->
            if (message != null) {
                try {
                    // 记录接收到的消息，用于调试
                    LOG.info("Received message from web: $message")

                    // 特殊消息类型直接处理
                    if (message == "bridge_test") {
                        sendToWeb("bridge_test_response", mapOf("success" to true))
                        return@addHandler null
                    }

                    // 尝试解析为JSON
                    try {
                        val json = JsonParser.parseString(message).asJsonObject

                        if (json.has("type") && json.has("data")) {
                            val messageType = json.get("type").asString
                            val data = json.get("data").asJsonObject

                            when (messageType) {
                                "searchInCode" -> {
                                    LOG.info("Processing searchInCode request")
                                    val codeText = data.get("code").asString
                                    searchInCurrentEditor(codeText)
                                }

                                "getFileExplanation" -> {
                                    val filePath = data.get("path").asString
                                    refreshExplanation(filePath)
                                }

                                "ready" -> {
                                    // Web页面已准备好，可以发送初始数据
                                    val currentFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
                                    if (currentFile != null) {
                                        refreshExplanation(currentFile.path)
                                    }
                                    sendToWeb("test_response", mapOf("message" to "IDE已收到准备就绪消息"))
                                }

                                "bridge_initialized" -> {
                                    sendToWeb("bridge_confirmed", mapOf("timestamp" to System.currentTimeMillis()))
                                }

                                else -> {
                                    LOG.warn("Unhandled message type: $messageType")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        LOG.error("Error parsing JSON message: $message", e)
                        // 发送错误信息回前端
                        sendToWeb(
                            "error", mapOf(
                                "message" to "消息处理失败: ${e.message}"
                            )
                        )
                    }
                } catch (e: Exception) {
                    LOG.error("Failed to handle JS message", e)
                    // 发送错误信息回前端
                    sendToWeb(
                        "error", mapOf(
                            "message" to "消息处理失败: ${e.message}"
                        )
                    )
                }
            }
            null
        }
    }
}

/**
 * 自定义Schema处理器工厂
 */
class CodeAskSchemeHandlerFactory : CefSchemeHandlerFactory {
    companion object {
        private val LOG = Logger.getInstance(CodeAskSchemeHandlerFactory::class.java)
    }

    override fun create(
        browser: CefBrowser?,
        frame: CefFrame?,
        schemeName: String?,
        request: CefRequest?
    ): CefResourceHandler {
        LOG.info("Creating resource handler for URL: ${request?.url}")
        return CodeAskResourceHandler()
    }
}

/**
 * 自定义资源处理器
 */
class CodeAskResourceHandler : CefResourceHandler {
    companion object {
        private val LOG = Logger.getInstance(CodeAskResourceHandler::class.java)

        // 默认HTML内容
        private const val DEFAULT_HTML = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <title>CodeAsk</title>
                <style>
                    body {
                        font-family: Arial, sans-serif;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        height: 100vh;
                        margin: 0;
                        background-color: #f0f0f0;
                        color: #333;
                    }
                    .container {
                        text-align: center;
                        padding: 20px;
                        border-radius: 8px;
                        background-color: white;
                        box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                    }
                    h1 { color: #2675bf; }
                </style>
            </head>
            <body>
                <div class="container">
                    <h1>CodeAsk</h1>
                    <p>正在加载资源...</p>
                    <p>如果您看到此页面，说明资源加载遇到问题。</p>
                </div>
                <script>
                    console.log("Default page loaded");
                    setTimeout(() => {
                        location.reload();
                    }, 5000);
                </script>
            </body>
            </html>
        """
    }

    private var inputStream: InputStream? = null
    private var mimeType: String = "text/html"
    private var processed = false

    override fun processRequest(request: CefRequest?, callback: CefCallback?): Boolean {
        processed = true
        val url = request?.url ?: return false

        LOG.info("Processing request for URL: $url")

        try {
            // 转换URL到资源路径
            val resourcePath = url.replace("http://app/", "webview/")

            // 特殊处理根路径请求
            if (url == "http://app/" || url.endsWith("/")) {
                val indexResource = javaClass.classLoader.getResource("webview/index.html")
                if (indexResource != null) {
                    inputStream = indexResource.openStream()
                    mimeType = "text/html"
                    callback?.Continue()
                    return true
                }
            }

            val resource = javaClass.classLoader.getResource(resourcePath)

            if (resource != null) {
                inputStream = resource.openStream()

                // 设置MIME类型
                mimeType = when {
                    url.endsWith(".css") -> "text/css"
                    url.endsWith(".js") -> "application/javascript"
                    url.endsWith(".html") -> "text/html"
                    url.endsWith(".svg") -> "image/svg+xml"
                    url.endsWith(".png") -> "image/png"
                    url.endsWith(".jpg") -> "image/jpeg"
                    else -> "application/octet-stream"
                }
            } else {
                // 如果是请求主页但找不到资源，提供默认HTML内容
                if (url.endsWith("/index.html") || url == "http://app/") {
                    val defaultHtml = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta charset="UTF-8">
                            <title>CodeAsk</title>
                            <style>
                                body {
                                    font-family: Arial, sans-serif;
                                    display: flex;
                                    justify-content: center;
                                    align-items: center;
                                    height: 100vh;
                                    margin: 0;
                                    background-color: #f0f0f0;
                                    color: #333;
                                }
                                .container {
                                    text-align: center;
                                    padding: 20px;
                                    border-radius: 8px;
                                    background-color: white;
                                    box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                                }
                                h1 { color: #2675bf; }
                                pre { 
                                    background-color: #f5f5f5; 
                                    padding: 10px; 
                                    border-radius: 4px;
                                    text-align: left;
                                    overflow: auto;
                                }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <h1>CodeAsk</h1>
                                <p>资源加载问题</p>
                                <p>无法找到index.html文件，请检查资源路径。</p>
                                <pre>
请求URL: $url
资源路径: $resourcePath
                                </pre>
                                <p>请检查日志获取更多信息。</p>
                                <script>
                                    console.log("Default page loaded due to missing index.html");
                                    // 5秒后自动刷新
                                    setTimeout(() => {
                                        location.reload();
                                    }, 5000);
                                </script>
                            </div>
                        </body>
                        </html>
                    """.trimIndent()
                    inputStream = ByteArrayInputStream(defaultHtml.toByteArray())
                    mimeType = "text/html"
                } else {
                    // 返回false表示无法处理此请求
                    return false
                }
            }

            callback?.Continue()
            return true
        } catch (e: Exception) {
            LOG.error("Error processing request: $url", e)
            return false
        }
    }

    override fun getResponseHeaders(response: CefResponse?, responseLength: IntRef?, redirectUrl: StringRef?) {
        response?.mimeType = mimeType
        response?.status = 200

        inputStream?.let {
            try {
                responseLength?.set(it.available())
            } catch (e: Exception) {
                responseLength?.set(0)
            }
        } ?: run {
            response?.status = 404
            responseLength?.set(0)
        }
    }

    override fun readResponse(
        dataOut: ByteArray?,
        bytesToRead: Int,
        bytesRead: IntRef?,
        callback: CefCallback?
    ): Boolean {
        val stream = inputStream ?: return false

        return try {
            val available = stream.available()

            if (available > 0) {
                val toRead = minOf(available, bytesToRead)
                val actualRead = stream.read(dataOut, 0, toRead)

                bytesRead?.set(actualRead)
                true
            } else {
                stream.close()
                inputStream = null
                false
            }
        } catch (e: Exception) {
            LOG.error("Error reading response", e)
            false
        }
    }

    override fun cancel() {
        inputStream?.close()
        inputStream = null
    }
} 