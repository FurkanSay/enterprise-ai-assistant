// YARP reverse proxy registration — route table lives in appsettings.json
// under "ReverseProxy".
namespace Gateway.Api.Extensions;

public static class ProxyExtensions
{
    public static void AddReverseProxy(this WebApplicationBuilder builder)
    {
        builder.Services
            .AddReverseProxy()
            .LoadFromConfig(builder.Configuration.GetSection("ReverseProxy"));
    }
}
