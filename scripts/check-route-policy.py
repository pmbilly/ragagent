#!/usr/bin/env python3
"""路由权限覆盖守卫（C9）：每条控制器路由必须命中 RBAC 规则，或登记为公开/自服务路由。

口径：
  · 路由来自 `domains/.../*Controller.java` 的类级 `@RequestMapping` + 方法级 Mapping；
  · 规则来自 `boot/.../RbacRoutePolicies.java` 的 `addRule` / `addSystemAdminRule`；
  · 匹配按 Spring Ant 语义：`*` 一段、`**` 任意段；路径变量归一为 `*`，`{*slug}` 归一为 `**`；
  · 未命中规则的路由必须出现在 `scripts/route-policy.baseline.json`（写明公开/所有权理由）；
  · 基线里已不再未命中的条目、以及不命中任何路由的“幽灵规则”都会判失败。

用法：
  python3 scripts/check-route-policy.py            # 检查
  python3 scripts/check-route-policy.py --map      # 重新生成 docs/architecture/route-policy-map.md
"""

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DOMAINS = ROOT / "domains/src/main/java"
POLICIES = ROOT / "boot/src/main/java/com/ragagent/config/RbacRoutePolicies.java"
BASELINE = ROOT / "scripts/route-policy.baseline.json"
MAP = ROOT / "docs/architecture/route-policy-map.md"

ANN = re.compile(r"@(RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\b")
VERBS = {
    "GetMapping": "GET",
    "PostMapping": "POST",
    "PutMapping": "PUT",
    "DeleteMapping": "DELETE",
    "PatchMapping": "PATCH",
}
COMMENT = re.compile(r"/\*.*?\*/|//[^\n]*", re.S)


def strip_comments(text: str) -> str:
    return COMMENT.sub("", text)


def annotation_args(text: str, pos: int) -> str:
    start = text.find("(", pos)
    if start < 0:
        return ""
    depth = 1
    i = start + 1
    in_string = False
    escaped = False
    while i < len(text) and depth:
        ch = text[i]
        if in_string:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
        else:
            if ch == '"':
                in_string = True
            elif ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
        i += 1
    return text[start + 1:i - 1]


def split_top(text: str) -> list[str]:
    parts = []
    depth = 0
    start = 0
    in_string = False
    escaped = False
    for i, ch in enumerate(text):
        if in_string:
            if escaped:
                escaped = False
            elif ch == "\\":
                escaped = True
            elif ch == '"':
                in_string = False
        else:
            if ch == '"':
                in_string = True
            elif ch in "({[":
                depth += 1
            elif ch in ")}]":
                depth -= 1
            elif ch == "," and depth == 0:
                parts.append(text[start:i])
                start = i + 1
    parts.append(text[start:])
    return [part.strip() for part in parts if part.strip()]


def annotation_paths(args: str) -> list[str]:
    tokens = split_top(args)
    for token in tokens:
        match = re.match(r"(?:value|path)\s*=\s*(.*)$", token, re.S)
        if match:
            return re.findall(r'"([^"]*)"', match.group(1))
    for token in tokens:
        if (token.startswith('"') or token.startswith("{")) and not re.match(
                r"(method|consumes|produces|headers|params)\s*=", token):
            return re.findall(r'"([^"]*)"', token)
    return [""]


def annotation_methods(args: str) -> list[str]:
    match = re.search(r"method\s*=\s*\{?([^}]*)\}?", args)
    if not match:
        return ["*"]
    found = re.findall(r"RequestMethod\.(\w+)", match.group(1))
    return found or ["*"]


def parse_routes() -> list[dict]:
    routes = []
    for path in sorted(DOMAINS.rglob("*Controller.java")):
        text = strip_comments(path.read_text(encoding="utf-8"))
        class_at = text.find("class ")
        base = [""]
        for match in ANN.finditer(text[:class_at] if class_at > 0 else text):
            if match.group(1) == "RequestMapping":
                base = annotation_paths(annotation_args(text, match.start())) or [""]
                break
        for match in ANN.finditer(text):
            name = match.group(1)
            if name == "RequestMapping" and 0 <= match.start() < class_at:
                continue
            args = annotation_args(text, match.start())
            paths = annotation_paths(args)
            verbs = ["*"] if name == "RequestMapping" else [VERBS[name]]
            if name == "RequestMapping":
                verbs = annotation_methods(args)
            for root in base:
                for suffix in paths:
                    if not suffix.startswith("/"):
                        suffix = "/" + suffix
                    full = (root.rstrip("/") + "/" + suffix.lstrip("/")) if root else suffix
                    full = re.sub(r"/+", "/", full)
                    for verb in verbs:
                        routes.append({
                            "method": verb,
                            "path": full,
                            "file": path.name,
                            "controller": path.stem,
                        })
    seen = set()
    unique = []
    for route in routes:
        key = (route["method"], route["path"])
        if key in seen:
            continue
        seen.add(key)
        unique.append(route)
    return unique


def parse_rules() -> list[dict]:
    text = POLICIES.read_text(encoding="utf-8")
    rules = []
    for match in re.finditer(
            r'addRule\("(\w+)",\s*"([^"]+)",\s*TenantRole\.(\w+),\s*(true|false)\)', text):
        rules.append({
            "method": match.group(1),
            "pattern": match.group(2),
            "role": match.group(3),
            "orSystemAdmin": match.group(4) == "true",
        })
    for match in re.finditer(r'addSystemAdminRule\("(\w+)",\s*"([^"]+)"', text):
        rules.append({
            "method": match.group(1),
            "pattern": match.group(2),
            "role": "SYSTEM_ADMIN",
            "orSystemAdmin": False,
        })
    for match in re.finditer(r'addPublicRule\("(\w+)",\s*"([^"]+)"', text):
        rules.append({
            "method": match.group(1),
            "pattern": match.group(2),
            "role": "PUBLIC",
            "orSystemAdmin": False,
        })
    return rules


def normalize_route(path: str) -> str:
    path = re.sub(r"\{[^}]*\*[^}]*\}", "**", path)
    path = re.sub(r"\{[^}]+\}", "*", path)
    return path


def segment_match(pattern: str, path: str) -> bool:
    pat = pattern.strip("/").split("/") if pattern.strip("/") else []
    seg = path.strip("/").split("/") if path.strip("/") else []

    def walk(i: int, j: int) -> bool:
        if i == len(pat):
            return j == len(seg)
        if pat[i] == "**":
            return walk(i + 1, j) or (j < len(seg) and walk(i, j + 1))
        if j >= len(seg):
            return False
        return (pat[i] == "*" or pat[i] == seg[j]) and walk(i + 1, j + 1)

    return walk(0, 0)


def matches(route: dict, rule: dict) -> bool:
    methods = {route["method"]}
    if route["method"] == "HEAD":
        methods.add("GET")
    if rule["method"] not in methods and route["method"] != "*":
        return False
    return segment_match(rule["pattern"], normalize_route(route["path"]))


def load_baseline() -> dict[str, str]:
    if not BASELINE.exists():
        return {}
    data = json.loads(BASELINE.read_text(encoding="utf-8"))
    return data.get("unguarded", {})


def key(route: dict) -> str:
    return f'{route["method"]} {route["path"]}'


def classify(route: dict) -> str | None:
    path = route["path"]
    public_auth = {
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/auth/auto-setup",
        "/api/v1/auth/invitations/lookup",
        "/api/v1/auth/register-by-invite",
        "/api/v1/auth/config",
        "/api/v1/auth/validate",
        "/api/v1/auth/refresh",
        "/api/v1/auth/oidc/config",
        "/api/v1/auth/oidc/url",
        "/api/v1/auth/oidc/start",
        "/api/v1/auth/oidc/callback",
    }
    if path in public_auth:
        return "public auth flow：登录/OIDC/刷新/校验由认证域自身处理"
    if path.startswith("/api/v1/auth/"):
        return "authenticated self-service：AuthFilter 要求有效会话，角色下限不适用"
    if path.startswith("/api/v1/embed/"):
        return "公开 embed 渠道：channel token 在控制器内校验"
    if path.startswith("/api/v1/im/callback/"):
        return "IM webhook：渠道签名在适配器内校验"
    if path == "/api/v1/mcp-oauth/callback":
        return "MCP OAuth 回调：state 在 OAuth 流程内校验"
    if path == "/api/v1/me/invitations/accept-by-token":
        return "邀请 token 自鉴权：公开接受流程"
    if path.startswith("/api/v1/me/invitations"):
        return "当前用户自服务：身份来自会话上下文，不按租户角色门槛"
    if path in ("/api/v1/tenants", "/api/v1/tenants/"):
        return "登录后租户自助目录/创建：tenantless 场景，角色下限不适用"
    if path.startswith("/api/v1/messages/") or path.startswith("/api/v1/sessions/"):
        return "会话/消息所有权守卫在控制器或服务内，角色门槛不适用"
    if path.startswith("/files") or path.startswith("/r/") or path.startswith("/api/v1/files/"):
        return "签名文件 URL / 公开文件代理，凭签名而非租户角色"
    if route["method"] == "HEAD":
        return "HEAD 是 GET 的自动派生；RbacInterceptor 已按 GET 规则兜底"
    return None


def check() -> int:
    routes = parse_routes()
    rules = parse_rules()
    baseline = load_baseline()
    problems = []
    used_baseline = set()
    protected = 0
    public = 0

    for route in routes:
        hit = next((rule for rule in rules if matches(route, rule)), None)
        if hit:
            if hit["role"] == "PUBLIC":
                public += 1
            else:
                protected += 1
            if key(route) in baseline:
                problems.append(f"基线已过期（路由已登记）：{key(route)}")
            continue
        reason = baseline.get(key(route))
        if reason:
            public += 1
            used_baseline.add(key(route))
        else:
            suggested = classify(route)
            if suggested:
                problems.append(f"未登记公开路由：{key(route)}（{route['controller']}）"
                                f" → 可登记理由：{suggested}")
            else:
                problems.append(f"未覆盖路由：{key(route)}（{route['controller']}）")

    stale = sorted(set(baseline) - used_baseline)
    if stale:
        problems.append("基线条目已不再未命中（请删除）：" + ", ".join(stale[:10])
                        + (" …" if len(stale) > 10 else ""))

    ghosts = []
    for rule in rules:
        if not any(matches(route, rule) for route in routes):
            ghosts.append(f'{rule["method"]} {rule["pattern"]}')
    if ghosts:
        problems.append("幽灵规则（不命中任何路由）：" + ", ".join(ghosts[:10])
                        + (" …" if len(ghosts) > 10 else ""))

    expected_map = build_map(routes, rules, baseline)
    if not MAP.exists() or MAP.read_text(encoding="utf-8") != expected_map:
        problems.append("路由对照表缺失或过期：运行 python3 scripts/check-route-policy.py --map")

    if problems:
        print(f"✗ 路由权限覆盖：{len(problems)} 处问题（路由 {len(routes)} / 规则 {len(rules)}）")
        for problem in problems:
            print("   " + problem)
        return 1
    print(f"✓ 路由权限覆盖：{len(routes)} 条路由 / {len(rules)} 条规则；"
          f"受保护 {protected}，登记公开/自服务 {public}")
    return 0


def build_map(routes: list[dict], rules: list[dict], baseline: dict[str, str]) -> str:
    lines = [
        "# 路由 → RBAC 规则对照表",
        "",
        "> 由 `scripts/check-route-policy.py --map` 从控制器注解与 `RbacRoutePolicies` 生成，勿手改。",
        "> 角色只表达**下限**；所有权/API-Key/签名等守卫在控制器或服务内，见 `baseline` 理由。",
        "",
        "| 方法 | 路由 | 角色下限 / 状态 | 控制器 |",
        "|---|---|---|---|",
    ]
    for route in sorted(routes, key=lambda r: (r["path"], r["method"])):
        hit = next((rule for rule in rules if matches(route, rule)), None)
        if hit:
            role = hit["role"]
            if hit["orSystemAdmin"]:
                role += "（或 system_admin）"
        else:
            reason = baseline.get(key(route), "")
            role = "public/self-service" + (f"：{reason}" if reason else "")
        lines.append(
            f'| {route["method"]} | `{route["path"]}` | {role} | `{route["controller"]}` |'
        )
    return "\n".join(lines) + "\n"


def render_map() -> int:
    routes = parse_routes()
    rules = parse_rules()
    baseline = load_baseline()
    MAP.write_text(build_map(routes, rules, baseline), encoding="utf-8")
    print(f"✓ 已生成 {MAP.relative_to(ROOT)}（{len(routes)} 条路由）")
    return 0


if __name__ == "__main__":
    if "--map" in sys.argv:
        sys.exit(render_map())
    sys.exit(check())
