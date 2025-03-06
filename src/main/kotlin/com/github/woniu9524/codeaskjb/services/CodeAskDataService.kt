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
 * 负责加载和解析.codeaskdata文件，提供文件解释数据访问
 */
@Service(Service.Level.PROJECT)
class CodeAskDataService(private val project: Project) {
    // 配置JSON解析器，忽略未知键
    private val json = Json { ignoreUnknownKeys = true }
    
    // 数据缓存
    private var codeAskData: CodeAskData? = null
    private var dataFile: VirtualFile? = null
    
    init {
        // 初始化时加载数据
        loadData()
        
        // 监听文件变化，当.codeaskdata文件变更时重新加载数据
        VirtualFileManager.getInstance().addVirtualFileListener(object : VirtualFileListener {
            override fun contentsChanged(event: VirtualFileEvent) {
                if (event.file == dataFile) {
                    loadData()
                }
            }
        }, project)
    }
    
    /**
     * 加载.codeaskdata文件内容
     * 文件位于项目根目录下
     */
    fun loadData() {
        try {
            val projectBasePath = project.basePath ?: return
            val codeAskDataFile = File(Paths.get(projectBasePath, ".codeaskdata").toString())
            
            // 文件不存在则直接返回
            if (!codeAskDataFile.exists()) {
                return
            }
            
            // 刷新文件并加载
            dataFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(codeAskDataFile)
            if (dataFile == null) {
                return
            }
            
            // 解析JSON数据
            val content = String(dataFile!!.contentsToByteArray())
            codeAskData = json.decodeFromString(content)
        } catch (e: Exception) {
            // 解析错误时静默处理
        }
    }
    
    /**
     * 获取指定文件的解释数据
     * 
     * @param filePath 文件绝对路径
     * @return 文件解释数据列表，每个元素为插件名和对应的解释
     */
    fun getFileExplanation(filePath: String): List<Pair<String, FileData>> {
        val data = codeAskData ?: return emptyList()
        val result = mutableListOf<Pair<String, FileData>>()
        
        // 将绝对路径转换为项目相对路径
        val projectBasePath = project.basePath ?: return emptyList()
        val relativePath = if (filePath.startsWith(projectBasePath)) {
            filePath.substring(projectBasePath.length + 1)
        } else {
            filePath
        }
        
        // 查找所有插件中匹配该文件的解释
        data.plugins.forEach { (_, pluginData) ->
            pluginData.files.find { it.filename == relativePath }?.let {
                result.add(Pair(pluginData.pluginName, it))
            }
        }
        
        return result
    }
    
    /**
     * 获取所有插件名称
     * 
     * @return 插件名称列表
     */
    fun getAllPluginNames(): List<String> {
        return codeAskData?.plugins?.values?.map { it.pluginName } ?: emptyList()
    }
    
    companion object {
        /**
         * 获取服务实例
         */
        @JvmStatic
        fun getInstance(project: Project): CodeAskDataService = project.service()
    }
} 