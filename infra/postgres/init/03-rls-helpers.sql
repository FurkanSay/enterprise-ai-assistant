-- RLS helper functions.
-- Every connection from a service MUST call:
--     SET app.current_tenant_id = '<tenant-uuid>';
-- before issuing any query against tenant-scoped tables.
--
-- The helper below returns NULL if the setting is missing (instead of error),
-- so that infrastructure/admin queries (without tenant context) get an empty
-- result rather than crashing — fail-safe, not fail-open.

CREATE OR REPLACE FUNCTION public.current_tenant_id() RETURNS uuid
    LANGUAGE sql STABLE AS $$
    SELECT NULLIF(current_setting('app.current_tenant_id', true), '')::uuid;
$$;

COMMENT ON FUNCTION public.current_tenant_id() IS
'Returns the tenant_id set on the current session via SET app.current_tenant_id. NULL if unset.';
