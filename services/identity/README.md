# 🟦 Identity

> **C# .NET 10** — OAuth2 / OIDC, user & tenant CRUD, JWT issuance. **Clean Architecture** 4 katman.

## Sorumluluk

- User & tenant CRUD, role assignment
- Login / refresh token / logout / password reset
- JWT issuance (HS256 dev, RS256 prod — RSA key in Vault)
- RBAC policy hosting

## Sorumluluk dışı

- JWT validation (Gateway yapar)
- Document yetkilendirme (Documents servisi kendi RBAC layer'ı)

## Clean Architecture katmanları

```
src/
├── Identity.Domain/         ← Entities, value objects, business rules
│   └── Zero external deps   ← EF Core, MediatR, ASP.NET — HİÇBİRİ
├── Identity.Application/    ← Use cases (MediatR), validators (FluentValidation)
│   └── Depends on Domain
├── Identity.Infrastructure/ ← EF Core, BCrypt, JWT impl, repositories
│   └── Depends on Application + Domain
└── Identity.Api/            ← Minimal API endpoints, OTel, Serilog
    └── Depends on all three (composition root)
```

**Bağımlılık yönü:** Api → Infrastructure → Application → Domain. **Asla** ters.

## Bir use case'in akışı (Login örneği)

```
HTTP POST /v1/auth/login
    ↓
Api/Endpoints/AuthEndpoints.cs
    ↓ mediator.Send(LoginCommand)
Application/Auth/Login/LoginCommand.cs
    ├─ LoginCommandValidator (FluentValidation)
    └─ LoginCommandHandler
         ├─ IUserRepository.FindByEmailAsync()  ← abstraction
         ├─ IPasswordHasher.Verify()            ← abstraction
         └─ ITokenService.Issue()               ← abstraction
              ↓ runtime'da inject edilir:
Infrastructure/
   ├─ Persistence/Repositories/UserRepository.cs (EF Core)
   ├─ Services/BCryptPasswordHasher.cs           (BCrypt.Net)
   └─ Services/JwtTokenService.cs                (JWT)
    ↓ TokenPair
HTTP 200 { accessToken, refreshToken, ... }
```

Application **HİÇBİR** infrastructure detayı bilmez — sadece abstraction'lar. Test edebilmek için `IUserRepository`'yi mockla, EF Core'a dokunmazsın.

## Çalıştır

```bash
# Migration oluştur (Infrastructure projesinden, Api startup projesi)
dotnet ef migrations add Initial \
    --project src/Identity.Infrastructure \
    --startup-project src/Identity.Api

# Migration uygula
dotnet ef database update \
    --project src/Identity.Infrastructure \
    --startup-project src/Identity.Api

# Dev server
cd src/Identity.Api
dotnet run    # http://localhost:8081

# Test
dotnet test
```

## Tasarım kararları

### Why Clean Architecture
- **Domain testlenebilir** — DB/HTTP yok, sadece C# objesi.
- **Use case'ler izole** — her handler bağımsız.
- **Infrastructure swap** — bugün Postgres, yarın CosmosDB; sadece Infrastructure değişir.
- **Avrupa enterprise standartı** — Spring Boot kuşağı için familiar görünür.

### Why MediatR
CQRS lite — komut/sorgu handler'ları decouple. Pipeline behavior (validation, logging, transactions) middleware gibi takılır.

### Why HS256 dev / RS256 prod
HS256 tek key — dev için yeter. RS256 asymmetric — Gateway public key ile validate eder, Identity private key ile sign eder. Production'da Vault'tan okuyacağız.
