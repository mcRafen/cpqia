package it.cpqia.grid;

import it.cpqia.domain.AppUser;
import it.cpqia.grid.GridModels.CreateRequest;
import it.cpqia.grid.GridModels.Data;
import it.cpqia.grid.GridModels.Layout;
import it.cpqia.grid.GridModels.LayoutRequest;
import it.cpqia.grid.GridModels.UpdateRequest;
import it.cpqia.security.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** API JSON delle griglie (/api/grid/...). Ogni chiamata verifica permessi e visibilita' dei dati lato server. */
@RestController
@RequestMapping("/api/grid")
public class GridController {

    private final GridService grids;
    private final CurrentUserService current;

    public GridController(GridService grids, CurrentUserService current, it.cpqia.trash.TrashService trashService, it.cpqia.geo.GeoAgentService geoAgent) {
        this.geoAgent = geoAgent;
        this.trashService = trashService;
        this.grids = grids;
        this.current = current;
    }

    private final it.cpqia.trash.TrashService trashService;
    private final it.cpqia.geo.GeoAgentService geoAgent;

    /** pulsante «Geolocalizza» nella scheda di una sede Sanmarco (solo administrator): calcola e salva latitudine e longitudine */
    @PostMapping("/sedi/{id}/geolocate")
    public Map<String, Object> geolocateSede(@PathVariable long id, Authentication auth) {
        if (!current.get(auth).administrator()) {
            throw new AccessDeniedException("Solo un amministratore può geolocalizzare le sedi");
        }
        return geoAgent.locateNow("sede", id);
    }

    @PostMapping("/trash/empty")
    public Map<String, Object> emptyTrash(Authentication auth) {
        var r = trashService.purgeAll(current.get(auth));
        String msg = r.deleted() + " elementi eliminati definitivamente" + (r.skipped() > 0 ? "; " + r.skipped() + " restano nel Cestino perché ancora collegati ad altri dati" : "");
        return Map.of("ok", true, "message", msg, "deleted", r.deleted(), "skipped", r.skipped());
    }

    @GetMapping("/{key}")
    public Data data(@PathVariable String key, Authentication auth) {
        return grids.data(key, current.get(auth));
    }

    @PatchMapping("/{key}/{id}")
    public Map<String, Object> update(@PathVariable String key, @PathVariable String id, @RequestBody UpdateRequest req, Authentication auth) {
        return grids.update(key, id, req.field(), req.value(), current.get(auth));
    }

    /** scheda di modifica: aggiorna piu' campi della riga in una volta */
    @PostMapping("/{key}/{id}/update")
    public Map<String, Object> updateMany(@PathVariable String key, @PathVariable String id, @RequestBody CreateRequest req, Authentication auth) {
        return grids.updateMany(key, id, req.values(), current.get(auth));
    }

    @PostMapping("/{key}")
    public Map<String, Object> create(@PathVariable String key, @RequestBody CreateRequest req, Authentication auth) {
        return grids.create(key, req.values(), current.get(auth));
    }

    @PostMapping("/{key}/{id}/delete")
    public Map<String, Object> delete(@PathVariable String key, @PathVariable String id, Authentication auth) {
        grids.delete(key, id, current.get(auth));
        return Map.of("ok", true, "message", "Elemento spostato nel Cestino");
    }

    /** elimina le righe selezionate (griglie che lo prevedono) */
    @PostMapping("/{key}/delete-many")
    public Map<String, Object> deleteMany(@PathVariable String key, @RequestBody GridModels.IdsRequest req, Authentication auth) {
        int n = grids.deleteMany(key, req.ids(), current.get(auth));
        return Map.of("ok", true, "deleted", n, "message", n + (n == 1 ? " elemento eliminato" : " elementi eliminati"));
    }

    @PostMapping("/{key}/{id}/action/{name}")
    public Map<String, Object> action(@PathVariable String key, @PathVariable String id, @PathVariable String name, Authentication auth) {
        return Map.of("ok", true, "message", grids.action(key, name, id, current.get(auth)));
    }

    public record ExportRequest(List<String> columns, List<String> rows) {
    }

    @PostMapping("/{key}/export.xlsx")
    public ResponseEntity<byte[]> exportXlsx(@PathVariable String key, @RequestBody ExportRequest req, Authentication auth) {
        byte[] body = grids.exportXlsx(key, req.columns(), req.rows(), current.get(auth));
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + key + ".xlsx\"")
                .contentType(org.springframework.http.MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }

    // ---- impostazioni per utente (righe per pagina)
    @GetMapping("/{key}/settings")
    public Map<String, Object> settings(@PathVariable String key, Authentication auth) {
        return Map.of("pageSize", grids.pageSize(key, current.get(auth)));
    }

    @PostMapping("/{key}/settings")
    public Map<String, Object> saveSettings(@PathVariable String key, @RequestBody Map<String, Integer> body, Authentication auth) {
        Integer size = body.get("pageSize");
        if (size == null) {
            throw new IllegalArgumentException("Numero di righe per pagina mancante");
        }
        grids.savePageSize(key, size, current.get(auth));
        return Map.of("ok", true, "pageSize", size);
    }

    // ---- viste salvate
    @GetMapping("/{key}/layouts")
    public List<Layout> layouts(@PathVariable String key, Authentication auth) {
        return grids.layouts(key, current.get(auth));
    }

    @PostMapping("/{key}/layouts")
    public Map<String, Object> saveLayout(@PathVariable String key, @RequestBody LayoutRequest req, Authentication auth) {
        AppUser u = current.get(auth);
        grids.saveLayout(key, req.name(), req.config(), Boolean.TRUE.equals(req.makeDefault()), u);
        return Map.of("ok", true);
    }

    @PostMapping("/{key}/layouts/{id}/default")
    public Map<String, Object> defaultLayout(@PathVariable String key, @PathVariable long id, @RequestBody Map<String, Boolean> body, Authentication auth) {
        grids.setDefaultLayout(key, id, Boolean.TRUE.equals(body.get("on")), current.get(auth));
        return Map.of("ok", true);
    }

    @DeleteMapping("/{key}/layouts/{id}")
    public Map<String, Object> deleteLayout(@PathVariable String key, @PathVariable long id, Authentication auth) {
        grids.deleteLayout(key, id, current.get(auth));
        return Map.of("ok", true);
    }

    // ---- errori in formato JSON ({error: messaggio}), letti da grid.js
    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(Map.of("message", e.getMessage() == null ? "Richiesta non valida" : e.getMessage()));
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> denied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage() == null ? "Non autorizzato" : e.getMessage()));
    }
}
