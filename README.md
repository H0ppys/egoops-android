# EgoOps Collect（Android 采集器）

## 1. 一句话定位

门店采集点拍摄上报 App：填采集点（门店/地址/场景/面积）→ 按「门头照 + 全景 + 对角 + 任务照/视频」
逐项拍摄 → 本地质检与水印 → 断点续传上传到 EgoOps 服务端。拆分为「装配层 Activity + 功能控制器」模块化工程。

## 2. 原始来源

| 项 | 值 |
| --- | --- |
| 原始工程 | `%WORKSPACE%\android\`（Windows 工作区内的 Android 子工程） |
| 原始文件 | `src/com/egoops/collect/MainActivity.java`（2844 行，125 KB）、`Api/Store/Model/Uploader/UploadService/WatermarkVideo/Geo/MapSnap/ApkProvider/BuildConfigVersion.java` |
| 构建方式 | `build.ps1`（aapt2 + javac + d8 + apksigner，无 Gradle） |
| 版本 | versionCode 74 / versionName 74.0（见 `BuildConfigVersion.java` 与 `AndroidManifest.xml`） |
| 原文件镜像 | `legacy/`（逐字节一致，只读参照，不参与编译） |

## 3. 目录结构

```
egoops-android/
├── README.md  LICENSE  .gitignore
├── AndroidManifest.xml
├── build.ps1                     # 原样保留（与原始工程逐字节一致）
├── res/                          # mipmap / raw(pca.json, scenes.json, egoops_cert.crt) / values / xml
├── src/main/java/com/egoops/collect/
│   ├── MainActivity.java         # 装配层（307 行，原 2844 行）
│   ├── Api.java  Store.java  Model.java  Uploader.java  UploadService.java
│   ├── ApkProvider.java  Geo.java  MapSnap.java  WatermarkVideo.java  BuildConfigVersion.java
│   ├── state/                    # Shots  SiteFlow
│   ├── ui/                       # StartPage  ShootPage  TaskDonePage  DonePage  ShotRenderer  Dialogs
│   ├── capture/                  # CaptureFlow  QualityCheck  WatermarkStamper
│   ├── settings/                 # SceneCatalog  RegionCatalog  AccountManager  SettingsDialog
│   ├── queue/                    # UploadCoordinator  Thumbnails  AutoUpdate
│   ├── perm/                     # Permissions
│   ├── location/                 # LocationTracker
│   └── diag/                     # CrashReporter
├── legacy/                       # 原始 MainActivity.java 等 11 个文件
├── docs/{ARCHITECTURE,SPLIT,PACKAGING}.md
└── tests/{check_repo.py,check_verbatim.py}
```

## 4. 安装与运行

前置：JDK 17、Android SDK Build-Tools 34、Android Platform 34（`android.jar`），以及一台 Android 5.0+ 手机。

```powershell
# 1) 静态校验（不需要 Android SDK）
python tests/check_repo.py
python tests/check_verbatim.py

# 2) 构建 APK（build.ps1 要求本仓库位于 <构建根>/android，<构建根>/tools 下有工具链，详见 docs/PACKAGING.md）
powershell -File .\build.ps1

# 3) 安装
adb install -r .\egoops-collect-v74.apk
```

首次启动会请求相机、录音、读媒体、通知权限；进入首页先填**邀请码**（决定可见的场景分类），
再填采集员姓名、门店名称、省市区 + 街道、场景分类、场地面积，然后「开始拍摄」。

## 5. 模块说明

| 包 / 类 | 职责 | 关键入口 |
| --- | --- | --- |
| `MainActivity` | 装配层：生命周期、全屏容器、布局工具（`dp`/`roundRect`/`toast`/`pageShell`）、共享状态 | `onCreate`、`buildUi`、`showPage` |
| `state.Shots` | 拍摄项矩阵与进度判定 | `shotsOf`、`hasShot`、`firstUndone`、`doneCount` |
| `state.SiteFlow` | 采集主流程 | `beginShooting`、`stepShot`、`resumeLastSite`、`finishAll`、`resetForNext` |
| `ui.StartPage` | 第 1 屏：邀请码/姓名/门店/地址/场景/面积 | `buildStartPage` |
| `ui.ShootPage` | 第 2 屏骨架：手势翻项 + 上传状态条 | `buildShootPage` |
| `ui.ShotRenderer` | 当前拍摄项卡片（预览、进度点、重拍入口） | `renderShot` |
| `ui.TaskDonePage` / `ui.DonePage` | 任务完成屏 / 结束屏 | `buildTaskDonePage`、`buildDonePage` |
| `ui.Dialogs` | 指引、区域+动作、重新开始、崩溃回看 | `askTask`、`showAreaForTask1`、`confirmRestart` |
| `capture.CaptureFlow` | 拍摄动作流：系统相机/相册/水印相机、批量导入、素材落盘 | `captureShot`、`ingest`、`autoGrabLatest`、`acceptCapture` |
| `capture.QualityCheck` | 质检：模糊（拉普拉斯方差）/过暗/过亮/视频过短 | `checkQuality` |
| `capture.WatermarkStamper` | 打卡水印烧录 + SHA1 防伪码 | `stampWatermark`、`antiCodeOf` |
| `settings.SceneCatalog` | 场景分类两级联动，远端优先、`res/raw/scenes.json` 兜底 | `loadScenes`、`refreshSceneSpinner` |
| `settings.RegionCatalog` | 省市区三级联动（`res/raw/pca.json`） | `loadRegions`、`updateCities` |
| `settings.AccountManager` | 登录、邀请码、厂商标签 | `autoLogin`、`loginNow`、`askInvite` |
| `settings.SettingsDialog` | 服务器地址设置 | `showSettings` |
| `queue.UploadCoordinator` | 上传触发、进度回调、续传、远端删除 | `startUpload`、`resumePendingUpload`、`archiveUploaded` |
| `queue.Thumbnails` | 照片/视频缩略图 | `saveThumb`、`saveVideoThumb` |
| `queue.AutoUpdate` | 版本检查、APK 下载、交系统安装器 | `checkUpdate`、`downloadAndInstall` |
| `perm.Permissions` | 运行时权限申请 | `askAllPermissions` |
| `location.LocationTracker` | 定位、WGS84→GCJ02、逆地理、地图截图 | `startGps`、`useLocation` |
| `diag.CrashReporter` | 全局崩溃捕获与上报 | `installCrashReporter` |
| `Api`/`Uploader`/`Store`/`Model` | 网络、分片上传、本地队列、数据模型（未拆分，原样） | — |

## 6. 配置项

| 配置 | 位置 | 默认值 / 占位符 | 说明 |
| --- | --- | --- | --- |
| 服务器地址 | `Store.server()`（SharedPreferences `server`），运行期可在「设置服务器」里改 | `https://<FILL_ME>/relay` | App 内可改；源码里的默认值仅作兜底 |
| 邀请码 | `Store.invite()`（`invite`），首页输入 | `<FILL_ME>` | 决定可见场景分类，需与管理员约定 |
| 采集员姓名 | `Store.name()`（`name`） | `<FILL_ME>` | 变更姓名会清 token 重新登录 |
| 登录 token | `Store.token()`（`token`） | 运行期写入 | **不要**写进仓库 |
| 场景分类表 | `res/raw/scenes.json` | 离线全量 | 远端 `/api/scenes?vendor=<邀请码>` 优先 |
| 省市区表 | `res/raw/pca.json` | 31 省 / 300+ 市 / 2900+ 区县 | 本地解析 |
| 高德 Web key | `MapSnap.DEFAULT_KEY`、`Geo.regeo/staticMap` 的 `key` 参数 | `<FILL_ME>` | 逆地理与地图截图需要；请换成自己申请的 key |
| TLS 证书 | `res/raw/egoops_cert.crt` + `res/xml/network_security_config.xml` | 随包 | 自签/固定证书方案，换域名要同步替换 |
| 版本号 | `AndroidManifest.xml` + 构建时生成的 `BuildConfigVersion.java` | 74 / 74.0 | 由 `build.ps1` 同步 |

> 仓库内不含 `debug.keystore`、`*.apk`、`*.apk.idsig`、token、抓取到的业务数据。

## 7. 打包

```powershell
powershell -File .\build.ps1                     # 常规版   -> egoops-collect-v74.apk
powershell -File .\build.ps1 -NoCapture          # 不拍摄版 -> egoops-nocap-v74.apk（隐藏直接拍摄，走相册/水印相机）
powershell -File .\build.ps1 -Variant 采集水印相机  # 换名版   -> egoops-wm-v74.apk
```

流程要点（详见 `docs/PACKAGING.md`）：

1. `aapt2 compile/link` 出 `R.java` 与 `base.apk`（minSdk 21 / targetSdk 34）；
2. `javac -source 8 -target 8 -classpath android.jar` 编译 `gen` + 全部 `src`；
3. `d8 --min-api 21` 生成 `classes.dex`，塞回 APK；
4. `zipalign -f 4` → `apksigner sign`（缺 keystore 时自动用 keytool 生成 debug 签名）。

## 8. 拆分说明

原 `MainActivity.java`（2844 行）拆成 **1 个装配层 + 21 个控制器**，共搬运 73 个成员（4 个常量数组、68 个方法），
只做四类机械改写：可见性提升、接收者显式传参（`host`）、跨类限定（`ClassName.name`）、常量前缀限定；
方法体逐字保留，不重写算法。

* 映射表（原行段 → 新文件，含每个符号）：[`docs/SPLIT.md`](docs/SPLIT.md)
* 模块职责与数据流：[`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)
* 强耦合未拆分清单一览（`onActivityResult`/`onResume`/`onRequestPermissionsResult` 等）也在 `docs/SPLIT.md` §4
* 校验：
  * `python tests/check_repo.py` —— 括号配平、类名=文件名、package=目录、`com.egoops` 导入可解析
  * `python tests/check_verbatim.py` —— 68 个方法体与 `legacy/MainActivity.java` 对应行段逐字一致
