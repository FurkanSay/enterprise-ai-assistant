# kai-protos (Python)

Generated Python stubs for the Enterprise AI Assistant gRPC + protobuf contracts.

**Do not edit files in this directory by hand.** They are produced by:

```bash
make proto       # at the repo root
```

The single source of truth is [protos/](../../../protos/). See [ADR-003](../../../docs/architecture/003-grpc-contracts.md).

## Usage from a service

Add a path dependency in your service's `pyproject.toml`:

```toml
[project]
dependencies = [
    "kai-protos",
    # ...
]

[tool.uv.sources]
kai-protos = { path = "../../libs/generated/python", editable = true }
```

Then import as namespace packages:

```python
from documents.v1 import documents_pb2, documents_pb2_grpc
from common.v1 import tenant_pb2
```

## What is generated

| Package | Service |
|---|---|
| `common.v1` | shared types (TenantContext, CommonError, PageRequest) |
| `documents.v1` | DocumentsService |
| `processing.v1` | ProcessingService |
| `aiengine.v1` | AiEngineService |
| `identity.v1` | IdentityService |
