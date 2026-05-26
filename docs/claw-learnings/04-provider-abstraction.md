# 04 — Provider Abstraction (Multi-LLM)

**Kaynak:**
- [`api/src/providers/mod.rs`](../../claw-code/rust/crates/api/src/providers/mod.rs) — trait, kind, metadata, model registry
- [`api/src/client.rs`](../../claw-code/rust/crates/api/src/client.rs) — `ProviderClient` enum dispatcher
- [`api/src/providers/anthropic.rs`](../../claw-code/rust/crates/api/src/providers/anthropic.rs) — Anthropic wire impl
- [`api/src/providers/openai_compat.rs`](../../claw-code/rust/crates/api/src/providers/openai_compat.rs) — OpenAI-compat wire impl

## 🎯 Tek Cümle Özet
Provider abstraction = **wire format adapter pattern** + **static model registry**. Agent loop tek interface görür (`ProviderClient`), arkada 3 provider × 2 wire format işbirliği yapar.

## 🧩 Temel Kavramlar

### Wire Format ≠ Provider
- **Provider:** kim sağlıyor (Anthropic, xAI, OpenAI, DashScope)
- **Wire format:** request/response JSON şekli (`AnthropicMessages` veya `OpenAiChatCompletions`)

Bu ayrım önemli: xAI ve OpenAI **aynı wire format'ı** konuşur. DashScope/Qwen de OpenAI format'ı. Yani 3 provider için **2 client** yetiyor (DRY).

### Statik Dispatch (Enum) vs Dinamik Dispatch (Trait Object)
Claw **enum dispatch** seçmiş:
```rust
pub enum ProviderClient {
    Anthropic(AnthropicClient),
    Xai(OpenAiCompatClient),
    OpenAi(OpenAiCompatClient),
}
```
**Avantaj:** Sıfır runtime overhead, no heap allocation, compiler tüm yolları görür → inlining.
**Dezavantaj:** Yeni provider eklemek enum'a variant eklemek demek (compile-time decision). Plugin sistemi gibi runtime-loaded provider yok.

Trait object (`Box<dyn Provider>`) seçilseydi:
- Avantaj: runtime registration, plugin desteği
- Dezavantaj: vtable overhead + heap allocation + monomorphization yok

Claw'da provider seti sabit → enum doğru karar. Senin platformda dinamik gerekirse trait object daha uygun olabilir.

### Capability Matrix
Her provider'ın hangi özelliği desteklediği [`providers/mod.rs:68-85`]:
```rust
pub struct ProviderCapabilityReport {
    pub tool_calls: ProviderFeatureSupport,
    pub streaming: ProviderFeatureSupport,
    pub streaming_usage: ProviderFeatureSupport,
    pub prompt_cache: ProviderFeatureSupport,
    pub reasoning_effort: ProviderFeatureSupport,
    pub reasoning_content_history: ProviderFeatureSupport,
    pub web_search: ProviderFeatureSupport,
    pub web_fetch: ProviderFeatureSupport,
    // ...
}
```
`Supported | Unsupported | PassthroughAsTool` — sadece bool değil, 3 state. Bazı feature'lar "native değil ama tool olarak sağlanabilir" (örn. OpenAI'da prompt cache yok ama tool ile manuel cache yönetilebilir).

## 🔁 Akış

```
User: claw prompt --model opus "..."
            ↓
1. resolve_model_alias("opus")
   → "claude-opus-4-6"          (alias → canonical)
            ↓
2. detect_provider_kind(...)
   → ProviderKind::Anthropic
            ↓
3. metadata_for_model(...)
   → ProviderMetadata {
       provider, auth_env, base_url_env, default_base_url
     }
            ↓
4. ProviderClient::from_model("opus")
   → match kind {
       Anthropic → ProviderClient::Anthropic(AnthropicClient::from_env())
       Xai       → ProviderClient::Xai(OpenAiCompatClient::from_env(xai_config))
       OpenAi    → ProviderClient::OpenAi(OpenAiCompatClient::from_env(openai_or_dashscope))
     }
            ↓
5. agent loop: client.stream_message(request)
   → match self { ... } dispatch to concrete client
   → returns MessageStream (also an enum wrapper)
            ↓
6. stream.next_event() → StreamEvent (normalized across providers)
```

**Kritik nokta:** `StreamEvent` provider-agnostic. Anthropic SSE ile OpenAI SSE farklı format yollar ama her ikisi de aynı `StreamEvent` enum'una normalize edilir. Bu sayede `run_turn` provider'ı bilmiyor.

## ⚠️ Kritik Tasarım Kararları (Why?)

### 1. Provider kind = wire format değil
**Karar:** [`providers/mod.rs:32-37`] `ProviderKind::{Anthropic, Xai, OpenAi}` 3 variant ama 2 wire format.
**Why:** Provider farklı auth env (`XAI_API_KEY` vs `OPENAI_API_KEY`), farklı base URL, farklı default model'a sahip. Wire format aynı olsa bile bunlar farklı **sağlayıcı**. Karıştırırsan xAI auth ile OpenAI endpoint'ine request atarsın.
**Senin platform:** Provider kayıt sistemini iki seviyeli kur — `Vendor` (kim) + `Protocol` (nasıl konuşur). Yeni provider eklerken protocol seç, vendor metadata ekle.

### 2. Statik MODEL_REGISTRY [`providers/mod.rs:121-203`]
**Karar:** Model alias'lar hardcoded const array. `"opus" → claude-opus-4-6` mapping kod içinde.
**Why:** Model isimleri compile-time'da bilinir ve nadiren değişir. Veritabanı/config'e atmak sadece komplikasyon ekler. KISS.
**Senin platform:** Sen multi-tenant'sın → muhtemelen DB'de `models` tablosu istersin (admin yeni model eklesin diye). Yine de **default registry** kod içinde + **DB override** mümkün olsun. Boot-time'da merge.

### 3. Alias resolution iki seviyeli
**Karar:** `"opus"` (alias) → `"claude-opus-4-6"` (canonical) → `Anthropic` (kind)
**Why:** Aliaslar kullanıcı dostu ("opus" yazması "claude-opus-4-6" yazmaktan kolay). Canonical model ID provider'a gönderilir. Kind dispatch için.
**Senin platform:** Aynı 3-katmanlı resolution. Tenant'lara da custom alias tanımlama yetkisi ver (`tenant.aliases["our-default"] = "claude-sonnet-4-6"`).

### 4. Capability matrix struct olarak
**Karar:** Bool field listesi yerine `ProviderFeatureSupport` enum'u.
**Why:** "Supported/Unsupported" yetmez. `PassthroughAsTool` üçüncü state önemli — bazı feature'lar fallback ile sağlanır (örn. OpenAI'da web search yok ama tool olarak ekleyebilirsin).
**Senin platform:** Aynı modeli kopyala. Tenant UI'da "bu provider'da X özelliği yok" demek yerine "bu provider'da X tool ile sağlanır, biraz daha yavaş" diyebilirsin.

### 5. Prompt cache sadece Anthropic'te
**Karar:** [`client.rs:60-72`] `with_prompt_cache`, `prompt_cache_stats` sadece Anthropic variant'ı için anlamlı; diğerleri pass-through.
**Why:** OpenAI/xAI native prompt cache yok (2026 başı itibariyle bazı modellerde geliyor ama farklı API). Boş impl yazıp her seferinde `None` döndürmek anti-pattern olurdu.
**Senin platform:** Capability matrix'ten feature flag oku, UI'da göster. Cache hit oranı sadece destekleyen provider'larda görünsün.

### 6. MessageStream da enum wrapper
**Karar:** [`client.rs:109-130`] `enum MessageStream { Anthropic(...), OpenAiCompat(...) }` + `next_event()` dispatch.
**Why:** Stream'in **stateful** olduğu için provider-specific kalmalı (Anthropic SSE state machine ≠ OpenAI SSE state machine). Ama dışarıdan tek interface (`next_event`).
**Senin platform:** Aynı pattern — provider-specific stream parser + ortak `StreamEvent` enum.

## 🏢 Multi-Tenant SaaS'e Çeviri

### Provider seçim flow'u
| Claw (single-user) | Sen (multi-tenant) |
|---|---|
| Env var (`ANTHROPIC_API_KEY`) | Tenant secret store (Vault/AWS Secrets Manager) |
| `ProviderClient::from_model(model)` | `provider_for(tenant_id, model_request)` |
| Hardcoded alias registry | DB-backed `models` + default registry merge |
| Single auth source | Tenant-scoped: BYO key VEYA shared pool |
| — | **Cost router:** ucuz model varsa onu seç (Haiku vs Opus) |
| — | **Failover:** Anthropic down ise OpenAI'a fallback |
| — | **Quota:** tenant'ın bu ay token bütçesi |

### Yeni capability'ler (Claw'da yok ama senin için)
1. **Provider failover policy:** Primary down ise secondary'ye route. State machine: `Healthy → Degraded → Failover`.
2. **Cost routing:** Aynı capability'i sağlayan en ucuz model. Compaction için Haiku, kompleks task için Opus.
3. **Region routing:** GDPR için EU tenant → EU endpoint (Anthropic'in regional endpoint'leri).
4. **Rate limit pooling:** Tenant başına RPM bütçesi. Bypass'lar için organization-level limit.
5. **Audit:** Her provider call → tenant_id + model + tokens + cost → billing.

### Wire format sayısı arttıkça
Claw'da 2 wire (Anthropic, OpenAI). Sen'in eklemek isteyeceklerin:
- **Ollama** (local LLM) — OpenAI uyumlu, ama base URL local
- **Google Gemini** — kendi format'ı (Anthropic'e benzer ama farklı)
- **Bedrock** — Anthropic + Cohere + Meta hepsi tek API arkasında

**Strateji:** Her yeni wire format için ayrı client struct. Provider enum büyür, wire client sayısı yavaş büyür. DRY yoluyla.

## ❓ Hızlı Hatırlatma Soruları
- Yeni provider eklemek için kaç yerde değişiklik gerekiyor? (Enum + registry + metadata — kontrollü)
- Wire format aynı olan provider'ları aynı client'ta birleştirdim mi? (DRY)
- Capability matrix kullanıyor muyum, yoksa `if provider == "anthropic"` check'leri etrafa saçtım mı? (Anti-pattern)
- Tenant'ın provider seçimi DB'den mi geliyor, hardcoded mi? (Multi-tenant için DB)
- Failover/cost routing var mı, yoksa tek provider'a kilitliyim mi?
- Stream normalization yapıyor muyum, yoksa her tüketici provider format'ını bilmek zorunda mı?
