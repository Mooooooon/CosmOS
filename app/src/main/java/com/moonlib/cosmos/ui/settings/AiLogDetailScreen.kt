package com.moonlib.cosmos.ui.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moonlib.cosmos.data.settings.AiLog
import com.moonlib.cosmos.ui.theme.StarWhite
import org.json.JSONObject

/**
 * AI 通讯日志详情页面。
 * 职责单一：渲染单条日志的可折叠详情板块。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiLogDetailScreen(
    log: AiLog?,
    onBackClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "日志详情",
                        fontSize = 20.sp,
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
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (log == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "未找到该日志信息", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AiLogTextSection(
                    title = "用户输入内容",
                    icon = Icons.AutoMirrored.Filled.Message,
                    iconBgColor = Color(0xFF3B82F6),
                    rawText = log.userInput,
                    copyToastText = "用户输入已复制",
                    useMonospace = false
                )

                AiLogTextSection(
                    title = "请求详情",
                    icon = Icons.Default.Info,
                    iconBgColor = Color(0xFFF59E0B),
                    rawText = log.requestDetails.ifBlank { "旧日志未保存请求详情，请在产生新日志后查看完整通信请求结构。" },
                    copyToastText = "请求详情已复制",
                    formatAsJson = log.requestDetails.isNotBlank()
                )

                AiLogTextSection(
                    title = "AI 返回的全部内容",
                    icon = Icons.Default.Terminal,
                    iconBgColor = Color(0xFF10B981),
                    rawText = log.aiResponse,
                    copyToastText = "AI 响应已复制",
                    formatAsJson = true
                )

                AiLogTextSection(
                    title = "本次发送的全部提示词",
                    icon = Icons.Default.Psychology,
                    iconBgColor = Color(0xFF8B5CF6),
                    rawText = log.prompt,
                    copyToastText = "提示词已复制"
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun AiLogTextSection(
    title: String,
    icon: ImageVector,
    iconBgColor: Color,
    rawText: String,
    copyToastText: String,
    useMonospace: Boolean = true,
    formatAsJson: Boolean = false
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val displayText = remember(rawText, formatAsJson) {
        if (formatAsJson) formatJson(rawText) else rawText
    }
    var softWrap by remember { mutableStateOf(true) }

    CollapsibleDetailSectionCard(
        title = title,
        icon = icon,
        iconBgColor = iconBgColor,
        showWrapToggle = useMonospace,
        isSoftWrap = softWrap,
        onWrapToggle = { softWrap = it },
        onCopyClick = {
            clipboardManager.setText(AnnotatedString(rawText))
            Toast.makeText(context, copyToastText, Toast.LENGTH_SHORT).show()
        }
    ) {
        SelectionContainer {
            if (softWrap || !useMonospace) {
                Text(
                    text = displayText,
                    fontSize = if (useMonospace) 12.sp else 14.sp,
                    fontFamily = if (useMonospace) FontFamily.Monospace else FontFamily.Default,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp),
                    softWrap = true,
                    style = LocalTextStyle.current.copy(lineHeight = if (useMonospace) 18.sp else 20.sp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.background,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp)
                ) {
                    Text(
                        text = displayText,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun CollapsibleDetailSectionCard(
    title: String,
    icon: ImageVector,
    iconBgColor: Color,
    onCopyClick: () -> Unit,
    showWrapToggle: Boolean,
    isSoftWrap: Boolean,
    onWrapToggle: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (expanded) "折叠" else "展开",
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = StarWhite,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (expanded && showWrapToggle) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSoftWrap) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.05f)
                            )
                            .clickable { onWrapToggle(!isSoftWrap) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (isSoftWrap) "自动换行" else "单行排版",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSoftWrap) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            }
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                }

                IconButton(
                    onClick = onCopyClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制内容",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        if (expanded) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp)
                ) {
                    content()
                }
            }
        }
    }
}

private fun formatJson(raw: String): String {
    val trimmed = raw.trim()
    var clean = trimmed
    if (clean.startsWith("```")) {
        val firstLineEnd = clean.indexOf("\n")
        if (firstLineEnd != -1) {
            clean = clean.substring(firstLineEnd + 1)
        }
        if (clean.endsWith("```")) {
            clean = clean.substring(0, clean.length - 3)
        }
        clean = clean.trim()
    }

    try {
        if (clean.startsWith("{") && clean.endsWith("}")) {
            return JSONObject(clean).toString(4)
        }
    } catch (e: Exception) {
        // 解析失败，继续退化到原始文本。
    }

    try {
        if (clean.startsWith("[") && clean.endsWith("]")) {
            return org.json.JSONArray(clean).toString(4)
        }
    } catch (e: Exception) {
        // 解析失败，继续退化到原始文本。
    }

    try {
        val startBrace = clean.indexOf("{")
        val endBrace = clean.lastIndexOf("}")
        if (startBrace != -1 && endBrace != -1 && endBrace > startBrace) {
            val candidate = clean.substring(startBrace, endBrace + 1)
            return JSONObject(candidate).toString(4)
        }
    } catch (e: Exception) {
        // 提取 JSONObject 失败，保留原文。
    }

    return raw
}
