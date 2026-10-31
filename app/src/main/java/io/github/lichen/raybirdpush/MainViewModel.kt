package io.github.lichen.raybirdpush

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.lichen.raybirdpush.core.Resources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address

class MainViewModel(app: Application) : AndroidViewModel(app) {
    val preparing = MutableStateFlow(false)
    val error = MutableStateFlow("")
    val interfaces = MutableStateFlow<List<LanInterface>>(emptyList())
    private var prepareResources: Resources? = null
    private var prepareJob: Job? = null
    init {
        PushStore.history.value = DeviceHistory(app).read()
        // Only our own cache directory; no user files or TV files.
        if (!PushStore.busy.value && PushStore.selected.value == null) {
            File(app.cacheDir, "selected-apk").listFiles()?.forEach { it.delete() }
        }
        refreshInterfaces()
    }
    fun refreshInterfaces() {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        interfaces.value = cm.allNetworks.flatMap { network ->
            val caps = cm.getNetworkCapabilities(network)
            if (caps == null || caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) ||
                !(caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))) emptyList()
            else cm.getLinkProperties(network)?.let { link ->
                link.linkAddresses.filter { it.address is Inet4Address && it.address.isSiteLocalAddress }
                    .map { LanInterface(network, it.address, if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) "Wi-Fi" else "以太网") }
            }.orEmpty()
        }
    }
    fun select(uri: Uri) {
        if (preparing.value || PushStore.busy.value) return
        preparing.value = true; error.value = ""
        PushStore.update(io.github.lichen.raybirdpush.core.SessionStatus())
        PushStore.http.value = ""
        val resources = Resources()
        prepareResources = resources
        prepareJob = viewModelScope.launch {
            var prepared: SelectedApk? = null
            try {
                withContext(Dispatchers.IO) {
                    prepared = ApkRepository.prepare(getApplication(), uri, resources)
                }
                PushStore.selected.value?.file?.delete()
                PushStore.selected.value = prepared
                prepared = null
            } catch (_: kotlinx.coroutines.CancellationException) {
                error.value = "已取消 APK 读取"
            } catch (_: Exception) {
                // Do not expose provider URIs/paths or APK metadata through exception messages.
                error.value = "无法读取此 APK。请选择完整单包（≤512 MB），不支持 APKS、XAPK 或拆分包。"
            } finally {
                prepared?.file?.delete()
                resources.close(); prepareResources = null; preparing.value = false
            }
        }
    }
    fun cancelPreparation() { prepareResources?.close(); prepareJob?.cancel() }
    fun removeHistory(ip: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val history = DeviceHistory(getApplication<Application>())
            runCatching { history.remove(ip); PushStore.history.value = history.read() }
                .onFailure { error.value = "无法删除本地连接记录，请稍后重试" }
        }
    }
    override fun onCleared() { cancelPreparation(); super.onCleared() }
}
