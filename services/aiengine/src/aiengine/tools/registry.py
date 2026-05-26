"""GlobalToolRegistry — three-source aggregator (builtin + plugin + runtime).

Mirrors Claude Code's Rust GlobalToolRegistry.
See: docs/claw-learnings/03-tool-registry.md
"""

from dataclasses import dataclass, field
from functools import lru_cache
from typing import Any

import structlog

from aiengine.core.errors import ToolExecutionError, ToolNotFoundError
from aiengine.core.tenant import TenantContext
from aiengine.tools.base import (
    PermissionClassifier,
    PermissionMode,
    ToolHandler,
    ToolSpec,
)
from aiengine.tools.permissions import check_permission

log = structlog.get_logger(__name__)


@dataclass(frozen=True, slots=True)
class RegisteredTool:
    spec: ToolSpec
    handler: ToolHandler
    classifier: PermissionClassifier | None = None


@dataclass(slots=True)
class GlobalToolRegistry:
    """Three-source tool aggregator.

    - builtin: compile-time registered (in-process Python handlers)
    - plugin:  tenant-defined plugins (future — DB-loaded handlers via sandbox)
    - runtime: gRPC-delegated tools (e.g. file generation served by Documents)
    """

    builtin: dict[str, RegisteredTool] = field(default_factory=dict)
    plugin: dict[str, RegisteredTool] = field(default_factory=dict)
    runtime: dict[str, RegisteredTool] = field(default_factory=dict)

    def register_builtin(
        self,
        spec: ToolSpec,
        handler: ToolHandler,
        classifier: PermissionClassifier | None = None,
    ) -> None:
        """Register a built-in tool. Raises on name collision."""
        if spec.name in self.builtin:
            raise ValueError(f"Built-in tool '{spec.name}' already registered.")
        self.builtin[spec.name] = RegisteredTool(spec, handler, classifier)

    def register_plugin(self, spec: ToolSpec, handler: ToolHandler) -> None:
        """Register a plugin tool. Cannot shadow built-in names."""
        if spec.name in self.builtin:
            raise ValueError(
                f"Plugin tool '{spec.name}' conflicts with a built-in tool name."
            )
        if spec.name in self.plugin:
            raise ValueError(f"Duplicate plugin tool name: {spec.name}")
        self.plugin[spec.name] = RegisteredTool(spec, handler)

    def all_tools(self) -> list[RegisteredTool]:
        return [*self.builtin.values(), *self.plugin.values(), *self.runtime.values()]

    def definitions_for_llm(
        self, allowed_tools: list[str] | None = None
    ) -> list[dict[str, Any]]:
        """Generate Anthropic-format tool definitions for the LLM.

        Currently returns ALL tools (base+deferred). Future: only base by default,
        deferred discoverable via ToolSearch (see Claw's pattern).
        """
        all_specs = (t.spec for t in self.all_tools())
        if allowed_tools is None:
            specs = all_specs
        else:
            allowed = set(allowed_tools)
            specs = (s for s in all_specs if s.name in allowed)
        return [s.to_anthropic_schema() for s in specs]

    async def execute(
        self,
        tenant: TenantContext,
        tool_name: str,
        tool_input: dict[str, Any],
    ) -> str:
        """Look up tool by name + enforce permission + run handler."""
        tool = (
            self.builtin.get(tool_name)
            or self.plugin.get(tool_name)
            or self.runtime.get(tool_name)
        )
        if tool is None:
            raise ToolNotFoundError(f"Unknown tool: {tool_name}")

        # Dynamic classification or static spec default
        required_mode: PermissionMode = (
            tool.classifier(tool_input) if tool.classifier else tool.spec.required_permission
        )
        check_permission(tenant, tool_name, required_mode, tool_input)

        log.debug(
            "tool.execute.start",
            tool=tool_name,
            required_mode=required_mode.name,
        )

        try:
            return await tool.handler(tenant=tenant, tool_input=tool_input)
        except Exception as exc:
            log.exception("tool.execute.failed", tool=tool_name)
            raise ToolExecutionError(
                f"Tool '{tool_name}' execution failed: {exc}",
                details={"tool": tool_name},
            ) from exc


@lru_cache(maxsize=1)
def get_tool_registry() -> GlobalToolRegistry:
    """Process-level singleton. Built-in tools auto-register on import."""
    registry = GlobalToolRegistry()
    # Late-import to avoid circular deps
    from aiengine.tools.builtin import register_all_builtin

    register_all_builtin(registry)
    return registry
