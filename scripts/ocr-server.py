#!/usr/bin/env python3
"""本地 OCR 服务（RapidOCR，CPU）。

用法：
    python3 -m venv /tmp/ocrenv
    /tmp/ocrenv/bin/pip install rapidocr-onnxruntime
    /tmp/ocrenv/bin/python scripts/ocr-server.py 18092

接口：
    GET  /health -> {"ok": true}
    POST /ocr    -> 请求体为图片字节，返回 {"text": "...", "lines": n, "elapsedMs": n}

只做"图片 → 文字"，不依赖大模型；后端通过 knowledge.ocr.endpoint 调用。
"""

import json
import sys
import tempfile
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

from rapidocr_onnxruntime import RapidOCR

ENGINE = RapidOCR()


def _boxes(result) -> list[dict]:
    """把检测框整理成带坐标的文本块。"""
    boxes = []
    for item in result or []:
        if len(item) < 3 or not item[1] or not item[1].strip():
            continue
        xs = [point[0] for point in item[0]]
        ys = [point[1] for point in item[0]]
        boxes.append({
            "x1": min(xs), "x2": max(xs), "y1": min(ys), "y2": max(ys),
            "cx": (min(xs) + max(xs)) / 2,
            "cy": (min(ys) + max(ys)) / 2,
            "text": item[1].strip(),
        })
    return boxes


def _columns(boxes: list[dict], gap: float) -> list[list[dict]]:
    """按中心 x 把文本块聚成列：相邻中心差超过 gap 即换列。"""
    ordered = sorted(boxes, key=lambda b: b["cx"])
    columns: list[list[dict]] = []
    current: list[dict] = []
    for box in ordered:
        if current and box["cx"] - current[-1]["cx"] > gap:
            columns.append(current)
            current = []
        current.append(box)
    if current:
        columns.append(current)
    return columns


def _is_multi_column(boxes: list[dict], gap: float) -> bool:
    """同一水平带内出现不同 x 簇的文本，才按多列处理。"""
    columns = _columns(boxes, gap)
    if len(columns) < 2:
        return False
    for i in range(len(columns)):
        for j in range(i + 1, len(columns)):
            for left in columns[i]:
                for right in columns[j]:
                    overlap = min(left["y2"], right["y2"]) - max(left["y1"], right["y1"])
                    if overlap > 0.5 * min(left["y2"] - left["y1"], right["y2"] - right["y1"]):
                        return True
    return False


def recognize(data: bytes) -> str:
    """识别图片字节，按版面坐标还原列关系。

    PPT 导出的页面多为卡片/多列排版，纯按检测顺序输出会把「车名 → 价格 → 定位」
    的对应关系拍平。多列页把横跨页面的宽块（标题/页脚/总结句）单独成段，其余按列
    聚类、列内按 y 排序；单列页仍按 y 排序，避免短标题被拆成假列。
    """
    with tempfile.NamedTemporaryFile(suffix=".jpg") as tmp:
        tmp.write(data)
        tmp.flush()
        result, _ = ENGINE(tmp.name)
    boxes = _boxes(result)
    if not boxes:
        return ""

    page_width = max(b["x2"] for b in boxes)
    page_height = max(b["y2"] for b in boxes)
    gap = max(page_width * 0.05, 50)
    wide = page_width * 0.45
    top: list[dict] = []
    bottom: list[dict] = []
    normal: list[dict] = []
    for box in boxes:
        if (box["x2"] - box["x1"]) > wide:
            (top if box["cy"] < page_height * 0.25 else bottom).append(box)
        else:
            normal.append(box)

    if not _is_multi_column(normal, gap):
        return "\n".join(b["text"] for b in sorted(boxes, key=lambda b: b["y1"]))

    sections: list[str] = []
    if top:
        sections.append("\n".join(b["text"] for b in sorted(top, key=lambda b: b["y1"])))
    for column in _columns(normal, gap):
        sections.append("\n".join(b["text"] for b in sorted(column, key=lambda b: b["y1"])))
    if bottom:
        sections.append("\n".join(b["text"] for b in sorted(bottom, key=lambda b: b["y1"])))
    return "\n\n".join(section for section in sections if section)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        sys.stderr.write("[ocr] " + (fmt % args) + "\n")

    def _send_json(self, status: int, payload: dict):
        body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path == "/health":
            self._send_json(200, {"ok": True})
        else:
            self._send_json(404, {"error": "not found"})

    def do_POST(self):
        if self.path != "/ocr":
            self._send_json(404, {"error": "not found"})
            return
        length = int(self.headers.get("Content-Length") or 0)
        if length <= 0:
            self._send_json(400, {"error": "empty body"})
            return
        data = self.rfile.read(length)
        started = time.time()
        try:
            text = recognize(data)
        except Exception as exc:  # noqa: BLE001 - 服务边界统一转 500
            self._send_json(500, {"error": str(exc)})
            return
        self._send_json(200, {
            "text": text,
            "lines": text.count("\n") + 1 if text else 0,
            "elapsedMs": int((time.time() - started) * 1000),
        })


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 18092
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    print(f"ocr server listening on :{port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
