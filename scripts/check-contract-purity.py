#!/usr/bin/env python3
"""契约层纯度守卫：`:contract` 必须是纯 JDK。

判据（硬零，不做棘轮）：
  C1 导入根：contract/src/main 只允许 java. / javax. / com.ragagent.
  C2 构建依赖：contract/build.gradle.kts 不得声明 api / implementation /
     compileOnly / runtimeOnly / annotationProcessor（测试依赖除外）。
  C3 框架形状：contract/src/main 不得出现 Spring / MyBatis / JPA 注解，
     也不得实现 TypeHandler。

红态探针（改本脚本或 contract 构建后必跑）：
  临时在 contract 主码放一个 `import org.springframework.stereotype.Component;`
  的文件，本脚本必须非零退出；跑完删除探针。
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "contract" / "src" / "main" / "java"
BUILD_FILE = ROOT / "contract" / "build.gradle.kts"

ALLOWED_IMPORT_ROOTS = ("java.", "javax.", "com.ragagent.")

IMPORT_RE = re.compile(r"^import\s+(?:static\s+)?([\w.]+);", re.M)
COMMENT_BLOCK_RE = re.compile(r"/\*.*?\*/", re.S)
COMMENT_LINE_RE = re.compile(r"//[^\n]*")

# C2：只盯生产依赖；testImplementation / testRuntimeOnly 等测试面允许。
PROD_DEP_RE = re.compile(
    r"^\s*(api|implementation|compileOnly|runtimeOnly|annotationProcessor)\s*\("
)

# C3：契约面出现这些形状即视为装配/持久化代码回流。
FORBIDDEN_SHAPES = (
    "@TableName",
    "@Entity",
    "@Component",
    "@Service",
    "@Repository",
    "@RestController",
    "@Controller",
    "@Configuration",
    "@ConfigurationProperties",
    "implements TypeHandler",
    "extends TypeHandler",
)


def contract_sources() -> list[pathlib.Path]:
    if not SRC.is_dir():
        return []
    return sorted(p for p in SRC.rglob("*.java") if "/build/" not in str(p))


def check_imports(files: list[pathlib.Path]) -> list[str]:
    problems = []
    for path in files:
        text = path.read_text(encoding="utf-8")
        for imported in IMPORT_RE.findall(text):
            if not imported.startswith(ALLOWED_IMPORT_ROOTS):
                rel = path.relative_to(ROOT)
                problems.append(f"C1 {rel}: import {imported}")
    return problems


def check_build() -> list[str]:
    if not BUILD_FILE.is_file():
        return [f"C2 缺少构建文件：{BUILD_FILE.relative_to(ROOT)}"]
    problems = []
    for lineno, line in enumerate(BUILD_FILE.read_text(encoding="utf-8").splitlines(), 1):
        if PROD_DEP_RE.match(line):
            problems.append(
                f"C2 {BUILD_FILE.relative_to(ROOT)}:{lineno}: {line.strip()}"
                "（契约面不允许生产依赖）"
            )
    return problems


def strip_comments(text: str) -> str:
    """注释里提到框架注解是说明，不是实现；只检查真正的代码。"""
    return COMMENT_LINE_RE.sub("", COMMENT_BLOCK_RE.sub("", text))


def check_shapes(files: list[pathlib.Path]) -> list[str]:
    problems = []
    for path in files:
        code = strip_comments(path.read_text(encoding="utf-8"))
        for shape in FORBIDDEN_SHAPES:
            if shape in code:
                rel = path.relative_to(ROOT)
                problems.append(f"C3 {rel}: 出现 {shape}")
    return problems


def main() -> int:
    files = contract_sources()
    if not files:
        print(f"✗ 契约层纯度：找不到 {SRC.relative_to(ROOT)} 下的源码")
        return 1

    problems = check_imports(files) + check_build() + check_shapes(files)
    if problems:
        print("✗ 契约层纯度守卫失败：")
        for problem in problems:
            print("   " + problem)
        print("  契约面只允许纯 JDK 的 API / SPI / 不可变载荷；框架与持久化形状留在实现模块。")
        return 1

    print(f"✓ 契约层纯度：{len(files)} 个文件，导入根 / 构建依赖 / 框架形状均为 0")
    return 0


if __name__ == "__main__":
    sys.exit(main())
