# syntax=docker/dockerfile:1.7
# Build context = repo root.

FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src

COPY services/Directory.Build.props                                         services/
COPY services/identity/Identity.sln                                        services/identity/
COPY services/identity/src/Identity.Api/Identity.Api.csproj                services/identity/src/Identity.Api/
COPY services/identity/src/Identity.Application/Identity.Application.csproj services/identity/src/Identity.Application/
COPY services/identity/src/Identity.Domain/Identity.Domain.csproj          services/identity/src/Identity.Domain/
COPY services/identity/src/Identity.Infrastructure/Identity.Infrastructure.csproj services/identity/src/Identity.Infrastructure/
COPY libs/generated/dotnet/Kai.Protos.csproj                                libs/generated/dotnet/

RUN dotnet restore services/identity/Identity.sln

COPY services/identity/src/   services/identity/src/
COPY libs/generated/dotnet/   libs/generated/dotnet/

RUN dotnet publish services/identity/src/Identity.Api/Identity.Api.csproj \
    -c Release -o /app/publish \
    --no-restore /p:UseAppHost=false

FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS runtime
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

WORKDIR /app
RUN groupadd -r identity && useradd -r -g identity -d /app -s /sbin/nologin identity

COPY --from=build --chown=identity:identity /app/publish .

ENV ASPNETCORE_URLS=http://+:8081 \
    DOTNET_NOLOGO=true

EXPOSE 8081
USER identity

HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -fsS http://localhost:8081/health/live || exit 1

ENTRYPOINT ["dotnet", "Identity.Api.dll"]
