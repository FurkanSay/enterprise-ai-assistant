# syntax=docker/dockerfile:1.7
# Build context = repo root (so this Dockerfile can see libs/generated/).

# ─── Build ─────────────────────────────────────────────────────────────
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

# Restore step uses only project manifests for layer cache.
COPY services/Directory.Build.props                      services/
COPY services/gateway/src/Gateway.Api/Gateway.Api.csproj services/gateway/src/Gateway.Api/
COPY libs/generated/dotnet/Kai.Protos.csproj             libs/generated/dotnet/
RUN dotnet restore services/gateway/src/Gateway.Api/Gateway.Api.csproj

# Real source.
COPY services/gateway/src/      services/gateway/src/
COPY libs/generated/dotnet/     libs/generated/dotnet/

RUN dotnet publish services/gateway/src/Gateway.Api/Gateway.Api.csproj \
    -c Release -o /app/publish \
    --no-restore /p:UseAppHost=false

# ─── Runtime ───────────────────────────────────────────────────────────
FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app
RUN groupadd -r gateway && useradd -r -g gateway -d /app -s /sbin/nologin gateway

COPY --from=build --chown=gateway:gateway /app/publish .

ENV ASPNETCORE_URLS=http://+:8080 \
    DOTNET_RUNNING_IN_CONTAINER=true \
    DOTNET_NOLOGO=true

EXPOSE 8080
USER gateway

HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD curl -fsS http://localhost:8080/health/live || exit 1

ENTRYPOINT ["dotnet", "Gateway.Api.dll"]
