# syntax=docker/dockerfile:1.7

FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src
COPY src/ src/
COPY Identity.sln .
RUN dotnet restore Identity.sln
RUN dotnet publish src/Identity.Api/Identity.Api.csproj -c Release -o /app/publish --no-restore /p:UseAppHost=false

FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS runtime
WORKDIR /app
RUN groupadd -r identity && useradd -r -g identity -d /app -s /sbin/nologin identity
COPY --from=build --chown=identity:identity /app/publish .
ENV ASPNETCORE_URLS=http://+:8081 DOTNET_NOLOGO=true
EXPOSE 8081
USER identity
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 \
    CMD curl -fsS http://localhost:8081/health/live || exit 1
ENTRYPOINT ["dotnet", "Identity.Api.dll"]
