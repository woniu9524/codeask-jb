package com.github.woniu9524.codeaskjb.model

import kotlinx.serialization.Serializable

/**
 * CodeAsk 数据模型
 * 用于解析和存储.codeaskdata文件内容
 */
@Serializable
data class CodeAskData(
    // 插件数据映射，键为插件ID
    val plugins: Map<String, PluginData> = emptyMap(),
    // 全局分析结果
    val globalAnalysis: GlobalAnalysis? = null
)

/**
 * 插件数据模型
 * 包含插件的基本信息和相关文件的处理结果
 */
@Serializable
data class PluginData(
    // 插件名称
    val pluginName: String,
    // 模型ID
    val modelId: String? = null,
    // 系统提示词
    val systemPrompt: String? = null,
    // 用户提示词
    val userPrompt: String? = null,
    // 插件规则
    val rules: Rules? = null,
    // 已处理的文件列表
    val files: List<FileData> = emptyList()
)

/**
 * 插件规则
 * 定义插件的处理规则和显示偏好
 */
@Serializable
data class Rules(
    // 支持的文件扩展名
    val fileExtensions: List<String> = emptyList(),
    // 是否显示已处理的文件
    val showProcessed: Boolean = true,
    // 是否显示已更新的文件
    val showUpdated: Boolean = false
)

/**
 * 文件数据
 * 存储单个文件的解释内容及状态
 */
@Serializable
data class FileData(
    // 文件路径
    val filename: String,
    // 文件哈希值，用于检测文件是否变更
    val fileHash: String? = null,
    // 解释内容
    val result: String,
    // 处理状态
    val status: String
)

/**
 * 全局分析数据
 * 存储项目级别的分析结果
 */
@Serializable
data class GlobalAnalysis(
    // 全局分析结果映射，键为分析ID
    val results: Map<String, GlobalAnalysisResult> = emptyMap()
)

/**
 * 全局分析结果
 * 单个全局分析的详细信息
 */
@Serializable
data class GlobalAnalysisResult(
    // 分析名称
    val globalAnalysisName: String,
    // 单页提示词
    val singlePagePrompt: String? = null,
    // 摘要提示词
    val summaryPrompt: String? = null,
    // 分析摘要
    val summary: String,
    // 时间戳
    val timestamp: Long
) 