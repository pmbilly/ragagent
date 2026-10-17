#!/usr/bin/env python3
"""版本一致性守卫：版本目录 / README / Dockerfile / 模块构建文件不得漂移。

判据（硬零）：
  V1 `libs.versions.toml` 的 Spring Boot 与 README 的 badge/技术栈大版本一致；
  V2 Boot 3.5 对应 springdoc 2.8.x（Boot 4 才用 springdoc 3.x）；
  V3 `mybatis-plus-jsqlparser` 必须与 starter 共用 `mybatis-plus` 版本引用；
  V4 模块构建文件不得自行声明 `spring-boot-dependencies`（BOM 只在 convention）；
  V5 Dockerfile 的 Gradle 基础镜像大版本与 wrapper distribution 一致。

红态探针：把 README badge 的 3.5 改成 3.4，本脚本必须非零退出；跑完恢复。
"""

import pathlib
import re
import sys
import tomllib

ROOT = pathlib.Path(__file__).resolve().parent.parent
CATALOG = ROOT / "gradle" / "libs.versions.toml"
README = ROOT / "README.md"
DOCKERFILE = ROOT / "Dockerfile"
WRAPPER = ROOT / "gradle" / "wrapper" / "gradle-wrapper.properties"


def main() -> int:
    data = tomllib.loads(CATALOG.read_text(encoding="utf-8"))
    versions = data["versions"]
    boot = versions["spring-boot"]
    boot_mm = ".".join(boot.split(".")[:2])
    springdoc = versions["springdoc"]
    readme = README.read_text(encoding="utf-8")
    problems = []

    # V1 README 的大版本与目录一致（badge 用 %20，正文用空格；两处都检查）
    badges = re.findall(r"Spring%20Boot-(\d+\.\d+)", readme)
    bodies = re.findall(r"Spring Boot (\d+\.\d+)", readme)
    if not badges and not bodies:
        problems.append(f"V1 README 未体现 Spring Boot {boot_mm}（目录为 {boot}）")
    for found in badges + bodies:
        if found != boot_mm:
            problems.append(f"V1 README 写的是 Spring Boot {found}，目录为 {boot}")

    # V2 Boot 与 springdoc 对应线
    if boot_mm == "4.1" and not springdoc.startswith("3.1."):
        problems.append(f"V2 Boot {boot} 应配 springdoc 3.1.x，当前 {springdoc}")
    elif boot_mm == "3.5" and not springdoc.startswith("2.8."):
        problems.append(f"V2 Boot {boot} 应配 springdoc 2.8.x，当前 {springdoc}")

    # V3 jsqlparser artifact 与 starter 共用版本引用
    catalog_text = CATALOG.read_text(encoding="utf-8")
    starter = re.search(r"^mybatis-plus-starter\s*=\s*\{[^}]*version\.ref\s*=\s*\"([^\"]+)\"",
                        catalog_text, re.M)
    parser = re.search(r"^mybatis-plus-jsqlparser\s*=\s*\{[^}]*version\.ref\s*=\s*\"([^\"]+)\"",
                       catalog_text, re.M)
    if not starter or not parser or starter.group(1) != parser.group(1):
        problems.append(
            f"V3 mybatis-plus-jsqlparser 必须与 starter 共用 version.ref（"
            f"{starter.group(1) if starter else 'missing'} / {parser.group(1) if parser else 'missing'}）"
        )

    # V4 模块构建文件不得自行声明 Boot BOM
    for build_file in ROOT.glob("*/build.gradle.kts"):
        text = build_file.read_text(encoding="utf-8")
        if "org.springframework.boot:spring-boot-dependencies" in text:
            problems.append(f"V4 {build_file.relative_to(ROOT)} 自行声明了 Boot BOM")

    # V5 Dockerfile Gradle 基础镜像与 wrapper 大版本一致
    wrapper_match = re.search(r"gradle-(\d+\.\d+(?:\.\d+)?)-bin\.zip", WRAPPER.read_text(encoding="utf-8"))
    docker_match = re.search(r"^FROM\s+gradle:(\d+\.\d+)", DOCKERFILE.read_text(encoding="utf-8"), re.M)
    if not wrapper_match or not docker_match:
        problems.append("V5 无法解析 wrapper / Dockerfile 的 Gradle 版本")
    else:
        wrapper_mm = ".".join(wrapper_match.group(1).split(".")[:2])
        if wrapper_mm != docker_match.group(1):
            problems.append(
                f"V5 Dockerfile Gradle {docker_match.group(1)} ≠ wrapper {wrapper_match.group(1)}"
            )

    if problems:
        print("✗ 版本一致性守卫失败：")
        for p in problems:
            print("   " + p)
        return 1
    print(f"✓ 版本一致性：Boot {boot} / springdoc {springdoc} / wrapper 与 README、Dockerfile 一致")
    return 0


if __name__ == "__main__":
    sys.exit(main())
