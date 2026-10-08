"""后端源码根的**单一事实来源**（多模块）。

B116 起后端拆成多个 Gradle 模块（`server` / `common` / …），守卫脚本不得再硬编码
单模块路径——否则新增模块时守卫会**静默失明**（B116 实测：拆出 :common 后
`check-package-cycles.py` 仍扫 `server/...`，R5/R6/R8 立刻少覆盖 175 个文件）。

新增模块只需在 MODULE_DIRS 里加一行。
"""

import pathlib

REPO = pathlib.Path(__file__).resolve().parent.parent

#: 参与守卫的后端 Gradle 模块目录（按依赖自底向上列，便于阅读）
MODULE_DIRS = ["server", "common"]


def java_roots(sources=("main",)):
    """返回 [(源集, 模块, <模块>/src/<源集>/java)]，只含实际存在的目录。"""
    out = []
    for m in MODULE_DIRS:
        for s in sources:
            d = REPO / m / "src" / s / "java"
            if d.is_dir():
                out.append((s, m, d))
    return out


def backend_pkg_roots(sources=("main",)):
    """返回各模块下的 `com/ragagent` 包根（守卫的"域"都挂在它下面）。"""
    return [d / "com" / "ragagent" for _, _, d in java_roots(sources)
            if (d / "com" / "ragagent").is_dir()]


def all_java_files(sources=("main",)):
    """所有后端 `.java`（跨模块合并，稳定排序）。"""
    out = []
    for _, _, d in java_roots(sources):
        out.extend(d.rglob("*.java"))
    return sorted(out)


def find_pkg_path(rel):
    """在任一模块下找 `com/ragagent/<rel>`；找不到返回 None（报错即可暴露搬家漏改）。"""
    for r in backend_pkg_roots():
        p = r / rel
        if p.exists():
            return p
    return None
