package com.moonlib.cosmos.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.settings.VoiceServiceProfile
import com.moonlib.cosmos.data.settings.VoiceServiceType
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceServiceConfigScreen(
    initialProfile: VoiceServiceProfile?,
    onBackClick: () -> Unit,
    onSaveClick: (VoiceServiceProfile) -> Unit,
    modifier: Modifier = Modifier
) {
    val serviceType = VoiceServiceType.MINIMAX
    var name by remember { mutableStateOf(initialProfile?.name ?: "") }
    var apiKey by remember { mutableStateOf(initialProfile?.apiKey ?: "") }
    var baseUrl by remember { mutableStateOf(initialProfile?.baseUrl ?: serviceType.defaultUrl) }
    var modelName by remember { mutableStateOf(initialProfile?.modelName ?: serviceType.defaultModel) }
    var isKeyVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (name.isBlank()) name = serviceType.displayName
    }

    val isFormValid = name.isNotBlank() && apiKey.isNotBlank() && baseUrl.isNotBlank() && modelName.isNotBlank()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (initialProfile == null) "添加语音服务" else "修改语音服务",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                windowInsets = WindowInsets(0.dp),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "MiniMax 语音合成",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    VoiceTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = "配置名称",
                        placeholder = "例如：我的语音服务"
                    )
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key") },
                        placeholder = { Text("输入 MiniMax API 访问密钥") },
                        singleLine = true,
                        visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "切换可见性"
                                )
                            }
                        },
                        colors = voiceTextFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    VoiceTextField(
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        label = "API 端点 (Base URL)",
                        placeholder = serviceType.defaultUrl
                    )
                    VoiceTextField(
                        value = modelName,
                        onValueChange = { modelName = it },
                        label = "语音模型",
                        placeholder = serviceType.defaultModel
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("speech-2.8-turbo", "speech-2.8-hd", "speech-2.6-turbo").forEach { model ->
                            Button(
                                onClick = { modelName = model },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (modelName == model) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    contentColor = if (modelName == model) Color.White else MaterialTheme.colorScheme.primary
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                                contentPadding = PaddingValues(horizontal = 10.dp)
                            ) {
                                Text(model, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (isFormValid) {
                        onSaveClick(
                            VoiceServiceProfile(
                                id = initialProfile?.id ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                serviceType = serviceType,
                                apiKey = apiKey.trim(),
                                baseUrl = baseUrl.trim().removeSuffix("/"),
                                modelName = modelName.trim(),
                                isActive = initialProfile?.isActive ?: false
                            )
                        )
                    }
                },
                enabled = isFormValid,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Text("保存配置", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun VoiceTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = { Text(placeholder) },
        singleLine = true,
        colors = voiceTextFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun voiceTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
)
