package com.moonlib.cosmos.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.settings.AiProfile
import com.moonlib.cosmos.data.settings.AiServiceType
import com.moonlib.cosmos.data.settings.AiVertexConfig
import java.util.UUID

/**
 * 添加 / 编辑 AI 模型服务配置的表单页面
 *
 * 职责单一：负责输入及修改 AI 配置项，执行输入校验，并在保存时向外抛出模型对象
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelServiceConfigScreen(
    initialProfile: AiProfile?,
    onBackClick: () -> Unit,
    onSaveClick: (AiProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val isEditMode = initialProfile != null

    // ── 核心输入状态 ──────────────────────────────────────────
    var serviceType by remember {
        mutableStateOf(initialProfile?.serviceType ?: AiServiceType.OPEN_AI)
    }
    var name by remember {
        mutableStateOf(initialProfile?.name ?: "")
    }
    var apiKey by remember {
        mutableStateOf(initialProfile?.apiKey ?: "")
    }
    var baseUrl by remember {
        mutableStateOf(initialProfile?.baseUrl ?: AiServiceType.OPEN_AI.defaultUrl)
    }
    var modelName by remember {
        mutableStateOf(initialProfile?.modelName ?: AiServiceType.OPEN_AI.defaultModel)
    }
    var temperature by remember {
        mutableStateOf(initialProfile?.temperature ?: 1.0f)
    }
    var thinkingLevel by remember {
        mutableStateOf(initialProfile?.thinkingLevel ?: "default")
    }
    var vertexRegion by remember {
        mutableStateOf(initialProfile?.vertexRegion ?: AiVertexConfig.DEFAULT_REGION)
    }

    // 状态控制：API Key 是否可见
    var isApiKeyVisible by remember { mutableStateOf(false) }

    // 状态控制：模型选择对话框是否可见
    var showModelSelectDialog by remember { mutableStateOf(false) }

    // ── 自动填充智能逻辑 ──────────────────────────────────────
    val updateServiceType = { type: AiServiceType ->
        val previousType = serviceType
        serviceType = type
        
        // 自动填写配置名称默认提示（如果用户没写或者还是旧服务商名）
        if (name.isEmpty() || AiServiceType.entries.any { name == it.displayName }) {
            name = type.displayName
        }

        // 只有当端点和模型是旧服务商的默认值，或为空时，才进行自动覆盖填充
        val currentDefaults = AiServiceType.entries.map { it.defaultUrl }
        if (baseUrl.trim() in currentDefaults || baseUrl.trim().isEmpty() || previousType == AiServiceType.VERTEX) {
            baseUrl = type.defaultUrl
        }

        val currentModelDefaults = AiServiceType.entries.map { it.defaultModel }
        if (modelName.trim() in currentModelDefaults || modelName.trim().isEmpty()) {
            modelName = type.defaultModel
        }

        if (type == AiServiceType.VERTEX && vertexRegion.isBlank()) {
            vertexRegion = AiVertexConfig.DEFAULT_REGION
        }
    }

    // 初始化时赋默认值名字
    LaunchedEffect(key1 = initialProfile) {
        if (!isEditMode && name.isEmpty()) {
            name = serviceType.displayName
        }
    }

    // ── 表单合法性校验 ────────────────────────────────────────
    val vertexBaseUrl = remember(serviceType, apiKey, vertexRegion) {
        if (serviceType == AiServiceType.VERTEX && apiKey.isNotBlank()) {
            runCatching { AiVertexConfig.buildNativeBaseUrl(apiKey, vertexRegion) }.getOrNull()
        } else {
            null
        }
    }
    val effectiveBaseUrl = remember(serviceType, baseUrl, vertexBaseUrl) {
        if (serviceType == AiServiceType.VERTEX) {
            vertexBaseUrl.orEmpty()
        } else {
            baseUrl
        }
    }
    val isFormValid = name.isNotBlank() &&
        apiKey.isNotBlank() &&
        effectiveBaseUrl.isNotBlank() &&
        modelName.isNotBlank() &&
        (serviceType != AiServiceType.VERTEX || vertexRegion.isNotBlank())

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "修改配置文件" else "添加配置文件",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                windowInsets = WindowInsets(0.dp),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // ── 1. 服务商快捷选择面板 ───────────────────────────────
            ServiceTypeSelector(
                selectedType = serviceType,
                onTypeSelected = updateServiceType
            )

            // ── 2. 表单配置字段 ─────────────────────────────────────
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 配置名称
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("配置名称") },
                        placeholder = { Text("例如：我的生产模型") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // API Key / Vertex 服务账号 JSON
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = {
                            Text(if (serviceType == AiServiceType.VERTEX) "服务账号 JSON" else "API Key")
                        },
                        placeholder = {
                            Text(if (serviceType == AiServiceType.VERTEX) "粘贴完整 Google Cloud Service Account JSON" else "输入您的 API 访问密钥")
                        },
                        singleLine = serviceType != AiServiceType.VERTEX,
                        minLines = if (serviceType == AiServiceType.VERTEX) 5 else 1,
                        maxLines = if (serviceType == AiServiceType.VERTEX) 8 else 1,
                        visualTransformation = if (isApiKeyVisible || serviceType == AiServiceType.VERTEX) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            if (serviceType != AiServiceType.VERTEX) {
                                IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                    Icon(
                                        imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "切换可见性",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (serviceType == AiServiceType.VERTEX) {
                        OutlinedTextField(
                            value = vertexRegion,
                            onValueChange = { vertexRegion = it.ifBlank { AiVertexConfig.DEFAULT_REGION } },
                            label = { Text("地区 (Location)") },
                            placeholder = { Text("global") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = if (effectiveBaseUrl.isNotBlank()) {
                                "端点: $effectiveBaseUrl"
                            } else {
                                "端点: 等待有效服务账号 JSON"
                            },
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    } else {
                        // Base URL
                        OutlinedTextField(
                            value = baseUrl,
                            onValueChange = { baseUrl = it },
                            label = { Text("API 端点 (Base URL)") },
                            placeholder = { Text("例如：https://api.openai.com/v1") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Model Name + 获取按钮
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = modelName,
                            onValueChange = { modelName = it },
                            label = { Text("模型名称 (Model Name)") },
                            placeholder = { Text("例如：gpt-4o 或 deepseek-chat") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            onClick = { showModelSelectDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .height(56.dp)
                                .align(Alignment.Bottom)
                        ) {
                            Text(
                                text = "获取",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    ModelTemperatureSlider(
                        temperature = temperature,
                        onTemperatureChange = { temperature = it }
                    )

                    if (serviceType != AiServiceType.MINIMAX) {
                        ThinkingLevelSelector(
                            serviceType = serviceType,
                            modelName = modelName,
                            thinkingLevel = thinkingLevel,
                            onThinkingLevelChange = { thinkingLevel = it }
                        )
                    }
                }
            }

            // ── 3. 保存动作按钮 ─────────────────────────────────────
            Button(
                onClick = {
                    if (isFormValid) {
                        val finalProfile = AiProfile(
                            id            = initialProfile?.id ?: UUID.randomUUID().toString(),
                            name          = name.trim(),
                            serviceType   = serviceType,
                            apiKey        = apiKey.trim(),
                            baseUrl       = effectiveBaseUrl.trim(),
                            modelName     = modelName.trim(),
                            temperature   = normalizeTemperature(temperature),
                            isActive      = initialProfile?.isActive ?: false,
                            thinkingLevel = thinkingLevel,
                            vertexRegion  = AiVertexConfig.normalizeRegion(vertexRegion)
                        )
                        onSaveClick(finalProfile)
                    }
                },
                enabled = isFormValid,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text(
                    text = "保存配置",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isFormValid) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                )
            }
        }
    }

    // ── 4. 智能模型选择弹窗 ─────────────────────────────────────
    if (showModelSelectDialog) {
        ModelSelectDialog(
            serviceType = serviceType,
            apiKey = apiKey,
            baseUrl = effectiveBaseUrl,
            vertexRegion = vertexRegion,
            onDismiss = { showModelSelectDialog = false },
            onModelSelected = { selectedModel ->
                modelName = selectedModel
                showModelSelectDialog = false
            }
        )
    }
}
