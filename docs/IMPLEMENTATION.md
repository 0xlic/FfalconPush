# 实现与验证记录

## 0.2.1 拼写修正与公开源码

- 按用户更正，名称统一为 `FfalconPush`；桌面资源、工程名、离线 NOTICE、UI 测试与当前使用文档同步修改。保留 applicationId `io.github.lichen.raybirdpush`、签名及电视历史存储；versionCode `4` / versionName `0.2.1`。
- 用户明确选择公开仓库 `https://github.com/0xlic/FfalconPush`，保留雷鸟 Logo，暂不指定项目整体开源许可证。保留商标来源/非官方声明，不将用户选择描述为 Agent 已独立核实品牌授权。
- 本轮获授权初始化 Git、创建源码提交并推送 `main`。仅包含源码、Gradle wrapper、矢量图和文档；排除 local/artifacts/build、APK、原始日志、真实设备地址和凭据。使用 GitHub noreply 提交邮箱，远程 URL 不带认证信息。未上传安装包或创建 Release。
- 实际执行 Debug/Release 单测、lintRelease、assembleRelease：`local/publication-build.log` 为 `BUILD SUCCESSFUL in 16s`；两个变体各 `58 tests, 0 failures, 0 errors`；lint `0 errors, 8 warnings`。本轮没有连接或操作电视。
- 本地 APK：`artifacts/FfalconPush-0.2.1.apk`，7,666,048 字节，SHA-256 `fd1bebb7c5c9322cf19861be168bef3910f7326163539349bd546cf8d9764397`。签名验证通过，证书与 0.2.0 一致；aapt 确认 label `FfalconPush`、versionCode `4`。原始证据在 `artifacts/FfalconPush-0.2.1-{signature,verification}.txt` / `local/publication-apk-badging.txt`，均不推送。
- 新版手机覆盖安装、记录保留及目标电视回归仍待用户验证；公开源码不是扩大兼容性声明，也不等于正式二进制发行。

以下为各旧版本历史记录，旧名称、未初始化 Git 等描述仅对应其当时状态。


## 0.2.0 图形界面与图标收尾

### 用户反馈与本轮范围

用户反馈此前安装包“已经测试完成，可以正常使用”。记录为旧包手机向电视正常推送的用户实测反馈；未附手机/电视型号、固件、版本号及逐项场景，不扩大兼容范围，也不把本版视为已经过手机实测。

按用户明确要求：名称严格使用 `FfclconPush`；主屏去掉应用名、介绍、本机地址和调试信息，仅保留电视、APK、推送/取消图形控件。暖白/浅绿配色、自绘电视插画、圆角留白，加入入场淡入、按压回弹、传输光点、进度环和状态过渡。只有实际安装完成状态才画成功勾号；同意提示、错误/未知、取消不撤销安装等必要信息仍用短文字表达。

电视选择/历史记录/手动输入在底部面板；多个接口时仅显示友好网络名称。APK 详情可核对包名/版本/大小或更换。许可和主动诊断分享收进电视面板，不显示主屏日志。通知授权仅由用户点击推送触发，回调不重发任务。

启动图标参考 TCL 官方 2017-04-10 新闻稿第 2 页的雷鸟几何鸟标进行矢量重绘，非官方矢量母版。采用 VectorDrawable + adaptive/monochrome，不放大低清图片。来源、商标归属及公开分发前限制见 `artwork/README.md` 和第三方说明。

### 修改文件

- 重写 `app/src/main/java/io/github/lichen/raybirdpush/MainActivity.kt`；新增 `ui/PushScreen.kt`、`ui/Glyph.kt`、`ui/Presentation.kt`。
- `MainViewModel.kt`：友好接口名称、新选择清理旧结果展示、非技术错误短提示。`ApkRepository.kt`：准备线程生成固定 160×160 图标；`PushService.kt`：给脱敏导出补充 HTTP 下载请求已收到的事件，不记录 URL。
- `app/build.gradle.kts`：versionCode `3`、versionName `0.2.0`，仅测试增加 Robolectric/Compose UI Test；`settings.gradle.kts` 工程名同步。
- 图标/名称：`AndroidManifest.xml`、`res/values/strings.xml`、`colors.xml`、`drawable/ic_launcher_foreground.xml`、`mipmap-anydpi/ic_launcher.xml`、`artwork/*`；移除不再使用的旧 `drawable/ic_app.xml`。
- 测试新增 `ui/PresentationTest.kt`、`ui/ScreenTest.kt`。文档更新 README、架构、协议证据、路线图、测试说明、本记录与第三方归属；离线 `NOTICE.txt` 更新品牌和鸟标来源。
- applicationId/namespace、连接历史 SharedPreferences 名称/键、签名及 TCP/HTTP 协议行为保持不变。未初始化 Git，未提交、推送或公开发布。

### 实际检查与原始证据

```sh
ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew \
  :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
```

执行过一次 `clean` 完整构建，并在后续增量构建复核。最终 `local/redesign-verification.log`：

```text
> Task :app:testDebugUnitTest
> Task :app:testReleaseUnitTest
> Task :app:lintRelease
> Task :app:assembleRelease
BUILD SUCCESSFUL in 16s
```

- Debug / Release **各 58 用例，failures=0、errors=0**。原有协议/资源/HTTP/假电视 43 项保留，新增 6 项纯展示逻辑测试与 9 项 Android 资源/Compose 交互/图标测试。原始 XML 在 `app/build/test-results/test{Debug,Release}UnitTest/`，汇总 `artifacts/FfclconPush-0.2.0-verification.txt`。
- UI 测试覆盖：主屏无应用名/技术信息、图形控件可访问、选择不联网、真实元信息可查看、等待同意和取消可用、取消/未知不伪装成功、320 dp 小屏可达、Android SharedPreferences 适配器重建读回、API 35 矢量/monochrome 图标、API 26 图标加载与主屏控件。
- API 35 Robolectric 原生绘制实际 Activity view，生成 `artifacts/screenshots/`（home/ready/consent/unknown/compact/icon）。已读取并检查这些渲染 PNG；示例地址/应用均为合成测试数据。**这是本机资源/绘制模拟，不是手机或系统模拟器截图**，也不能验证手机动画流畅度或后台服务。
- Lint：`0 errors, 8 warnings`。保留 7 项固定工具链/依赖更新提示（含测试依赖 Robolectric），1 项 SharedPreferences KTX 风格提示；没有屏蔽检查。
- APK：`artifacts/FfclconPush-0.2.0.apk`，7,666,048 字节。SHA-256 `325833741fbbf79034b3162d1865633ad889e61005873c5b79cb3b368ea89fac`。
- `apksigner verify --verbose --print-certs`：`Verifies`、v2 签名通过；证书 SHA-256 与 0.1.1 一致。`aapt dump badging`：label `FfclconPush`、包名 `io.github.lichen.raybirdpush`、versionCode `3`、versionName `0.2.0`，非调试。原始输出在 `artifacts/FfclconPush-0.2.0-signature.txt` / `local/redesign-apk-badging.txt`；`artifacts/SHA256SUMS` 校验通过。

### 遇到的问题与清理

- 最初 Compose `captureToImage` 依赖 PixelCopy，在 Robolectric 中 4 个用例报 `ComposeTimeoutException`，语义断言可通过。改为原生图形模式下 UI idle 后对实际 `decorView.draw(Canvas(bitmap))` 捕获绘制，不依赖 PixelCopy；保留原断言并检查输出 PNG，未跳过用例。
- 移动 adaptive icon 资源目录后，一次增量 AAPT 报 `resource mipmap/ic_launcher ... not found`。执行干净构建后通过，随后增量构建也通过，无需保留未使用图标或抑制 lint。失败工具输出摘录保留在 `local/redesign-initial-failures.txt`。
- 没有连接电视、没有调用 ADB、没有安装/卸载手机或电视应用、没有系统设置变更。自动网络测试仍只使用回环假电视，监听/流/计时资源按原 finally/use 关闭；APK 临时文件删除，UI 测试不启动真实传输。
- 本轮预览用 Python 工具放在 `local/` 并在结束时移除；官方参考 PDF 和构建日志留在忽略目录供复核，构建/截图留在 build/artifacts。第三方测试依赖仅缓存用于构建，不打包入 APK，不清理用户其他资源。

**下一步**：覆盖安装 0.2.0（无需卸载），检查名称/桌面遮罩、历史电视保留、真实 APK 图标、触摸/动画、允许/拒绝/取消/后台/二次推送。以上本轮真机回归仍待用户执行；旧包的成功反馈不能代替新版验证。


## 0.1.1 名称更正

按用户要求，产品名称统一为 `ffalcon apk push`，雷鸟英文名为 FFALCON，不再使用 Raybird 作为产品名称。

- 修改：`app/src/main/res/values/strings.xml` 新增统一名称，Manifest/主界面/诊断分享引用该资源；`NOTICE.txt`、Gradle 工程名、README、测试说明及路线图同步更正。界面版本改为读取 BuildConfig。
- `app/build.gradle.kts`：versionCode `1 → 2`，versionName `0.1.0-experimental → 0.1.1-experimental`。特意保留原 namespace/applicationId `io.github.lichen.raybirdpush` 及私有记录存储路径，避免改名变成另一个 App、丢失连接记录。
- 新包：`artifacts/ffalcon-apk-push-0.1.1-experimental.apk`，7,632,456 字节。SHA-256：`8ad893a98d8a90af92a5cc6b98267fcaeeaad6b0e142e2a718adeda7e65dee76`。
- 实际执行 Debug/Release 单测、lintRelease、assembleRelease，`local/rename-build.log` 原始结果：`BUILD SUCCESSFUL in 6s`；JUnit XML 两个变体各 `43 tests, 0 failures, 0 errors`；lint `0 errors, 7 warnings`，无新增警告。
- `aapt dump badging` 确认 `application-label:'ffalcon apk push'`、包名不变、versionCode `2`、release 非调试；`apksigner verify` 通过，证书 SHA-256 与 0.1.0 完全相同。证据：`local/rename-apk-badging.txt`、`artifacts/ffalcon-0.1.1-signature.txt`、`artifacts/ffalcon-0.1.1-verification.txt`、`artifacts/SHA256SUMS`。
- 未连接电视、未安装到手机、未改动任何系统设置。只重新构建及执行回环测试；测试资源按原有 finally/use 释放。覆盖更新后连接记录实际保留、手机界面显示及电视推送仍待用户真机验证。下一步直接覆盖安装新包，不需要卸载旧版。

以下保留 0.1.0 的历史实现与原始构建证据；其中旧安装包名称和哈希只对应旧版本。

# 0.1.0 实现与验证记录

## 交付范围

按用户要求先实现可自行安装的手机实验版，再由用户做真实设备测试；新增要求“连接记录持久化，不必每次输入 IP”已纳入实现。

- Android 单模块工程及 Gradle wrapper，Kotlin / Compose。
- 手动电视 IPv4、用户选定 Wi-Fi/以太网 IPv4 接口，不做扫描。
- 成功握手后保存最近 8 个电视地址（私有 SharedPreferences），重新打开默认填入最近地址，支持选择/忘记。恢复配置不自动连接/推送，备份和设备迁移排除本地记录。
- SAF 选择单 APK、私有随机缓存、大小/ZIP 完整性/CRC/包元信息检查，显示内容 SHA-256（不宣称电视也校验）。
- TCP 帧、心跳、安装请求、严格会话回执匹配、状态机、独立期限与取消。
- 单 APK 临时 LAN HTTP 下载服务，精确随机令牌路由、目标源地址限制、GET/HEAD、有界请求与资源关闭。
- 用户操作触发 dataSync 前台服务，正常结束即停止，无开机入口、自动重试或 sticky 保活。
- 界面提示电视操作“右 → 同意 → 确认”；主动分享脱敏诊断；离线第三方许可。

只实现 LAN HTTP。ADB reverse、外链下载、批量、设备扫描和遥控均未实现。

## 修改文件

- 工程：`settings.gradle.kts`、`build.gradle.kts`、`gradle.properties`、`gradlew`、`gradlew.bat`、`gradle/wrapper/*`、`app/build.gradle.kts`。
- 手机代码：`app/src/main/AndroidManifest.xml`、`app/src/main/java/io/github/lichen/raybirdpush/`（UI/ViewModel、APK、历史记录、Store、Service）及 `core/`（协议、资源、HTTP、会话）。
- 资源：`app/src/main/res/`、`app/src/main/assets/licenses/`。
- 测试：`app/src/test/java/io/github/lichen/raybirdpush/`，共 7 个测试类。
- 文档：`README.md`、`THIRD_PARTY_NOTICES.md`、`docs/ARCHITECTURE.md`、`docs/PROTOCOL.md`、`docs/ROADMAP.md`、`docs/TESTING.md`、本文。
- 产物：`artifacts/`，原始日志：`local/`，均已由现有 `.gitignore` 排除。当前目录没有 `.git`，未初始化/提交/推送仓库。

## 实际执行与证据

环境：JDK 17，Gradle 8.13，AGP 8.13.2，Android SDK 36.1，Build Tools 36.1.0。构建/测试日志见 `local/verified-build.log`。

```sh
ANDROID_HOME="$HOME/Library/Android/sdk" ./gradlew \
  :app:testDebugUnitTest :app:testReleaseUnitTest :app:lintRelease :app:assembleRelease
```

最终日志包含：

```text
> Task :app:testDebugUnitTest
> Task :app:testReleaseUnitTest
> Task :app:lintRelease
> Task :app:assembleRelease
BUILD SUCCESSFUL
```

JUnit XML 原始报告：`app/build/test-results/testDebugUnitTest/TEST-*.xml` 和 `testReleaseUnitTest/TEST-*.xml`，逐项汇总在 `artifacts/verification.txt`。

| 测试类 | 每个变体用例数 | 覆盖 |
|---|---:|---|
| DeviceHistoryTest | 3 | 存储适配器重建后恢复、删除、去重、上限、损坏数据、只读无动作 |
| ApkFilesTest | 7 | 内容哈希、空/超限输入、复制取消、ZIP 结构、截断、CRC 损坏、展开限制、校验期限 |
| HttpTest | 10 | 精确文件/HEAD、错误令牌/路径/方法/来源、Range 策略、请求上限、超长头、慢请求期限、请求中/传输中取消、旧路由拒绝及监听关闭 |
| ProtocolTest | 9 | UTF-8 字节长度、逐字节拆帧/连续帧、半帧超时恢复、异常长度、畸形编码、截断、字段注入、真实大小字段、错误回执/地址 |
| SessionTest | 7 | 完成匹配、不倒退、拒绝/失败、心跳/重复进度不延期、资源释放、断链未知、连接取消 |
| MockTvTest | 4 | 本机假电视完整 HTTP 下载/安装回执（连续两次）、拒绝、失败、未验证 installed 消息后断链未知；插入旧回执和其他包名回执 |
| TimeoutTest | 3 | 独立 watchdog 中断持续半帧、半帧接收不妨碍心跳、总期限不被阶段迁移重置 |
| **合计** | **43** | **Debug 43 / Release 43，均 failures=0、errors=0** |

这些是 JVM 测试与**电脑回环网络模拟**，不是 Android 模拟器、手机或电视实测；历史记录测试使用可重建的内存存储适配器，手机 SharedPreferences 的实际落盘/恢复仍待用户测试。

Lint 原始报告：`app/build/reports/lint-results-release.txt` / `.html`：

```text
0 errors, 7 warnings
```

7 项警告为：6 项固定工具链/依赖已有更新提示，以及 1 项建议用 SharedPreferences KTX 的风格提示。保留 `commit()` 是为了检查保存结果；没有因此关闭 lint 或隐藏其他检查。

### APK 检查

```sh
apksigner verify --verbose --print-certs artifacts/raybird-push-0.1.0-experimental.apk
aapt dump badging artifacts/raybird-push-0.1.0-experimental.apk
```

原始签名结果在 `artifacts/signature.txt`：

```text
Verifies
Verified using v2 scheme (APK Signature Scheme v2): true
Number of signers: 1
Signer #1 certificate DN: C=US, O=Android, CN=Android Debug
```

元信息：包名 `io.github.lichen.raybirdpush`、versionCode `1`、versionName `0.1.0-experimental`、minSdk `26`、targetSdk `36`；release 非调试构建，签名仍是本机 debug 证书，不是生产发布证书。

产物 `artifacts/raybird-push-0.1.0-experimental.apk`，7,632,096 字节；SHA-256：

```text
55471f08b469ffda183c1c62e6544bdf011d54e7d4f8b4b9610cefdae2f1b107
```

`artifacts/SHA256SUMS` 可复核此本地构建产物。再次构建可能产生不同文件哈希，以重新执行的签名与哈希检查为准。

## 本轮遇到并处理的问题

- 初次测试任务曾为 `NO-SOURCE`，不计为单测通过；之后实际补齐 43 项测试并执行两个变体。
- 增加历史存储适配器时，`DeviceHistory(getApplication())` 因构造重载产生 Kotlin 类型推断失败；改为明确 `getApplication<Application>()`，随后构建通过。
- 超长 HTTP 头测试初始失败：`HttpTest > oversizedHeadersRejected FAILED / java.net.SocketException`。服务在读满上限后拒绝并关闭连接，未读完输入可能使 TCP 返回 RST 而非可读取的 400；测试改为明确接受这两种安全拒绝，同时验证未计为合法请求、未传文件且下个合法 HEAD 仍成功。最终用例通过，未扩大服务授权或头大小上限。原失败记录在 `local/verification.log`。
- 收尾源码检查发现：取消命令会递增 Android Service 的 startId，旧 `stopSelf(startId)` 可能不停止服务。改为会话完成后 `stopSelf()`；唯一任务 claim 保持到服务销毁，不能并发开始第二任务。此修正已编译，**Android 服务实际退出仍待手机验证**。
- 心跳移到独立计时执行器，与 watchdog 分离，避免持续半帧接收阻止心跳；新增对应自动测试。

## 清理及未验证项

- 本轮没有接触用户电视、没有使用 ADB、没有安装/卸载应用或改变任何电视设置。
- 假电视与 HTTP 测试仅绑定 127.0.0.1；socket、监听、流与执行器在 finally/use 中关闭，含端口关闭和取消用例。测试临时文件执行后删除，后续运行固定在项目 `app/build/tmp/` 下。
- 已删除本轮创建的 `local/bootstrap/`；保留项目构建产物、诊断日志与必要 Gradle/SDK 缓存，不清理用户其他资源。
- **手机真机：待验证**（安装/启动、SAF/PackageManager、通知权限、前后台/锁屏、取消后服务退出、SharedPreferences 持久化）。
- **目标电视：待验证**（明文握手、LAN URL 可达、sizeMB 单位/取整假设、中文标签、回执格式、完整安装、拒绝/不操作/断链/二次推送）。
- 安装成功必须依赖匹配任务的 `PROGRESS stage=5`。`INSTALL installed` 未经目标电视验证，不作为等价成功信号。
- 无最终回执不宣称安装成功；取消不撤销电视上可能已开始的安装。

下一步：用户按照 [TESTING.md](TESTING.md) 自行安装到手机，只对指定授权电视测试，优先反馈“是否同意、是否发起 HTTP 请求、最终回执与电视实际结果”。P0 未通过前不扩大技术路线。
