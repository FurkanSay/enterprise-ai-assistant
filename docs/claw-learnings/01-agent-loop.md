# 01 — Agent Loop (`run_turn()`)

**Kaynak:** [`claw-code/rust/crates/runtime/src/conversation.rs:317-519`](../../claw-code/rust/crates/runtime/src/conversation.rs)

## 🎯 Tek Cümle Özet
Agent loop = "LLM'i çağır → cevabını parçala → tool çağırdıysa çalıştır → tool_result'ı history'e koy → tekrar LLM çağır → tool çağırmadığı an çık" döngüsü. **Bütün** AI agent sistemlerinin (Claude Code, Cursor, Aider, Claw, seninki) ortak kalbi.

## 🧩 Temel Kavramlar

### Agent ≠ LLM
LLM stateless metin üretici. Agent = LLM + tool execution + state + döngü. Bu yapıştırıcı katmana **harness** denir.

### Turn = Bir kullanıcı mesajına verilen tam cevap
İçinde N tane "iteration" olabilir (her iteration = bir LLM çağrısı). Kullanıcı `"test'leri çalıştır"` der → harness 5 iteration yapar (LLM → bash → LLM → read_file → LLM → cevap) → bu bir turn'dür.

### Iteration cap = sigorta
LLM bazen sonsuza tool çağırır (bug, halüsinasyon, kötü prompt). Cap olmadan cüzdan boşalır. Claw'da default ~50.

## 🔁 Beş-Fazlı Akış

```
FAZ A: Pre-flight           (323-339)
  ├─ Session sağlık kontrolü (compaction sonrası kanarya probe)
  └─ User mesajını session.messages'a push

FAZ B: State init           (341-345)
  └─ assistant_messages[], tool_results[], iterations=0

FAZ C: MAIN LOOP            (346-403)
  loop {
    iterations++
    iterations > max → ERROR (sigorta)
    request = { system_prompt, session.messages.clone() }  ← her turn TÜM history
    events = api_client.stream(request)
    assistant_message, usage, cache_events = build_assistant_message(events)
    usage_tracker.record(usage)
    pending_tool_uses = extract ToolUse blocks
    session.push_message(assistant_message)   ← ÖNCE push, sonra tool exec
    if pending_tool_uses.is_empty() { break } ← tek bitiş şartı
    → FAZ D
  }

FAZ D: Tool execution       (404-503)
  for each (tool_use_id, tool_name, input) in pending_tool_uses:
    pre_hook = run_pre_tool_use_hook()
    effective_input = pre_hook.updated_input ?? input
    permission_outcome = match {
      pre_hook.cancelled/failed/denied → Deny
      else with prompter → policy.authorize(prompt user)
      else                → policy.authorize(no prompt)
    }
    result_message = match permission_outcome {
      Allow → tool_executor.execute() + post_hook → tool_result
      Deny  → tool_result(error=true, reason=...)
    }
    session.push_message(result_message)

FAZ E: Cleanup              (506-518)
  auto_compaction = maybe_auto_compact()  ← turn SONUNDA, kullanıcıya gecikme yok
  return TurnSummary { messages, results, usage, iterations, ... }
```

## ⚠️ Kritik Tasarım Kararları (Why?)

### 1. Iteration limit non-negotiable
**Karar:** Her loop'un üst sınırı var.
**Why:** LLM hatası/halüsinasyonu sonsuz tool çağrısına yol açar. Cap olmadan fatura patlar.
**Senin platform:** Tenant-bazlı cap. Free=10, Pro=50, Enterprise=100. Aynı zamanda **token cap** ekle (iteration az ama her biri devasa olabilir).

### 2. Her iteration'da TÜM history yeniden gönderilir
**Karar:** `session.messages.clone()` her request'te.
**Why:** LLM stateless. Conversation continuity tek yoldan sağlanır: tüm history her seferinde.
**Sonuç:** Prompt caching kritik. Değişmeyen başlangıç (system prompt + ilk N mesaj) provider tarafında cache'lenir. Cache hit %90+ olmalı, yoksa maliyet katlanır.
**Senin platform:** Anthropic prompt caching aktif et. Cache breakpoint'leri konuşma boyunca yerleştir (her 10 mesajda).

### 3. Loop bitiş şartı: `tool_use` yoksa
**Karar:** `stop_reason` değil, content block listesi kontrol edilir.
**Why:** Provider'lar `stop_reason` formatını farklı kullanır (Anthropic "end_turn", OpenAI "stop"). Content block API kontratı daha standart.
**Senin platform:** Aynı yaklaşımı uygula — provider-agnostic bitiş kontrolü.

### 4. Assistant mesajı ÖNCE push, tool exec SONRA
**Karar:** [`push_message(assistant_message)` satır 395-397] → [`for tool_use` satır 404].
**Why (iki sebep):**
- **Protocol:** `tool_result`'ın referans verdiği `tool_use_id` history'de daha önce assistant block'ta geçmiş olmalı. Ters yaparsan provider 400 atar.
- **Resumability:** Tool execution sırasında crash olursa, assistant mesajı kayıtlı → restart'ta LLM'i tekrar çağırmaya gerek yok, sadece tool'ları replay et. Token tasarrufu + tutarlılık.

### 5. Permission: hook → policy sıralaması
**Karar:** Önce PreToolUse hook, sonra policy.
**Why:** Hook **dinamik context** ekler (runtime'da bilinen şeyler — kullanıcı rolü, ortam, komut içeriği). Statik policy bu bağlamı bilemez. Hook policy'yi **bypass etmez**, **bilgilendirir** (`PermissionContext::new(override, reason)`).
**Senin platform:** Hook = tenant-specific kural lambda'sı. Çekirdek RBAC sabit, tenant kuralları hook ile eklenir.

### 6. Reddedilen tool → exception değil, history'e mesaj
**Karar:** `PermissionOutcome::Deny` → `tool_result(is_error=true, reason)` LLM'e yollanır.
**Why:** LLM red sebebini görür → **farklı bir yaklaşım dener** ("bash izni yok, read_file ile yapayım"). Exception fırlatırsan bu bilgi kaybolur, döngü çöker.
**Senin platform:** **Her** tool error'unu (permission, validation, execution) LLM'e bilgi olarak ver. Sadece sistem hataları (DB down, OOM) exception olsun.

### 7. Auto-compaction turn SONUNDA
**Karar:** [Satır 506] `maybe_auto_compact()` summary'den önce ama loop'tan sonra.
**Why:**
- Başta yapılsaydı her turn'e 2-5 sn ekstra gecikme.
- Bu turn basitse (tek tool) gereksiz compaction.
- Compaction fail olsa bile şu anki turn etkilenmez.
- Şu anki turn dahil edilebilir → daha akıllı özet.
**Senin platform:** Background worker (Celery/BullMQ/SQS) ile yap. Response döndükten sonra job kuyruğa düşsün. Bonus: compaction için **daha ucuz model** (Haiku) kullan.

## 🏢 Multi-Tenant SaaS'e Çeviri

| Claw (single-user CLI) | Sen (multi-tenant SaaS) |
|---|---|
| `max_iterations = 50` (hardcoded) | Tenant tier'a göre cap (`tenant.iteration_limit`) |
| `session.messages: Vec` in-memory | DB `sessions` + `messages` tabloları, `tenant_id` FK, RLS |
| `session.messages.clone()` her turn | DB'den çek + Redis cache (hot session) |
| Iteration counter sadece local | Counter + `tenant_id` → billing table |
| File-based hooks (shell scripts) | DB-tanımlı hooks (tenant admin UI'dan) |
| Health probe single-user | Tenant-scoped probe (RLS doğrulaması dahil) |
| Compaction LLM çağrısı sync | Async background worker, ucuz model |
| Crash recovery: dosya replay | Crash recovery: DB transaction + idempotency key |
| `system_prompt: Vec<String>` (config) | System prompt tenant-customizable + admin override + immutable safety footer |

## 🔌 Dış Bağımlılıklar (run_turn'ün ihtiyaçları)

`run_turn` şu trait'leri kullanır — bunlar **inject edilebilir** (DI), test edilebilir:
- `ApiClient` (line 57) → LLM provider
- `ToolExecutor` (line 62) → tool dispatcher
- `PermissionPolicy` → izin politikası
- `PermissionPrompter` → interactive user prompt (Optional)
- `HookRunner` (struct field) → hook execution

**Çıkarım:** Senin de aynı şekilde **interface-driven** yaz. Her bağımlılığı protocol/interface arkasına koy. Test sırasında mock, prod'da gerçek implementation.

## ❓ Hızlı Hatırlatma Soruları
(Geliştirme sırasında "yapı doğru mu?" diye check için)
- Tool error LLM'e gidiyor mu, exception mı atıyorum?
- Iteration cap var mı, tenant-bazlı mı?
- Compaction sync mi yapıyorum (yanlış), async mı (doğru)?
- Assistant mesajı tool exec'ten önce DB'ye yazılıyor mu?
- Permission policy'yi hook'larla genişletebiliyor muyum, yoksa bypass mı oluyor?
