using FluentValidation;
using MediatR;

namespace Identity.Application.Common;

/// <summary>
/// MediatR pipeline behavior — runs every FluentValidation IValidator
/// registered for the incoming request type before the handler executes.
/// Throws ValidationException on the first failure, which the endpoints
/// translate into 422 ValidationProblem responses.
///
/// Without this, the AbstractValidator subclasses we wrote (e.g.
/// RegisterCommandValidator) would only fire if someone explicitly called
/// `validator.Validate(...)`. We want them to be automatic.
/// </summary>
public sealed class ValidationBehavior<TRequest, TResponse>(
    IEnumerable<IValidator<TRequest>> validators)
    : IPipelineBehavior<TRequest, TResponse>
    where TRequest : notnull
{
    public async Task<TResponse> Handle(
        TRequest request,
        RequestHandlerDelegate<TResponse> next,
        CancellationToken ct)
    {
        if (!validators.Any())
            return await next();

        var context = new ValidationContext<TRequest>(request);
        var results = await Task.WhenAll(validators.Select(v => v.ValidateAsync(context, ct)));
        var failures = results.SelectMany(r => r.Errors).Where(f => f is not null).ToList();
        if (failures.Count > 0)
            throw new ValidationException(failures);

        return await next();
    }
}
