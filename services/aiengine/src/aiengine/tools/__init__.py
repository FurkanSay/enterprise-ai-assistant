"""Tool catalog — registry, base spec, permission classification, built-in handlers.

Design reference: docs/claw-learnings/03-tool-registry.md
"""

from aiengine.tools.base import PermissionMode, ToolHandler, ToolSpec
from aiengine.tools.registry import GlobalToolRegistry, get_tool_registry

__all__ = [
    "ToolSpec",
    "ToolHandler",
    "PermissionMode",
    "GlobalToolRegistry",
    "get_tool_registry",
]
