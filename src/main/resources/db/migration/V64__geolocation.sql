-- Geolocalizzazione di account e sedi Sanmarco, eseguita dall'agente Geo (Impostazioni > Integrazioni > Geo).
-- geo_status: PENDING = indirizzo inserito/modificato, da geolocalizzare automaticamente; TODO = presente prima dell'agente, lo elabora solo
-- l'esecuzione manuale o pianificata; NOT_FOUND = indirizzo non trovato sulla mappa; OK = coordinate calcolate (o di partenza).
ALTER TABLE account ADD COLUMN geo_status VARCHAR(10), ADD COLUMN geo_at TIMESTAMP;
ALTER TABLE sede    ADD COLUMN geo_status VARCHAR(10), ADD COLUMN geo_at TIMESTAMP;
UPDATE account SET geo_status = CASE WHEN latitude IS NOT NULL AND longitude IS NOT NULL THEN 'OK' ELSE 'TODO' END;
UPDATE sede    SET geo_status = CASE WHEN latitude IS NOT NULL AND longitude IS NOT NULL THEN 'OK' ELSE 'TODO' END;

-- inserimento o modifica dell'indirizzo (da qualunque percorso: scheda, griglia, importazione): coordinate azzerate e account da geolocalizzare
CREATE FUNCTION geo_reset_account() RETURNS TRIGGER AS $fn$
BEGIN
    IF TG_OP = 'INSERT'
       OR (OLD.street_type, OLD.street_name, OLD.street_number, OLD.postal_code, OLD.city, OLD.province)
          IS DISTINCT FROM (NEW.street_type, NEW.street_name, NEW.street_number, NEW.postal_code, NEW.city, NEW.province) THEN
        NEW.latitude := NULL; NEW.longitude := NULL; NEW.geo_key := NULL; NEW.geo_at := NULL; NEW.geo_status := 'PENDING';
    END IF;
    RETURN NEW;
END
$fn$ LANGUAGE plpgsql;
CREATE TRIGGER trg_geo_account BEFORE INSERT OR UPDATE ON account FOR EACH ROW EXECUTE FUNCTION geo_reset_account();

CREATE FUNCTION geo_reset_sede() RETURNS TRIGGER AS $fn$
BEGIN
    IF TG_OP = 'INSERT'
       OR (OLD.name, OLD.street, OLD.street_number, OLD.postal_code, OLD.city, OLD.province)
          IS DISTINCT FROM (NEW.name, NEW.street, NEW.street_number, NEW.postal_code, NEW.city, NEW.province) THEN
        NEW.latitude := NULL; NEW.longitude := NULL; NEW.geo_key := NULL; NEW.geo_at := NULL; NEW.geo_status := 'PENDING';
    END IF;
    RETURN NEW;
END
$fn$ LANGUAGE plpgsql;
CREATE TRIGGER trg_geo_sede BEFORE INSERT OR UPDATE ON sede FOR EACH ROW EXECUTE FUNCTION geo_reset_sede();

-- agente Geo: pianificazione e storico delle esecuzioni (stessa struttura degli agenti di sincronizzazione)
CREATE TABLE geo_agent_config (
    id              INT PRIMARY KEY CHECK (id = 1),
    enabled         BOOLEAN     NOT NULL DEFAULT FALSE,
    mode            VARCHAR(10) NOT NULL DEFAULT 'DAILY' CHECK (mode IN ('INTERVAL', 'DAILY')),
    interval_min    INT         NOT NULL DEFAULT 60 CHECK (interval_min BETWEEN 5 AND 1440),
    daily_time      VARCHAR(5)  NOT NULL DEFAULT '03:00',
    last_run_at     TIMESTAMP
);
INSERT INTO geo_agent_config (id) VALUES (1);
CREATE TABLE geo_agent_run (
    id            BIGSERIAL PRIMARY KEY,
    started_at    TIMESTAMP   NOT NULL DEFAULT now(),
    finished_at   TIMESTAMP,
    trigger_type  VARCHAR(10) NOT NULL,                   -- SCHEDULED | MANUAL | AUTO (dopo un inserimento / una modifica)
    ok            BOOLEAN,
    read_count    INT NOT NULL DEFAULT 0,                  -- indirizzi elaborati
    inserted      INT NOT NULL DEFAULT 0,                  -- localizzati
    updated       INT NOT NULL DEFAULT 0,
    unchanged     INT NOT NULL DEFAULT 0,
    skipped       INT NOT NULL DEFAULT 0,                  -- non trovati
    message       VARCHAR(300),
    details       TEXT
);

-- i dati di geolocalizzazione sono automatici: non entrano nel journal delle modifiche
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
