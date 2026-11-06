package it.cpqia.web;

import it.cpqia.security.CurrentUserService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Preferenze di aspetto dell'utente (modalita' giorno / notte / sistema e palette di colori), salvate sul server. */
@RestController
public class UiPrefsController {

    public static final List<String> THEMES = List.of("light", "dark", "system");
    public static final List<String> PALETTES = List.of("teal", "viola", "foresta", "terra");

    public record Prefs(String theme, String palette) {
    }

    private final JdbcClient jdbc;
    private final CurrentUserService current;

    public UiPrefsController(JdbcClient jdbc, CurrentUserService current) {
        this.jdbc = jdbc;
        this.current = current;
    }

    /** preferenze salvate dell'utente (valori nulli = non scelte) */
    public Prefs load(long userId) {
        return jdbc.sql("select ui_theme, ui_palette from user_preference where user_id = :u").param("u", userId)
                .query((rs, n) -> new Prefs(rs.getString(1), rs.getString(2))).optional().orElse(new Prefs(null, null));
    }

    /** salva solo i valori indicati (tema e/o palette) */
    @PostMapping("/api/ui-prefs")
    public Map<String, Object> save(@RequestBody Prefs p, Authentication auth) {
        long uid = current.get(auth).id();
        String theme = p.theme() != null && THEMES.contains(p.theme()) ? p.theme() : null;
        String palette = p.palette() != null && PALETTES.contains(p.palette()) ? p.palette() : null;
        jdbc.sql("""
                insert into user_preference (user_id, ui_theme, ui_palette) values (:u, :t, :p)
                on conflict (user_id) do update set ui_theme = coalesce(:t, user_preference.ui_theme),
                    ui_palette = coalesce(:p, user_preference.ui_palette), updated_at = now()""")
                .param("u", uid).param("t", theme).param("p", palette).update();
        return Map.of("ok", true);
    }
}
