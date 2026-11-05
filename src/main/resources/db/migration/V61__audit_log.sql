-- Journaling delle modifiche: ogni INSERT / UPDATE (campo per campo) / DELETE sui record applicativi viene registrato da un trigger del database,
-- quindi anche gli aggiornamenti fatti da import, sincronizzazioni e pagine di amministrazione. L'utente arriva dall'applicazione
-- (variabile di sessione cpqia.user_email, impostata alla richiesta); data e ora hanno la precisione del secondo.
CREATE TABLE audit_log (
    id            BIGSERIAL PRIMARY KEY,
    at            TIMESTAMP    NOT NULL DEFAULT date_trunc('second', clock_timestamp()),
    table_name    VARCHAR(60)  NOT NULL,
    record_id     VARCHAR(100) NOT NULL,
    record_label  VARCHAR(300),
    action        VARCHAR(8)   NOT NULL CHECK (action IN ('INSERT', 'UPDATE', 'DELETE', 'RESTORE', 'PURGE')),   -- DELETE = eliminazione logica (Cestino), PURGE = definitiva
    user_id       BIGINT,
    user_name     VARCHAR(200) NOT NULL,
    changes       JSONB                                                  -- UPDATE: {campo: {old, new}}; INSERT: {campo: valore}; DELETE/PURGE: istantanea del record
);
CREATE INDEX ix_audit_record ON audit_log (table_name, record_id, at DESC, id DESC);
CREATE INDEX ix_audit_at ON audit_log (at DESC);

-- mascheramento dei campi riservati (password, segreti, token)
CREATE FUNCTION audit_mask(j JSONB) RETURNS JSONB AS $fn$
    SELECT coalesce(jsonb_object_agg(k, CASE WHEN k ~* '(password|secret|token)' THEN '"***"'::jsonb ELSE v END), '{}'::jsonb)
    FROM jsonb_each(j) AS e(k, v)
$fn$ LANGUAGE sql IMMUTABLE;

CREATE FUNCTION audit_trigger() RETURNS TRIGGER AS $fn$
DECLARE
    pk_cols   TEXT[] := string_to_array(coalesce(TG_ARGV[0], 'id'), ',');
    noisy     TEXT[] := ARRAY['updated_at', 'last_login_at', 'last_run_at'];
    rec       JSONB;
    old_j     JSONB;
    new_j     JSONB;
    act       TEXT;
    chg       JSONB;
    rid       TEXT;
    lbl       TEXT;
    uemail    TEXT := nullif(lower(current_setting('cpqia.user_email', true)), '');
    uid       BIGINT;
    uname     TEXT;
BEGIN
    IF TG_OP = 'DELETE' THEN
        rec := to_jsonb(OLD);
    ELSE
        rec := to_jsonb(NEW);
    END IF;
    SELECT string_agg(rec ->> c, '/' ORDER BY ord) INTO rid FROM unnest(pk_cols) WITH ORDINALITY AS u(c, ord);
    lbl := left(coalesce(rec ->> 'number', rec ->> 'code', rec ->> 'name', rec ->> 'company_name', rec ->> 'email', rec ->> 'model', rec ->> 'grid_key',
                         rec ->> 'label', rec ->> 'description'), 300);

    IF TG_OP = 'INSERT' THEN
        act := 'INSERT';
        chg := audit_mask(rec - noisy);
    ELSIF TG_OP = 'DELETE' THEN
        act := 'PURGE';
        chg := audit_mask(rec - noisy);
    ELSE
        old_j := to_jsonb(OLD) - noisy;
        new_j := to_jsonb(NEW) - noisy;
        IF new_j ? 'deleted_at' AND (old_j ->> 'deleted_at') IS NULL AND (new_j ->> 'deleted_at') IS NOT NULL THEN
            act := 'DELETE';
            chg := audit_mask(new_j - 'deleted_at' - 'deleted_by');
        ELSIF new_j ? 'deleted_at' AND (old_j ->> 'deleted_at') IS NOT NULL AND (new_j ->> 'deleted_at') IS NULL THEN
            act := 'RESTORE';
            chg := NULL;
        ELSE
            act := 'UPDATE';
            SELECT jsonb_object_agg(k, jsonb_build_object('old', old_j -> k, 'new', new_j -> k)) INTO chg
            FROM jsonb_object_keys(new_j) AS k
            WHERE old_j -> k IS DISTINCT FROM new_j -> k AND k NOT IN ('deleted_at', 'deleted_by');
            IF chg IS NULL THEN
                RETURN NULL;                                        -- nessun campo cambiato: nulla da registrare
            END IF;
            SELECT coalesce(jsonb_object_agg(k, CASE WHEN k ~* '(password|secret|token)' THEN '{"old":"***","new":"***"}'::jsonb ELSE v END), '{}'::jsonb)
            INTO chg FROM jsonb_each(chg) AS e(k, v);
        END IF;
    END IF;

    IF uemail IS NOT NULL THEN
        SELECT id, coalesce(nullif(full_name, ''), email) INTO uid, uname FROM app_user WHERE lower(email) = uemail LIMIT 1;
        uname := coalesce(uname, uemail);
    ELSE
        uname := 'Sistema';
    END IF;
    INSERT INTO audit_log (table_name, record_id, record_label, action, user_id, user_name, changes)
    VALUES (TG_TABLE_NAME, rid, lbl, act, uid, uname, chg);
    RETURN NULL;
END
$fn$ LANGUAGE plpgsql;

CREATE FUNCTION audit_attach(tbl TEXT, pk TEXT DEFAULT 'id') RETURNS VOID AS $fn$
BEGIN
    EXECUTE format('CREATE TRIGGER trg_audit AFTER INSERT OR UPDATE OR DELETE ON %I FOR EACH ROW EXECUTE FUNCTION audit_trigger(%L)', tbl, pk);
END
$fn$ LANGUAGE plpgsql;

SELECT audit_attach(t) FROM unnest(ARRAY['account', 'address_type', 'agent', 'app_driver', 'app_mode', 'app_user', 'application', 'company_type', 'comuni',
    'extra_group', 'extra_option', 'extra_service', 'ibmi_product', 'module', 'module_rule', 'offer', 'power_model', 'price_group', 'pricing_tier',
    'professional_profile', 'sede', 'travel_rate', 'agent_sync_config', 'ibmi_sync_config']) AS t;
SELECT audit_attach('account_profile_rate', 'account_id,profile_id');
SELECT audit_attach('module_price', 'module_id,mode_code');
SELECT audit_attach('price_group_base', 'group_id,mode_code');
SELECT audit_attach('pricing_tier_price', 'tier_id,mode_code');
SELECT audit_attach('grid_behavior', 'grid_key');
