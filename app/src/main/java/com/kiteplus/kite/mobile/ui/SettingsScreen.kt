package com.kiteplus.kite.mobile.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.kiteplus.kite.mobile.AppContainer
import com.kiteplus.kite.mobile.data.Discovery
import com.kiteplus.kite.mobile.data.remote.requireBody
import com.kiteplus.kite.mobile.data.remote.ApiFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
/**
 * Where the server lives, who is signed in, and the two background actions: find it on the network, or sync now.
 */
fun SettingsScreen(container: AppContainer, onBack: () -> Unit, onSync: () -> Unit) {
    val settings = container.settings
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(settings.serverUrl) }
    var user by remember { mutableStateOf(settings.userName) }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    /**
     * Saves the address and signs in, in that order: the gateway is built from the
     * address, so the sign-in cannot happen before it is stored.
     */
    suspend fun login() {
        busy = true
        message = null
        // The address is what builds the client, so it is committed before the
        // call rather than after a successful one.
        settings.serverUrl = url
        settings.userName = user

        val api = settings.api()
        if (api == null) {
            busy = false
            message = "地址不能为空"
            return
        }
        try {
            val info = api.login(user.trim(), password, true).requireBody()
            message = when {
                info.authenticated -> "登录成功${info.user?.let { "：$it" }.orEmpty()}"
                info.required -> "服务端拒绝登录"
                else -> "站点已登录"
            }
            password = ""
        } catch (e: ApiFailure) {
            message = "登录失败：${e.message}"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = "连接失败：${e.message ?: e.toString()}"
        } finally {
            busy = false
        }
    }

    /**
     * Proves the address before the password is involved: this endpoint answers
     * without a session, so a wrong IP and a wrong password stay distinguishable.
     */
    suspend fun probe() {
        busy = true
        message = null
        settings.serverUrl = url
        val api = settings.api()
        if (api == null) {
            busy = false
            message = "地址不能为空"
            return
        }
        val started = System.currentTimeMillis()
        try {
            val info = api.setup().requireBody()
            val ms = System.currentTimeMillis() - started
            message = "可连接（${ms}ms）" + if (info.required) "，这个站点需要登录" else ""
        } catch (e: ApiFailure) {
            message = "服务器：${e.message}"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = "连接失败：${e.message ?: e.toString()}"
        } finally {
            busy = false
        }
    }

    /**
     * Sweeps this network for a copy of Kite and drops the address it finds
     * into the field: a DHCP lease change should cost one tap, not an ipconfig.
     */
    suspend fun find() {
        busy = true
        message = "正在本地网络中寻找 Kite…"
        try {
            // Sweep the port already typed first: a server on 1718 is
            // still the server, and the default alone would miss it.
            val port = url.substringAfterLast(':', "").trim().toIntOrNull()
            val result = Discovery.findServer(port)
            val found = result.found
            if (found != null) {
                url = found
                message = "找到：$found（点「保存并登录」用起来）"
            } else if (result.scanned.isEmpty()) {
                message = "查找失败：手机没有拿到局域网地址（本机 IPv4：${
                    result.localAddresses.ifEmpty { listOf("无") }.joinToString()
                }）。先连上 Wi-Fi 再试。"
            } else {
                message = "查找失败：扫了 ${result.scanned.joinToString { "$it.x" }}，没有主机应答。" +
                    "手机本机 IPv4：${result.localAddresses.ifEmpty { listOf("无") }.joinToString()}。" +
                    "如果电脑的地址前三段和这些不同，说明两台不在同一个网段。"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            message = "查找失败：${e.message ?: e.toString()}"
        } finally {
            busy = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Text(
                "在电脑上执行命令：kite run --addr 0.0.0.0:1717，然后把电脑在局域网里的地址填在下面。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                label = { Text("服务端地址") },
                placeholder = { Text("http://192.168.1.20:1717") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = user,
                onValueChange = { user = it },
                label = { Text("用户名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("密码") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { scope.launch { login() } },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (busy) "连接中…" else "保存并登录")
            }
            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { scope.launch { probe() } },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("测试连接（不用密码）") }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { scope.launch { find() } },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("自动查找电脑") }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onSync,
                enabled = !busy && settings.isConfigured,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("立即同步") }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = {
                    settings.clearSession()
                    message = "已退出，本机文章仍然保留"
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("退出登录") }

            message?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
