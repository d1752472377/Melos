package com.aesthetic.music.ui.shell.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aesthetic.music.ui.components.AppDialog
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft

/** 歌单导入弹窗：粘贴网易云歌单链接或输入歌单 ID */
@Composable
fun ImportPlaylistDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    AppDialog(
        onDismissRequest = onDismiss,
        title = "导入歌单",
        confirmText = "导入",
        onConfirm = {
            val id = Regex("""\d{5,}""").find(input.trim())?.value ?: input.trim()
            if (id.isNotEmpty()) onImport(id)
        },
        cancelText = "取消",
    ) {
        Column {
            Text(
                "粘贴网易云歌单链接或直接输入歌单 ID\n如: https://music.163.com/#/playlist?id=5202687076",
                style = MaterialTheme.typography.bodySmall,
                color = TextSoft,
            )
            Spacer(Modifier.size(10.dp))
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Stone100, RoundedCornerShape(0.dp))
                    .padding(12.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                decorationBox = { inner ->
                    if (input.isEmpty()) {
                        Text("歌单链接 / ID", color = TextSoft, style = MaterialTheme.typography.bodyMedium)
                    }
                    inner()
                },
            )
        }
    }
}
