package com.kiteplus.kite.mobile.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kiteplus.kite.mobile.AppContainer
import com.kiteplus.kite.mobile.data.local.ContentEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
/**
 * Every article, newest edit first, as the entry point of the app.
 */
fun ListScreen(
    container: AppContainer,
    syncing: Boolean,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit,
    onSync: () -> Unit,
) {
    val rowsFlow = remember { container.repository.observeList() }
    val rows by rowsFlow.collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kite") },
                actions = {
                    TextButton(onClick = onSync, enabled = !syncing) {
                        Text(if (syncing) "同步中…" else "同步")
                    }
                    TextButton(onClick = onSettings) { Text("设置") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNew,
                text = { Text("新建") },
                icon = {},
            )
        },
    ) { padding ->
        if (rows.isEmpty()) {
            EmptyList(
                configured = container.settings.isConfigured,
                onSettings = onSettings,
                modifier = Modifier.padding(padding),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                items(rows, key = { it.id }) { row ->
                    ItemRow(row = row, onClick = { onOpen(row.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
/**
 * One article in the list: title, when it changed, and whether it is waiting to be sent.
 */
private fun ItemRow(row: ContentEntity, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = row.title.ifBlank { "（无标题）" },
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.status,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (row.dirty || row.isNew) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (row.isNew) "未上传" else "未同步",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (row.hasConflict) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "有冲突",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
/**
 * What the list says when it is empty — which is a different message before the server is configured.
 */
private fun EmptyList(configured: Boolean, onSettings: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = if (configured) "这里还没有文章" else "先填上服务端地址",
            style = MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (configured) {
                "电脑上的 Kite 用 --addr 0.0.0.0:1717 启动，登录一次就会同步过来。"
            } else {
                "Kite 跑在你电脑上。填好局域网地址并登录，文章就会同步到这台手机。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onSettings) { Text(if (configured) "去设置里同步" else "填写地址") }
    }
}
