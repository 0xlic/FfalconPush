# 第三方来源与许可证

0.2.1 使用独立编写的协议/HTTP 实现，未复制或打包 Aerial 源码。AndroidX、Kotlin 和 coroutines 依赖随 APK 打包；许可文本与归属在 `app/src/main/assets/licenses/`，App 内“第三方许可”可离线查看。

## Aerial TV

仓库：https://github.com/pengchujin/aerial-tv

已参考的内容：

- tools/tcast-push.py：T-Cast 安装推送的 Python 参考实现。
- docs/tcast-protocol.md：握手、补充指令、固件兼容性和下载地址限制。
- docs/devices/tcl-mt5879-cn.md：多屏互动安装与电视确认操作。

本次阅读的上游许可证为 MIT，版权声明：Copyright (c) 2026 pengchujin。

如果后续复制、翻译或改编其代码的实质部分，必须核实对应提交的许可证，在分发中保留原版权与完整 MIT 许可文本，并记录来源文件和提交。仅放一个仓库链接不能代替代码分发时的许可证义务。

## 依赖（0.2.1）

| 名称 / 版本 | 来源 | 许可 / 使用 |
|---|---|---|
| AndroidX Compose BOM 2025.10.01（Material3 1.4.0、UI 1.9.4） | https://developer.android.com/jetpack/androidx/releases/compose | Apache-2.0；未修改，运行库随包分发 |
| AndroidX Activity 1.11.0、Lifecycle 2.9.4 及传递依赖（含 graphics-path 1.0.1） | https://developer.android.com/jetpack/androidx | Apache-2.0；未修改，随包分发 |
| Kotlin stdlib 2.2.21 | https://github.com/JetBrains/kotlin | Apache-2.0；未修改，随包分发 |
| kotlinx.coroutines 1.10.2 | https://github.com/Kotlin/kotlinx.coroutines | Apache-2.0；未修改，随包分发 |
| JetBrains annotations 23.0.0（传递依赖） | https://github.com/JetBrains/java-annotations | Apache-2.0；未修改 |
| Robolectric 4.16.1 | https://robolectric.org | MIT；仅本地 Android 资源/绘制测试，不随 APK 分发 |
| Compose UI Test 1.9.4 / AndroidX Test（测试传递依赖） | https://developer.android.com/jetpack/androidx | Apache-2.0；仅测试，不随 APK 分发 |
| JUnit 4.13.2 / Hamcrest 1.3 | https://junit.org/junit4/ / https://hamcrest.org/ | EPL-1.0 / BSD-3-Clause；仅 JVM 测试，不随 APK 分发 |
| AGP 8.13.2、Gradle 8.13、Kotlin/Compose 编译插件 2.2.21 | Google Maven / Gradle / JetBrains | Apache-2.0；构建工具，不随 APK 分发 |

Apache 2.0 完整文本来自 https://www.apache.org/licenses/LICENSE-2.0.txt；库提供的 META-INF 许可文件保留。依赖许可通过本轮下载的 Maven POM 核对；实际解析版本可用 `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` 复核。

HTTP：未选择外部嵌入式库，`core/ApkHttpServer` 只实现本次单 APK GET/HEAD，连接/读取/写出均可由关闭 socket 取消，JVM 测试覆盖授权、期限和释放；Android 系统行为待实测。ADB：未引入客户端，不具备 reverse 能力。选择更多依赖前仍须核实许可、维护状态、取消及 Android 兼容性。

## 0.2.0 雷鸟鸟标

按用户要求，启动图标参考 TCL 官方新闻稿第 2 页 FFALCON 标志进行几何矢量重绘：
https://doc.irasia.com/listco/hk/tclelectronics/press/p170410.pdf

这是参考重绘，不是官方矢量母版。SVG 源及用途记录见 `artwork/README.md`；矢量图随 APK 打包，归属和非官方声明已加入 App 离线 NOTICE。标志商标属于相应权利人，未发现通用开源授权，不将其纳入 Apache/MIT，也不声称 TCL/雷鸟背书。本轮公开源码前，用户在知悉公开分发需具备相应权利的提示后选择保留该 Logo；Agent 未独立验证授权文件，也不向仓库使用者授予商标权。后续使用或分发仍须自行核实适用条件。本轮仅推送源码，不发布公开 APK/Release。

用户明确选择暂不指定项目整体开源许可证；源码公开不等于授予通用开源许可。第三方许可仍仅适用于各自组件，不得据此推定项目自有代码或品牌素材亦受相同许可。
