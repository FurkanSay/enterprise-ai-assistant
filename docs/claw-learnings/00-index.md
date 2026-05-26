# Claw Code — Mimari Öğrenme Notları

Bu klasör, `claw-code` (Rust implementation of Claude Code) kaynak kodunun incelenmesinden çıkan **kurumsal AI asistan platformuna uygulanabilir** mimari kararları içerir.

## Amaç
- Claw'ın çözdüğü problemleri **anlamak** (kavram seviyesinde, kod ezberi değil).
- Her katmandan **multi-tenant SaaS** mimarimize **birebir kopyalanacak** vs. **uyarlanacak** kararları ayırmak.
- Geliştirme sırasında "bu kısmı nasıl yapmıştık?" sorusuna **kavramsal hatırlatıcı** olmak.

## İçindekiler

| # | Dosya | Konu | Durum |
|---|---|---|---|
| 01 | [01-agent-loop.md](./01-agent-loop.md) | `run_turn()` — agent loop'un kalbi, 5 faz, iterasyon yönetimi | ✅ |
| 02 | [02-permission-enforcer.md](./02-permission-enforcer.md) | Permission hiyerarşisi, dynamic classification, hook layering | ✅ |
| 03 | [03-tool-registry.md](./03-tool-registry.md) | GlobalToolRegistry, ToolSpec, base/deferred, 40+ tool inventory, dynamic classification, ToolSearch | ✅ |
| 04 | [04-provider-abstraction.md](./04-provider-abstraction.md) | Provider trait, enum dispatch, multi-LLM routing | ✅ |
| 05 | [05-session-persistence.md](./05-session-persistence.md) | Session struct, JSONL append-only, multi-tenant'a uyarlama | ✅ |

## Kaynak repo
Lokal klon: [`claw-code/`](../../claw-code/) — repo: https://github.com/ultraworkers/claw-code

## Kullanım kuralı
Bu notlar **kavramsal referans**. Spesifik bir tool veya kod yazarken:
1. İlgili katmanın notunu aç → temel kararı hatırla.
2. **Sonra** koda gir, ezberden değil prensipten yaz.
3. Notu **güncellemek** isterken: önce kavramı doğrula (claw kodunu tekrar oku), sonra not'u güncelle.
