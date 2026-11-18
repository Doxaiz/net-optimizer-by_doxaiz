package com.doxaiz.netoptimizer.ui.settings

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.doxaiz.netoptimizer.core.diagnostics.CrashLog

@Composable
fun Diagnostics(onContinue: (() -> Unit)? = null) {
    val context = LocalContext.current
    var report by remember { mutableStateOf(CrashLog.read(context)) }
    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Диагностика", style = MaterialTheme.typography.titleLarge)
        Text("Последний сбой хранится на телефоне. В отчёте могут быть личные данные — проверьте его перед отправкой.")
        if (report == null) {
            Text("Сохранённых сбоев нет.")
        } else {
            SelectionContainer {
                Text(report!!, modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Net Optimizer — диагностика")
                        putExtra(Intent.EXTRA_TEXT, report)
                    }, "Поделиться отчётом"))
                }) { Text("Поделиться") }
                OutlinedButton(onClick = { CrashLog.clear(context); report = null }) { Text("Удалить") }
            }
        }
        if (onContinue != null) Button(onClick = onContinue) { Text("Продолжить запуск") }
    }
}
