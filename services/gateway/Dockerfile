# syntax=docker/dockerfile:1.7

# ─── Build ─────────────────────────────────────────────────────────────
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

COPY src/Gateway.Api/Gateway.Api.csproj src/Gateway.Api/
RUN dotnet restore src/Gateway.Api/Gateway.Api.csproj

COPY src/ src/
RUN dotnet publish src/Gateway.Api/Gateway.Api.csproj \
    -c Release -o /app/publish \
    --no-restore /p:UseAppHost=false

# ─── Runtime ───────────────────────────────────────────────────────────
FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS runtime
WORKDIR /app

# Non-root user
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
