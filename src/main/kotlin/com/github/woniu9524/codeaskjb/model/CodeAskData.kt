package com.github.woniu9524.codeaskjb.model

import kotlinx.serialization.Serializable

/**
 * CodeAsk 数据模型
 * 用于解析和存储.codeaskdata文件内容
 */
@Serializable
data class CodeAskData(
    val plugins: Map<String, PluginData> = emptyMap(),
    val globalAnalysis: GlobalAnalysis? = null
)

/**
 * 插件数据
 */
@Serializable
data class PluginData(
    val pluginName: String,
    val modelId: String? = null,
    val systemPrompt: String? = null,
    val userPrompt: String? = null,
    val rules: Rules? = null,
    val files: List<FileData> = emptyList()
)

/**
 * 规则数据
 */
@Serializable
data class Rules(
    val fileExtensions: List<String> = emptyList(),
    val showProcessed: Boolean = true,
    val showUpdated: Boolean = false
)

/**
 * 文件数据
 */
@Serializable
data class FileData(
    val filename: String,
    val fileHash: String? = null,
    val result: String,
    val status: String
)

/**
 * 全局分析数据
 */
@Serializable
data class GlobalAnalysis(
    val results: Map<String, GlobalAnalysisResult> = emptyMap()
)

/**
 * 全局分析结果
 */
@Serializable
data class GlobalAnalysisResult(
    val globalAnalysisName: String,
    val singlePagePrompt: String? = null,
    val summaryPrompt: String? = null,
    val summary: String,
    val timestamp: Long
) 