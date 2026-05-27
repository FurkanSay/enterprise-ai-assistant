using FluentValidation;
using Identity.Application.Auth.Login;
using Identity.Application.Auth.Me;
using Identity.Application.Auth.Register;
using MediatR;
using Microsoft.AspNetCore.Mvc;

namespace Identity.Api.Endpoints;

public static class AuthEndpoints
{
    public static IEndpointRouteBuilder MapAuthEndpoints(this IEndpointRouteBuilder app)
    {
        var group = app.MapGroup("/v1/auth").WithTags("auth");

        // ── POST /v1/auth/register ──────────────────────────────────────
        group.MapPost("/register", async (
            RegisterCommand command,
            ISender mediator,
            CancellationToken ct) =>
        {
            try
            {
                var response = await mediator.Send(command, ct);
                return Results.Created($"/v1/auth/users/{response.UserId}", response);
            }
            catch (ValidationException ex)
            {
                return Results.ValidationProblem(
                    ex.Errors.GroupBy(e => e.PropertyName)
                             .ToDictionary(g => g.Key, g => g.Select(e => e.ErrorMessage).ToArray()));
            }
            catch (EmailAlreadyRegisteredException ex)
            {
                return Results.Problem(
                    statusCode: StatusCodes.Status409Conflict,
                    title: "Email already registered",
                    detail: ex.Message);
            }
        });

        // ── POST /v1/auth/login ─────────────────────────────────────────
        group.MapPost("/login", async (
            LoginCommand command,
            ISender mediator,
            CancellationToken ct) =>
        {
            try
            {
                var response = await mediator.Send(command, ct);
                return Results.Ok(response);
            }
            catch (ValidationException ex)
            {
                return Results.ValidationProblem(
                    ex.Errors.GroupBy(e => e.PropertyName)
                             .ToDictionary(g => g.Key, g => g.Select(e => e.ErrorMessage).ToArray()));
            }
            catch (InvalidCredentialsException)
            {
                return Results.Problem(
                    statusCode: StatusCodes.Status401Unauthorized,
                    title: "Invalid credentials",
                    detail: "Email or password is incorrect.");
            }
        });

        // ── GET /v1/auth/me ─────────────────────────────────────────────
        // Gateway-protected. We read tenant/user from the forwarded
        // headers, NOT the JWT — downstream services never re-validate
        // tokens (single point of truth = Gateway).
        group.MapGet("/me", async (
            [FromHeader(Name = "X-User-Id")] Guid? userId,
            [FromHeader(Name = "X-Tenant-Id")] Guid? tenantId,
            ISender mediator,
            CancellationToken ct) =>
        {
            if (userId is null || tenantId is null || userId == Guid.Empty || tenantId == Guid.Empty)
                return Results.Problem(
                    statusCode: StatusCodes.Status401Unauthorized,
                    title: "Missing tenant context",
                    detail: "Gateway must forward X-User-Id and X-Tenant-Id.");

            try
            {
                var response = await mediator.Send(new MeQuery(userId.Value, tenantId.Value), ct);
                return Results.Ok(response);
            }
            catch (UserNotFoundException)
            {
                return Results.Problem(
                    statusCode: StatusCodes.Status404NotFound,
                    title: "User not found",
                    detail: "The token references a user that no longer exists in this tenant.");
            }
        });

        return app;
    }
}
