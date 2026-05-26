# 02 — Permission Enforcer

**Kaynak:**
- [`runtime/src/permission_enforcer.rs`](../../claw-code/rust/crates/runtime/src/permission_enforcer.rs) — kararı veren
- [`runtime/src/permissions.rs`](../../claw-code/rust/crates/runtime/src/permissions.rs) — kuralları tutan

## 🎯 Tek Cümle Özet
Permission sistemi **statik mode hiyerarşisi + dinamik command/path classification + hook override** zincirinden oluşur. Her tool çağrısı bu üç katmandan geçer; ilk "deny" kararı kazanır.

## 🧩 Temel Kavramlar

### Policy vs Enforcer (SRP)
- **`PermissionPolicy`** = "kurallar" — mode + tool requirements + allow/deny/ask rules + hook overrides
- **`PermissionEnforcer`** = "yargıç" — policy'yi yorumlayıp `Allowed`/`Denied` kararı verir

Bu ayrım kritik: kuralları değiştirmek (yeni rule eklemek) yargıcı değiştirmek değildir. Test edilebilirlik 10x artar.

### Permission Mode Hiyerarşisi
```rust
enum PermissionMode {
    ReadOnly,          // En kısıtlı
    WorkspaceWrite,    // + workspace içi yazma
    DangerFullAccess,  // + her şey (bash, MCP, dış path)
    Prompt,            // Kullanıcıya sor (özel mod)
    Allow,             // Bypass — her şeye izin
}
// PartialOrd implemented: ReadOnly < WorkspaceWrite < DangerFullAccess
```

`active_mode >= required_mode` ile karşılaştırma → tek satır check.

### Dynamic vs Static Classification
- **Static:** `tool_requirements: BTreeMap<String, PermissionMode>` — tool adına göre sabit ("read_file → ReadOnly")
- **Dynamic:** Runtime'da input'a bakarak hesaplama:
  - **Bash:** `is_read_only_command()` whitelist + redirect/in-place flag detection
  - **File write:** `is_within_workspace()` path prefix check

Statik tek başına yetmez çünkü `bash` tek başına ne kadar tehlikeli olduğu **komuta** bağlı. Dynamic classification olmasaydı ya hep deny olurdu (kullanışsız) ya hep allow (güvensiz).

## 🔁 Karar Zinciri

```
TOOL CHECK FLOW:
  ┌─ 1. Hook override? ────────┐
  │  context.override_decision  │  Hook setlediyse → bu kazanır
  │  = Allow/Deny/Ask           │
  └──────────┬──────────────────┘
             ↓ (no override)
  ┌─ 2. Deny rule match? ───────┐
  │  find_matching_rule(deny..) │  En güçlü → hemen Deny
  └──────────┬──────────────────┘
             ↓ (no deny match)
  ┌─ 3. Ask/Allow rule + Mode ──┐
  │  required = required_mode_for(tool)
  │  if active >= required → Allow
  │  else → Deny veya Prompt'a yönlendir
  └──────────┬──────────────────┘
             ↓ (for bash/file write)
  ┌─ 4. Dynamic classification ─┐
  │  check_bash(command)        │  Whitelist + flag check
  │  check_file_write(path)     │  Workspace boundary
  └─────────────────────────────┘

RESULT: EnforcementResult::{ Allowed | Denied { tool, active, required, reason } }
```

## ⚠️ Kritik Tasarım Kararları (Why?)

### 1. Prompt mode = "enforcer hard-deny etmez"
**Karar:** [`permission_enforcer.rs:42-44`] Active mode `Prompt` ise enforcer otomatik `Allowed` döner.
**Why:** Enforcer'ın prompter'a erişimi yok. "Prompt" demek "interactive katmana defer et" demek. Enforcer hard-deny ederse interactive flow çalışamaz.
**Senin platform:** `Prompt` mode'u "human-in-the-loop approval queue"ya çevir — admin onay verene kadar pending state'te bekleyen tool çağrıları.

### 2. Whitelist > Blacklist (bash)
**Karar:** [`permission_enforcer.rs:194-272`] Read-only komut listesi **explicit** (60+ komut). Bilinmeyen komut → write sayılır.
**Why:** Blacklist eksik kalır (yeni binary'ler, alias'lar, path manipulation). Whitelist güvenli default verir: bilinmiyorsa yasak.
**Senin platform:** SQL classification yaparken aynısı — `SELECT` whitelist, kalan her şey (INSERT, UPDATE, DELETE, DROP, ALTER, GRANT, EXEC, ...) write sayılır. CTE'ler ve SELECT INTO için ekstra parse gerekir.

### 3. Redirect/in-place flag detection
**Karar:** [`permission_enforcer.rs:268-271`] `cat file > out.txt` whitelist'te olsa bile reject. `--in-place`, `-i`, `>`, `>>` patternleri write sinyali.
**Why:** "Komut adı read-only ama davranış write." Naive whitelist bunu kaçırır.
**Senin platform:** SQL'de aynı problem — `INSERT INTO ... SELECT` veya `WITH x AS (UPDATE ...) SELECT ...` gibi gizli write'lar. AST seviyesinde parse zorunlu (regex yetmez).

### 4. Denied result = LLM'e geri yollanır (exception değil)
**Karar:** [`permission_enforcer.rs:14-24`] `EnforcementResult::Denied { tool, active_mode, required_mode, reason }` — yapısal mesaj.
**Why:** Bu mesaj LLM'e `tool_result(is_error=true)` olarak gider → LLM "ah, izin yok, başka yol deneyeyim" anlayabilir. String yerine yapısal payload audit/UI tarafında da kullanılır.
**Senin platform:** Aynı yapı + tenant_id + user_id + timestamp → audit log row. Yapısal field'lar SIEM/dashboard'da query edilebilir.

### 5. Hook policy'yi bypass edemez, sadece bilgilendirir
**Karar:** [`permissions.rs:196-198`] Hook `override_decision = Allow` dese bile policy yine de değerlendirme yapar (deny rule önce kontrol edilir).
**Why:** Hook user-defined → güvensiz. Hook "her şeye allow" diyebilir ama deny rule yine de devreye girer. Defense-in-depth.
**Senin platform:** Tenant admin tanımlı hooks **asla** core RBAC'ı bypass etmemeli. Hook = enrichment, RBAC = enforcement.

### 6. WorkspaceWrite = path prefix check
**Karar:** [`permission_enforcer.rs:108-142`] WorkspaceWrite mode'da yazma için path workspace altında olmalı.
**Why:** Tool'a "yaz" diyorsun ama dosya `/etc/passwd` olabilir. Path validation tool'un sorumluluğunda olmamalı — permission layer'da olmalı.
**Senin platform:** **Tenant data boundary** = workspace_root muadili. Her DB query'de `WHERE tenant_id = ?` — ama bunu **Postgres RLS** ile zorunlu kıl. Application code'a güvenme.

## 🏢 Multi-Tenant SaaS'e Çeviri

### Mode hiyerarşisi map'i
| Claw | Sen |
|---|---|
| `ReadOnly` | `tenant_data_read` — SELECT, file read, web fetch |
| `WorkspaceWrite` | `tenant_data_write` — INSERT/UPDATE, dosya üret, TodoWrite muadili |
| `DangerFullAccess` | `tenant_admin_action` — DELETE, email gönder, dış API write, dış sistem trigger |
| `Prompt` | `requires_human_approval` — onay queue'sunda bekler |
| `Allow` | (yok — production'da hiç olmasın) |

### Dynamic classifier muadilleri
| Claw'da | Sen'de gerekli |
|---|---|
| `classify_bash_permission(cmd)` | `classify_sql_permission(sql)` — AST parse, INSERT/SELECT/CTE detection |
| `is_within_workspace(path, root)` | `is_tenant_owned(resource_id, tenant_id)` — RLS + app-level check |
| `check_bash` redirect detection | `classify_email_permission` — recipient domain tenant'a mı ait |
| — (Claw'da yok) | `classify_api_call(endpoint, method)` — webhook/external API write detection |

### Hook sistemi
Claw'da file-based shell script hook. Sen'de:
- **Tablo:** `tenant_hooks` (tenant_id, hook_type, tool_pattern, script_or_rule, priority)
- **Execution:** Sandboxed Lua/JS expression evaluator (full code execution değil — DSL)
- **Audit:** Her hook çağrısı log'lansın, override decision + reason kaydedilsin

### Defense-in-depth katmanları (claw 4 → sen 6)
1. Hook override (tenant kuralı)
2. Deny rules (org policy)
3. Static mode check (active >= required)
4. Dynamic classification (bash/sql/path)
5. **DB Row-Level Security** (Postgres RLS — bypass impossible)
6. **Audit log** (post-execution, tamper-evident)

## ❓ Hızlı Hatırlatma Soruları
- Policy değişikliği enforcer'ı bozar mı? (Hayır — SRP korunmuş mu?)
- Yeni tool eklediğimde required_mode default'u DangerFullAccess oluyor mu? (Güvenli default)
- SQL classifier whitelist mi blacklist mi? (Whitelist olmalı)
- Hook'lar core RBAC'ı bypass edebiliyor mu? (Hayır — sadece enrich)
- Denied sonucu LLM'e gidiyor mu, sadece log'a mı yazılıyor? (LLM görmeli ki adapt etsin)
- Tenant boundary check sadece app code'da mı, DB RLS de var mı? (İkisi birden)
