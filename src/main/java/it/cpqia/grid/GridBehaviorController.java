package it.cpqia.grid;

import it.cpqia.grid.GridModels.Behavior;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Impostazioni > Comportamento griglie: funzioni disponibili in ciascuna griglia (solo administrator). */
@Controller
@PreAuthorize("hasRole('ADMIN')")
public class GridBehaviorController {

    public record Row(String key, String title, Behavior behavior) {
    }

    public record SaveRow(String key, boolean configurable, boolean moveColumns, boolean sortColumns, boolean pivot, boolean totals, boolean subtotals, Boolean inlineEdit) {
    }

    private final GridRegistry registry;
    private final GridBehaviorService service;

    public GridBehaviorController(GridRegistry registry, GridBehaviorService service) {
        this.registry = registry;
        this.service = service;
    }

    @GetMapping("/admin/grid-behavior")
    public String page(Model m) {
        Map<String, Behavior> saved = service.all();
        List<Row> rows = new ArrayList<>();
        for (GridSpec s : registry.all()) {
            rows.add(new Row(s.key(), s.title, saved.getOrDefault(s.key(), Behavior.ALL)));
        }
        m.addAttribute("rows", rows);
        m.addAttribute("active", "grid-behavior");
        return "admin/grid-behavior";
    }

    @PostMapping("/api/grid-behavior")
    @ResponseBody
    public Map<String, Object> save(@RequestBody List<SaveRow> rows) {
        for (SaveRow r : rows) {
            if (registry.get(r.key()) == null) {
                throw new IllegalArgumentException("Griglia sconosciuta: " + r.key());
            }
            service.save(r.key(), new Behavior(r.configurable(), r.moveColumns(), r.sortColumns(), r.pivot(), r.totals(), r.subtotals(), !Boolean.FALSE.equals(r.inlineEdit())));
        }
        return Map.of("ok", true);
    }
}
