#!/usr/bin/env python3
"""契约层纯度守卫（C1/C2/C3；2026-10-15 上线；判据＝整改方案 §5.1/§5.2 + HANDOFF §12.0）。

**为什么要有它**：终态 `contracts` ＝「纯 JDK：公开 API / SPI / 不可变载荷」。本仓 `common`
是它的**过渡形态**，但 2026-10-15 实测已有：

  · 14 个持久化实体（`@TableName`）+ 12 个域侧 TypeHandler（另 2 个 `platform/persistence/`
    下的通用基础设施 handler 属合法）；
  · 一批重框架依赖（Spring MVC/JDBC/Redis/Boot、MyBatis、Servlet、OTel、…）；
  · build 脚本声明同样如此（方案问题表第 66 行点名的形态）。

没有门禁时，"清域间边"的最省事手段就是**把实体上提 L1**——A③ 全程实测：越提越多
（14 实体 ≈ −60 类）。这条路能达"边数"指标，却**直接违背 §5.1 的 `contracts` 判据**
（见 HANDOFF §12.0 欠债登记）⇒ 必须把通道冻结，只许减。

**三条棘轮（均只许减；`--write` 重登记并声明责任）**：

  C1 受控导入根：`common` 主源码里 import 的**非白名单根**，按「根 → 文件集合」登记。
     新根、或新文件进入某受控根 ⇒ 红；文件离开 ⇒ 提示 `--write` 收紧。
  C2 持久化形状：`common` 主源码里含 `@TableName` 的实体、与继承/实现 TypeHandler 的类型。
     `platform/persistence/` 下的通用基础设施 handler **不计**（属 `platform` 正当内容）。
  C3 build 依赖清单：`common/build.gradle.kts` 的 `api(...)` / `implementation(...)` 坐标集合。
     新增坐标 ⇒ 红（要加必须在本脚本说明理由并 `--write`，即"加依赖要签字的显式动作"）。
  C4 载荷落点：`common/.../com/ragagent/common/**` 下的**载荷型文件**
     （`*View` / `*Facts` / `*Payload` / `*Response` / `*Port`）——终态应落 **`:contract`** 或回各域
     （HANDOFF §12.0/§12.5 G1）；新增 ⇒ 红，减少 ⇒ 提示 `--write` 收紧。
     （`:common` 已 `api(project(":contract"))`，端口/视图可落 `:contract` 并被 common 与各域共用。）
     两个**已登记白名单**（不计入本项、各有理由）：`PLATFORM_FACE`（带 servlet/Spring/MyBatis ⇒ 归
     未来 platform 模块）与 `PENDING_DEJACKSON`（载荷形状用 JsonNode ⇒ 待"去 Jackson"专项后入 :contract）。

白名单（= 允许的根；改这里等于改判据，需同步 HANDOFF §12.0）：
  `java.` / `javax.` / `com.ragagent.` / `com.fasterxml.jackson`（序列化）/ `org.slf4j`（日志）
"""

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
BASELINE = ROOT / "scripts" / "contracts-purity.baseline.json"
COMMON_SRC = ROOT / "common" / "src" / "main" / "java"
BUILD_FILE = ROOT / "common" / "build.gradle.kts"

# 允许的导入根（判据白名单）
ALLOWED = ("java.", "javax.", "com.ragagent.", "com.fasterxml.jackson", "org.slf4j")

# `platform/persistence/` 下的通用基础设施 handler 属 platform 正当内容，不计入 C2
INFRA_HANDLER_DIR = "com/ragagent/platform/persistence/"

# 根归一化：粗粒度、稳定（棘轮只看变化，不看粒度）
ROOT_RULES = (
    ("org.springframework", 2),
    ("org.slf4j", 2),
    ("com.baomidou", 2),
    ("jakarta.", 3),
    ("org.apache", 2),
    ("io.opentelemetry", 2),
    ("net.sf", 2),
    ("com.google", 2),
)


def normalize_root(pkg: str) -> str:
    for prefix, segs in ROOT_RULES:
        if pkg.startswith(prefix):
            return ".".join(pkg.split(".")[:segs])
    return ".".join(pkg.split(".")[:2])


def scan_imports() -> dict[str, list[str]]:
    """C1：受控根 → 文件集合（相对 common/src/main/java）。"""
    out: dict[str, set[str]] = {}
    for f in COMMON_SRC.rglob("*.java"):
        rel = str(f.relative_to(COMMON_SRC))
        s = f.read_text(encoding="utf-8")
        for m in re.finditer(r"^import (?:static )?([\w.]+);", s, re.M):
            pkg = m.group(1)
            if pkg.startswith(ALLOWED) or pkg == "javax":
                continue
            out.setdefault(normalize_root(pkg), set()).add(rel)
    return {k: sorted(v) for k, v in out.items()}


def strip_comments(src: str) -> str:
    """剥离注释：C2 只认真代码里的注解（javadoc 里提 @TableName 不算持久化形状）。"""
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    return re.sub(r"//[^\n]*", "", src)


def scan_persistence() -> dict[str, list[str]]:
    """C2：持久化形状（实体 + 域侧 TypeHandler）。"""
    entities, handlers = [], []
    for f in COMMON_SRC.rglob("*.java"):
        rel = str(f.relative_to(COMMON_SRC))
        s = strip_comments(f.read_text(encoding="utf-8"))
        if "@TableName" in s:
            entities.append(rel)
        elif re.search(r"(BaseTypeHandler|implements TypeHandler|TypeHandler<)", s):
            if not rel.startswith(INFRA_HANDLER_DIR):
                handlers.append(rel)
    return {"entities": sorted(entities), "handlers": sorted(handlers)}


def scan_build_deps() -> list[str]:
    """C3：common/build.gradle.kts 的依赖坐标集合。"""
    if not BUILD_FILE.exists():
        return []
    s = BUILD_FILE.read_text(encoding="utf-8")
    pat = re.compile(r'''^\s*(?:api|implementation|compileOnly|runtimeOnly)\(["\']([^"\']+)["\']\)''', re.M)
    return sorted({m.group(1) for m in pat.finditer(s)})


PAYLOAD_SUFFIXES = ("View", "Facts", "Payload", "Response", "Port")

# ── 已登记白名单（两类，均为"有据的归属"，不计入 C4 的"待归位载荷"）──
# ① platform 面：带框架类型（servlet / Spring / MyBatis），**永远进不了零依赖的 :contract**，
#    归属未来的 `platform` 模块（§12.5 提取顺序里 platform 先于三 runtime）。
PLATFORM_FACE = {
    "com/ragagent/common/apikey/ApiKeyAuthPort.java":
        "带 jakarta.servlet 请求/响应类型 ⇒ platform 面",
    "com/ragagent/common/knowledge/KnowledgeCatalogPort.java":
        "带 MyBatis-Plus Page ⇒ platform 面（分页属技术适配）",
    "com/ragagent/common/session/embed/EmbedSessionPort.java":
        "带 jakarta.servlet + Spring ResponseEntity ⇒ platform 面",
        "com/ragagent/common/storage/fileserve/GlobalFileServicePort.java":
        "会话装配面的进程级文件服务端口：**签名含 L1 `WritableFileContentService`** ⇒ 契约装不下"
        "（与该组件的 servlet 兄弟同族）；未来路径：把纯 JDK 的 `FileContentService`/"
        "`WritableFileContentService` 上提契约、并把 `FileTransport` 的 servlet 面切出去后可入 contract.storage",
    "com/ragagent/common/storage/fileserve/ProviderFileStorePort.java":
        "provider 文件存储端口：**方法面含 `FileTransport`**，而后者带 jakarta.servlet 请求/响应类型 "
        "⇒ 零依赖契约装不下；随 `GlobalFileServicePort` 的未来路径一并处置",
    "com/ragagent/common/session/qa/SessionQaPort.java":
        "会话 QA 面端口：带 `platform.event.EventBus`（platform 类型）与平台面载荷 "
        "`SessionView`/`MessageView`（见其登记）⇒ 零依赖契约装不下",
"com/ragagent/common/storage/fileserve/FileProxyPort.java":
        "带 jakarta.servlet 请求/响应类型 ⇒ platform 面",
    "com/ragagent/common/session/embed/SessionView.java":
        "会话 QA 面载荷与装配面读写形状（platform 面端口载荷）；JSON 等价由 session-*.json / "
        "emb-pub-sessions-*.json 金片钉住",
    "com/ragagent/common/session/embed/MessageView.java":
        "embed 面 messages/load 响应形状 + 会话 QA 面消息载荷（platform 面端口载荷）；"
        "JSON 逐字节等价由 emb-pub-messages-load*.json 金片钉住",
    "com/ragagent/common/session/embed/MessageSuggestionView.java":
        "embed 面端口（EmbedSessionPort）的**响应线格式形状**，随该端口同属 platform 面；"
        "JSON 逐字节等价由 emb-pub-suggestions-get.json 金片钉住",
}

# ② 待"去 Jackson"专项：载荷形状本身用了 JsonNode（`ChunkFacts` / `KnowledgeBaseView` /
#    `KnowledgeDocumentFacts`）⇒ :contract 零依赖装不下；与 B258 登记的"`AppError` 去 Jackson"
#    **同一课题**（改造口径与风险见 HANDOFF §12.5）；改造完成即整批入 :contract。
PENDING_DEJACKSON = {
}

# ② Jackson 语义必要（G1-b 收口新增，2026-10-15）：**载荷本身不依赖框架类型**，
# 但其线格式依赖 Jackson 注解语义（零依赖 :contract 无法表达）——硬搬会导致
# **抽象泄漏**（契约的字节隐式依赖 L1 类的注解）⇒ 诚实留 L1 并在此登记依据。
# 与 ① 的区别：① 是"装了框架类型搬不动"，② 是"能装但语义装不下"。
PENDING_DEJACKSON_EXTRA = {
    "com/ragagent/common/session/qa/QaRequestPayload.java":
        "对话入参载荷：含 `ObjectNode`（Jackson 树）且依赖 L1 值类型 `MessageAttachment` "
        "⇒ 随 **session 域批次**（值类型回迁）一并去 Jackson 后入 contract.session",
    "com/ragagent/common/session/PipelineMessageView.java":
        "管线消息视图：依赖 L1 `retrieval/SearchResult` ⇒ 随 **检索域批次**（SearchResult 去 Jackson/归位）"
        "一并处置后入契约",
}

JACKSON_SEMANTICS_REQUIRED = {
    "com/ragagent/common/auth/AdminUserView.java":
        "字段 preferences 是 L1 类型 `UserPreferences`，其 `@JsonInclude(NON_NULL)` **决定出面字节**"
        "（null 字段省略：reg-prefs-*.json 等金片钉住）、`@JsonIgnoreProperties(ignoreUnknown=true)` "
        "**决定入面容错**（客户端 PATCH 可带未知键）⇒ 零依赖契约装不下这两种语义；"
        "若改 `Object preferences` 可硬搬，但契约线格式将隐式依赖 L1 类注解＝抽象泄漏 ⇒ 留 L1。"
        "未来路径：线格式改恒出 null（或客户端面改 Map 形状）后可入 contract.auth",
    "com/ragagent/common/auth/AdminUserPort.java":
        "管理面端口（读/建/晋升/撤权/重置密码/密码策略），与 AdminUserView 同族：视图留 L1 ⇒ 端口随留"
        "（契约接口不得引用 L1 类型）；随 AdminUserView 的未来路径一并入 contract.auth",
}

REGISTERED = {**PLATFORM_FACE, **PENDING_DEJACKSON, **PENDING_DEJACKSON_EXTRA, **JACKSON_SEMANTICS_REQUIRED}


def scan_payloads() -> list[str]:
    """C4：`common`（非 platform）里的载荷型文件，**扣除已登记白名单**（platform 面 / 待去 Jackson）。"""
    base = ROOT / "common" / "src" / "main" / "java"
    root = base / "com" / "ragagent" / "common"
    out = []
    for f in root.rglob("*.java"):
        if not f.name.endswith(tuple(s + ".java" for s in PAYLOAD_SUFFIXES)):
            continue
        rel = str(f.relative_to(base))
        if rel in REGISTERED:
            continue
        out.append(rel)
    return sorted(out)


def collect() -> dict:
    return {"imports": scan_imports(), "persistence": scan_persistence(),
            "build_deps": scan_build_deps(), "payloads": scan_payloads()}


def load() -> dict:
    if not BASELINE.exists():
        return {}
    return json.loads(BASELINE.read_text(encoding="utf-8"))


def write(cur: dict) -> None:
    data = {
        "note": "契约层纯度基线（C1 受控导入根 / C2 持久化形状 / C3 build 依赖）——只许减；"
                "由 scripts/check-contracts-purity.py --write 生成。判据见 HANDOFF §12.0。",
        **cur,
    }
    BASELINE.write_text(json.dumps(data, ensure_ascii=False, indent=1, sort_keys=True) + "\n", encoding="utf-8")
    total = sum(len(v) for v in cur["imports"].values())
    print(f"✓ 基线已写入：受控导入根 {len(cur['imports'])} 个 / 共 {total} 文件，"
          f"实体 {len(cur['persistence']['entities'])}，域侧 handler {len(cur['persistence']['handlers'])}，"
          f"build 依赖 {len(cur['build_deps'])}，载荷型文件 {len(cur['payloads'])}"
          f"（登记：platform 面 {len(PLATFORM_FACE)} · 待去 Jackson {len(PENDING_DEJACKSON)} · Jackson 语义必要 {len(JACKSON_SEMANTICS_REQUIRED)}）")


def main() -> int:
    cur = collect()
    if "--write" in sys.argv:
        write(cur)
        return 0
    base = load()
    if not base:
        print("✗ 守卫失败：缺少基线（先跑 --write 生成并声明责任）")
        return 1

    problems, shrinks = [], []
    # C1
    for root, files in cur["imports"].items():
        if root not in base["imports"]:
            problems.append(f"C1 新受控导入根 `{root}`（{len(files)} 文件，如 {files[0]}）"
                            f"——`contracts` 要求纯 JDK；确需则在本脚本白名单/说明后 --write")
            continue
        new_files = sorted(set(files) - set(base["imports"][root]))
        if new_files:
            problems.append(f"C1 受控根 `{root}` 新增引入文件：{new_files[:4]}"
                            f"（共 {len(new_files)} 个）——换包/搬迁属正常，核后 --write")
        if not new_files and len(files) < len(base["imports"][root]):
            shrinks.append(f"C1 `{root}` 减少 {len(base['imports'][root]) - len(files)} 个文件")
    for root in base["imports"]:
        if root not in cur["imports"]:
            shrinks.append(f"C1 `{root}` 已完全移除")
    # C2
    for key, label in (("entities", "持久化实体（@TableName）"), ("handlers", "域侧 TypeHandler")):
        new = sorted(set(cur["persistence"][key]) - set(base["persistence"][key]))
        if new:
            problems.append(f"C2 新增{label}：{new[:4]}（共 {len(new)} 个）"
                            f"——持久化形状不得住 L1（HANDOFF §12.0 判据 1）")
        gone = sorted(set(base["persistence"][key]) - set(cur["persistence"][key]))
        if gone:
            shrinks.append(f"C2 {label} 减少 {len(gone)} 个（{gone[:3]}）")
    # C4
    if "payloads" not in base:
        problems.append("C4 基线缺少 payloads 字段（旧基线）⇒ 确认后用 --write 重建")
    # 空集是**正当终态**（全部载荷已入 contract 或按证据登记）⇒ 只按"键是否存在"判旧基线
    base_payloads = base.get("payloads") or []
    new_payloads = sorted(set(cur["payloads"]) - set(base_payloads))
    if new_payloads:
        problems.append(f"C4 common 新增载荷型文件：{new_payloads[:4]}（共 {len(new_payloads)} 个）"
                        f"——载荷应落 :contract 或回域（HANDOFF §12.5 G1）")
    gone_payloads = sorted(set(base_payloads) - set(cur["payloads"]))
    if gone_payloads:
        shrinks.append(f"C4 载荷型文件减少 {len(gone_payloads)} 个（{gone_payloads[:3]}）")
    # C3
    new_deps = sorted(set(cur["build_deps"]) - set(base["build_deps"]))
    if new_deps:
        problems.append(f"C3 common/build.gradle.kts 新增依赖：{new_deps}"
                        f"——`contracts` 要纯 JDK；确需则说明理由后 --write")
    gone_deps = sorted(set(base["build_deps"]) - set(cur["build_deps"]))
    if gone_deps:
        shrinks.append(f"C3 build 依赖减少 {len(gone_deps)} 个（{gone_deps[:3]}）")

    if problems:
        print("✗ 守卫失败：")
        for p in problems:
            print("   " + p)
        return 1
    msg = (f"✓ 契约层纯度：受控导入根 {len(cur['imports'])} 个（{sum(len(v) for v in cur['imports'].values())} 文件）"
           f"· 实体 {len(cur['persistence']['entities'])} · 域侧 handler {len(cur['persistence']['handlers'])}"
           f"· build 依赖 {len(cur['build_deps'])} · 载荷型文件 {len(cur['payloads'])}（全部冻结只许减；"
           f"另有 {len(PLATFORM_FACE)} 件 platform 面 + {len(PENDING_DEJACKSON)} 件待去 Jackson + {len(JACKSON_SEMANTICS_REQUIRED)} 件 Jackson 语义必要已登记）")
    if shrinks:
        print(msg + f"；可收紧 {len(shrinks)} 处 —— 对账后跑 --write：")
        for s in shrinks[:8]:
            print("    － " + s)
    else:
        print(msg)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
