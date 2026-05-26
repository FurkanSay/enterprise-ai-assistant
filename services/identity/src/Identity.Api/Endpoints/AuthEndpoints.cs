using Identity.Application.Auth.Login;
using MediatR;

namespace Identity.Api.Endpoints;

public static class AuthEndpoints
{
    public static IEndpointRouteBuilder MapAuthEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/v1/auth").WithTags("auth");

        group.MapPost("/login", async (LoginCommand command, ISender mediator, CancellationToken ct) =>
        {
            try
            {
                var response = await mediator.Send(command, ct);
                return Results.Ok(response);
            }
            catch (InvalidCredentialsException)
            {
                return Results.Problem(
                    statusCode: StatusCodes.Status401Unauthorized,
                    title: "Invalid credentials",
                    detail: "Email or password is incorrect.");
            }
        });

        // TODO: refresh, logout, password reset
        return app;
    }
}
