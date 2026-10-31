package io.github.lichen.raybirdpush

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.lichen.raybirdpush.core.Phase
import io.github.lichen.raybirdpush.core.SessionStatus
import io.github.lichen.raybirdpush.core.privateIpv4
import io.github.lichen.raybirdpush.ui.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))
        setContent { PushTheme { Screen() } }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun Screen(vm: MainViewModel = viewModel()) {
        val history by PushStore.history.collectAsStateWithLifecycle()
        var ip by rememberSaveable { mutableStateOf(DeviceHistory(this).read().firstOrNull().orEmpty()) }
        var lanAddress by rememberSaveable { mutableStateOf("") }
        var showTv by rememberSaveable { mutableStateOf(false) }
        var showApk by rememberSaveable { mutableStateOf(false) }
        var showStatus by rememberSaveable { mutableStateOf(false) }
        var showLicenses by rememberSaveable { mutableStateOf(false) }
        var message by remember { mutableStateOf("") }
        var notificationAsked by rememberSaveable { mutableStateOf(false) }
        var licenseText by remember { mutableStateOf("") }
        val lans by vm.interfaces.collectAsStateWithLifecycle()
        val apk by PushStore.selected.collectAsStateWithLifecycle()
        val busy by PushStore.busy.collectAsStateWithLifecycle()
        val preparing by vm.preparing.collectAsStateWithLifecycle()
        val error by vm.error.collectAsStateWithLifecycle()
        val status by PushStore.status.collectAsStateWithLifecycle()
        val diagnostics by PushStore.diagnostics.collectAsStateWithLifecycle()
        val snackbar = remember { SnackbarHostState() }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::select) }
        val notifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { /* Optional: never gates or restarts a transfer. */ }
        LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshInterfaces() }
        LaunchedEffect(error, message) {
            val text = error.ifEmpty { message }
            if (text.isNotEmpty()) {
                snackbar.showSnackbar(text)
                if (vm.error.value == text) vm.error.value = ""
                if (message == text) message = ""
            }
        }
        LaunchedEffect(showLicenses) {
            if (showLicenses && licenseText.isEmpty()) licenseText = withContext(Dispatchers.IO) {
                runCatching {
                    listOf("NOTICE.txt", "Apache-2.0.txt").joinToString("\n\n") { assets.open("licenses/$it").bufferedReader().use { reader -> reader.readText() } }
                }.getOrDefault("许可文件读取失败，请重试")
            }
        }

        fun send() {
            if (busy || preparing) return
            vm.refreshInterfaces() // local interface information only, no network request or device discovery
            val choices = vm.interfaces.value
            val lan = choices.firstOrNull { it.address.hostAddress == lanAddress } ?: choices.singleOrNull()
            if (lan == null) {
                showTv = true
                message = if (choices.isEmpty()) "请先连接 Wi-Fi 或以太网" else "请选择用于传输的网络"
                return
            }
            val selected = apk ?: return
            try {
                val target = privateIpv4(ip.trim())
                require(target != lan.address) { "电视地址不能是手机自身地址" }
                val input = PushInput(ip.trim(), lan, selected)
                if (!PushStore.claim(input)) return
                PushStore.update(SessionStatus(Phase.CONNECTING))
                try {
                    startForegroundService(Intent(this@MainActivity, PushService::class.java).setAction("PUSH"))
                } catch (_: Exception) {
                    PushStore.release(input)
                    PushStore.update(SessionStatus(Phase.FAILED, "传输服务未能启动，请保持 App 前台后重试"))
                    return
                }
                if (Build.VERSION.SDK_INT >= 33 && !notificationAsked &&
                    ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationAsked = true
                    // Permission UI failure must not release an already started session.
                    runCatching { notifications.launch(Manifest.permission.POST_NOTIFICATIONS) }
                }
            } catch (e: IllegalArgumentException) { message = e.message.orEmpty(); showTv = true }
        }

        Box(Modifier.fillMaxSize()) {
            PushScreen(ip, apk, busy, preparing, status,
                onTv = { showTv = true },
                onApk = { if (apk == null) picker.launch(arrayOf("*/*")) else showApk = true },
                onSend = ::send,
                onCancel = {
                    if (preparing) vm.cancelPreparation()
                    else startService(Intent(this@MainActivity, PushService::class.java).setAction("CANCEL"))
                }, onStatus = { showStatus = true })
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(20.dp))
        }

        if (showTv) {
            ModalBottomSheet(onDismissRequest = { showTv = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = Paper) {
                TvChooser(ip, history, lans, lanAddress,
                    onSelect = { chosen ->
                        ip = chosen; showTv = false
                        if (!busy && !preparing) PushStore.update(SessionStatus())
                    }, onForget = { forgotten -> vm.removeHistory(forgotten); if (ip == forgotten) ip = "" },
                    onNetwork = { lanAddress = it.address.hostAddress.orEmpty() },
                    onRefresh = vm::refreshInterfaces,
                    onLicenses = { showLicenses = true },
                    onShare = if (diagnostics.isEmpty()) null else ({
                        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "${getString(R.string.app_name)} 脱敏诊断\n$diagnostics")
                        }, "手动分享脱敏诊断"))
                    }))
            }
        }
        if (showApk && apk != null) {
            val selected = apk!!
            ModalBottomSheet(onDismissRequest = { showApk = false }, containerColor = Paper) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 28.dp).navigationBarsPadding().padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(selected.label, style = MaterialTheme.typography.titleLarge)
                    Text(selected.pkg, style = MaterialTheme.typography.bodyMedium, color = Quiet)
                    Text("${selected.version} (${selected.code}) · ${fileSize(selected.bytes)}", color = Quiet)
                    IconAction(Mark.FOLDER, "更换 APK", { showApk = false; picker.launch(arrayOf("*/*")) }, Modifier.align(Alignment.End))
                }
            }
        }
        if (showStatus) AlertDialog(onDismissRequest = { showStatus = false },
            icon = { Glyph(if (status.phase == Phase.INSTALLED) Mark.CHECK else Mark.INFO) },
            title = { Text(statusCaption(status, false).ifEmpty { "推送" }) },
            text = { Text(status.detail.ifEmpty {
                if (status.phase == Phase.INSTALLED) "已收到匹配本次任务的电视安装完成回执。" else "请在电视核对安装结果。"
            }) }, confirmButton = { TextButton(onClick = { showStatus = false }) { Text("关闭") } })
        if (showLicenses) AlertDialog(onDismissRequest = { showLicenses = false },
            title = { Text("第三方许可") }, text = { Text(licenseText, Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text("关闭") } })
    }
}

@Composable private fun IconAction(mark: Mark, label: String, action: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = action, modifier = modifier.size(48.dp).semantics { contentDescription = label }) { Glyph(mark) }
}

@Composable private fun TvChooser(current: String, history: List<String>, networks: List<LanInterface>, selectedNetwork: String,
    onSelect: (String) -> Unit, onForget: (String) -> Unit, onNetwork: (LanInterface) -> Unit, onRefresh: () -> Unit,
    onLicenses: () -> Unit, onShare: (() -> Unit)?) {
    var draft by rememberSaveable { mutableStateOf(current) }
    var invalid by remember { mutableStateOf(false) }
    fun apply() {
        if (runCatching { privateIpv4(draft.trim()) }.isSuccess) onSelect(draft.trim()) else invalid = true
    }
    Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)
        .navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Glyph(Mark.TV, Modifier.padding(end = 12.dp))
            Text("电视", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconAction(Mark.REFRESH, "刷新网络", onRefresh)
        }
        if (history.isNotEmpty()) {
            history.forEach { address ->
                Surface(shape = RoundedCornerShape(18.dp), color = Color.White) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { onSelect(address) }, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                            Text(address, Modifier.weight(1f)); if (current == address) Glyph(Mark.CHECK, Modifier.size(18.dp))
                        }
                        IconAction(Mark.TRASH, "忘记 $address", { onForget(address) })
                    }
                }
            }
        }
        OutlinedTextField(draft, { draft = it; invalid = false }, modifier = Modifier.fillMaxWidth(),
            label = { Text("电视 IP") }, singleLine = true, isError = invalid, shape = RoundedCornerShape(18.dp),
            supportingText = if (invalid) ({ Text("请输入有效的私有 IPv4 地址") }) else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { apply() }),
            trailingIcon = { IconAction(Mark.CHECK, "选择此电视", ::apply) })
        if (networks.isEmpty()) Text("请连接 Wi-Fi 或以太网", color = Quiet, style = MaterialTheme.typography.bodySmall)
        if (networks.size > 1) {
            Text("传输网络", color = Quiet, style = MaterialTheme.typography.labelMedium)
            networks.forEachIndexed { index, lan ->
                FilterChip(selected = lan.address.hostAddress == selectedNetwork, onClick = { onNetwork(lan) },
                    label = { Text("${lan.name} ${index + 1}") }, leadingIcon = { Glyph(Mark.WIFI, Modifier.size(18.dp)) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (onShare != null) IconAction(Mark.SHARE, "分享脱敏诊断", onShare)
            IconAction(Mark.LICENSE, "第三方许可", onLicenses)
        }
    }
}
