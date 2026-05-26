# 05 — Session & Persistence

**Kaynak:**
- [`runtime/src/session.rs`](../../claw-code/rust/crates/runtime/src/session.rs) — `Session`, `ConversationMessage`, `ContentBlock`, persistence

## 🎯 Tek Cümle Özet
Session = "in-memory state + opsiyonel disk persistence + log rotation + truncation/redaction + workspace binding + heartbeat" — tek struct hepsini koordine eder. Multi-tenant SaaS'te bu yapının %80'ini DB'ye taşımak gerekir, %20'si (rate limit/heartbeat/truncation) aynı kalır.

## 🧩 Temel Kavramlar

### ContentBlock taxonomy
```rust
pub enum ContentBlock {
    Text { text },                                      // assistant cevap metni
    Thinking { thinking, signature },                   // reasoning model'larda gizli zincir
    ToolUse { id, name, input },                        // LLM tool çağırdı
    ToolResult { tool_use_id, tool_name, output, is_error },  // tool sonucu
}
```
Bu **provider-agnostic intermediate representation**. Anthropic ile OpenAI farklı block tipleri yollar, hepsi bu enum'a normalize edilir.

### Message ≠ Turn
- **Message:** Bir tarafın (user/assistant/tool) tek seferlik söylediği.
- **Turn:** Bir user mesajıyla başlayıp tool loop bitene kadar süren N mesaj.

Bir turn = 1 user message + N (assistant + tool_result) çift.

### Persistence layer
```rust
struct SessionPersistence { path: PathBuf }
```
**Opsiyonel** — Session disk olmadan da çalışır (in-memory mode). Disk binding `with_persistence_path()` çağrısıyla aktif olur. Aktifse her `push_message()` otomatik append.

### Workspace root binding
```rust
pub workspace_root: Option<PathBuf>
```
**Neden var?** Yorumdaki açıklama altın:
> *"The global session store under `~/.local/share/opencode` is shared across every `opencode serve` instance, so without an explicit workspace root parallel lanes can race and report success while writes land in the wrong CWD."*

Yani: birden çok agent paralel çalışıyorsa, hangi workspace'e ait olduğu kayıtlı olmalı. Yoksa A workspace'inde yapılan değişiklik B workspace'ine "başarılı" olarak rapor edilebilir.

**Senin için kritik:** Bu birebir senin **tenant binding** problemine eşdeğer. Session.tenant_id olmadan multi-tenant SaaS bozulur.

## 🔁 Akış

```
SESSION LIFECYCLE:

Session::new()
   ↓
.with_persistence_path("~/.local/share/claw/sessions/<id>.jsonl")
.with_workspace_root("/path/to/project")
   ↓
push_user_text("user mesajı")
   ├─ touch()                        ← updated_at güncellenir
   ├─ messages.push(msg)             ← in-memory önce
   ├─ append_persisted_message(msg)  ← disk'e JSONL append
   │  └─ FAIL? messages.pop()        ← rollback (atomicity)
   ↓
push_message(assistant_response)
   (same flow)
   ↓
push_message(tool_result)
   (same flow)
   ↓
... loop ...
   ↓
ROTATION TRIGGER: file > ROTATE_AFTER_BYTES (256 KB)
   ├─ rotate_session_file_if_needed()
   ├─ session.jsonl → session.jsonl.1
   ├─ session.jsonl.1 → session.jsonl.2
   ├─ ... up to MAX_ROTATED_FILES (3)
   └─ cleanup oldest
   ↓
COMPACTION TRIGGER: estimate_session_tokens() > threshold
   ├─ compact_session() — LLM ile özet çıkar
   ├─ messages = [summary_message, recent_messages]
   ├─ compaction = Some(SessionCompaction { count, removed, summary })
   └─ Sonraki run_turn'de health probe çalışır (kanarya)
```

## ⚠️ Kritik Tasarım Kararları (Why?)

### 1. In-memory + disk atomicity
**Karar:** [`session.rs:256-270`] Disk yazımı fail olursa in-memory'den de pop.
```rust
self.messages.push(message);
let persist_result = self.append_persisted_message(...);
if let Err(error) = persist_result {
    self.messages.pop();
    return Err(error);
}
```
**Why:** Restart sonrası in-memory uçar, disk kalır. Eğer push edilmiş ama disk'e yazılmamışsa, restart sonrası "kullanıcı bu mesajı yazmıştı ama benden cevap yok" gibi anomali olur. Atomicity şart.
**Senin platform:** DB transaction içinde yap. `BEGIN; INSERT message; INSERT into outbox; COMMIT;` — outbox pattern.

### 2. JSONL append-only format
**Karar:** Her mesaj tek satır JSON, dosyaya append.
**Why:**
- **Crash safety:** Yarım satır olursa parse fail eder ama önceki satırlar bozulmaz.
- **Replay kolay:** Line-by-line read, her satırı parse et.
- **Append O(1):** Tüm dosyayı tekrar yazmaya gerek yok.
- **grep edilebilir:** Operasyonel debugging.
**Senin platform:** DB'de **append-only event table** (`session_events` — id, session_id, tenant_id, timestamp, event_type, payload). Materialize edilmiş view ile şu anki state üretilir.

### 3. Log rotation (256 KB threshold + 3 arşiv)
**Karar:** [`session.rs:14-15`] `ROTATE_AFTER_BYTES = 256 * 1024`, `MAX_ROTATED_FILES = 3`.
**Why:** Tek dosya sınırsız büyürse:
- Load time uzar (tüm history parse)
- Disk kullanımı kontrolsüz
- Grep yavaşlar
Rotation ile bounded disk usage + cold archive accessible.
**Senin platform:** DB'de "active messages" + "archived messages" tablo ayrımı. Veya time-series partitioning (Postgres `pg_partman`). Sıcak veri hızlı, soğuk veri ucuz storage'da.

### 4. Field-level truncation + redaction
**Karar:** [`session.rs:16-18`]
```rust
const MAX_JSONL_FIELD_CHARS: usize = 16 * 1024;
const JSONL_TRUNCATION_MARKER: &str = "… [truncated for session JSONL]";
const JSONL_REDACTION_MARKER: &str = "[redacted]";
```
**Why:** Tool output bazen devasa (10 MB bash output). JSONL'i şişirir, parse'ı yavaşlatır. Truncation **disk için**, in-memory full kalabilir.
**Why redaction:** Tool output secret içerebilir (env dump, config). Pattern detection ile sansürlenir.
**Senin platform:** İki seviyeli — DB'ye **full** yaz (encrypted at rest), API/UI response'unda **truncated + redacted** dön. Audit gerekirse decrypt edilir.

### 5. Workspace root binding
**Karar:** [`session.rs:116-125`] Session'a workspace path kayıtlı; restart sonra yanlış CWD'ye yazımı engeller.
**Why:** Comment'te belirtilen "phantom completions" bug'ı — paralel session'lar aynı CWD'yi paylaşırsa yazımlar karışır.
**Senin platform:** Bu birebir **tenant binding**. Her session `tenant_id` ile mühürlü. Yanlış tenant'a write engellenir (DB level + app level).

### 6. Touch + counter pattern (zaman/ID)
**Karar:** [`session.rs:19-20`]
```rust
static SESSION_ID_COUNTER: AtomicU64 = AtomicU64::new(0);
static LAST_TIMESTAMP_MS: AtomicU64 = AtomicU64::new(0);
```
**Why:** Session ID üretimi monotonic counter + timestamp. Aynı millisaniyede 2 session yaratılırsa ID collision olmasın. `AtomicU64` lock-free.
**Senin platform:** **UUIDv7** (time-ordered UUID) ile çöz — distributed sistemde counter kullanılamaz. Ama monotonic ordering korunur.

### 7. Heartbeat + liveness
**Karar:** [`session.rs:90-106`]
```rust
enum SessionLiveness { Healthy, Stalled, TransportDead, Unknown }
struct SessionHeartbeat { session_id, observed_at_ms, transport_alive, liveness }
```
**Why:** Long-running session'lar zombie kalabilir (network kopuk, process asılı). Heartbeat ile "bu session hâlâ aktif mi?" cevabı.
**Senin platform:** Multi-tenant'ta zombie session detection **kritik** — zombie session token tüketir. Heartbeat + idle timeout + admin force-kill yetkisi.

### 8. Compaction → health probe
**Karar:** [`conversation.rs:301-315`] Compaction sonrası `run_session_health_probe()` çalışır — `glob_search` ile non-destructive probe.
**Why:** Compaction LLM çağrısı içerir, hata olabilir, session yarı-bozuk kalabilir. Probe = "session hâlâ tool çağırabiliyor mu?" testi.
**Senin platform:** Aynı pattern — risky operation (compaction, migration, schema change) sonrası health check.

## 🏢 Multi-Tenant SaaS'e Çeviri

### Storage modeli
| Claw (filesystem) | Sen (DB) |
|---|---|
| `~/.local/share/claw/sessions/<id>.jsonl` | `sessions` tablosu + `session_messages` tablosu |
| `workspace_root: PathBuf` | `tenant_id` FK + opsiyonel `project_id` FK |
| File rotation (256 KB) | Time/size partitioning (pg_partman) |
| In-memory `Vec<Message>` | DB + Redis hot cache (en son N mesaj) |
| Atomic file write | DB transaction + outbox pattern |
| `prompt_history: Vec` | `user_prompts` audit tablosu (tenant_id, user_id, timestamp) |

### Şema önerisi (Postgres)
```sql
-- Ana tablo
CREATE TABLE sessions (
    id UUID PRIMARY KEY,                    -- UUIDv7
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    project_id UUID,                        -- workspace muadili
    model TEXT,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    last_heartbeat_at TIMESTAMPTZ,
    liveness TEXT,                          -- enum
    compaction_count INT DEFAULT 0,
    compaction_summary TEXT,                -- son özet
    archived_at TIMESTAMPTZ                 -- soft delete
);

-- RLS zorunlu
ALTER TABLE sessions ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON sessions
    USING (tenant_id = current_setting('app.current_tenant_id')::uuid);

-- Append-only mesajlar
CREATE TABLE session_messages (
    id UUID PRIMARY KEY,
    session_id UUID REFERENCES sessions(id),
    tenant_id UUID NOT NULL,                -- denormalize (RLS için)
    role TEXT NOT NULL,                     -- user/assistant/tool/system
    blocks JSONB NOT NULL,                  -- ContentBlock[]
    token_usage JSONB,
    created_at TIMESTAMPTZ NOT NULL,
    sequence_number BIGINT NOT NULL         -- ordering guarantee
);
CREATE INDEX ON session_messages (session_id, sequence_number);

-- RLS aynı pattern
ALTER TABLE session_messages ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON session_messages
    USING (tenant_id = current_setting('app.current_tenant_id')::uuid);

-- Hot cache (Redis)
-- key: session:<id>:messages
-- value: last 50 messages as JSON array
-- TTL: 1 hour
```

### Crash recovery & idempotency
| Claw | Sen |
|---|---|
| Push → fail → in-memory pop | Begin TX → INSERT message → INSERT event → COMMIT (atomic) |
| Crash mid-tool → restart replay JSONL | Idempotency key per tool call → resume after crash |
| Workspace boundary check | Tenant boundary check (RLS + app) |

### Compaction
- Background job (Celery/Sidekiq/BullMQ)
- Trigger: `token_count > threshold * tenant.compaction_factor`
- Compaction LLM call → cheap model (Haiku) → tenant'a maliyet düşür
- Yeni "summary message" insert, eski mesajlar `archived_at` ile mark (silme yok — audit)

### Truncation/redaction
- DB'ye **full** payload (encrypted column for sensitive tools)
- API response'a **truncated + redacted** dön
- Audit endpoint admin için full payload (decrypt + log access)

### Zombie session
- Cron job (her 5 dakikada): `UPDATE sessions SET liveness='stalled' WHERE last_heartbeat_at < NOW() - INTERVAL '15 minutes' AND archived_at IS NULL`
- Stalled session'lar UI'da gri görünür, kullanıcı resume edebilir
- Stalled > 24 saat → otomatik archive

## ❓ Hızlı Hatırlatma Soruları
- Mesaj kaydı atomik mi (DB transaction)? Yarı-yazılmış state olabilir mi?
- Tenant_id her message row'unda denormalize ediliyor mu (RLS için)?
- Workspace/project binding session'a yazılı mı?
- Tool output truncation/redaction var mı, yoksa full PII LLM'e ve DB'ye akıyor mu?
- Heartbeat + zombie detection cron'u kuruldu mu?
- Compaction async background job mu, sync mi (sync = UX katili)?
- Replay/resume için idempotency key var mı?
- Hot cache var mı (Redis), her message için DB hit ediyor muyum?
