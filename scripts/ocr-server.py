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


def recognize(data: bytes) -> list[str]:
    """识别图片字节，返回文本行。RapidOCR 对 bytes 支持不稳定，统一走临时文件。"""
    with tempfile.NamedTemporaryFile(suffix=".jpg") as tmp:
        tmp.write(data)
        tmp.flush()
        result, _ = ENGINE(tmp.name)
    if not result:
        return []
    return [item[1] for item in result if len(item) > 1 and item[1]]


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
            lines = recognize(data)
        except Exception as exc:  # noqa: BLE001 - 服务边界统一转 500
            self._send_json(500, {"error": str(exc)})
            return
        self._send_json(200, {
            "text": "\n".join(lines),
            "lines": len(lines),
            "elapsedMs": int((time.time() - started) * 1000),
        })


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 18092
    server = ThreadingHTTPServer(("0.0.0.0", port), Handler)
    print(f"ocr server listening on :{port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
