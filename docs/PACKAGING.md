# 打包说明（PACKAGING）

本仓库**不使用 Gradle**，沿用原始工程的「PowerShell + aapt2 + javac + d8 + apksigner」直接构建方式。
`build.ps1` 原样保留（与原始工程逐字节一致），本文说明它依赖的目录假设和实测步骤。

## 1. 工具链

| 组件 | 版本 | 路径（相对构建根目录） |
| --- | --- | --- |
| JDK | 17.0.2 | `tools/jdk/jdk-17.0.2` |
| Android Build-Tools | 34.0.0（目录名 `android-14`） | `tools/build-tools/android-14` |
| Android Platform | android-34 | `tools/platform/android-34/android.jar` |

需要的最小可执行文件：`aapt2.exe`、`d8.bat`、`zipalign.exe`、`apksigner.bat`、`javac.exe`、`keytool.exe`。

## 2. `build.ps1` 的目录假设（关键）

脚本内部固定了这三个路径：

```powershell
$ROOT  = Split-Path -Parent $PSScriptRoot      # 脚本所在目录的父目录
$APP   = Join-Path $ROOT 'android'             # 工程目录 = ROOT/android
$BUILD = Join-Path $APP  'build'
$JDK   = Join-Path $ROOT 'tools\jdk\jdk-17.0.2'
$BT    = Join-Path $ROOT 'tools\build-tools\android-14'
$AJAR  = Join-Path $ROOT 'tools\platform\android-34\android.jar'
```

也就是说：**脚本必须放在 `<构建根>/android/` 里，且 `<构建根>/tools/` 下要有工具链**。
因此把本仓库 clone 成 `android` 目录即可原样使用：

```
<构建根>/
  android/            <- 本仓库（build.ps1、res/、AndroidManifest.xml、src/）
  tools/
    jdk/jdk-17.0.2/
    build-tools/android-14/
    platform/android-34/android.jar
```

## 3. 源码布局差异与桥接

本仓库按 github-out 规范把源码放在 `src/main/java/com/egoops/collect/…`，
而 `build.ps1` 有两条硬编码：

1. `Get-ChildItem "$APP\src" -Recurse -Filter *.java` —— **递归**收集，`src/main/java` 也能被收进来，无需改动；
2. 生成版本号时写入 `$APP\src\com\egoops\collect\BuildConfigVersion.java` —— 这是**旧布局**的固定路径。

因为第 2 条，直接用本仓库的 `src/main/java` 布局跑 `build.ps1` 会因为目标目录不存在而失败，
需要把源码镜像成旧布局（目录结构一样，只是少了 `main/java` 两层）。实测用的桥接步骤：

```powershell
$WS = 'C:\path\to\workspace'                 # 含 tools\ 的构建根
$REPO = "$WS\android"                        # 本仓库
New-Item -ItemType Directory -Force "$WS\android\src\com\egoops\collect" | Out-Null
Copy-Item "$REPO\src\main\java\com\egoops\collect\*" "$WS\android\src\com\egoops\collect\" -Recurse -Force
pwsh -File "$WS\android\build.ps1"           # Windows PowerShell 5.1 也可：powershell -File ...
```

> 若不想动仓库，可把仓库 clone 到临时目录再展开：
> `git clone <repo> android && Copy-Item src\main\java\com\egoops\collect\* src\com\egoops\collect\ -Recurse`
> （此时 `src/main/java` 与 `src/com/egoops/collect` 会各有一份同名类，`javac` 会报重复类，
> 所以**二者只保留一份**：镜像时用 Move 而不是 Copy，或直接在上面的临时目录里操作。）

## 4. 构建流程（`build.ps1` 逐步说明）

| 步骤 | 命令要点 | 产物 |
| --- | --- | --- |
| clean | 删除 `android/build`，重建 `gen/classes/dex/res` | — |
| aapt2 compile | `aapt2 compile --dir res -o build/res.zip` | 资源扁平包 |
| aapt2 link | `aapt2 link -o build/base.apk -I android.jar --manifest AndroidManifest.xml --java build/gen --min-sdk-version 21 --target-sdk-version 34 --version-code 74 --version-name 74.0 <flats>` | `base.apk` + `build/gen/.../R.java` |
| 版本号同步 | 从 `AndroidManifest.xml` 抽 `versionCode/versionName`，写入 `BuildConfigVersion.java`（`CODE`/`NAME`/`NO_CAPTURE`） | 覆盖同名源文件 |
| javac | `javac -encoding UTF-8 -source 8 -target 8 -nowarn -classpath android.jar -d build/classes <gen + src>` | `.class` |
| d8 | `d8 --lib android.jar --min-api 21 --output build/dex <所有 .class>` | `classes.dex` |
| 打包 | 把 `classes.dex` 写进 `base.apk` → `app-unaligned.apk` | — |
| zipalign | `zipalign -f 4` | `app-aligned.apk` |
| 签名 | `apksigner sign --ks android/debug.keystore --ks-pass pass:android --key-pass pass:android` | `egoops-collect-v74.apk` |

`debug.keystore` **不在仓库里**：`build.ps1` 检测到缺失时会用 `keytool` 自动生成
（`CN=Android Debug, O=Android, C=US`，口令 `android`）。发布版请换成自己的 keystore，并且不要提交进仓库。

变体开关：

```powershell
pwsh build.ps1                 # 常规版      -> egoops-collect-v74.apk
pwsh build.ps1 -NoCapture      # 不拍摄版    -> egoops-nocap-v74.apk
pwsh build.ps1 -Variant 采集水印相机   # 换名版 -> egoops-wm-v74.apk
```

`-NoCapture` 会同时把 `BuildConfigVersion.NO_CAPTURE` 置为 `true`，App 内隐藏直接拍摄按钮、改走相册/水印相机流程。

## 5. 实测结果

在 Windows + JDK 17.0.2 + build-tools 34 + android-34 环境下，用第 3 节的镜像方式跑**原样** `build.ps1`：

```
== clean
== aapt2 compile
== aapt2 link
== javac
== d8
== package classes.dex
== zipalign
== sign
Signer #1 certificate DN: CN=Android Debug, O=Android, C=US
Signer #1 certificate SHA-256 digest: 3f3b46f63ba305bb3b418b68c9cb2147a158a5a563ef0e9150ba26afa1a99c53

BUILD OK: ...\egoops-collect-v74.apk (113 KB)
```

```
$ aapt dump badging egoops-collect-v74.apk
package: name='com.egoops.collect' versionCode='74' versionName='74.0' ... compileSdkVersion='34'
sdkVersion:'21'
targetSdkVersion:'34'
uses-permission: name='android.permission.INTERNET'
```

## 6. 没有工具链时怎么验证

`javac` 与 `android.jar` 都不可用时，仍然可以跑两项纯静态校验（不需要 Android SDK）：

```bash
python tests/check_repo.py       # 括号配平 / 类名=文件名 / package=目录 / import 可解析
python tests/check_verbatim.py   # 68 个方法体与 legacy/MainActivity.java 逐字一致
```

## 7. 常见问题

| 现象 | 原因 | 处理 |
| --- | --- | --- |
| `aapt2 link failed: AndroidManifest.xml not found` | 仓库没放在 `<构建根>/android` | 见 §2 |
| `javac failed: 找不到符号 R` | `aapt2 link` 的 `--java` 目录没进 javac 源列表 | 确认 `build/gen` 已生成 |
| `找不到 BuildConfigVersion.java 路径` | 用了 `src/main/java` 布局 | 见 §3 镜像步骤 |
| `d8 failed: duplicate class` | `src/main/java` 与 `src/com/egoops/collect` 各有一份 | 只保留一份 |
| 安装时 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 签名与已装版本不同 | 先卸载旧版，或复用同一 keystore |
| 高德逆地理/地图不返回 | `Geo`/`MapSnap` 里的 key 需要按厂商申请 | 见 README §6 配置项 |
