#!/usr/bin/env python3
"""
RagAgent MCP Server 模组测试脚本

测试模组的各种启动方式和功能。unittest discover 会收集本文件中的 TestCase；
也可直接运行: python test_module.py
"""

import os
import subprocess
import sys
import unittest
from pathlib import Path

MCP_SERVER_DIR = Path(__file__).resolve().parent

REQUIRED_FILES = [
    "__init__.py",
    "main.py",
    "run_server.py",
    "ragagent_mcp_server.py",
    "requirements.txt",
    "setup.py",
    "pyproject.toml",
    "README.md",
    "INSTALL.md",
    "LICENSE",
    "MANIFEST.in",
]


class ModuleIntegrationTest(unittest.TestCase):
    def test_imports(self):
        import mcp  # noqa: F401
        import requests  # noqa: F401
        import ragagent_mcp_server  # noqa: F401
        from ragagent_mcp_server import RagAgentClient, run  # noqa: F401
        import main  # noqa: F401

    def test_environment_optional_vars(self):
        os.getenv("RAGAGENT_BASE_URL")
        os.getenv("RAGAGENT_API_KEY")

    def test_client_creation(self):
        from ragagent_mcp_server import RagAgentClient

        base_url = os.getenv("RAGAGENT_BASE_URL", "http://localhost:8080/api/v1")
        api_key = os.getenv("RAGAGENT_API_KEY", "test_key")
        client = RagAgentClient(base_url, api_key)
        self.assertEqual(client.base_url, base_url)
        self.assertEqual(client.api_key, api_key)

    def test_required_files_exist(self):
        missing = [name for name in REQUIRED_FILES if not (MCP_SERVER_DIR / name).exists()]
        self.assertEqual(missing, [], f"Missing files: {missing}")

    def test_main_help(self):
        result = subprocess.run(
            [sys.executable, "main.py", "--help"],
            capture_output=True,
            text=True,
            timeout=10,
            cwd=MCP_SERVER_DIR,
        )
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_main_check_only(self):
        result = subprocess.run(
            [sys.executable, "main.py", "--check-only"],
            capture_output=True,
            text=True,
            timeout=10,
            cwd=MCP_SERVER_DIR,
        )
        self.assertEqual(result.returncode, 0, result.stderr)

    def test_wiki_tools(self):
        import ragagent_mcp_server

        client = ragagent_mcp_server.RagAgentClient("http://localhost:8080/api/v1", "test")
        for method in ["wiki_search", "wiki_read_page", "wiki_index_view"]:
            self.assertTrue(hasattr(client, method), f"RagAgentClient missing: {method}")
            self.assertTrue(callable(getattr(client, method)), f"{method} not callable")

    def test_pyproject_metadata(self):
        text = (MCP_SERVER_DIR / "pyproject.toml").read_text(encoding="utf-8")
        self.assertIn("ragagent-mcp", text)
        self.assertIn("ragagent-mcp-server", text)


if __name__ == "__main__":
    unittest.main(verbosity=2)
