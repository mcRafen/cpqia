package it.cpqia.web;

import it.cpqia.domain.AppUser;
import it.cpqia.domain.Customer;
import it.cpqia.repo.CustomerRepository;
import it.cpqia.security.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/customers")
public class CustomerController {

    private final CustomerRepository customers;
    private final CurrentUserService current;
    private final it.cpqia.service.VatLookupService vatLookup;
    private final it.cpqia.trash.TrashService trash;
    private final it.cpqia.rates.AccountRateRepository accountRates;

    public CustomerController(CustomerRepository customers, CurrentUserService current, it.cpqia.service.VatLookupService vatLookup,
                              it.cpqia.trash.TrashService trash, it.cpqia.rates.AccountRateRepository accountRates) {
        this.accountRates = accountRates;
        this.trash = trash;
        this.customers = customers;
        this.current = current;
        this.vatLookup = vatLookup;
    }

    @GetMapping
    public String list(@RequestParam(required = false) String q, Model model, Authentication auth) {
        model.addAttribute("q", q == null ? "" : q);
        model.addAttribute("customers", customers.search(q, 200, current.get(auth)));
        return "customers/list";
    }

    /** sezioni tariffe della scheda: profili professionali e trasferte (listino + tariffa dedicata) */
    private void addRateSections(Model model, Long accountId, Map<String, String> typed) {
        model.addAttribute("rateSections", List.of(
                new RateSection("pr_", "Profili professionali (€/h)", accountRates.effective(it.cpqia.rates.RateTable.PROFILES, accountId))));
        model.addAttribute("travelRates", accountRates.effective(it.cpqia.rates.RateTable.TRAVEL, null));
        Long chosen = null;
        if (typed != null && typed.get("travelRateId") != null && !typed.get("travelRateId").isBlank()) {
            try {
                chosen = Long.valueOf(typed.get("travelRateId").trim());
            } catch (NumberFormatException e) {
                chosen = null;
            }
        } else if (typed == null) {
            chosen = accountRates.travelRateId(accountId);
        }
        model.addAttribute("travelRateId", chosen);
        String[] ag = accountId == null ? new String[]{"", ""} : customers.agentInfo(accountId);
        model.addAttribute("agentCode", ag[0]);
        model.addAttribute("agentName", ag[1]);
        model.addAttribute("clientLocked", accountId != null && customers.findById(accountId).map(c -> "CLIENTE".equals(c.type())).orElse(false));
        model.addAttribute("typed", typed == null ? Map.<String, String>of() : typed);
    }

    public record RateSection(String prefix, String title, List<it.cpqia.rates.AccountRateRepository.EffRate> rows) {
    }

    /** legge i campi pr_<id> / tr_<id>: vuoto = listino; restituisce l'errore o null */
    private String parseRates(Map<String, String[]> params, String prefix, Map<Long, BigDecimal> out) {
        for (Map.Entry<String, String[]> e : params.entrySet()) {
            if (!e.getKey().startsWith(prefix) || e.getValue().length == 0) {
                continue;
            }
            String v = e.getValue()[0] == null ? "" : e.getValue()[0].trim().replace("€", "").replace(" ", "");
            if (v.isEmpty()) {
                continue;
            }
            try {
                BigDecimal b = new BigDecimal(v.contains(",") ? v.replace(".", "").replace(',', '.') : v);
                if (b.signum() < 0 || b.compareTo(new BigDecimal("10000000")) > 0) {
                    return "Tariffe: importo non valido (" + v + ")";
                }
                out.put(Long.parseLong(e.getKey().substring(prefix.length())), b.setScale(2, java.math.RoundingMode.HALF_UP));
            } catch (NumberFormatException ex) {
                return "Tariffe: importo non valido (" + v + ")";
            }
        }
        return null;
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('P_MANAGE_CUSTOMERS')")
    public String create(Model model) {
        model.addAttribute("form", new CustomerForm());
        model.addAttribute("agents", customers.agents());
        addRateSections(model, null, null);
        model.addAttribute("code", null);
        return "customers/form";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('P_MANAGE_CUSTOMERS')")
    public String edit(@PathVariable long id, Model model, Authentication auth) {
        Customer c = customers.findById(id, current.get(auth)).orElseThrow();
        model.addAttribute("form", CustomerForm.from(c));
        model.addAttribute("agents", customers.agents());
        addRateSections(model, id, null);
        model.addAttribute("geo", customers.geo(id));
        model.addAttribute("code", c.code());
        return "customers/form";
    }

    @PostMapping("/save")
    @PreAuthorize("hasAuthority('P_MANAGE_CUSTOMERS')")
    public String save(@Valid @ModelAttribute("form") CustomerForm form, BindingResult br, Model model,
                       Authentication auth, RedirectAttributes ra, jakarta.servlet.http.HttpServletRequest request) {
        Map<Long, BigDecimal> profileRates = new java.util.LinkedHashMap<>();
        String rateErr = parseRates(request.getParameterMap(), "pr_", profileRates);
        Long travelRateId = null;
        String tr = request.getParameter("travelRateId");
        if (tr != null && !tr.isBlank()) {
            try {
                travelRateId = Long.valueOf(tr.trim());
            } catch (NumberFormatException e) {
                rateErr = "Spese di trasferta: voce non valida";
            }
        }
        if (rateErr != null) {
            br.reject("rates", rateErr);
        }
        if (!br.hasErrors() && form.getVatNumber() != null
                && customers.vatExists(form.getVatNumber().trim().replaceAll("\\s", ""), form.getId())) {
            br.rejectValue("vatNumber", "dup", "Esiste gia' un account con questa partita IVA");
        }
        // codice agente (solo prospect): deve esistere nella tabella Agenti
        boolean prospectForm = form.getId() == null || !customers.findById(form.getId()).map(c -> "CLIENTE".equals(c.type())).orElse(false);
        if (prospectForm && form.getAgentCode() != null && !form.getAgentCode().isBlank() && !customers.agentExists(form.getAgentCode().trim())) {
            br.rejectValue("agentCode", "agent", "Codice agente non presente nella tabella Agenti");
        }
        if (br.hasErrors()) {
            model.addAttribute("agents", customers.agents());
            Map<String, String> typed = new java.util.HashMap<>();
            request.getParameterMap().forEach((k, v) -> {
                if ((k.startsWith("pr_") || "travelRateId".equals(k)) && v.length > 0) {
                    typed.put(k, v[0]);
                }
            });
            addRateSections(model, form.getId(), typed);
            if (form.getId() != null) {
                model.addAttribute("geo", customers.geo(form.getId()));
            }
            model.addAttribute("code", form.getId() == null ? null : customers.findById(form.getId()).map(Customer::code).orElse(null));
            return "customers/form";
        }
        AppUser me = current.get(auth);
        if (form.getId() == null) {
            form.setType("PROSPECT");   // nuove anagrafiche: solo prospect
            long id = customers.insert(form.toCustomer(), me.id());      // i prospect non hanno tariffe ne' trasferta dedicate
            ra.addFlashAttribute("msg", "Account creato (codice " + customers.findById(id).orElseThrow().code() + ")");
        } else {
            customers.findById(form.getId(), me).orElseThrow();      // account non visibile all'utente (agente): non modificabile
            // il tipo non si modifica da qui: un prospect diventa cliente con la Sync Clienti
            form.setType(customers.findById(form.getId()).orElseThrow().type());
            // account Cliente (anagrafica da IBM Power System): si salvano solo tariffe e diritto di trasferta
            boolean locked = "CLIENTE".equals(customers.findById(form.getId()).orElseThrow().type());
            if (!locked) {
                customers.update(form.toCustomer());
            }
            if (locked) {                       // tariffe e trasferta dedicate solo per i Clienti: nel prospect la sezione non c'e' e restano invariate
                accountRates.saveProfileRates(form.getId(), profileRates);
                accountRates.setTravelRate(form.getId(), travelRateId);
            }
            ra.addFlashAttribute("msg", locked ? "Tariffe e trasferta dell'account aggiornate" : "Account aggiornato");
        }
        return "redirect:/customers";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('P_MANAGE_CUSTOMERS')")
    public String delete(@PathVariable long id, Authentication auth, RedirectAttributes ra) {
        try {
            trash.delete(it.cpqia.trash.TrashKind.CUSTOMER, id, current.get(auth));
            ra.addFlashAttribute("msg", "Account spostato nel Cestino");
        } catch (IllegalArgumentException e) {
            ra.addFlashAttribute("err", e.getMessage());
        }
        return "redirect:/customers";
    }

    // ------------------------------------------------------------------ API per il wizard offerta
    @GetMapping("/api/search")
    @ResponseBody
    public List<Customer> search(@RequestParam(required = false) String q, Authentication auth) {
        return customers.search(q, 30, current.get(auth));
    }

    @GetMapping("/api/{id}")
    @ResponseBody
    public Customer get(@PathVariable long id, Authentication auth) {
        return customers.findById(id, current.get(auth)).orElseThrow();
    }

    public record VatLookupResponse(it.cpqia.service.VatLookupService.Result result, Customer existing) {
    }

    /** dati anagrafici dalla partita IVA (VIES) + eventuale anagrafica gia' presente con la stessa P.IVA */
    @GetMapping("/api/vat-lookup")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('P_MANAGE_CUSTOMERS','P_CREATE_OFFERS')")
    public VatLookupResponse vatLookup(@RequestParam String vat, Authentication auth) {
        it.cpqia.service.VatLookupService.Result r = vatLookup.lookup(vat);
        Customer existing = r.vat() == null ? null : customers.findByVat(r.vat().replaceAll("^[A-Za-z]{2}", "")).orElse(null);
        if (existing != null && !it.cpqia.repo.CustomerRepository.visibleTo(existing, current.get(auth))) {
            existing = null;                                                     // account di un altro agente: non si mostra
        }
        return new VatLookupResponse(r, existing);
    }

    /** codifica di una nuova anagrafica direttamente dal wizard: restituisce il record creato (con codice) */
    @PostMapping("/api")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('P_MANAGE_CUSTOMERS','P_CREATE_OFFERS')")
    public ResponseEntity<?> createApi(@Valid @RequestBody CustomerForm form, Authentication auth) {
        form.setType("PROSPECT");   // dal wizard si creano solo prospect, qualunque valore arrivi dal client
        String vat = form.getVatNumber().trim().replaceAll("\\s", "");
        if (customers.vatExists(vat, null)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Dati non validi",
                    "errors", Map.of("vatNumber", "Esiste gia' un account con questa partita IVA")));
        }
        long id = customers.insert(form.toCustomer(), current.get(auth).id());
        return ResponseEntity.ok(customers.findById(id).orElseThrow());
    }
}
