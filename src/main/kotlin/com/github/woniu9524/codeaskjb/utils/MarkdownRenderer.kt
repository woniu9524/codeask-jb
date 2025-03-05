package com.github.woniu9524.codeaskjb.utils

import com.intellij.ide.ui.LafManager
import com.intellij.util.ui.UIUtil
import java.util.concurrent.ConcurrentHashMap

/**
 * Markdown渲染工具类
 * 负责将Markdown文本转换为HTML，支持标准Markdown语法和Mermaid图表
 */
class MarkdownRenderer {
    companion object {
        private const val MERMAID_SCRIPT = """
            <script type="module">
                import mermaid from 'https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.esm.min.mjs';
                mermaid.initialize({ 
                    startOnLoad: true,
                    theme: 'default',
                    securityLevel: 'loose',
                    fontFamily: 'sans-serif'
                });
                window.onload = function() {
                    mermaid.run();
                }
            </script>
        """

        // 缓存已渲染的Markdown
        private val renderCache = ConcurrentHashMap<String, String>()

        // 正则表达式预编译
        private val mermaidRegex = "```mermaid\n?([\\s\\S]*?)\n?```".toRegex()
        private val codeBlockRegex = "```([\\w]*)(?:\\s*\n)([\\s\\S]*?)\n?```".toRegex()
        private val inlineCodeRegex = "`([^`]+)`".toRegex()
        private val headerRegex = "^\\s*(#{1,6})\\s+(.+)$".toRegex(RegexOption.MULTILINE)
        private val boldRegex = "\\*\\*([^*]+)\\*\\*".toRegex()
        private val italicRegex = "\\*([^*]+)\\*".toRegex()
        private val unorderedListRegex = "^\\s*[-*+]\\s+(.+)$".toRegex(RegexOption.MULTILINE)
        private val listGroupRegex = "(?:<li>.+?</li>\\s*)+".toRegex()
        private val orderedListRegex = "^\\s*\\d+\\.\\s+(.+)$".toRegex(RegexOption.MULTILINE)
        private val orderedListGroupRegex = "(?<!<ul>\n)(?:<li>.+?</li>\\s*)+".toRegex()
        private val linkRegex = "\\[([^\\]]+)\\]\\(([^)]+)\\)".toRegex()
        private val imageRegex = "!\\[([^\\]]+)\\]\\(([^)]+)\\)".toRegex()
        private val blockquoteRegex = "^\\s*>\\s+(.+)$".toRegex(RegexOption.MULTILINE)
        private val hrRegex = "^\\s*([-*_]{3,})\\s*$".toRegex(RegexOption.MULTILINE)
        private val multipleNewlinesRegex = "\n\n+".toRegex()
        private val singleNewlineRegex = "\n".toRegex()
        private val emptyParagraphRegex = "<p></p>".toRegex()
        private val paragraphStartFixRegex = "<p><(ul|ol|h\\d|blockquote|pre|div)>".toRegex()
        private val paragraphEndFixRegex = "</(ul|ol|h\\d|blockquote|pre|div)></p>".toRegex()

        // 添加一个新的正则表达式，用于识别内联代码中的标识符（如类名、方法名等）
        private val codeIdentifierRegex = "\\b([A-Za-z_][A-Za-z0-9_]*)\\b".toRegex()

        // 添加特殊标记符号的识别(如在截图中看到的TEMP_SCALE, TempBuffer等)
        private val specialIdentifierRegex = "\\b([A-Z][A-Z0-9_]*)\\b".toRegex()

        /**
         * 将Markdown文本转换为HTML
         *
         * @param text Markdown文本
         * @param includeMermaid 是否包含Mermaid支持
         * @param useCache 是否使用缓存
         * @return 转换后的HTML
         */
        fun renderMarkdown(text: String, includeMermaid: Boolean = true, useCache: Boolean = true): String {
            // 检查缓存
            val cacheKey = "$text:$includeMermaid"
            if (useCache && renderCache.containsKey(cacheKey)) {
                return renderCache[cacheKey]!!
            }

            // 预处理文本，确保代码块格式正确
            val sanitizedText = sanitizeMarkdown(text)
            val processedText = preprocessMarkdown(sanitizedText)
            
            val htmlText = parseMarkdown(processedText)
            val result = wrapHtml(htmlText, includeMermaid)

            // 添加到缓存
            if (useCache) {
                renderCache[cacheKey] = result

                // 限制缓存大小
                if (renderCache.size > 100) {
                    // 简单的策略：删除一些项以避免无限增长
                    val keysToRemove = renderCache.keys.take(20)
                    keysToRemove.forEach { renderCache.remove(it) }
                }
            }

            return result
        }

        /**
         * 清除渲染缓存
         */
        fun clearCache() {
            renderCache.clear()
        }

        /**
         * 清理Markdown文本，移除可能导致渲染问题的内容
         */
        private fun sanitizeMarkdown(text: String): String {
            var result = text
            
            // 1. 移除可能的HTML注释
            result = result.replace("<!--[\\s\\S]*?-->".toRegex(), "")
            
            // 2. 移除不可见的控制字符
            result = result.replace("[\\x00-\\x09\\x0B\\x0C\\x0E-\\x1F\\x7F]".toRegex(), "")
            
            // 3. 修复不平衡的代码块标记
            var count = 0
            result.forEach { if (it == '`') count++ }
            if (count % 2 != 0) {
                result += "`" // 添加一个反引号以平衡
            }
            
            // 4. 确保文本以换行符结束
            if (!result.endsWith("\n")) {
                result += "\n"
            }
            
            return result
        }

        /**
         * 预处理Markdown文本，修复可能的格式问题
         */
        private fun preprocessMarkdown(text: String): String {
            var result = text
            
            // 1. 移除连续的```标记，可能是由于错误格式导致的
            result = result.replace("```\\s*```".toRegex(), "")
            
            // 2. 移除只包含空白字符的代码块
            result = result.replace("```(\\w*)[\\s\\n]*```".toRegex(), "")
            
            // 3. 确保代码块格式正确（开始标记后有换行，结束标记前有换行）
            result = result.replace("```(\\w*)([^\\n])".toRegex()) {
                "```${it.groupValues[1]}\n${it.groupValues[2]}"
            }
            result = result.replace("([^\\n])```".toRegex()) {
                "${it.groupValues[1]}\n```"
            }
            
            // 4. 修复没有结束标记的代码块
            result = result.replace("```(\\w*)(\\s*[^`]*?)$".toRegex()) {
                "```${it.groupValues[1]}${it.groupValues[2]}\n```"
            }
            
            // 5. 处理特殊情况：移除可能导致问题的控制字符
            result = result.replace("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]".toRegex(), "")
            
            return result
        }

        /**
         * 解析Markdown文本为HTML
         */
        private fun parseMarkdown(text: String): String {
            // 使用SimpleMarkdownParser处理文本，防止嵌套和复杂的格式问题
            val processedText = text
            
            // 处理代码块，确保它们全部被正确处理
            // 我们使用一个简单的方法：先用占位符替换所有代码块，处理其他Markdown元素后再替换回来
            val codeBlocks = mutableListOf<String>()
            val placeholderRegex = "CODE_BLOCK_PLACEHOLDER_(\\d+)".toRegex()
            
            // 替换代码块为占位符
            var tempText = processedText.replace(codeBlockRegex) { matchResult ->
                val language = matchResult.groupValues[1].trim()
                val code = matchResult.groupValues[2].trim()
                
                // 跳过空代码块
                if (code.isBlank()) {
                    return@replace ""
                }
                
                val index = codeBlocks.size
                val placeholder = "CODE_BLOCK_PLACEHOLDER_$index"
                
                // 保存代码块信息
                codeBlocks.add("$language:::$code")
                
                placeholder
            }
            
            // 处理Mermaid图表
            tempText = tempText.replace(mermaidRegex) { matchResult ->
                val mermaidCode = matchResult.groupValues[1].trim()
                
                // 跳过空Mermaid图表
                if (mermaidCode.isBlank()) {
                    return@replace ""
                }
                
                """<div class="mermaid">
                   |$mermaidCode
                   |</div>""".trimMargin()
            }
            
            // 现在处理其他Markdown元素
            var htmlText = tempText
            
            // 处理行内代码 (`code`)
            htmlText = inlineCodeRegex.replace(htmlText) { matchResult ->
                val code = matchResult.groupValues[1]
                // 对行内代码进行高亮处理
                val highlightedCode = highlightInlineCode(code)
                
                // 预处理代码（移除括号内容）
                val processedCode = preprocessCodeText(code)
                
                // 对URL进行编码，确保特殊字符不会导致问题
                val encodedCode = java.net.URLEncoder.encode(processedCode, "UTF-8")
                
                // 直接将处理后的代码放在URL中，不再依赖HTML属性
                "<a href=\"#code-search?code=$encodedCode\" class=\"inline-code-link\"><code class=\"inline-code\">$highlightedCode</code></a>"
            }

            // 处理标题 (# 标题)
            htmlText = headerRegex.replace(htmlText) { matchResult ->
                val level = matchResult.groupValues[1].length
                val title = matchResult.groupValues[2].trim()
                "<h$level>$title</h$level>"
            }

            // 处理粗体 (**粗体**)
            htmlText = boldRegex.replace(htmlText) { matchResult ->
                "<strong>${matchResult.groupValues[1]}</strong>"
            }

            // 处理斜体 (*斜体*)
            htmlText = italicRegex.replace(htmlText) { matchResult ->
                "<em>${matchResult.groupValues[1]}</em>"
            }

            // 处理无序列表 (- 项目)
            htmlText = unorderedListRegex.replace(htmlText) { matchResult ->
                "<li>${matchResult.groupValues[1]}</li>"
            }
            // 将连续的<li>元素包裹在<ul>标签中
            htmlText = listGroupRegex.replace(htmlText) { matchResult ->
                "<ul>\n${matchResult.value}\n</ul>"
            }

            // 处理有序列表 (1. 项目)
            htmlText = orderedListRegex.replace(htmlText) { matchResult ->
                "<li>${matchResult.groupValues[1]}</li>"
            }
            // 将连续的<li>元素包裹在<ol>标签中，但避免重复处理已包含在<ul>中的<li>
            htmlText = orderedListGroupRegex.replace(htmlText) { matchResult ->
                "<ol>\n${matchResult.value}\n</ol>"
            }

            // 处理超链接 [文字](链接)
            htmlText = linkRegex.replace(htmlText) { matchResult ->
                val text = matchResult.groupValues[1]
                val url = matchResult.groupValues[2]
                "<a href=\"$url\">$text</a>"
            }

            // 处理图片 ![alt](url)
            htmlText = imageRegex.replace(htmlText) { matchResult ->
                val alt = matchResult.groupValues[1]
                val url = matchResult.groupValues[2]
                "<img src=\"$url\" alt=\"$alt\" />"
            }

            // 处理引用块 (> 引用)
            htmlText = blockquoteRegex.replace(htmlText) { matchResult ->
                "<blockquote>${matchResult.groupValues[1]}</blockquote>"
            }

            // 处理水平线 (---, ***, ___)
            htmlText = hrRegex.replace(htmlText) {
                "<hr/>"
            }

            // 最后处理段落和换行
            // 先将连续的多个换行替换为段落分隔符
            htmlText = multipleNewlinesRegex.replace(htmlText) {
                "</p><p>"
            }

            // 处理单个换行为<br/>
            htmlText = singleNewlineRegex.replace(htmlText) {
                "<br/>"
            }

            // 包装可能裸露的内容到段落标签中
            htmlText = "<p>" + htmlText + "</p>"

            // 修复由于替换可能导致的多余或空段落
            htmlText = emptyParagraphRegex.replace(htmlText, "")
            htmlText = paragraphStartFixRegex.replace(htmlText) { matchResult ->
                "<${matchResult.groupValues[1]}>"
            }
            htmlText = paragraphEndFixRegex.replace(htmlText) { matchResult ->
                "</${matchResult.groupValues[1]}>"
            }
            
            // 最后，将代码块占位符替换回实际的代码块
            htmlText = placeholderRegex.replace(htmlText) { matchResult ->
                val index = matchResult.groupValues[1].toInt()
                if (index < codeBlocks.size) {
                    val blockInfo = codeBlocks[index].split(":::", limit = 2)
                    val language = blockInfo[0]
                    val code = blockInfo[1]
                        .replace("&", "&amp;")
                        .replace("<", "&lt;")
                        .replace(">", "&gt;")
                        .replace("\"", "&quot;")
                    
                    // 为代码块中的标识符添加特殊样式
                    val highlightedCode = if (language.isEmpty() || language == "plain") {
                        code
                    } else {
                        highlightCodeSyntax(code)
                    }
                    
                    val langClass = if (language.isNotEmpty()) " class=\"language-$language\"" else ""
                    "<pre><code$langClass>$highlightedCode</code></pre>"
                } else {
                    "" // 如果索引超出范围，返回空字符串
                }
            }

            return htmlText
        }

        /**
         * 处理内联代码文本，移除括号内容
         */
        private fun preprocessCodeText(code: String): String {
            // 移除括号及其内容的正则表达式
            val bracketsContentRegex = "\\([^)]*\\)".toRegex()
            return code.replace(bracketsContentRegex, "").trim()
        }

        /**
         * 简单的代码语法高亮
         */
        private fun highlightCodeSyntax(code: String): String {
            var result = code

            // 高亮关键字和标识符
            result = codeIdentifierRegex.replace(result) { matchResult ->
                val identifier = matchResult.value
                val keywords = setOf(
                    "if", "else", "for", "while", "return", "break", "continue",
                    "class", "interface", "enum", "extends", "implements", "new",
                    "public", "private", "protected", "static", "final", "void",
                    "int", "long", "float", "double", "boolean", "char", "byte",
                    "String", "Object", "List", "Map", "Set", "function", "var", "let", "const"
                )

                when {
                    keywords.contains(identifier) ->
                        "<span class=\"keyword\">$identifier</span>"
                    identifier.first().isUpperCase() ->
                        "<span class=\"class-name\">$identifier</span>"
                    else ->
                        "<span class=\"identifier\">$identifier</span>"
                }
            }

            // 高亮特殊的全大写标识符（如常量）
            result = specialIdentifierRegex.replace(result) { matchResult ->
                "<span class=\"constant\">${matchResult.value}</span>"
            }

            return result
        }

        /**
         * 对行内代码进行语法高亮
         */
        private fun highlightInlineCode(code: String): String {
            var result = code

            // 对特殊类名、方法名进行高亮
            val specialCodeElements = setOf(
                "GetTemperatureData", "TemperatureData", "TempBuffer", "TEMP_SCALE",
                "RGB", "YUV", "YUVToRGB", "clamp"
            )

            // 高亮特殊符号
            for (element in specialCodeElements) {
                result = result.replace("\\b$element\\b".toRegex()) {
                    "<span class=\"${getStyleClassForElement(element)}\">$element</span>"
                }
            }

            // 高亮特殊的全大写标识符（如常量）
            result = specialIdentifierRegex.replace(result) { matchResult ->
                "<span class=\"constant\">${matchResult.value}</span>"
            }

            return result
        }

        /**
         * 根据元素类型返回相应的CSS类名
         */
        private fun getStyleClassForElement(element: String): String {
            return when {
                element.first().isUpperCase() && element.all { it.isUpperCase() || it == '_' } -> "constant"
                element.first().isUpperCase() -> "class-name"
                element.matches("^[a-z][a-zA-Z0-9]*$".toRegex()) -> "method"
                else -> "identifier"
            }
        }

        /**
         * 将HTML内容包装在完整的HTML文档中
         */
        private fun wrapHtml(htmlContent: String, includeMermaid: Boolean): String {
            val mermaidSupport = if (includeMermaid) MERMAID_SCRIPT else ""

            // 检测当前主题是否为暗色主题
            val isDarkTheme = try {
                val lafManager = LafManager.getInstance()
                UIUtil.isUnderDarcula() || lafManager.currentLookAndFeel.name.contains("Dark", ignoreCase = true)
            } catch (e: Exception) {
                false // 如果发生异常，默认使用亮色主题
            }

            // 根据主题选择适当的颜色
            val backgroundColor = if (isDarkTheme) "#2B2B2B" else "#FFFFFF"
            val textColor = if (isDarkTheme) "#A9B7C6" else "#000000"
            val codeBackgroundColor = if (isDarkTheme) "#3C3F41" else "#F5F5F5"
            val codeBorderColor = if (isDarkTheme) "#555555" else "#E0E0E0"
            val inlineCodeBackgroundColor = if (isDarkTheme) "#3C3F41" else "#F0F0F0"
            val linkColor = if (isDarkTheme) "#589DF6" else "#0366D6"
            val blockquoteColor = if (isDarkTheme) "#808080" else "#666666"
            val blockquoteBorderColor = if (isDarkTheme) "#555555" else "#DDDDDD"

            // 为代码高亮设置颜色
            val keywordColor = if (isDarkTheme) "#CC7832" else "#0000FF" // 橙色/蓝色
            val classNameColor = if (isDarkTheme) "#A9B7C6" else "#008000" // 浅蓝/绿色
            val identifierColor = if (isDarkTheme) "#FFC66D" else "#660E7A" // 黄色/紫色
            val methodColor = if (isDarkTheme) "#FFC66D" else "#000080" // 黄色/深蓝色
            val constantColor = if (isDarkTheme) "#9876AA" else "#0000FF" // 紫色/蓝色

            return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body {
                            font-family: "JetBrains Mono", "Segoe UI", "Fira Code", Consolas, monospace;
                            font-size: 13px;
                            line-height: 1.6;
                            margin: 10px;
                            background-color: $backgroundColor;
                            color: $textColor;
                        }
                        code.inline-code {
                            font-family: "JetBrains Mono", Consolas, monospace;
                            background-color: $inlineCodeBackgroundColor;
                            padding: 2px 4px;
                            border-radius: 3px;
                            border: 1px solid $codeBorderColor;
                            font-weight: bold;
                            color: ${if (isDarkTheme) "#A9B7C6" else "#000000"};
                        }
                        pre {
                            background-color: $codeBackgroundColor;
                            padding: 10px;
                            border-radius: 5px;
                            border: 1px solid $codeBorderColor;
                            overflow: auto;
                            margin: 10px 0;
                            min-height: 20px; /* 确保即使为空也有最小高度 */
                            max-height: 500px; /* 限制最大高度 */
                            display: block; /* 确保显示为块级元素 */
                        }
                        pre code {
                            font-family: "JetBrains Mono", Consolas, monospace;
                            background-color: transparent;
                            padding: 0;
                            color: $textColor;
                            border: none;
                            font-weight: normal;
                            display: block; /* 确保代码块始终显示为块级元素 */
                            min-height: 1em; /* 确保代码块有最小高度 */
                            white-space: pre; /* 保留空白符 */
                            word-wrap: normal; /* 防止自动换行 */
                        }
                        blockquote {
                            border-left: 4px solid $blockquoteBorderColor;
                            padding-left: 10px;
                            color: $blockquoteColor;
                            margin: 10px 0;
                        }
                        img {
                            max-width: 100%;
                            border: 1px solid $codeBorderColor;
                            border-radius: 4px;
                        }
                        h1, h2, h3, h4, h5, h6 {
                            font-family: "Segoe UI", sans-serif;
                            margin-top: 20px;
                            margin-bottom: 10px;
                            font-weight: 600;
                            line-height: 1.25;
                            color: ${if (isDarkTheme) "#D4D4D4" else "#000000"};
                        }
                        h1 { font-size: 1.8em; }
                        h2 { font-size: 1.6em; }
                        h3 { font-size: 1.4em; }
                        h4 { font-size: 1.2em; }
                        h5 { font-size: 1.1em; }
                        h6 { font-size: 1em; }
                        ul, ol {
                            padding-left: 20px;
                        }
                        li {
                            margin: 4px 0;
                        }
                        a {
                            color: $linkColor;
                            text-decoration: none;
                        }
                        a:hover {
                            text-decoration: underline;
                        }
                        .mermaid {
                            margin: 16px 0;
                            text-align: center;
                            background-color: ${if (isDarkTheme) "#3C3F41" else "#FFFFFF"};
                            padding: 10px;
                            border-radius: 8px;
                        }
                        hr {
                            border: none;
                            height: 1px;
                            background-color: ${if (isDarkTheme) "#555555" else "#DDDDDD"};
                            margin: 16px 0;
                        }
                        
                        /* 代码高亮样式 */
                        .keyword {
                            color: $keywordColor;
                            font-weight: bold;
                        }
                        .class-name {
                            color: $classNameColor;
                            font-weight: bold;
                        }
                        .identifier {
                            color: $identifierColor;
                        }
                        .method {
                            color: $methodColor;
                        }
                        .constant {
                            color: $constantColor;
                            font-weight: bold;
                        }
                        
                        /* 适配IDEA暗色主题的额外调整 */
                        ${if (isDarkTheme) """
                            ::selection {
                                background-color: #214283;
                                color: #A9B7C6;
                            }
                        """ else ""}
                        .inline-code-link {
                            cursor: pointer;
                            text-decoration: none;
                        }
                        .inline-code-link:hover code {
                            border-color: $linkColor;
                        }
                        .inline-code-link code {
                            color: $linkColor;
                            background-color: ${if (isDarkTheme) "#364759" else "#E6F5FF"};
                            border: 1px solid ${if (isDarkTheme) "#3D6185" else "#CCE5FF"};
                        }
                    </style>
                    $mermaidSupport
                </head>
                <body>
                $htmlContent
                </body>
                </html>
            """.trimIndent()
        }
    }
} 