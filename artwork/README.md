# FfalconPush 图标

- 来源依据：TCL Multimedia 官方新闻稿，2017-04-10，第 2 页 FFALCON 鸟标。
- 原文：https://doc.irasia.com/listco/hk/tclelectronics/press/p170410.pdf
- `ffalcon-mark.svg`：参考鸟标几何轮廓手工重绘的三条折线，不是官方矢量母版，未使用模糊截图放大。
- App 使用 `app/src/main/res/drawable/ic_launcher_foreground.xml` 的同形状 VectorDrawable；`mipmap-anydpi/ic_launcher.xml` 提供 adaptive 前景、背景和 monochrome，支持圆形/圆角/主题图标。前景内容位于自适应安全区，减少遮罩裁切。
- 调色仅为本项目界面适配：深绿鸟标、暖白背景。主屏电视与包裹插画/小图形为独立绘制，不冒充官方素材。
- `artifacts/FfalconPush-icon-1024.png` 是 SVG 的 1024×1024 预览，APK 中实际图标是可按密度绘制的矢量，不依赖这个 PNG。
- `artifacts/screenshots/icon.png` 是测试环境从 Android AdaptiveIconDrawable 实际绘制的预览；桌面最终遮罩取决于手机 launcher。

FFALCON 名称及鸟标商标属于相应权利人。未发现官方为该标志授予通用开源许可，因此不将其标为 Apache/MIT，也不暗示本项目获得 TCL/雷鸟授权。本轮公开源码前，用户在明确提示所需使用权的选项中选择保留该 Logo；未由 Agent 独立核实授权文件。这不构成对其他使用者的商标/素材授权，后续使用与分发仍须自行核实适用条件。
