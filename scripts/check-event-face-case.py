#!/usr/bin/env python3
"""事件面 / 路由面的命名口径守卫（B93b，2026-10-08）。

背景：阶段 3 把 HTTP 的 JSON 面、B93a 把 wiki 图片标记、B93b 把事件面（SSE/Redis 流）
与事件名/响应类型值全部 camel 化，并顺带统一了 URL 路径变量与自有查询参数名。
本脚本防四类回流（历史教训：键名失配在 Java/Jackson 侧**静默**，不报错）：

  ① 事件载荷类（`event/payload/*.java`）不得再出现 snake 值的 `@JsonProperty`
     —— 也允许显式 camel（与字段同名）注解，因为字段注解承载 include/顺序语义；
  ② 事件名（`EventType`）与响应类型（`ResponseType`）的**值**不得含 `_` 或 `.`
     —— 家族前缀保留为 camel 前缀（`agentStep`），不再用点号层级；
  ③ URL 路径模板里的**路径变量**不得含 `_`（`{sessionId}`；路径变量名不进具体 URL，
     改它对外零影响；`{id}`/`{key}` 这类单词不受限）；
  ④ `@RequestParam` 的显式名不得含 `_`，除白名单（OIDC 标准参数，属外部协议）；
  ⑤ **手搓事件载荷**（不经 `event/payload` 类、直接 `map.put("键", …)` 或 helper 二参形态的
     事件生产/消费文件）里的**键字面量**不得含 `_` —— ① 只扫 `event/payload/*.java`，而这批文件正是
     B93b 记录里"机制二"漏扫过的地方（曾静默丢 `tool_call_id`；B135b2 又收尾了
     `finalContent`/`userCreatedAt`/`assistantCreatedAt` 3 键）。名单须逐条写理由。

豁免：
  · 供应商/平台线格式（`llm/chat/**`、`datasource/connector/**`、`im/feishu/**`）本脚本不扫；
  · 查询参数白名单见 QUERY_WHITELIST（每条须注明判定依据）。
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
# 多模块（B116）：event/* 与 common/llm/ResponseType 都随底座搬入了 contracts，
# 路径改成按模块查找（新增模块无需改这里）。
import _source_roots as _sr

PAYLOAD_DIR = _sr.find_pkg_path("event/payload")
EVENT_TYPE = _sr.find_pkg_path("event/EventType.java")
RESPONSE_TYPE = _sr.find_pkg_path("common/llm/ResponseType.java")

# 查询参数白名单（键 → 判定依据）。OIDC/OAuth2 标准参数，由外部协议决定，不得改名。
QUERY_WHITELIST = {
    "redirect_uri": "OIDC/OAuth2 标准参数（外部协议）",
    "error_description": "OIDC 标准参数（外部协议）",
    "response_types_supported": "OIDC 发现文档字段（外部协议）",
}

problems: list[str] = []


def check_payload_annotations() -> None:
    ann = re.compile(r'@JsonProperty\("([^"]+)"\)')
    for p in sorted(PAYLOAD_DIR.glob("*.java")):
        for i, line in enumerate(p.read_text(encoding="utf-8").splitlines(), 1):
            m = ann.search(line)
            if m and "_" in m.group(1):
                problems.append(
                    f"{p.relative_to(ROOT)}:{i} 事件载荷注解仍是 snake：{m.group(1)}"
                    "（载荷键一律 camel；字段注解值应等于 Java 字段名）"
                )


def check_enum_values() -> None:
    for path in (EVENT_TYPE, RESPONSE_TYPE):
        t = path.read_text(encoding="utf-8")
        for m in re.finditer(r'=\s*"([^"]+)"', t):
            v = m.group(1)
            if "_" in v or "." in v:
                problems.append(
                    f"{path.relative_to(ROOT)} 事件名/响应类型值含分隔符：{v}"
                    "（统一 camel、去点号，如 agent.step → agentStep）"
                )
        for m in re.finditer(r'\w+\("([^"]+)"\)', t):
            v = m.group(1)
            if "_" in v or "." in v:
                problems.append(f"{path.relative_to(ROOT)} 响应类型值含分隔符：{v}")


MAPPING = re.compile(r"@(?:Get|Post|Put|Delete|Patch|Request)Mapping\b([\s\S]*?)\)\s*(?:\{|;|public)")
PATH_VAR = re.compile(r"\{([A-Za-z_][A-Za-z0-9_]*)\}")
PATHVAR_ANN = re.compile(r'@PathVariable\(\s*(?:value\s*=\s*)?"([^"]+)"')
REQPARAM_ANN = re.compile(r'@RequestParam\(\s*(?:value\s*=\s*)?"([^"]+)"')


def check_routes() -> None:
    for _r in _sr.backend_pkg_roots():
        for p in sorted(_r.rglob("*.java")):
            rel = str(p.relative_to(ROOT))
            t = p.read_text(encoding="utf-8")
            for m in MAPPING.finditer(t):
                body = m.group(1)
                if '"' not in body:
                    continue
                for v in PATH_VAR.findall(body):
                    if "_" in v:
                        line = t[: m.start()].count("\n") + 1
                        problems.append(
                            f"{rel}:{line} 路径变量含下划线：{{{v}}}"
                            "（改 camel；路径变量名不进具体 URL，对外零影响）"
                        )
            for i, line in enumerate(t.splitlines(), 1):
                for rx, why in ((PATHVAR_ANN, "路径变量"), (REQPARAM_ANN, "查询参数")):
                    for m in rx.finditer(line):
                        name = m.group(1)
                        if "_" not in name:
                            continue
                        if why == "查询参数" and name in QUERY_WHITELIST:
                            continue
                        problems.append(f"{rel}:{i} {why}名含下划线：{name}（{why}名统一 camel）")


# ── ⑤ 手搓事件载荷的键字面量（名单逐条写理由）────────────────────────────────
# 为什么需要它：① 只覆盖 `event/payload/*.java`；SSE/进度/steer 事件还有一批"手搓 map"的生产与
# 消费文件，键写在 `put/set/get/path/containsKey/getString(...)` 或 helper 二参形态里
# （B93b 的"机制二"：`mapString(chunk.getData(), "tool_call_id")` 曾被四类键位模式全部漏掉）。
EVENT_MAP_FILES = {
    'session/controller/QaSseOrchestrator.java':
        'agentQuery/data 载荷生产侧（手工 put；B135b2 收尾 3 键）',
    'session/service/AgentStreamBridge.java':
        'SSE 事件桥（手工 put；B93b 已 camel 38 处键位，B135b2 收尾 finalContent）',
    'session/sse/StreamResponseBuilder.java':
        'SSE 响应构建 + Redis 回放的引用重建（读 SearchResult 形状）',
    'chatpipeline/PipelineProgress.java':
        '进度事件生产侧（B134 已 camel；棘轮自此覆盖）',
    'session/controller/SteerController.java':
        'steer 事件载荷生产侧（B135c 已 camel）',
    'session/service/QaSupport.java':
        'mention 载荷生产侧（B135c 已 camel）',
    'session/service/SteerSinkBridge.java':
        'mention 载荷读取/规范化侧（B135c 已 camel）',
    'im/runtime/ToolDisplay.java':
        'IM 侧读取同一批事件载荷（step.arguments/step.data）',
}
# 值面豁免（B88 决策：工具名与 schema enum 值是"各自语义"，不是字段名；§15.3 ②）——
# 它们会出现在键位调用里（如 `LABELS.get("wiki_search")`），但语义是**值**。逐条登记。
VALUE_TOKENS = {
    'im/runtime/ToolDisplay.java': {
        'data_analysis', 'data_schema', 'database_query', 'edit_sandbox_file', 'execute_skill_script',
        'get_document_content', 'get_document_info', 'get_related_documents', 'grep_chunks',
        'image_analysis', 'knowledge_graph_extract', 'knowledge_search', 'list_knowledge_chunks',
        'list_sandbox_files', 'query_knowledge_graph', 'query_understand', 'read_sandbox_file',
        'read_skill', 'search_knowledge', 'shell_exec', 'todo_write', 'web_fetch', 'web_search',
        'wiki_read_page', 'wiki_read_source_doc', 'wiki_search', 'write_sandbox_file',
    },
}

# 键位形态：① 直接调用 `x.put("k"` / `.getString(m, "k"` 等；② helper 二参形态 `foo(map, "k")`
KEY_CALL = re.compile(r'\.(?:put|putAll|set|get|path|containsKey|getString|getFloat64|textOr)\s*\(\s*"([^"]+)"')
KEY_HELPER = re.compile(r'(?<![\w.])\w+\([^;()]*,\s*"([^"]+)"\)')


def check_event_map_keys() -> None:
    for rel, why in sorted(EVENT_MAP_FILES.items()):
        path = ROOT / 'server/src/main/java/com/ragagent' / rel
        if not path.exists():
            problems.append(f'{rel} 不在预期路径（EVENT_MAP_FILES 需更新）')
            continue
        for i, line in enumerate(path.read_text(encoding='utf-8').splitlines(), 1):
            s = line.strip()
            if s.startswith('*') or s.startswith('//'):
                continue                      # 注释/javadoc 里的键名不算（如 {@code session_id}）
            allowed = VALUE_TOKENS.get(rel, set())
            for m in KEY_CALL.finditer(line):
                if '_' in m.group(1) and m.group(1) not in allowed:
                    problems.append(f'{rel}:{i} 手搓事件载荷键仍是 snake：{m.group(1)}（{why}）')


def main() -> int:
    check_payload_annotations()
    check_enum_values()
    check_routes()
    check_event_map_keys()
    if problems:
        print("✗ 事件面/路由面命名口径违例：\n")
        for x in problems:
            print("    " + x)
        print("\n  若确属外部协议（OIDC/供应商/平台），在脚本白名单里登记判定依据。")
        return 1
    print("✓ 事件载荷注解 / 事件名与响应类型值 / 路径变量 / 查询参数：全部 camel（或已登记豁免）。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
