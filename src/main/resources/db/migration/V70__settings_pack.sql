-- Aggiornamento delle impostazioni: il WAR puo' contenere un pacchetto (settings/settings-pack.json) con il contenuto delle tabelle di Impostazioni del
-- database di sviluppo; all'avvio viene applicato una sola volta per ogni versione del pacchetto (impronta salvata qui).
CREATE TABLE settings_pack_applied (
    id          INT PRIMARY KEY CHECK (id = 1),
    sha256      CHAR(64)  NOT NULL,
    applied_at  TIMESTAMP NOT NULL DEFAULT now(),
    summary     TEXT
);

-- durante l'importazione (variabile di transazione cpqia.settings_import = on) il journal e la geolocalizzazione automatica non intervengono
CREATE OR REPLACE FUNCTION audit_trigger() RETURNS TRIGGER AS $fn$
DECLARE
    pk_cols   TEXT[] := string_to_array(coalesce(TG_ARGV[0], 'id'), ',');
    noisy     TEXT[] := ARRAY['updated_at', 'last_login_at', 'last_run_at', 'latitude', 'longitude', 'geo_key', 'geo_status', 'geo_at'];
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
    IF current_setting('cpqia.settings_import', true) = 'on' THEN RETURN NULL; END IF;     -- importazione delle impostazioni (aggiornamento del WAR): non si registra
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

CREATE OR REPLACE FUNCTION geo_reset_account() RETURNS TRIGGER AS $fn$
BEGIN
    IF current_setting('cpqia.settings_import', true) = 'on' THEN RETURN NEW; END IF;       -- importazione delle impostazioni: le coordinate del pacchetto restano
    IF TG_OP = 'INSERT'
       OR (OLD.street_type, OLD.street_name, OLD.street_number, OLD.postal_code, OLD.city, OLD.province)
          IS DISTINCT FROM (NEW.street_type, NEW.street_name, NEW.street_number, NEW.postal_code, NEW.city, NEW.province) THEN
        NEW.latitude := NULL; NEW.longitude := NULL; NEW.geo_key := NULL; NEW.geo_at := NULL; NEW.geo_status := 'PENDING';
    END IF;
    RETURN NEW;
END
$fn$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION geo_reset_sede() RETURNS TRIGGER AS $fn$
BEGIN
    IF current_setting('cpqia.settings_import', true) = 'on' THEN RETURN NEW; END IF;       -- importazione delle impostazioni: le coordinate del pacchetto restano
    IF TG_OP = 'INSERT'
       OR (OLD.name, OLD.street, OLD.street_number, OLD.postal_code, OLD.city, OLD.province)
          IS DISTINCT FROM (NEW.name, NEW.street, NEW.street_number, NEW.postal_code, NEW.city, NEW.province) THEN
        NEW.latitude := NULL; NEW.longitude := NULL; NEW.geo_key := NULL; NEW.geo_at := NULL; NEW.geo_status := 'PENDING';
    END IF;
    RETURN NEW;
END
$fn$ LANGUAGE plpgsql;
