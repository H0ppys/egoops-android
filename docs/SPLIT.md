# 拆分映射表（SPLIT）

* 原文件：`legacy/MainActivity.java`（2844 行，只读镜像，内容与原始工程逐字节一致）
* 拆分后：`MainActivity.java` **307 行**（装配层），21 个模块文件，合计搬运 **73 个成员**（4 个常量数组 + 68 个方法）
* 搬运原则：**逐字搬运**，只允许四类机械改写（见文末 §3）；`tests/check_verbatim.py` 会验证 68 个方法体逐字一致

## 1. 成员映射

| 原文件 | 原行段 | 原符号 | 新文件 | 说明 |
| --- | --- | --- | --- | --- |
| MainActivity.java | 57-62 | `SHOTS_SITE` | state/Shots.java | 常量数组，原样搬运 |
| MainActivity.java | 64-75 | `SHOTS_TASK1` | state/Shots.java | 常量数组，原样搬运 |
| MainActivity.java | 77-85 | `SHOTS_AREA` | state/Shots.java | 常量数组，原样搬运 |
| MainActivity.java | 120-128 | `GUIDE` | state/Shots.java | 拍摄要点文案，原样搬运 |
| MainActivity.java | 87-92 | `shotsOf(Model.Task)` | state/Shots.java | static 化，语义不变 |
| MainActivity.java | 890-894 | `guideOf(String)` | state/Shots.java | static 化，语义不变 |
| MainActivity.java | 1214-1217 | `labelOf(Model.Task)` | state/Shots.java | static 化，语义不变 |
| MainActivity.java | 1219-1227 | `hasShot(int,int)` | state/Shots.java | 接收者显式传入 host |
| MainActivity.java | 1229-1233 | `firstUndone(int)` | state/Shots.java | 接收者显式传入 host |
| MainActivity.java | 1235-1240 | `doneCount(int)` | state/Shots.java | 接收者显式传入 host |
| MainActivity.java | 1116-1123 | `firstIncompleteTask()` | state/Shots.java | 接收者显式传入 host |
| MainActivity.java | 1280-1285 | `firstUndoneOrLast(int)` | state/Shots.java | 接收者显式传入 host |
| MainActivity.java | 1002-1114 | `beginShooting()` | state/SiteFlow.java | 表单校验 + site 赋值 + 进拍摄页 |
| MainActivity.java | 1243-1278 | `stepShot(int)` | state/SiteFlow.java | 左右滑动翻项（含跨任务） |
| MainActivity.java | 2427-2449 | `resumeLastSite()` | state/SiteFlow.java | 续拍入口 |
| MainActivity.java | 2340-2361 | `finishAll()` | state/SiteFlow.java | 收尾前校验任务名/区域 |
| MainActivity.java | 2684-2694 | `resetForNext()` | state/SiteFlow.java | 换采集点重置 |
| MainActivity.java | 1210-1212 | `task(int)` | state/SiteFlow.java | 取任务 |
| MainActivity.java | 1203-1208 | `keepName()` | state/SiteFlow.java | 回写门店名（原文件内无调用点，保留未删） |
| MainActivity.java | 1964-1975 | `syncTaskNamesAsync()` | state/SiteFlow.java | 后台同步任务名 |
| MainActivity.java | 276-517 | `buildStartPage()` | ui/StartPage.java | 第 1 屏视图构建 |
| MainActivity.java | 521-594 | `buildShootPage()` | ui/ShootPage.java | 第 2 屏骨架 + 手势翻项 |
| MainActivity.java | 598-657 | `buildTaskDonePage()` | ui/TaskDonePage.java | 任务完成屏 |
| MainActivity.java | 661-701 | `buildDonePage()` | ui/DonePage.java | 结束屏 |
| MainActivity.java | 1287-1479 | `renderShot()` | ui/ShotRenderer.java | 拍摄项卡片渲染（预览/进度点/重拍） |
| MainActivity.java | 896-902 | `showGuide(String)` | ui/Dialogs.java | 拍摄指引弹窗 |
| MainActivity.java | 1166-1187 | `confirmRestart()` | ui/Dialogs.java | 重新开始确认（含远端删点） |
| MainActivity.java | 1954-1960 | `addTask()` | ui/Dialogs.java | 新增任务入口（原文件内无调用点，保留未删） |
| MainActivity.java | 1978-2036 | `showAreaForTask1()` | ui/Dialogs.java | 任务1 区域填写 |
| MainActivity.java | 2260-2338 | `askTask(String)` | ui/Dialogs.java | 区域 + 动作填写 |
| MainActivity.java | 2471-2498 | `showLastCrash()` | ui/Dialogs.java | 崩溃回看 |
| MainActivity.java | 2793-2817 | `showSettings()` | settings/SettingsDialog.java | 服务器地址设置 |
| MainActivity.java | 2819-2826 | `labelText(String)` | settings/SettingsDialog.java | 标签构造工具 |
| MainActivity.java | 705-727 | `refreshSceneSpinner()` | settings/SceneCatalog.java | 分类→场景二级联动 |
| MainActivity.java | 729-736 | `autoLoadScenes()` | settings/SceneCatalog.java | 输入防抖（800ms）加载 |
| MainActivity.java | 738-740 | `loadScenes()` | settings/SceneCatalog.java | 无参重载 |
| MainActivity.java | 743-818 | `loadScenes(String)` | settings/SceneCatalog.java | 远端分类 + 离线 raw/scenes.json 兜底 |
| MainActivity.java | 821-863 | `loadRegions()` | settings/RegionCatalog.java | res/raw/pca.json 解析 |
| MainActivity.java | 865-875 | `updateCities(int)` | settings/RegionCatalog.java | 省→市 |
| MainActivity.java | 877-888 | `updateDistricts(int)` | settings/RegionCatalog.java | 市→区 |
| MainActivity.java | 2698-2723 | `autoLogin()` | settings/AccountManager.java | 静默登录 |
| MainActivity.java | 2726-2747 | `askInvite()` | settings/AccountManager.java | 邀请码弹窗 |
| MainActivity.java | 2750-2763 | `checkInviteThen(String)` | settings/AccountManager.java | 邀请码校验并刷新分类 |
| MainActivity.java | 2766-2779 | `refreshVendorLabel()` | settings/AccountManager.java | 邀请码/厂商标签刷新 |
| MainActivity.java | 2781-2791 | `loginNow(String)` | settings/AccountManager.java | 登录请求 |
| MainActivity.java | 1481-1541 | `captureShot(String,String)` | capture/CaptureFlow.java | 调起系统相机/录像 |
| MainActivity.java | 1543-1553 | `WM_PACKAGES` | capture/CaptureFlow.java | 已知水印相机包名表 |
| MainActivity.java | 1556-1663 | `openWatermarkCamera(String,String)` | capture/CaptureFlow.java | 水印相机三级探测与选择 |
| MainActivity.java | 1666-1675 | `launchWm(Intent)` | capture/CaptureFlow.java | 跳转并记录启动时刻 |
| MainActivity.java | 1678-1689 | `pickMultiple()` | capture/CaptureFlow.java | 批量选择（原文件内无调用点，保留未删） |
| MainActivity.java | 1692-1727 | `ingestMultiple(ClipData)` | capture/CaptureFlow.java | 批量导入到未完成项 |
| MainActivity.java | 1730-1786 | `autoGrabLatest(String,String)` | capture/CaptureFlow.java | 水印相机回来自动取回 |
| MainActivity.java | 1789-1794 | `pickShot(String,String)` | capture/CaptureFlow.java | 相册单选 |
| MainActivity.java | 1877-1952 | `ingest(Uri,boolean)` | capture/CaptureFlow.java | 落盘 + 水印 + 质检 + 收录 |
| MainActivity.java | 958-1000 | `acceptCapture(File,boolean)` | capture/CaptureFlow.java | 质检通过后收录（含旧文件远端删除） |
| MainActivity.java | 905-951 | `checkQuality(String,boolean)` | capture/QualityCheck.java | 模糊/过暗/视频过短判定 |
| MainActivity.java | 953-955 | `gray(int)` | capture/QualityCheck.java | 灰度换算 |
| MainActivity.java | 2041-2056 | `antiCodeOf(File)` | capture/WatermarkStamper.java | SHA1 前 12 位防伪码 |
| MainActivity.java | 2059-2159 | `stampWatermark(File,long,String)` | capture/WatermarkStamper.java | 打卡水印烧录 |
| MainActivity.java | 1797-1819 | `askAllPermissions()` | perm/Permissions.java | 运行时权限申请 |
| MainActivity.java | 2162-2198 | `startGps()` | location/LocationTracker.java | 取一次定位 |
| MainActivity.java | 2202-2258 | `useLocation(Location)` | location/LocationTracker.java | WGS84→GCJ02 + 逆地理 + 地图截图 |
| MainActivity.java | 1190-1194 | `deleteSiteRemote(String)` | queue/UploadCoordinator.java | 远端删采集点 |
| MainActivity.java | 1197-1201 | `deleteFileRemote(String)` | queue/UploadCoordinator.java | 远端删单文件 |
| MainActivity.java | 2366-2390 | `archiveUploaded()` | queue/UploadCoordinator.java | 传完生成缩略图并删原件 |
| MainActivity.java | 2452-2468 | `resumePendingUpload()` | queue/UploadCoordinator.java | 启动时续传 |
| MainActivity.java | 2612-2682 | `startUpload()` | queue/UploadCoordinator.java | 上传主流程 + 进度回调 |
| MainActivity.java | 2392-2409 | `saveThumb(File,File,int)` | queue/Thumbnails.java | 照片缩略图 |
| MainActivity.java | 2411-2424 | `saveVideoThumb(File,File)` | queue/Thumbnails.java | 视频首帧缩略图 |
| MainActivity.java | 2501-2527 | `checkUpdate()` | queue/AutoUpdate.java | 版本查询 |
| MainActivity.java | 2530-2580 | `downloadAndInstall(String,long)` | queue/AutoUpdate.java | APK 下载 + 完整性校验 |
| MainActivity.java | 2582-2610 | `installApk(File)` | queue/AutoUpdate.java | 交系统安装器 |
| MainActivity.java | 1126-1164 | `installCrashReporter()` | diag/CrashReporter.java | 全局崩溃捕获与上报 |

## 2. 留在 MainActivity 的部分

| 原行段 | 符号 | 处理 | 原因 |
| --- | --- | --- | --- |
| 39-55 | `class MainActivity`、`REQ_*`、配色常量 | 保留（改 `public`） | 宿主类与全局常量 |
| 94-133 | 全部状态字段 | 保留（改 `public`） | 模块共享状态，拆出去会改变对象身份/生命周期 |
| 137-176 | `onCreate` | 保留（调用点改为模块调用） | 纯装配，本身就是装配层 |
| 181-195 | `buildUi` | 保留 | 只做四个页面的挂载 |
| 197-272 | `full/hideKeyboard/showPage/pageShell/scrollWrap/bottomBar/primaryButton/secondaryButton` | 保留（改 `public`） | 布局工具，被 4 个页面模块共用；依赖 `this` 作为 Context |
| 1821-1829 | `onRequestPermissionsResult` | 保留（内部转调 `CaptureFlow.captureShot`） | **未拆分（强耦合）**：系统回调必须由 Activity 覆写 |
| 1831-1842 | `onResume` | 保留（内部转调 `CaptureFlow.autoGrabLatest`） | **未拆分（强耦合）**：水印相机返回时机依赖 Activity 生命周期 |
| 1844-1874 | `onActivityResult` | 保留（内部转调 `CaptureFlow.ingest/ingestMultiple`） | **未拆分（强耦合）**：`RESULT_OK`/requestCode 只在这里拿得到 |
| 201 | `curPage` | 保留 | `showPage` 的游标，与 `onCreate` 同生命周期 |
| 2830-2843 | `roundRect/dp/toast` | 保留（改 `public`） | 依赖 `getResources()`/`Toast.makeText(this, …)`，模块通过 `host.` 调用 |

## 3. 允许的机械改写（逐字搬运的边界）

1. **可见性**：`private` → `public`（字段、工具方法、常量）；
2. **接收者显式化**：原 `private void foo()` → `public static void foo(MainActivity host)`，
   方法体内的 `site` → `host.site`、`toast(...)` → `host.toast(...)`、`this` → `host`；
3. **跨类限定**：被搬走的成员调用统一写成 `ClassName.name(...)`（含 `host` 传参）；
4. **常量限定**：`BG/MUTED/REQ_PHOTO…` → `MainActivity.BG/…`；`MODE_PRIVATE/LOCATION_SERVICE` → `android.content.Context.*`；
5. **方法引用**：`this::checkUpdate` → `() -> AutoUpdate.checkUpdate(this)`（语义等价的 lambda 形式）。

除以上五类，方法体一个字符都没动：`tests/check_verbatim.py` 对 68 个方法逐一归一化比对，
结果 **一致 68 / 不一致 0**。

## 4. 强耦合未拆分清单

| 触发点 | 未拆分理由 | 现状 |
| --- | --- | --- |
| `onActivityResult` | 依赖 Activity 的 requestCode/resultCode 契约和 `tempUri` 兜底，抽成独立类需要额外回调注入，属于"改动结构"而非"搬运" | 保留 + 转调 `CaptureFlow` |
| `onResume` | 每 500ms 延迟取回水印相机成果，依赖 `tvStep` 已挂载 | 保留 + 转调 `CaptureFlow` |
| `onRequestPermissionsResult` | 系统回调，且要按 `pendingShot` 重入 `captureShot` | 保留 + 转调 `CaptureFlow` |
| `onCreate` 内的队列恢复判定 | 与 `store` 初始化、`site` 身份绑定，抽走会引入第二次赋值时机差异 | 保留 |
| `showPage` / `hideKeyboard` | 直接持有四个页面引用与 `InputMethodManager` | 保留（工具层） |
| `dp/roundRect/toast` | 依赖 Activity 的 `getResources()` / `Toast` | 保留（工具层） |
