# 架构说明（Architecture）

## 1. 背景

原始工程是单文件巨型 Activity：`MainActivity.java` 共 **2844 行**，把界面构建、拍摄动作、
上传队列、定位水印、权限、账号、自动更新全部塞在一个类里。本次拆分把它变成
**「装配层 + 功能控制器」** 的结构，逻辑逐字搬运，不改算法。

## 2. 分层

```
                     ┌──────────────────────────────┐
                     │  MainActivity（装配层）       │
                     │  · onCreate / 生命周期转发     │
                     │  · 全屏容器 buildUi()         │
                     │  · 布局工具 dp/roundRect/toast │
                     │  · 共享状态字段（site/current*）│
                     └───────────┬──────────────────┘
                                 │ static 调用（首参传 host）
      ┌──────────────┬───────────┼────────────┬──────────────┬───────────┐
      │ ui/          │ state/    │ capture/   │ settings/    │ queue/    │
      │ 4 个页面      │ Shots     │ 拍摄动作流  │ 场景/省市区   │ 上传/更新  │
      │ ShotRenderer │ SiteFlow  │ 质检/水印   │ 账号/设置     │ 缩略图     │
      └──────────────┴───────────┴────────────┴──────────────┴───────────┘
      ┌──────────────┬───────────┬──────────────────────────────────────┐
      │ perm/        │ location/ │ diag/                                │
      │ 权限          │ 定位+逆地理│ 崩溃上报                              │
      └──────────────┴───────────┴──────────────────────────────────────┘
                                 │
                     ┌───────────┴──────────────────────────────┐
                     │ 数据/网络层（未拆分，原样保留）             │
                     │ Api · Store · Model · Uploader            │
                     │ UploadService · ApkProvider · Geo         │
                     │ MapSnap · WatermarkVideo · BuildConfigVersion │
                     └──────────────────────────────────────────┘
```

## 3. 模块职责

| 包 | 类 | 职责 | 行数 |
| --- | --- | --- | --- |
| `com.egoops.collect` | `MainActivity` | 装配层：生命周期、全屏容器、布局工具、共享状态 | 307 |
| `com.egoops.collect.state` | `Shots` | 拍摄项矩阵（SHOTS_SITE/TASK1/AREA、GUIDE）、进度判定 | 115 |
| | `SiteFlow` | 采集主流程：开始拍摄、翻项、续拍、收尾、重置 | 255 |
| `com.egoops.collect.ui` | `StartPage` | 第 1 屏（门店/地址/场景/面积） | 275 |
| | `ShootPage` | 第 2 屏骨架（手势翻项 + 上传状态条） | 101 |
| | `TaskDonePage` / `DonePage` | 任务完成屏 / 结束屏 | 86 / 67 |
| | `ShotRenderer` | 当前拍摄项卡片渲染 | 223 |
| | `Dialogs` | 指引、区域/任务、重新开始、崩溃回看 | 235 |
| `com.egoops.collect.capture` | `CaptureFlow` | 相机/相册/水印相机、素材落盘与收录 | 472 |
| | `QualityCheck` | 模糊/过暗/视频过短判定 | 71 |
| | `WatermarkStamper` | 水印烧录 + 防伪码 | 147 |
| `com.egoops.collect.settings` | `SceneCatalog` | 场景分类二级联动、远端/离线加载 | 141 |
| | `RegionCatalog` | 省市区加载与联动 | 90 |
| | `AccountManager` | 登录、邀请码、厂商标签 | 116 |
| | `SettingsDialog` | 服务器地址设置 | 58 |
| `com.egoops.collect.queue` | `UploadCoordinator` | 上传触发、进度回调、续传、远端清理 | 152 |
| | `Thumbnails` | 缩略图生成 | 55 |
| | `AutoUpdate` | 版本检查、APK 下载与安装 | 139 |
| `com.egoops.collect.perm` | `Permissions` | 运行时权限申请 | 50 |
| `com.egoops.collect.location` | `LocationTracker` | 定位、逆地理、地图截图 | 123 |
| `com.egoops.collect.diag` | `CrashReporter` | 全局崩溃捕获与上报 | 61 |

## 4. 状态归属

所有可变状态仍然是 **MainActivity 的字段**（`public`，原来只有可见性不同）：

| 分组 | 字段 |
| --- | --- |
| 依赖 | `store`、`api` |
| 业务数据 | `site`、`currentTask`、`currentShot`、`pendingShot`、`uploading`、`tempUri`、`lastSceneInvite` |
| 定位 | `gpsAddress`、`gpsCoords`、`mapSnapFile` |
| 水印相机 | `awaitingWatermarkImport`、`wmLaunchAt` |
| 视图 | `pageStart/pageShoot/pageTaskDone/pageDone`、`shotArea`、`tvStep/tvDots/tvUpload/tvDoneStatus/...`、`etInvite/etCollector/etStoreName/etArea/etStreet`、`spProv/spCity/spDist/spCat/spScene2`、`provArr/cityArr/distArr`、`sceneItems/categories/shownIndex` |
| 常量 | `REQ_*`（请求码）、`BG/CARD/PRIMARY/OK/TEXT/MUTED/DANGER`（配色） |

模块是 `final class` + 私有构造 + 全静态方法，**不持有 Activity 引用**，每次调用把
`MainActivity host` 作为第一个参数传入。这样：

* 没有静态成员泄漏 Activity 的风险（不会持有到下次启动）；
* `site = new Model.Site()` 这类**重新赋值**仍然发生在宿主上，所有模块看到的是同一份最新状态；
* 单测/静态检查可以只依赖签名。

## 5. 数据流

```
onCreate
  └─ CrashReporter.installCrashReporter(host)
  └─ Store.loadQueue() → 决定是否恢复 site
  └─ buildUi() → StartPage / ShootPage / TaskDonePage / DonePage
  └─ showPage(1) → AccountManager.autoLogin → SceneCatalog.loadScenes → RegionCatalog.loadRegions
  └─ Dialogs.showLastCrash → UploadCoordinator.resumePendingUpload → Permissions.askAllPermissions
  └─ AutoUpdate.checkUpdate（3 秒后，NO_CAPTURE 版本跳过）

开始拍摄  SiteFlow.beginShooting（校验 → site 赋值 → renderShot + startGps）
拍摄一项  CaptureFlow.captureShot / openWatermarkCamera / pickShot
          → onActivityResult → CaptureFlow.ingest / ingestMultiple
          → QualityCheck.checkQuality（不合格弹窗）→ CaptureFlow.acceptCapture
          → WatermarkStamper.stampWatermark / antiCodeOf
          → Shots.hasShot/doneCount 判定 → 下一项或任务完成屏
上传      UploadCoordinator.startUpload → Uploader(api, site, store, listener)
          → UploadService 通知进度 → onDone: archiveUploaded → Thumbnails
```

## 6. 依赖方向

* `ui/*` → `state/*`、`capture/*`、`settings/*`、`MainActivity`（布局工具 + 字段）
* `state/*` → `Shots`、`ui/ShotRenderer`、`Dialogs`、`queue/UploadCoordinator`
* `capture/*` → `Shots`、`QualityCheck`、`WatermarkStamper`、`queue/*`、`Dialogs`
* `settings/*` → `MainActivity`、`capture/CaptureFlow`、`location/LocationTracker`、`queue/UploadCoordinator`
* 所有模块 → `com.egoops.collect.*`（Api/Store/Model/Uploader/R/BuildConfigVersion）

Java 允许同包族之间的双向引用，这里不引入接口层，保持搬运成本最低；
若后续要进一步解耦，建议先把 `Model.Site` 的读写收敛成 `SiteRepository`。
