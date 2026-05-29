package com.moonlib.cosmos.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.settings.AiProfile
import com.moonlib.cosmos.data.settings.AiServiceType
import com.moonlib.cosmos.ui.theme.StarWhite
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
        mutableStateOf(initialProfile?.temperature ?: 0.7f)
    }

    // 状态控制：API Key 是否可见
    var isApiKeyVisible by remember { mutableStateOf(false) }

    // ── 自动填充智能逻辑 ──────────────────────────────────────
    val updateServiceType = { type: AiServiceType ->
        serviceType = type
        
        // 自动填写配置名称默认提示（如果用户没写或者还是旧服务商名）
        if (name.isEmpty() || name == "OpenAI" || name == "DeepSeek" || name == "Gemini") {
            name = type.displayName
        }

        // 只有当端点和模型是旧服务商的默认值，或为空时，才进行自动覆盖填充
        val currentDefaults = listOf(
            AiServiceType.OPEN_AI.defaultUrl,
            AiServiceType.DEEP_SEEK.defaultUrl,
            AiServiceType.GEMINI.defaultUrl
        )
        if (baseUrl.trim() in currentDefaults || baseUrl.trim().isEmpty()) {
            baseUrl = type.defaultUrl
        }

        val currentModelDefaults = listOf(
            AiServiceType.OPEN_AI.defaultModel,
            AiServiceType.DEEP_SEEK.defaultModel,
            AiServiceType.GEMINI.defaultModel
        )
        if (modelName.trim() in currentModelDefaults || modelName.trim().isEmpty()) {
            modelName = type.defaultModel
        }
    }

    // 初始化时赋默认值名字
    LaunchedEffect(key1 = initialProfile) {
        if (!isEditMode && name.isEmpty()) {
            name = serviceType.displayName
        }
    }

    // ── 表单合法性校验 ────────────────────────────────────────
    val isFormValid = name.isNotBlank() && apiKey.isNotBlank() && baseUrl.isNotBlank() && modelName.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditMode) "修改配置文件" else "添加配置文件",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = StarWhite
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = StarWhite
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = StarWhite
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
            Column {
                Text(
                    text = "选择服务商类型",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiServiceType.values().forEach { type ->
                        val isSelected = serviceType == type
                        val brandColor = when (type) {
                            AiServiceType.OPEN_AI -> Color(0xFF10B981)
                            AiServiceType.DEEP_SEEK -> Color(0xFF3B82F6)
                            AiServiceType.GEMINI -> Color(0xFF8B5CF6)
                        }

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { updateServiceType(type) },
                            border = if (isSelected) {
                                BorderStroke(2.dp, StarWhite)
                            } else {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                            },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (isSelected) Modifier.background(brandColor) else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = type.displayName,
                                    color = StarWhite,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

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
                            focusedTextColor = StarWhite,
                            unfocusedTextColor = StarWhite,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // API Key
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("输入您的 API 访问密钥") },
                        singleLine = true,
                        visualTransformation = if (isApiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { isApiKeyVisible = !isApiKeyVisible }) {
                                Icon(
                                    imageVector = if (isApiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "切换可见性",
                                    tint = StarWhite.copy(alpha = 0.6f)
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = StarWhite,
                            unfocusedTextColor = StarWhite,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Base URL
                    OutlinedTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = { Text("API 端点 (Base URL)") },
                        placeholder = { Text("例如：https://api.openai.com/v1") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = StarWhite,
                            unfocusedTextColor = StarWhite,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Model Name
                    OutlinedTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        label = { Text("模型名称 (Model Name)") },
                        placeholder = { Text("例如：gpt-4o 或 deepseek-chat") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = StarWhite,
                            unfocusedTextColor = StarWhite,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // 温度 (Temperature) 滑动条
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "创意温度 (Temperature)",
                                color = StarWhite.copy(alpha = 0.8f),
                                fontSize = 13.sp
                            )
                            Text(
                                text = String.format("%.1f", temperature),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Slider(
                            value = temperature,
                            onValueChange = { temperature = it },
                            valueRange = 0.0f..1.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("精确 (0.0)", color = StarWhite.copy(alpha = 0.4f), fontSize = 10.sp)
                            Text("默认 (0.7)", color = StarWhite.copy(alpha = 0.4f), fontSize = 10.sp)
                            Text("极富创意 (1.5)", color = StarWhite.copy(alpha = 0.4f), fontSize = 10.sp)
                        }
                    }
                }
            }

            // ── 3. 保存动作按钮 ─────────────────────────────────────
            Button(
                onClick = {
                    if (isFormValid) {
                        val finalProfile = AiProfile(
                            id          = initialProfile?.id ?: UUID.randomUUID().toString(),
                            name        = name.trim(),
                            serviceType = serviceType,
                            apiKey      = apiKey.trim(),
                            baseUrl     = baseUrl.trim(),
                            modelName   = modelName.trim(),
                            temperature = temperature,
                            isActive    = initialProfile?.isActive ?: false
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
                    color = if (isFormValid) StarWhite else StarWhite.copy(alpha = 0.3f)
                )
            }
        }
    }
}
