package com.moonlib.cosmos.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.settings.AiAuthorizationHeader
import com.moonlib.cosmos.data.settings.AiServiceType
import com.moonlib.cosmos.data.settings.ModelListCacheRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 异步在线拉取模型列表的方法
 * 支持标准 OpenAI 接口格式，并针对 Gemini 官方 API 端点做了智能适配
 */
suspend fun fetchModelsOnline(
    serviceType: AiServiceType,
    apiKey: String,
    baseUrl: String,
    vertexRegion: String = "global"
): List<String> = withContext(Dispatchers.IO) {
    val isGeminiOfficial = serviceType == AiServiceType.GEMINI && baseUrl.contains("googleapis.com")
    if (serviceType == AiServiceType.VERTEX) {
        return@withContext fetchVertexModels(apiKey)
    }

    val urlStr = if (isGeminiOfficial) {
        val base = baseUrl.removeSuffix("/")
        "$base/v1beta/models?key=$apiKey"
    } else {
        val base = baseUrl.removeSuffix("/")
        "$base/models"
    }

    val url = URL(urlStr)
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    conn.connectTimeout = 60000
    conn.readTimeout = 60000
    
    // 非 Gemini 官方接口需要添加 Bearer token
    if (!isGeminiOfficial) {
        conn.setRequestProperty("Authorization", AiAuthorizationHeader.create(serviceType, apiKey))
    }
    conn.setRequestProperty("Content-Type", "application/json")
    conn.setRequestProperty("Accept", "application/json")

    val responseCode = conn.responseCode
    if (responseCode == 200) {
        val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
        val models = mutableListOf<String>()
        val json = JSONObject(jsonText)
        
        if (isGeminiOfficial) {
            if (json.has("models")) {
                val modelsArray = json.getJSONArray("models")
                for (i in 0 until modelsArray.length()) {
                    val modelObj = modelsArray.getJSONObject(i)
                    if (modelObj.has("name")) {
                        val fullName = modelObj.getString("name")
                        // "models/gemini-1.5-flash" -> "gemini-1.5-flash"
                        val shortName = fullName.substringAfter("models/")
                        models.add(shortName)
                    }
                }
            }
        } else {
            // OpenAI 标准响应格式
            if (json.has("data")) {
                val dataArray = json.getJSONArray("data")
                for (i in 0 until dataArray.length()) {
                    val modelObj = dataArray.getJSONObject(i)
                    if (modelObj.has("id")) {
                        models.add(modelObj.getString("id"))
                    }
                }
            }
        }
        models.distinct().sorted()
    } else {
        val errorText = try {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: Exception) {
            ""
        }
        val tip = if (responseCode == 401) "API Key 无效或未授权" else if (responseCode == 404) "端点路由错误 (404)" else "请求失败"
        throw Exception("HTTP $responseCode: $tip ${errorText.take(100)}")
    }
}

private suspend fun fetchVertexModels(
    serviceAccountJson: String
): List<String> = withContext(Dispatchers.IO) {
    val url = URL("https://aiplatform.googleapis.com/v1beta1/publishers/google/models?listAllVersions=true&pageSize=200")
    val conn = url.openConnection() as HttpURLConnection
    conn.requestMethod = "GET"
    conn.connectTimeout = 60000
    conn.readTimeout = 60000
    conn.setRequestProperty("Authorization", AiAuthorizationHeader.create(AiServiceType.VERTEX, serviceAccountJson))
    conn.setRequestProperty("Accept", "application/json")

    if (conn.responseCode != 200) {
        val errorText = try {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: Exception) {
            ""
        }
        throw Exception(
            "Vertex 未返回模型列表 HTTP ${conn.responseCode}，请检查服务账号权限。${errorText.take(120)}"
        )
    }

    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
    val models = mutableListOf<String>()
    val array = json.optJSONArray("publisherModels") ?: json.optJSONArray("models")
    if (array != null) {
        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val id = item.optString("name").substringAfterLast("/")
                .takeIf { it.isNotBlank() }
            if (id != null && id.startsWith("gemini-")) {
                models.add(id)
            }
        }
    }
    if (models.isEmpty()) {
        throw Exception("Vertex /models 响应为空，已保留推荐列表")
    }
    models.distinct().sorted()
}

private fun vertexRecommendModels(): List<String> {
    return listOf(
        "gemini-3-pro-preview",
        "gemini-3-flash-preview",
        "gemini-2.5-pro",
        "gemini-2.5-flash",
        "gemini-2.5-flash-lite",
        "gemini-2.5-flash-image-preview",
        "gemini-2.0-flash-001",
        "gemini-2.0-flash-lite-001",
        "gemini-1.5-pro-002",
        "gemini-1.5-flash-002"
    )
}

/**
 * 智能模型选择与在线获取对话框
 * 职责单一：负责展示及选择 AI 模型列表，提供本地快捷推荐和在线拉取功能
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectDialog(
    serviceType: AiServiceType,
    apiKey: String,
    baseUrl: String,
    vertexRegion: String = "global",
    onDismiss: () -> Unit,
    onModelSelected: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val cacheRepository = remember { ModelListCacheRepository(context) }
    val cacheKey = remember(serviceType, baseUrl, vertexRegion) {
        ModelListCacheRepository.key(serviceType, baseUrl, vertexRegion)
    }
    var isLoading by remember { mutableStateOf(false) }
    var onlineModels by remember(cacheKey) { mutableStateOf(cacheRepository.getModels(cacheKey)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // 本地推荐模型，作为无网或未填写 Key 时的兜底
    val localRecommendModels = when (serviceType) {
        AiServiceType.OPEN_AI -> listOf("gpt-4o", "gpt-4o-mini", "o1-mini", "o1-preview", "gpt-4-turbo", "gpt-3.5-turbo")
        AiServiceType.DEEP_SEEK -> listOf("deepseek-chat", "deepseek-coder")
        AiServiceType.GEMINI -> listOf("gemini-1.5-flash", "gemini-1.5-pro", "gemini-1.0-pro", "gemini-2.0-flash-exp")
        AiServiceType.VERTEX -> vertexRecommendModels()
    }

    // 初始化时优先使用缓存；没有缓存时才自动拉取一次。
    LaunchedEffect(cacheKey) {
        if (onlineModels == null && apiKey.isNotBlank() && baseUrl.isNotBlank()) {
            isLoading = true
            errorMessage = null
            try {
                val fetched = fetchModelsOnline(serviceType, apiKey, baseUrl, vertexRegion)
                onlineModels = fetched
                cacheRepository.saveModels(cacheKey, fetched)
            } catch (e: Exception) {
                errorMessage = e.message ?: "网络连接失败，请检查网络"
            } finally {
                isLoading = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "选择模型名称",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "当前服务商: ${serviceType.displayName}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 搜索输入框
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("搜索模型...", fontSize = 13.sp) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "搜索",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp),
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = "正在获取最新模型列表...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                } else {
                    // 异常提示区域
                    errorMessage?.let { err ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "在线拉取失败，已展示本地推荐列表：",
                                    color = MaterialTheme.colorScheme.error,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = err,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                    fontSize = 11.sp,
                                    maxLines = 2
                                )
                            }
                        }
                    }

                    // 确定要展示的模型数据集
                    val baseModelsList = onlineModels ?: localRecommendModels
                    val filteredModels = baseModelsList.filter {
                        it.contains(searchQuery, ignoreCase = true)
                    }

                    if (filteredModels.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "未搜索到匹配的模型",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f, fill = false)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val isOnline = onlineModels != null
                            Text(
                                text = if (isOnline) "已缓存模型 (${filteredModels.size} 个)" else "常用推荐模型",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(vertical = 4.dp, horizontal = 2.dp)
                            )

                            filteredModels.forEach { model ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onModelSelected(model) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                    ),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = model,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        
                                        if (model == serviceType.defaultModel) {
                                            Text(
                                                text = "默认",
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.primary,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier
                                                    .background(
                                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isLoading) {
                TextButton(
                    onClick = {
                        if (apiKey.isBlank() || baseUrl.isBlank()) {
                            errorMessage = "请先填写 API Key 和 API 端点后再尝试拉取"
                        } else {
                            coroutineScope.launch {
                                isLoading = true
                                errorMessage = null
                                try {
                                    val fetched = fetchModelsOnline(serviceType, apiKey, baseUrl, vertexRegion)
                                    onlineModels = fetched
                                    cacheRepository.saveModels(cacheKey, fetched)
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "拉取失败，请检查配置与网络"
                                } finally {
                                    isLoading = false
                                }
                            }
                        }
                    }
                ) {
                    Text("在线刷新", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(24.dp)
    )
}
