package com.github.woniu9524.codeaskjb.services

import com.github.woniu9524.codeaskjb.model.CodeAskData
import com.github.woniu9524.codeaskjb.model.FileData
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Paths

/**
 * 代码解释数据服务
 * 负责加载和解析.codeaskdata文件
 */
@Service(Service.Level.PROJECT)
class CodeAskDataService(private val project: Project) {
    private val json = Json { ignoreUnknownKeys = true }
    
    private var codeAskData: CodeAskData? = null
    private var dataFile: VirtualFile? = null
    
    init {
        // 初始化时尝试加载数据
        loadData()
        
        // 监听文件变化
        VirtualFileManager.getInstance().addVirtualFileListener(object : VirtualFileListener {
            override fun contentsChanged(event: VirtualFileEvent) {
                if (event.file == dataFile) {
                    loadData()
                }
            }
        }, project)
    }
    
    /**
     * 加载.codeaskdata文件
     */
    fun loadData() {
        try {
            val projectBasePath = project.basePath ?: return
            val codeAskDataFile = File(Paths.get(projectBasePath, ".codeaskdata").toString())
            
            if (!codeAskDataFile.exists()) {
                return
            }
            
            dataFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(codeAskDataFile)
            if (dataFile == null) {
                return
            }
            
            val content = String(dataFile!!.contentsToByteArray())
            codeAskData = json.decodeFromString(content)
        } catch (e: Exception) {
            // 处理异常但不记录日志
        }
    }
    
    /**
     * 获取指定文件的解释数据
     */
    fun getFileExplanation(filePath: String): List<Pair<String, FileData>> {
        val data = codeAskData ?: return emptyList()
        val result = mutableListOf<Pair<String, FileData>>()
        
        // 将相对路径转换为项目相对路径
        val projectBasePath = project.basePath ?: return emptyList()
        val relativePath = if (filePath.startsWith(projectBasePath)) {
            filePath.substring(projectBasePath.length + 1)
        } else {
            filePath
        }
        
        data.plugins.forEach { (_, pluginData) ->
            pluginData.files.find { it.filename == relativePath }?.let {
                result.add(Pair(pluginData.pluginName, it))
            }
        }
        
        return result
    }
    
    /**
     * 获取所有插件名称
     */
    fun getAllPluginNames(): List<String> {
        return codeAskData?.plugins?.values?.map { it.pluginName } ?: emptyList()
    }
    
    companion object {
        @JvmStatic
        fun getInstance(project: Project): CodeAskDataService = project.service()
    }
} 