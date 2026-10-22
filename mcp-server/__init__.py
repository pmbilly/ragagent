#!/usr/bin/env python3
"""
RagAgent MCP Server Package

A Model Context Protocol server that provides access to the RagAgent knowledge management API.
"""

__version__ = "1.1.1"
__author__ = "RagAgent Team"
__description__ = "RagAgent MCP Server - Model Context Protocol server for RagAgent API"

from ragagent_mcp_server import RagAgentClient, run

__all__ = ["RagAgentClient", "run"]
