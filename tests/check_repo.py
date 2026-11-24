#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""EgoOps Collect（Android）静态校验。

检查项：
  1. 括号配平：每个 .java 的 {} / () / [] 在跳过字符串、字符、注释后必须配平；
  2. 类名与文件名一致：文件名必须等于文件里主类型（public class/interface）名；
  3. package 与目录一致：com.egoops.collect.ui.X 必须位于 .../com/egoops/collect/ui/X.java；
  4. import 的 com.egoops 符号能找到定义：
       - 显式导入 Class            -> 必须存在同名类型定义
       - 通配导入 pkg.*            -> pkg 目录必须存在且含 .java
       - 正文里的全限定 com.egoops 引用 -> 必须能解析到类型或该类的静态成员
用法：
  python tests/check_repo.py [-v]
退出码：0 全部通过，1 有失败项。
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src", "main", "java")


def read(path):
    return io.open(path, encoding="utf-8").read().replace("\r\n", "\n")


def strip_code(text):
    """去掉字符串/字符/注释，保留换行以便定位。"""
    out = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if c == '"':
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == '"':
                    i += 1
                    break
                i += 1
            out.append('""')
            continue
        if c == "'":
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == "'":
                    i += 1
                    break
                i += 1
            out.append("''")
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "/":
            while i < n and text[i] != "\n":
                i += 1
            continue
        if c == "/" and i + 1 < n and text[i + 1] == "*":
            i += 2
            while i + 1 < n and not (text[i] == "*" and text[i + 1] == "/"):
                i += 1
            i += 2
            continue
        out.append(c)
        i += 1
    return "".join(out)


def check_balance(path, text):
    code = strip_code(text)
    problems = []
    for open_ch, close_ch in (("{", "}"), ("(", ")"), ("[", "]")):
        depth = 0
        line = 1
        for ch in code:
            if ch == "\n":
                line += 1
            elif ch == open_ch:
                depth += 1
            elif ch == close_ch:
                depth -= 1
                if depth < 0:
                    problems.append("多余的 '%s'（第 %d 行附近）" % (close_ch, line))
                    depth = 0
        if depth != 0:
            problems.append("'%s' 少 %d 个闭合" % (open_ch, depth))
    return problems


def collect_types(files):
    """返回 {(pkg, name): path} 与 {(pkg): [names]}。"""
    types = {}
    pkgs = {}
    for path in files:
        text = read(path)
        pkg = ""
        m = re.search(r"^\s*package\s+([\w.]+)\s*;", text, re.M)
        if m:
            pkg = m.group(1)
        code = strip_code(text)
        for tm in re.finditer(r"\b(?:class|interface|enum)\s+([A-Za-z_$][\w$]*)", code):
            types[(pkg, tm.group(1))] = path
            pkgs.setdefault(pkg, []).append(tm.group(1))
    return types, pkgs


def main_class_name(text):
    code = strip_code(text)
    m = re.search(r"\bpublic\s+(?:final\s+|abstract\s+)?(?:class|interface|enum)\s+([A-Za-z_$][\w$]*)", code)
    if m:
        return m.group(1)
    m = re.search(r"\b(?:class|interface|enum)\s+([A-Za-z_$][\w$]*)", code)
    return m.group(1) if m else None


def main():
    verbose = "-v" in sys.argv
    if not os.path.isdir(SRC):
        print("找不到源码目录：%s" % SRC)
        return 1
    files = []
    for dirpath, _dirnames, filenames in os.walk(SRC):
        for fn in sorted(filenames):
            if fn.endswith(".java"):
                files.append(os.path.join(dirpath, fn))
    files.sort()
    types, pkgs = collect_types(files)

    rows = []
    failures = 0
    for path in files:
        rel = os.path.relpath(path, ROOT).replace("\\", "/")
        text = read(path)
        problems = []

        # 1) 括号配平
        problems += check_balance(path, text)

        # 2) 类名 == 文件名
        cls = main_class_name(text)
        stem = os.path.basename(path)[:-5]
        if cls != stem:
            problems.append("主类型名 %r 与文件名 %r 不一致" % (cls, stem))

        # 3) package == 目录
        m = re.search(r"^\s*package\s+([\w.]+)\s*;", text, re.M)
        if not m:
            problems.append("缺少 package 声明")
        else:
            pkg = m.group(1)
            expect = os.path.join(*pkg.split("."))
            if not os.path.normpath(path).replace("\\", "/").endswith("/" + expect.replace("\\", "/") + "/" + stem + ".java"):
                problems.append("package %s 与所在目录不匹配" % pkg)

        # 4) import 的 com.egoops 符号
        for im in re.finditer(r"^\s*import\s+(static\s+)?(com\.egoops[\w.]*)\s*;", text, re.M):
            sym = im.group(2)
            if sym.endswith(".*"):
                pkg = sym[:-2]
                if pkg not in pkgs or not pkgs[pkg]:
                    problems.append("通配导入的包不存在或为空：%s" % pkg)
            else:
                pkg, _, name = sym.rpartition(".")
                if (pkg, name) not in types:
                    problems.append("导入的类型找不到定义：%s" % sym)

        # 4b) 正文里的全限定 com.egoops 引用（跳过 import 行，只认以大写类名结尾的引用）
        body = re.sub(r"^\s*import\s+.*$", "", strip_code(text), flags=re.M)
        for fm in re.finditer(r"\bcom\.egoops(?:\.[A-Za-z_$][\w$]*)+", body):
            ref = fm.group(0)
            parts = ref.split(".")
            if not parts[-1][:1].isupper():
                continue
            ok = False
            for cut in range(len(parts), 1, -1):
                pkg = ".".join(parts[:cut - 1])
                name = parts[cut - 1]
                if (pkg, name) in types:
                    ok = True
                    break
            if not ok:
                problems.append("全限定引用无法解析：%s" % ref)

        rows.append((rel, len(text.rstrip("\n").split("\n")), problems))
        if problems:
            failures += 1

    width = max(len(r[0]) for r in rows) if rows else 10
    print("=" * (width + 26))
    print("%-*s %6s  %s" % (width, "文件", "行数", "结果"))
    print("=" * (width + 26))
    for rel, nlines, problems in rows:
        print("%-*s %6d  %s" % (width, rel, nlines, "OK" if not problems else "FAIL"))
        if problems:
            for p in problems:
                print("    - %s" % p)
    print("=" * (width + 26))
    print("文件 %d 个，通过 %d 个，失败 %d 个" % (len(rows), len(rows) - failures, failures))
    print("类型定义 %d 个，包 %d 个：%s" % (len(types), len(pkgs), ", ".join(sorted(pkgs))))
    if verbose:
        for (pkg, name), p in sorted(types.items()):
            print("  %s.%s  <- %s" % (pkg, name, os.path.relpath(p, ROOT)))
    return 0 if failures == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
