# 架构草案

状态：当前 0.2.1（FfalconPush，修正 0.2.0 名称拼写）。用户报告此前手机推送已正常使用，但未附设备/版本和逐项记录；本次 UI/图标调整及生命周期异常场景待回归。ADB reverse 未实现。以下为设计边界；各版本落地差异见文末。

## 组件边界

```text
Compose UI / ViewModel
  └─ PushSessionCoordinator：唯一会话、状态机、取消、超时
      ├─ DeviceConnection：目标地址与 TCP 会话
      ├─ TcastCodec / TcastClient：帧编解码、心跳、安装指令、回执
      ├─ ApkRepository：SAF 输入、私有缓存、元信息、内容校验
      └─ ApkTransport：提供电视可实际下载的 URL
          ├─ LanHttpTransport：手机局域网直连，先验证固件兼容性
          └─ AdbReverseTransport：若目标固件需要，评估并实现手机 ADB 通道
```

先使用单模块 app，按功能分包：device、apk、protocol、transport、session、service、ui。协议帧、回执解析和状态迁移尽量不依赖 Android，便于 JVM 测试。不要在可达性和协议尚未验证时拆成大量模块。

## 状态机

```text
Idle → PreparingApk → Connecting → PreparingDownload → Requesting
     → AwaitingTvConsent → Downloading → Installing → Installed
```

任何非终态都可进入 Cancelled、Failed、TimedOut；失去连接但无法确认电视是否已完成时进入 OutcomeUnknown。

- UI 重建只订阅状态，不重发请求。
- 会话由 service/coordinator 管理，而不是由某个 Composable 或 Activity 持有 socket。
- 同一时间只有一个任务；任务完成前不允许第二个任务复用回执或端口状态。
- 记录本机 sessionId、目标、包名、URL 路径标识与最后回执，防止旧消息误报新任务成功。
- 获取 HTTP URL 后先验证本地服务已监听，再发安装请求；连接 6553 并不能验证电视会访问该 URL。

## APK 输入

使用 ACTION_OPEN_DOCUMENT 选择 APK，复制到应用私有缓存后解析 PackageInfo、版本与文件大小。文件名不作为可信包信息；拒绝不能解析、超过限制或不是单 APK 的输入。

不遍历手机存储、不导出已安装 App、不扫描其他 App 的私有目录。持久化 URI 授权只在确实需要时申请，结束后释放不再需要的授权。

## APK 下载路径：先做可行性验证

### A. 局域网 HTTP

电视下载地址必须是手机选定局域网接口的地址，例如：

```text
http://<PHONE_LAN_IP>:<PORT>/<RANDOM_SESSION_TOKEN>/package.apk
```

监听启动成功不表示电视可达。需要记录电视是否真的发起 GET/HEAD、是否受固件地址/端口校验限制、是否存在 AP 客户端隔离或路由问题。上游有“非标准局域网端口无请求”的记录，不能直接宣布此模式通用。

服务只处理本次 APK 的受控 GET/HEAD；Content-Length、Content-Type 正确，Range 行为明确。若支持 Range，覆盖边界、越界和断点重试测试；未实现时明确响应，不伪装支持。禁止任意路径、目录列表、上传和删除。

令牌至少 128 位，不写入完整日志。默认只允许目标电视地址请求。每个请求和整轮会话设时限，避免连接泄漏。

### B. 手机 ADB reverse 兼容模式

仅在下载路径验证表明确有必要时实现。电视授权的是手机自己的 ADB 身份，而不是电脑的密钥。确认依赖确实支持 reverse 的数据流建立与桥接；仅支持 shell、或只发送一条 reverse 命令，不等于 APK 下载通道可用。

MVP 不包含 Android 无线调试 TLS 配对、电视远程开 ADB、远程调试权限管理。若选择传统 TCP ADB，UI 明确显示支持范围及电视上“允许调试”的确认步骤。

### C. 外部下载 URL

后续可选。用户主动提供并确认 URL，按支持的 scheme、跳转、安全策略和包一致性校验。不得偷偷上传本地 APK 或引入第三方托管；不得为兼容性自动关闭 TLS 验证。

## 生命周期与 Android 系统限制

- 用户可见且主动发起推送时启动传输服务，整个会话只使用一个受控服务。
- API 26+ 使用适当的前台服务启动方式并及时登记通知；按目标 API 核实服务类型、相关权限、时间限制与通知权限行为。
- App 后台期间不能假设普通 startService 一定成功。已有电视助手实践中，前台按钮测试成功、后台入口却抛 IllegalStateException，必须分别验证。
- Android 手机的限制不能完全套用 Android 9 电视助手的实现；拒绝通知权限也不应被错误等同于所有系统版本都不能运行前台服务。
- 不在开机、App 图标入口、配置恢复或诊断读取时自动发起传输。用户取消后，先停止接收新请求，再关闭 socket/HTTP/ADB 通道，最后清理本会话缓存并退出服务。
- 无 START_STICKY 重启、无无限重试。异常保留可读原因，不吞掉后显示成功。

## 安全与诊断

不把 TCP 明文握手称为安全配对。局域网其他设备可能看到协议和 HTTP 内容，仅面向可信网络中的用户自有电视。APK 内容、ADB 密钥、完整下载令牌不进入日志。

可导出脱敏诊断：App/Android 版本、设备协议能力、状态迁移、相对时间、HTTP 是否收到请求、协议阶段、错误类别。默认去掉 IP、MAC、URI、下载令牌、文件路径和用户 App 清单。未经授权不自动上传。

## 0.1.0 实际落地

- `MainActivity` / `MainViewModel`：Compose UI、SAF、APK 准备取消。读取/校验在 IO 线程，最长 120 秒；选取完整 APK ≤512 MiB，所有 ZIP entry 校验大小/CRC，总展开上限 2 GiB，然后由 PackageManager 解析元信息。不将此校验称为签名认证或电视兼容性验证。
- `core/Protocol`：64 KiB 帧上限、严格 UTF-8、跨 socket 超时保留半帧；`SessionMachine`：只接受当前包名和当前 URL MD5 的回执，`PROGRESS 5` 才成功。URL MD5 只是关联字段。
- `core/PushSession`：TCP connect 5 秒、握手响应等待 10 秒，独立 watchdog 将请求前总阶段限定 20 秒。每 5 秒心跳；同意 90 秒、下载/安装各 180 秒、请求后总上限 600 秒。单独 watchdog 关闭阻塞读写，不能靠持续心跳或重复进度延长期限。
- `core/ApkHttpServer`：自实现小型 HTTP/1.x GET/HEAD 子集，无嵌入式 HTTP 依赖。绑定用户选定的 Wi-Fi/以太网 IPv4；TCP 控制 socket 同时绑定该 Android Network。不选择 VPN/蜂窝，不扫描设备。仅接受所选电视源地址和精确随机路由（192 位令牌）。8 KiB 请求头、3 秒读空闲时限、120 秒单请求期限，最多 16 次有效下载请求/64 次连接尝试。忽略 Range 并返回完整 200，明确 `Accept-Ranges: none`；不支持断点续传。仅报告本机写出字节，不伪称电视已完整保存。
- `PushService` / `PushStore`：用户点击才 claim 唯一任务并启动 dataSync 前台服务；不 sticky，不自动重试/恢复。取消、超时、正常结束均关闭 HTTP/TCP/计时器；服务销毁后不接续旧安装。终态删除本会话 APK；进程被系统杀死后，下次打开只清理自己的残留缓存，不重发任务。
- `DeviceHistory`：SharedPreferences 私有存储，成功协议握手后写入最近 8 个去重地址；重开默认填入最近一项，可单项忘记。读取不创建连接，既不进入脱敏诊断，也不进行系统备份/设备迁移。自动恢复 APK 或推送任务不在本版范围内。
- 本版没有独立“测试连接”按钮，使用“连接并推送”一次明确动作；在请求安装前必须完成握手。

### 工具链与系统限制核对

固定 JDK 17、Gradle 8.13（wrapper 含官方分发 SHA-256）、AGP 8.13.2、Kotlin/Compose compiler 2.2.21、Compose BOM 2025.10.01、Activity 1.11.0、Lifecycle 2.9.4、coroutines 1.10.2。选择可与本机 SDK 36.1 配套且已构建验证的稳定版本组合，不宣称全部为最新版本；lint 的较新依赖提示保留，不静默升级。

minSdk 26 / compileSdk 36.1 / targetSdk 36，Build Tools 36.1.0。声明 INTERNET、ACCESS_NETWORK_STATE、FOREGROUND_SERVICE、FOREGROUND_SERVICE_DATA_SYNC、POST_NOTIFICATIONS，无定位/扫描/所有文件权限。

- API 26+ 从前台按钮调用 startForegroundService，服务立即建立通知；API 29+ 明确 dataSync 类型，API 34+ 声明对应类型权限。
- API 33+ 通知授权由用户选择，不作为推送前提；拒绝后仍可在 App 内查看与取消，具体手机行为待实测。
- Android 15 对 target 35+ 的 dataSync 前台服务有累计时限，覆写 onTimeout 关闭会话并停止服务。本版自身期限远短于 6 小时，不使用启动广播/后台入口。
- Android 16 的局域网权限保护为 opt-in，本版 target 36 不启用兼容性 opt-in；若用户系统已启用该限制，连接可能被系统拒绝。未来提升 target/系统权限模型时需另行适配，不能宣称所有后续 Android 版本已支持。
- release 构建当前不压缩混淆，使用本机 debug 证书，仅用于实验。生产签名和正式发行不在本轮范围。

## 0.2.0 界面与图标收尾

- `ui/PushScreen.kt`：无标题/介绍/本机地址/日志的主屏，只有电视卡片、APK 卡片、图形推送/取消控件；暖白底、低饱和绿、留白与自绘矢量插画。卡片按压 spring 回弹，入场淡入、会话光点向电视流动、进度环、图标和状态交叉过渡。动画仅反映会话，不发送网络动作，不独立推导安装成功。
- 主屏电视地址指所选目标，不是本机 IP；读取记录只选择，不伪装已经连接。所有图形按钮提供 TalkBack 描述，触摸目标至少 48 dp；小屏可滚动，状态用 liveRegion 提示。
- 点电视进入底部面板：记录选择/删除、手动输入；只有多局域网接口时要求显式选择友好网络名称，不显示手机 IP、不扫描。进程内保留接口选择，唯一 Wi-Fi/以太网 IPv4 可直接使用；刷新仅读取本机 ConnectivityManager。
- 点已选 APK 卡片查看真实包名/版本/大小，并可换文件；卡片显示应用图标（准备线程生成 160×160 内存位图，失败退回包图形）。原 APK 格式/大小/CRC/字段校验均保留。
- 主屏不再显示日志、哈希和 HTTP 技术信息。用户主动诊断分享及第三方许可收在电视选择面板内，不自动分享。状态详情只显示可读原因，不展示完整日志。
- 通知权限仅在用户点击推送时可请求；权限回调不创建第二任务，也不作为传输硬前提。独立处理权限 UI 异常，不能因此释放已启动任务的唯一 claim。
- 选择 APK/电视是本地状态变化，重置过往结果展示；不发送请求。唯一任务、电视确认提示“右 → 同意 → 确认”、未知结果、取消不回滚等协议约束不变。
- `artwork/ffalcon-mark.svg` 与 `ic_launcher_foreground.xml` 为参考 TCL 官方新闻稿鸟标所做的几何矢量重绘，非官方矢量母版。launcher 使用 adaptive foreground/background/monochrome，避免低分辨率图片拉伸；保留商标归属，见 `artwork/README.md`。
- Robolectric 4.16.1 + Compose UI test 仅用于测试，不随 APK 分发。覆盖 API 35 原生绘制/语义、320 dp 小屏、API 26 图标加载/主屏、真实 SharedPreferences 适配器的仓库重建。渲染截图不是手机或系统模拟器实测。

核对来源：
- https://developer.android.com/build/releases/agp-8-13-0-release-notes
- https://developer.android.com/develop/background-work/services/fgs/service-types#data-sync
- https://developer.android.com/develop/background-work/services/fgs/timeout
- https://developer.android.com/privacy-and-security/local-network-permission
