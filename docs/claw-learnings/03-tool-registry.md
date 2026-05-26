# 03 — Tool Registry & Tool Inventory

**Kaynak:**
- [`tools/src/lib.rs`](../../claw-code/rust/crates/tools/src/lib.rs) — tek devasa dosyada (5000+ satır) registry + 40+ tool implementasyonu
- Specifik fonksiyonlar:
  - `ToolSpec` struct: [satır 102](../../claw-code/rust/crates/tools/src/lib.rs#L102)
  - `GlobalToolRegistry`: [satır 110](../../claw-code/rust/crates/tools/src/lib.rs#L110)
  - `mvp_tool_specs()`: [satır 393](../../claw-code/rust/crates/tools/src/lib.rs#L393) — built-in liste
  - `execute_tool_with_enforcer()`: [satır 1202](../../claw-code/rust/crates/tools/src/lib.rs#L1202) — dispatcher
  - `deferred_tool_specs()`: [satır 5088](../../claw-code/rust/crates/tools/src/lib.rs#L5088)
  - `search_tool_specs()`: [satır 5100](../../claw-code/rust/crates/tools/src/lib.rs#L5100)
  - Dynamic classifiers: [satır 2214-2253](../../claw-code/rust/crates/tools/src/lib.rs#L2214)

## 🎯 Tek Cümle Özet
Tool registry = "**üç kaynak** (builtin/plugin/runtime) × **iki kademe** (base/deferred) × **dinamik permission classification**". LLM her turn'de sadece 6 base tool görür; geri kalan 34+ tool'u `ToolSearch` ile keşfeder.

## 🧩 Temel Kavramlar

### ToolSpec = LLM'e gösterilen sözleşme
```rust
pub struct ToolSpec {
    pub name: &'static str,           // statik string → sıfır alloc
    pub description: &'static str,    // LLM'in tool seçimini yapacağı tek bilgi
    pub input_schema: Value,          // JSON Schema → LLM'in input nasıl üretmesi gerektiği
    pub required_permission: PermissionMode,  // statik default (dynamic override yapılır)
}
```
Bu 4 alan = **LLM'in tool ile etkileşim yüzeyi**. Description ne kadar net, schema ne kadar kısıtlayıcı, o kadar az hata.

### Üç kaynaktan tool toplama
```rust
pub struct GlobalToolRegistry {
    plugin_tools: Vec<PluginTool>,            // runtime'da inject (örn. tenant plugin)
    runtime_tools: Vec<RuntimeToolDefinition>, // runtime'da inject (örn. MCP bridge)
    enforcer: Option<PermissionEnforcer>,
}
```
Builtin'ler `mvp_tool_specs()` ile static — struct alanı değil. Çünkü değişmezler, alanda tutmak gereksiz alloc.

**Name collision check:**
- Plugin tool builtin adını shadow edemez ([`lib.rs:141-150`](../../claw-code/rust/crates/tools/src/lib.rs#L141))
- Runtime tool builtin+plugin adlarını shadow edemez ([`lib.rs:164-181`](../../claw-code/rust/crates/tools/src/lib.rs#L164))

Bu pattern **isim çakışmasından kaynaklanan sessiz override'ları** engeller — debugging cehennemi.

### Base 6 vs Deferred 34+
```rust
fn deferred_tool_specs() -> Vec<ToolSpec> {
    mvp_tool_specs().into_iter().filter(|spec| {
        !matches!(spec.name,
            "bash" | "read_file" | "write_file" | "edit_file"
            | "glob_search" | "grep_search")
    }).collect()
}
```
**Base 6** her turn LLM'e gider (~2-3k token JSON schema).
**Deferred 34+** sadece `ToolSearch` ile bulunduğunda yüklenir.

Sayısal ölçek:
- 40 tool × ~300 token = ~12k token boşa harcanır eğer hepsi her turn gönderilirse
- Base 6 ile ~2k token → **6x tasarruf**

### Dynamic permission classification
Statik `required_permission` sadece **default**. Asıl karar runtime'da:
```rust
// execute_tool_with_enforcer içinde:
"bash" => {
    let classified_mode = classify_bash_permission(&bash_input.command);
    maybe_enforce_permission_check_with_mode(enforcer, name, input, classified_mode)?;
    run_bash(bash_input)
}
"write_file" => {
    let required_mode = classify_file_path_permission(&file_input.path, true);
    maybe_enforce_permission_check_with_mode(enforcer, name, input, required_mode)?;
    run_write_file(file_input)
}
```
Aynı tool, farklı input → farklı izin gerektirebilir.

## 🔁 Execute Flow (LLM → result)

```
LLM cevabı: tool_use { id, name="edit_file", input={path, old, new} }
            ↓
run_turn → tool_executor.execute(name, input)
            ↓
GlobalToolRegistry::execute(name, input)
   ├─ mvp_tool_specs içinde mi? → execute_tool_with_enforcer
   └─ plugin tools içinde mi? → plugin.execute(input)
            ↓
execute_tool_with_enforcer:
   1. JSON input → typed struct (serde::from_value)
   2. Dynamic classification (path/command'a göre PermissionMode hesapla)
   3. Enforcer check (active >= required?) → Denied ise Err
   4. run_<tool>(typed_input) → String result
            ↓
LLM'e tool_result { tool_use_id, output, is_error }
```

## 📋 Tool Inventory (40+ tool)

### Filesystem & Search (6 BASE — her turn yüklenir)
| Tool | Default Permission | Açıklama |
|---|---|---|
| `bash` | DangerFullAccess (dynamic↓) | Shell, timeout/sandbox/network/filesystem flag'leri |
| `read_file` | ReadOnly (dynamic↑) | offset+limit ile read |
| `write_file` | WorkspaceWrite (dynamic↑) | Tam dosya |
| `edit_file` | WorkspaceWrite (dynamic↑) | String replace, `replace_all` flag |
| `glob_search` | ReadOnly (dynamic↑) | Pattern ile dosya bul |
| `grep_search` | ReadOnly (dynamic↑) | Regex content (ripgrep arayüzü, -A/-B/-C/-n/-i) |

### Web (deferred)
| Tool | Permission | Açıklama |
|---|---|---|
| `WebFetch` | ReadOnly | URL→metin + prompt cevapla |
| `WebSearch` | ReadOnly | Web arama + allowed/blocked domains |

### Productivity (deferred)
| Tool | Permission | Açıklama |
|---|---|---|
| `TodoWrite` | WorkspaceWrite | Task listesi (pending/in_progress/completed) |
| `NotebookEdit` | WorkspaceWrite | Jupyter cell replace/insert/delete |
| `Skill` | ReadOnly | Skill definition yükle (`/init`, `/review`...) |
| `Config` | WorkspaceWrite | Settings get/set |
| `EnterPlanMode` / `ExitPlanMode` | WorkspaceWrite | Plan modu |
| `Sleep` | ReadOnly | Wait (shell process tutmadan) |
| `StructuredOutput` | ReadOnly | Yapılı format çıktı |
| `SendUserMessage` / `Brief` | ReadOnly | Proaktif mesaj |
| `AskUserQuestion` | ReadOnly | Interaktif soru (options destekli) |
| `LSP` | ReadOnly | Language server (hover, def, refs) |
| `ToolSearch` | ReadOnly | Deferred tool keşfi |
| `REPL` | DangerFullAccess | Subprocess kod execute |
| `PowerShell` | DangerFullAccess (dynamic) | PowerShell komutu |

### Agent/Task Orchestration (deferred — 13!)
| Tool | Permission | Açıklama |
|---|---|---|
| `Agent` | DangerFullAccess | Specialized sub-agent (subagent_type) |
| `TaskCreate` / `RunTaskPacket` | DangerFullAccess | Background task başlat |
| `TaskGet` / `TaskList` / `TaskOutput` | ReadOnly | Task sorgu |
| `TaskUpdate` / `TaskStop` | DangerFullAccess | Task mesaj/dur |
| `WorkerCreate` ... `WorkerObserveCompletion` (8 tool) | Karışık | Multi-agent worker lifecycle |
| `TeamCreate` / `TeamDelete` | DangerFullAccess | Paralel worker team |
| `CronCreate` / `CronDelete` / `CronList` | Karışık | Zamanlanmış otomasyon |

### MCP & Remote (deferred — 5)
| Tool | Permission | Açıklama |
|---|---|---|
| `MCP` | DangerFullAccess | MCP server'da tool çağır |
| `ListMcpResources` / `ReadMcpResource` | ReadOnly | MCP resource |
| `McpAuth` | ReadOnly | MCP OAuth |
| `RemoteTrigger` | DangerFullAccess | Remote routine tetikle |

### Test/Internal
| Tool | Açıklama |
|---|---|
| `TestingPermission` | Sadece test — permission enforcement testleyen tool |

## ⚠️ Kritik Tasarım Kararları (Why?)

### 1. `&'static str` (`name`, `description`) — sıfır alloc
**Karar:** Tool name ve description compile-time string literal.
**Why:** Her LLM request'inde tool listesi serialize edilir. String clone yerine static reference → sıfır heap alloc.
**Senin platform:** Python'da bunun karşılığı yok (string'ler heap'te). TS'de aynı şekilde. Optimization yerine **module-level constant** kullan, request başına yeniden construct etme.

### 2. JSON Schema = LLM kontratı
**Karar:** Her tool için `input_schema: Value` (JSON Schema draft-7).
**Why:** LLM bu schema'yı görür, "bu tool ne ister" anlar. Schema'da `required`, `enum`, `type`, `additionalProperties: false` kullanılır → LLM'in geçersiz input üretme şansı düşer.
**Önemli:** `additionalProperties: false` neredeyse her schema'da var → LLM ekstra alan ekleyemez.
**Senin platform:** Pydantic (Python) veya Zod (TS) ile schema tanımla, JSON Schema'ya export et → LLM'e gönder. Tek kaynak, iki kullanım (runtime validation + LLM kontratı).

### 3. Base/Deferred ayrımı statik
**Karar:** Hangi tool base, hangisi deferred — kodda hardcoded match.
**Why:** Base set sık değişmez. DB'ye taşımak gereksiz indirection. KISS.
**Senin platform:** Bunu tenant-customizable yapma cazibesine kapılma. Base set evrensel olmalı (read/write/grep), tenant özelleştirmesi deferred katmanda olsun.

### 4. ToolSearch = fuzzy + canonical token
**Karar:** [`search_tool_specs`](../../claw-code/rust/crates/tools/src/lib.rs#L5100)
- `select:Read,Edit` → exact match
- `+slack send` → "slack" zorunlu, "send" ranking için
- `notebook jupyter` → her ikisi ranking
- Canonical tokenization: `SlackTool` ↔ `slack` (tool suffix atılır, non-alphanumeric silinir)
**Why:** LLM doğru tool adını bilemeyebilir ("notebook" demek isteyebilir ama tool adı "NotebookEdit"). Fuzzy match + canonical ile kurtarır.
**Senin platform:** Aynı pattern kritik — tool sayın büyüdükçe LLM yanlış isim yazacak. Levenshtein distance + canonical token + description full-text search kombinasyonu.

### 5. Name collision check (defensive)
**Karar:** Plugin tool builtin'i shadow ederse `Err` döner — kayıt fail olur.
**Why:** Sessiz override = debugging cehennemi. "Niye benim write_file başka şey yapıyor?" sorusu için saatler harcanır. Fail-fast yaklaşımı.
**Senin platform:** Plugin/MCP/runtime tool kayıt sisteminde **mutlaka** collision check. Builtin > runtime > plugin priority + fail on conflict.

### 6. Dynamic classifier'lar tek satır karar
**Karar:** [`lib.rs:2214-2253`]
```rust
fn classify_file_path_permission(path, allow_missing) -> PermissionMode {
    if path_within_current_workspace(path, allow_missing) {
        PermissionMode::WorkspaceWrite
    } else {
        PermissionMode::DangerFullAccess
    }
}
```
**Why:** Tool kodunu permission logic ile kirletmek yerine, **classifier fonksiyonu** ayrı tutulmuş. SRP.
**Senin platform:** Aynı yapı — `classify_<tool_type>_permission(input)` helper'ları.

### 7. Workspace-bound execution
**Karar:** Her file ops `std::env::current_dir()` ile başlar ([`lib.rs:2111`]):
```rust
fn run_read_file(input) -> Result<String, String> {
    let workspace = std::env::current_dir()?;
    to_pretty_json(read_file_in_workspace(&input.path, ..., &workspace)?)
}
```
**Why:** Workspace context tool'a explicit veriliyor → ileride workspace değiştirmek tek değişiklik. Global mutable state yok.
**Senin platform:** Tenant context aynı şekilde **her tool çağrısına explicit param**. Thread-local/global yerine **request context** struct.

### 8. Tool çıktısı = JSON string
**Karar:** Tüm `run_*` → `Result<String, String>` (pretty JSON).
**Why:** LLM JSON parse etmekte iyi. Tek format = simpler integration. Audit log'da search edilebilir.
**Senin platform:** Aynısı. Output her zaman JSON. Schema yoksa bile `{"text": "..."}` wrap.

### 9. Bash preflight guard (akıllı sigorta)
**Karar:** [`workspace_test_branch_preflight`](../../claw-code/rust/crates/tools/src/lib.rs#L1964) — `cargo test --workspace` çalıştırılmadan önce git branch divergence check.
**Why:** Eski branch'te test çalıştırmak false-positive üretebilir (eski test geçer ama main'de fail eder). Tool, kullanıcıyı **kendinden korur**.
**Senin platform:** Domain-specific preflight'lar çok değerli:
- `send_email` → "bu domain tenant whitelist'inde mi?"
- `run_query` → "bu query üretim DB'sinde mi, dev mi?"
- `delete_record` → "bu silme audit gerektiriyor mu?"
Bunları **policy** olarak değil, **tool davranışı** olarak gömme — daha rezilient.

## 🏢 Multi-Tenant SaaS'e Çeviri

### Tool inventory iskeleti (sen için önerilen)
Senin "MVP tool set: bilgi erişim + dosya üretimi + aksiyon; sandbox kod yürütme YOK" kararına göre:

#### Bilgi Erişim (Base — her turn yüklenir, ReadOnly)
| Tool | Açıklama |
|---|---|
| `db_query` | Tenant DB'de SELECT (RLS otomatik, AST validation) |
| `db_schema` | Tenant şema keşfi (5-katmanlı DB defense per memory) |
| `doc_search` | Tenant döküman/PDF/wiki içinde semantic search |
| `web_fetch` | Whitelisted URL'den içerik çek |
| `web_search` | Search engine API (tenant scope filtre) |

#### Dosya Üretimi (Base, WorkspaceWrite)
| Tool | Açıklama |
|---|---|
| `generate_document` | Word/PDF/Excel/PowerPoint template + data |
| `generate_chart` | Grafik üret (görsel/SVG/PNG) |
| `generate_report` | Yapılandırılmış rapor (markdown→PDF) |

#### Aksiyon (Deferred, DangerFullAccess çoğunluk)
| Tool | Açıklama |
|---|---|
| `send_email` | Tenant SMTP / SendGrid / Mailgun (recipient whitelist) |
| `create_calendar_event` | Google/Outlook (tenant entegrasyon) |
| `crm_create_record` | CRM API (Salesforce/HubSpot) |
| `erp_query` / `erp_update` | ERP entegrasyon |
| `slack_post_message` | Slack webhook |
| `ticket_create` | Jira/Linear/ServiceNow |

#### Meta (Deferred)
| Tool | Açıklama |
|---|---|
| `ToolSearch` | Deferred tool keşfi (fuzzy + canonical) |
| `TodoWrite` | İçinde bulunulan task'ı yapılandır |
| `AskUserQuestion` | Kullanıcıya soru (UI'da chip render) |
| `ScheduleJob` | Background job kuyruğa al |
| `RememberFact` | Tenant memory store'a kayıt |

### ToolSpec struct (Python örnek)
```python
from pydantic import BaseModel, Field
from typing import ClassVar

class ToolSpec(BaseModel):
    name: str                       # snake_case
    description: str                # LLM'e gidecek
    input_schema: dict              # Pydantic.model_json_schema()
    required_permission: str        # ReadOnly/WorkspaceWrite/AdminAction
    base_or_deferred: str           # "base" | "deferred"
    category: str                   # filesystem/web/action/meta
    tenant_scope: str               # "all_tenants" | "tenant_specific"
    classifier: ClassVar[callable] = None  # dynamic permission override
```

### Registry örnek
```python
class GlobalToolRegistry:
    def __init__(
        self,
        builtin: list[ToolSpec],
        plugin: list[ToolSpec],
        tenant_id: UUID,
    ):
        self._builtin = builtin
        self._plugin = plugin
        self._tenant_tools = self._load_tenant_tools(tenant_id)
        self._validate_no_collisions()

    def definitions_for_llm(self, allowed_tools: set[str] | None = None) -> list[dict]:
        # base tools her turn + deferred yalnızca search ile
        return [t.to_anthropic_schema() for t in self._base_tools()
                if not allowed_tools or t.name in allowed_tools]

    def execute(self, name: str, input: dict, tenant_ctx: TenantContext) -> dict:
        tool = self._find_tool(name)
        if tool is None:
            raise ToolNotFound(name)

        # dynamic classification
        required = tool.classifier(input) if tool.classifier else tool.required_permission
        self._enforcer.check(tool=name, required=required, tenant=tenant_ctx)

        return tool.handler(input, tenant_ctx)  # tenant_ctx EXPLICIT
```

### Multi-tenant farkları
| Claw | Sen |
|---|---|
| Tek workspace (cwd) | Tenant workspace (tenant_id + project_id) |
| Builtin/plugin/runtime üç katman | + **Tenant-specific tools** (4. katman) |
| `&'static str` (zero alloc) | Module-level constant + frozen Pydantic |
| Collision: fail at registry build | Aynı + tenant-level isolation (tenant A "send_email" override edebilir ama global'i etkilemez) |
| ToolSearch query global | + tenant-scoped (tenant'ın aktif tool'larında ara) |
| Dynamic classifier tek input | + tenant context (tenant'a göre farklı classifier) |
| Tool çıktısı in-memory | + audit log row (tenant_id, tool, input_hash, output_hash, timestamp) |

### Tool tasarımında 5 altın kural
1. **Description ≤ 200 karakter.** LLM tool seçimi için bunu okur. Roman yazma.
2. **Input schema'da `additionalProperties: false`.** LLM ekstra alan eklerse fail. Strict.
3. **`required` fields minimal tut.** Opsiyonel = LLM'e esneklik.
4. **Permission default'u şüphe halinde yüksek.** ReadOnly demek için emin ol.
5. **Output her zaman JSON.** Markdown/plaintext değil — LLM parse etmesi gerekirse.

### Anti-pattern listesi (Claw bunlardan kaçınmış)
| Anti-pattern | Doğrusu |
|---|---|
| Tek dev `do_anything` tool | Atomik tool'lar (read, write, edit ayrı) |
| Stringly-typed input | Typed struct + JSON schema |
| Tool içinde permission check | Enforcer katmanı ayrı |
| Free-form output | JSON output (parsable) |
| Tool kayıt sessiz override | Fail-on-collision |
| Tool description = `"executes X"` | "Use this when... Inputs:... Returns:..." |

## ❓ Hızlı Hatırlatma Soruları
- Tool description LLM'in seçim yapacağı yeterli bilgi veriyor mu, yoksa "executes X" mi?
- Input schema `additionalProperties: false` mı?
- Required permission default'u şüphe halinde yüksek mi?
- Dynamic classifier var mı (path/command'a göre override)?
- Tool çıktısı JSON mu (parsable)?
- Tool sayım büyüdü mü? Deferred sisteme geçtin mi (base 6-10, gerisi ToolSearch)?
- ToolSearch fuzzy + canonical tokenization yapıyor mu?
- Tenant context tool'a **explicit param** olarak gidiyor mu, global mi?
- Audit log her tool çağrısını yakalıyor mu (tenant_id + tool + input_hash + output_hash)?
- Name collision check var mı, sessiz override mümkün mü?
