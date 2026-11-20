#!/usr/bin/env python3
"""分包重构基线：端点 / 装配面 / 跨域引用三份清单。

用途：纯包移动（P2）与类职责归位（P3）的前后对拍。三份清单的 diff 必须为空，
或差异逐条可解释——它们替代界面录制，覆盖"用户可见"的真正来源：

- 端点：控制器类 + 注解 + 路径片段。前端契约的第一道面。
- 装配面：`@Mapper` 接口、`@Configuration` 类、`@Aspect` 切点里的包路径、
  `AutoConfiguration.imports` 的类全名、`logging.level` 键。搬家后
  "Bean 扫不到 / 切点失效 / 日志静默变少"全部在这一份里暴露。
- 跨域引用：domains 内 A 域引用 B 域的哪个子包、涉及多少文件。
  `api` 方向允许，`repository` / `mapper` / `domain` / `service` / `controller`
  方向只许减。

用法：
    python3 scripts/gen-package-baseline.py --write   # 生成基线
    python3 scripts/gen-package-baseline.py           # 校验（P1 起进闸门）

基线文件放在 `scripts/` 下，与其它守卫的 baseline 同惯例。
"""

import io
import json
import pathlib
import re
import sys

import _source_roots as _sr

ENDPOINTS = _sr.REPO / "scripts/endpoint-inventory.json"
BEANS = _sr.REPO / "scripts/bean-inventory.json"
CROSS = _sr.REPO / "scripts/cross-domain-imports.json"

COMMENT = re.compile(r"/\*.*?\*/|//[^\n]*", re.S)
CLASS_DECL = re.compile(r"^\s*(?:public\s+|final\s+|abstract\s+)*(?:class|interface|enum|record)\s+(\w+)", re.M)
MAPPING = re.compile(r"@(RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\b")
MAPPING_ARG = re.compile(r"@(?:RequestMapping|GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\s*\(([^)]*)\)")
MAPPER = re.compile(r"@Mapper\b")
CONFIG = re.compile(r"@Configuration\b")
ASPECT = re.compile(r"@Aspect\b")
POINTCUT = re.compile(r"@(?:Pointcut|Around|Before|After|AfterReturning|AfterThrowing)\s*\(([^;]*?)\)", re.S)
CPSCAN = re.compile(r"@ConfigurationPropertiesScan\s*\(([^)]*)\)", re.S)
MSCAN = re.compile(r"@MapperScan\s*\(([^)]*)\)", re.S)
PKG = re.compile(r"^\s*package\s+([\w.]+);", re.M)
IMPORT = re.compile(r"^\s*import\s+(?:static\s+)?com\.ragagent\.([a-z0-9]+)\.([\w.]*);", re.M)


def strip_comments(text):
	return COMMENT.sub("", text)


def rel(path):
	return str(path.relative_to(_sr.REPO))


def java_files():
	return [(p, strip_comments(p.read_text(encoding="utf-8", errors="replace")))
	        for p in _sr.all_java_files()]


def build_endpoints(files):
	out = []
	for path, text in files:
		if not path.name.endswith("Controller.java"):
			continue
		pkg = PKG.search(text)
		cls = CLASS_DECL.search(text)
		holder = (pkg.group(1) + "." if pkg else "") + (cls.group(1) if cls else path.stem)
		seen = {}
		for m in MAPPING.finditer(text):
			args = MAPPING_ARG.search(text, m.start())
			arg = args.group(1).strip() if args else ""
			key = (m.group(1), arg)
			seen[key] = seen.get(key, 0) + 1
			out.append({
				"holder": holder,
				"annotation": m.group(1),
				"args": arg,
				"seq": seen[key],
			})
	return sorted(out, key=lambda x: (x["holder"], x["annotation"], x["args"], x["seq"]))


def build_beans(files):
	mappers, configs, aspects = [], [], []
	cp_scan, mapper_scan = [], []
	for path, text in files:
		pkg = PKG.search(text)
		cls = CLASS_DECL.search(text)
		if not cls:
			continue
		fqn = (pkg.group(1) + "." if pkg else "") + cls.group(1)
		if MAPPER.search(text):
			mappers.append(fqn)
		if CONFIG.search(text):
			configs.append(fqn)
		if ASPECT.search(text):
			pointcuts = sorted({re.sub(r"\s+", " ", m.group(1).strip()) for m in POINTCUT.finditer(text)})
			aspects.append({"class": fqn, "pointcuts": pointcuts})
		# 两个精确包名单：搬家后类落在名单外=配置静默不绑定 / Mapper 静默不注册
		for m in CPSCAN.finditer(text):
			cp_scan.extend(re.findall(r'"([^"]+)"', m.group(1)))
		for m in MSCAN.finditer(text):
			mapper_scan.extend(re.findall(r'"([^"]+)"', m.group(1)))

	imports, factories, logging = [], [], []
	for _, mod, root in _sr.java_roots(("main",)):
		res = _sr.REPO / mod / "src/main/resources"
		if not res.is_dir():
			continue
		for f in sorted(res.rglob("AutoConfiguration.imports")):
			for line in f.read_text(encoding="utf-8", errors="replace").splitlines():
				line = line.strip()
				if line and not line.startswith("#"):
					imports.append(line)
		for f in sorted(res.rglob("spring.factories")):
			factories.append({"file": rel(f), "lines": [
				l.strip() for l in f.read_text(encoding="utf-8", errors="replace").splitlines()
				if l.strip() and not l.strip().startswith("#")
			]})
		for f in sorted(list(res.rglob("*.yml")) + list(res.rglob("*.yaml")) + list(res.rglob("*.properties"))):
			if f.suffix == ".properties":
				for line in f.read_text(encoding="utf-8", errors="replace").splitlines():
					if line.strip().startswith("logging.level."):
						logging.append("%s :: %s" % (rel(f), line.strip()))
				continue
			in_log = False
			for line in f.read_text(encoding="utf-8", errors="replace").splitlines():
				stripped = line.strip()
				if not stripped or stripped.startswith("#"):
					continue
				indent = len(line) - len(line.lstrip())
				if indent == 0:
					in_log = stripped.startswith("logging:")
					continue
				if in_log and stripped not in ("level:",):
					logging.append("%s :: %s" % (rel(f), stripped))

	return {
		"mappers": sorted(set(mappers)),
		"configurations": sorted(set(configs)),
		"aspects": sorted(aspects, key=lambda x: x["class"]),
		"mapperScan": sorted(set(mapper_scan)),
		"configurationPropertiesScan": sorted(set(cp_scan)),
		"autoConfigurationImports": sorted(set(imports)),
		"springFactories": factories,
		"loggingLevels": sorted(set(logging)),
	}


def build_cross(files):
	domains = sorted([p.name for p in (_sr.REPO / "app/src/main/java/com/ragagent").iterdir() if p.is_dir()])
	domset = set(domains)
	edges = {}
	contract = {}
	for path, text in files:
		if "/domains/" not in str(path):
			continue
		parts = path.relative_to(_sr.REPO / "app/src/main/java/com/ragagent").parts
		if len(parts) < 2 or parts[0] not in domset:
			continue
		src = parts[0]
		for m in IMPORT.finditer(text):
			dst, sub = m.group(1), m.group(2)
			if dst == "contract":
				contract[src] = contract.get(src, 0) + 1
				continue
			if dst not in domset or dst == src:
				continue
			top = sub.split(".")[0] if sub else ""
			key = (src, dst, top)
			edges[key] = edges.get(key, 0) + 1

	return {
		"domains": domains,
		"edges": [{"from": k[0], "to": k[1], "target": k[2], "imports": v}
		          for k, v in sorted(edges.items())],
		"contractImports": [{"from": k, "imports": v} for k, v in sorted(contract.items())],
	}


def load(path):
	if not path.exists():
		return None
	with io.open(path, "r", encoding="utf-8") as f:
		return json.load(f)


def write(path, payload):
	with io.open(path, "w", encoding="utf-8") as f:
		json.dump(payload, f, ensure_ascii=False, indent=2, sort_keys=False)
		f.write("\n")
	count = payload.get("count") or len(payload.get("edges") or []) or len(payload.get("mappers") or [])
	print("写出 %s（%d 项）" % (rel(path), count))


def diff(name, old, new, keyfield):
	if old is None:
		print("✗ %s 缺基线，先跑 --write" % name)
		return 1
	old_set = {json.dumps(x, ensure_ascii=False, sort_keys=True) for x in old[keyfield]}
	new_set = {json.dumps(x, ensure_ascii=False, sort_keys=True) for x in new[keyfield]}
	added, removed = sorted(new_set - old_set), sorted(old_set - new_set)
	if not added and not removed:
		print("✓ %s 无变化（%d 项）" % (name, len(new_set)))
		return 0
	print("✗ %s 有差异：新增 %d / 消失 %d" % (name, len(added), len(removed)))
	for x in added[:20]:
		print("    + " + x)
	for x in removed[:20]:
		print("    - " + x)
	return 1


def main():
	write_mode = "--write" in sys.argv
	files = java_files()
	endpoints = build_endpoints(files)
	beans = build_beans(files)
	cross = build_cross(files)

	payloads = [
		(ENDPOINTS, {"generated": "2026-11-19", "count": len(endpoints), "endpoints": endpoints}, "endpoints"),
		(BEANS, dict({"generated": "2026-11-19"}, **beans), "mappers"),
		(CROSS, dict({"generated": "2026-11-19"}, **cross), "edges"),
	]

	if write_mode:
		for path, payload, _ in payloads:
			write(path, payload)
		return 0

	rc = 0
	for path, payload, keyfield in payloads:
		rc |= diff(rel(path), load(path), payload, keyfield)
	return rc


if __name__ == "__main__":
	sys.exit(main())
