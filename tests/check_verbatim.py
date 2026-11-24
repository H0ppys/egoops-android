# -*- coding: utf-8 -*-
"""保真度校验：新模块的方法体与 legacy/MainActivity.java 对应行段逐字一致（仅归一化机械改写）。

归一化的机械改写只有四类：host 接收者显式传参、类名限定、Context 常量前缀、
this:: 与等价 lambda 形式。除此外方法体必须逐字相同。
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ORIG = os.path.join(ROOT, "legacy", "MainActivity.java")
REPO = os.path.join(ROOT, "src", "main", "java", "com", "egoops", "collect")

# (pkg, cls, [(start, end, method_name), ...])  —— 方法体行段（与 docs/SPLIT.md 一致）
TABLE = [
    ("state", "Shots", [(87, 92, "shotsOf"), (890, 894, "guideOf"), (1214, 1217, "labelOf"),
                        (1219, 1227, "hasShot"), (1229, 1233, "firstUndone"),
                        (1235, 1240, "doneCount"), (1117, 1123, "firstIncompleteTask"),
                        (1281, 1285, "firstUndoneOrLast")]),
    ("state", "SiteFlow", [(1002, 1114, "beginShooting"), (1243, 1278, "stepShot"),
                           (2427, 2449, "resumeLastSite"), (2340, 2361, "finishAll"),
                           (2684, 2694, "resetForNext"), (1210, 1212, "task"),
                           (1203, 1208, "keepName"), (1964, 1975, "syncTaskNamesAsync")]),
    ("ui", "StartPage", [(276, 517, "buildStartPage")]),
    ("ui", "ShootPage", [(521, 594, "buildShootPage")]),
    ("ui", "TaskDonePage", [(598, 657, "buildTaskDonePage")]),
    ("ui", "DonePage", [(661, 701, "buildDonePage")]),
    ("ui", "ShotRenderer", [(1287, 1479, "renderShot")]),
    ("ui", "Dialogs", [(896, 902, "showGuide"), (1166, 1187, "confirmRestart"),
                       (1954, 1960, "addTask"), (1978, 2036, "showAreaForTask1"),
                       (2260, 2338, "askTask"), (2471, 2498, "showLastCrash")]),
    ("settings", "SettingsDialog", [(2793, 2817, "showSettings"), (2819, 2826, "labelText")]),
    ("settings", "SceneCatalog", [(705, 727, "refreshSceneSpinner"), (729, 736, "autoLoadScenes"),
                                  (738, 740, "loadScenes"), (743, 818, "loadScenes")]),
    ("settings", "RegionCatalog", [(821, 863, "loadRegions"), (865, 875, "updateCities"),
                                   (877, 888, "updateDistricts")]),
    ("settings", "AccountManager", [(2698, 2723, "autoLogin"), (2726, 2747, "askInvite"),
                                    (2750, 2763, "checkInviteThen"), (2766, 2779, "refreshVendorLabel"),
                                    (2781, 2791, "loginNow")]),
    ("capture", "CaptureFlow", [(1481, 1541, "captureShot"), (1556, 1663, "openWatermarkCamera"),
                                (1666, 1675, "launchWm"), (1678, 1689, "pickMultiple"),
                                (1692, 1727, "ingestMultiple"), (1730, 1786, "autoGrabLatest"),
                                (1789, 1794, "pickShot"), (1877, 1952, "ingest"),
                                (958, 1000, "acceptCapture")]),
    ("capture", "QualityCheck", [(905, 951, "checkQuality"), (953, 955, "gray")]),
    ("capture", "WatermarkStamper", [(2041, 2056, "antiCodeOf"), (2059, 2159, "stampWatermark")]),
    ("perm", "Permissions", [(1797, 1819, "askAllPermissions")]),
    ("location", "LocationTracker", [(2162, 2198, "startGps"), (2202, 2258, "useLocation")]),
    ("queue", "UploadCoordinator", [(1190, 1194, "deleteSiteRemote"), (1197, 1201, "deleteFileRemote"),
                                    (2366, 2390, "archiveUploaded"), (2452, 2468, "resumePendingUpload"),
                                    (2612, 2682, "startUpload")]),
    ("queue", "Thumbnails", [(2392, 2409, "saveThumb"), (2411, 2424, "saveVideoThumb")]),
    ("queue", "AutoUpdate", [(2501, 2527, "checkUpdate"), (2530, 2580, "downloadAndInstall"),
                             (2582, 2610, "installApk")]),
    ("diag", "CrashReporter", [(1126, 1164, "installCrashReporter")]),
]

MODULE_CLASSES = sorted({c for _, c, _ in TABLE})


def body_of(text, name=None):
    """取第一个 '{' 到最后一个 '}' 之间的内容。"""
    i = text.index("{")
    j = text.rindex("}")
    return text[i + 1:j]


def find_method(src, name, arity_hint=None):
    """在新模块文件里定位静态方法（按名字，取第 arity_hint 个）。"""
    res = []
    for m in re.finditer(r"[ \t]*public static [\w\[\].$<>]+ %s\(" % re.escape(name), src):
        start = m.start()
        open_p = src.index("(", start)
        depth, i = 0, open_p
        while i < len(src):
            if src[i] == "(":
                depth += 1
            elif src[i] == ")":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        brace = src.index("{", i)
        depth2, j = 0, brace
        while j < len(src):
            if src[j] == "{":
                depth2 += 1
            elif src[j] == "}":
                depth2 -= 1
                if depth2 == 0:
                    break
            j += 1
        res.append(src[brace:j + 1])
    return res


_Q = "|".join(MODULE_CLASSES)
NORM_SUBS = [
    (r"\(\) -> (%s)\.([A-Za-z_]\w*)\(\s*host\s*\)" % _Q, r"lam_\2"),
    (r"(?<![\w.])this::([A-Za-z_]\w*)", r"lam_\1"),
    (r"\(\) -> ([A-Za-z_]\w*)\(\)", r"lam_\1"),
    (r"(%s)\.([A-Za-z_]\w*)\(\s*host\s*,\s*" % _Q, r"\2("),
    (r"(%s)\.([A-Za-z_]\w*)\(\s*host\s*\)" % _Q, r"\2()"),
    (r"\bhost\.", ""),
    (r"\bhost\b", "this"),
    (r"\bMainActivity\.", ""),
    (r"\bandroid\.content\.Context\.", ""),
]
for _c in MODULE_CLASSES:
    NORM_SUBS.append((r"\b%s\." % _c, r""))


def norm(t):
    t = re.sub(r"\s+", " ", t).strip()
    for a, b in NORM_SUBS:
        t = re.sub(a, b, t)
    t = re.sub(r"\s+", " ", t).strip()
    return t


orig = io.open(ORIG, encoding="utf-8").read().replace("\r\n", "\n").split("\n")
ok = fail = 0
problems = []
for pkg, cls, methods in TABLE:
    path = os.path.join(REPO, pkg, cls + ".java")
    src = io.open(path, encoding="utf-8").read().replace("\r\n", "\n")
    for start, end, name in methods:
        raw = "\n".join(orig[start - 1:end])
        ob = body_of(raw)
        cands = find_method(src, name)
        if not cands:
            problems.append("%s.%s 未在新文件中找到" % (cls, name))
            fail += 1
            continue
        nb = None
        for c in cands:
            if norm(body_of(c)) == norm(ob):
                nb = c
                break
        if nb is None:
            problems.append("%s.%s (原 %d-%d) 方法体不一致" % (cls, name, start, end))
            fail += 1
            n0 = norm(body_of(cands[0]))
            o0 = norm(ob)
            for k in range(min(len(n0), len(o0))):
                if n0[k] != o0[k]:
                    problems.append("   差异位置 %d:\n   原: ...%s\n   新: ...%s" %
                                    (k, o0[max(0, k - 60):k + 80], n0[max(0, k - 60):k + 80]))
                    break
            else:
                problems.append("   长度差异 原=%d 新=%d" % (len(o0), len(n0)))
        else:
            ok += 1

print("保真度校验：一致 %d / 不一致 %d（共 %d 个方法）" % (ok, fail, ok + fail))
for p in problems:
    print(p)
sys.exit(1 if fail else 0)
