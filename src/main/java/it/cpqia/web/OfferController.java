package it.cpqia.web;

import it.cpqia.docx.OfferDocxService;
import it.cpqia.domain.AppUser;
import it.cpqia.pricing.PricingModel.App;
import it.cpqia.repo.CatalogRepository;
import it.cpqia.security.CurrentUserService;
import it.cpqia.service.OfferDtos.CalcResponse;
import it.cpqia.service.OfferDtos.OfferHeader;
import it.cpqia.service.OfferDtos.OfferRequest;
import it.cpqia.service.OfferService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/offers")
public class OfferController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OfferController.class);

    private final OfferService offers;
    private final CatalogRepository catalog;
    private final CurrentUserService current;
    private final OfferDocxService docx;
    private final it.cpqia.extras.ExtraRepository extraRepo;
    private final it.cpqia.terms.TermsConfig termsConfig;
    private final it.cpqia.rates.RateRepository rateRepo;
    private final it.cpqia.geo.DistanceService distances;
    private final it.cpqia.power.PowerModelRepository powerModelRepo;
    private final it.cpqia.power.IbmiProductRepository ibmiProductRepo;
    private final it.cpqia.rates.AccountRateRepository accountRates;

    public OfferController(OfferService offers, CatalogRepository catalog, CurrentUserService current, OfferDocxService docx,
                           it.cpqia.extras.ExtraRepository extraRepo, it.cpqia.terms.TermsConfig termsConfig,
                           it.cpqia.rates.RateRepository rateRepo, it.cpqia.geo.DistanceService distances,
                           it.cpqia.rates.AccountRateRepository accountRates, it.cpqia.power.PowerModelRepository powerModelRepo,
                           it.cpqia.power.IbmiProductRepository ibmiProductRepo) {
        this.ibmiProductRepo = ibmiProductRepo;
        this.powerModelRepo = powerModelRepo;
        this.accountRates = accountRates;
        this.rateRepo = rateRepo;
        this.distances = distances;
        this.extraRepo = extraRepo;
        this.termsConfig = termsConfig;
        this.offers = offers;
        this.catalog = catalog;
        this.current = current;
        this.docx = docx;
    }

    // ------------------------------------------------------------------ pagine
    @GetMapping
    public String list(Authentication auth, Model model) {
        model.addAttribute("offers", offers.list(current.get(auth)));
        return "offers/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('P_CREATE_OFFERS')")
    public String create(@RequestParam(required = false) Long customerId, Authentication auth, Model model) {
        AppUser me = current.get(auth);
        LocalDate today = LocalDate.now();
        model.addAttribute("today", today.toString());
        model.addAttribute("nextProgressive", offers.nextProgressive(today, me.initials()));
        model.addAttribute("offerId", null);
        model.addAttribute("customerId", customerId);   // account di partenza (da Account > Nuova offerta)
        return "offers/wizard";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("hasAuthority('P_EDIT_OFFERS')")
    public String edit(@PathVariable long id, Authentication auth, Model model) {
        checkAccess(id, auth);
        model.addAttribute("today", LocalDate.now().toString());
        model.addAttribute("nextProgressive", 1);
        model.addAttribute("offerId", id);
        model.addAttribute("customerId", null);
        return "offers/wizard";
    }

    @GetMapping("/{id}/docx")
    @PreAuthorize("hasAuthority('P_EXPORT_DOCX')")
    public ResponseEntity<byte[]> download(@PathVariable long id, Authentication auth) throws IOException {
        checkAccess(id, auth);
        OfferHeader h = offers.header(id);
        byte[] bytes;
        try {
            bytes = docx.generate(h);
        } catch (Exception e) {
            log.error("Generazione Word fallita per l'offerta {}", id, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Generazione del Word non riuscita: " + e.getClass().getSimpleName() + (e.getMessage() == null ? "" : " - " + e.getMessage()));
        }
        String filename = docx.fileName(h);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(bytes);
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('P_DELETE_OFFERS')")
    public String delete(@PathVariable long id, Authentication auth, RedirectAttributes ra) {
        checkAccess(id, auth);
        offers.delete(id, current.get(auth));
        ra.addFlashAttribute("msg", "Offerta spostata nel Cestino");
        return "redirect:/offers";
    }

    // ------------------------------------------------------------------ API del wizard
    @GetMapping("/api/catalog")
    @ResponseBody
    public List<App> catalog() {
        // i mercati Galileo hanno centinaia di moduli con un listino proprio (pagina Galileo): qui servono solo nome e id
        return catalog.loadAll(true).stream()
                .map(a -> "GALILEO".equals(a.family())
                        ? new App(a.id(), a.code(), a.name(), a.family(), a.maintenancePct(), a.needsReview(), a.active(), a.notes(), a.modes(), a.drivers(), a.groups(), List.of())
                        : a)
                .toList();
    }

    /** tariffe attive: profili professionali (€/h) e trasferte Italia */
    @GetMapping("/api/rates")
    @ResponseBody
    public Map<String, Object> rates(@org.springframework.web.bind.annotation.RequestParam(required = false) Long customerId) {
        // importi applicabili: tariffa dedicata dell'account se presente, altrimenti listino
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("profiles", accountRates.effective(it.cpqia.rates.RateTable.PROFILES, customerId));
        out.put("travel", accountRates.effective(it.cpqia.rates.RateTable.TRAVEL, customerId));
        out.put("accountTravelRateId", accountRates.travelRateId(customerId));   // voce di trasferta assegnata all'account (o null)
        return out;
    }

    /** distanza (km) tra l'indirizzo dell'account e la sede Sanmarco piu' vicina */
    @GetMapping("/api/distance")
    @ResponseBody
    public it.cpqia.geo.DistanceService.Result distance(@org.springframework.web.bind.annotation.RequestParam long customerId) {
        return distances.nearestSede(customerId);
    }

    /** modelli IBM Power System attivi (pagina Contenuti dell'offerta) */
    @GetMapping("/api/power-models")
    @ResponseBody
    public java.util.List<it.cpqia.power.PowerModelRepository.PowerModel> powerModels() {
        return powerModelRepo.active();
    }

    /** prodotti IBM i attivi (pagina Power System dell'offerta) */
    @GetMapping("/api/ibmi-products")
    @ResponseBody
    public java.util.List<it.cpqia.power.IbmiProductRepository.Product> ibmiProducts() {
        return ibmiProductRepo.active();
    }

    @GetMapping("/api/terms")
    @ResponseBody
    public it.cpqia.terms.TermsModel.Config terms() {
        return termsConfig.config();
    }

    @GetMapping("/api/extras")
    @ResponseBody
    public List<it.cpqia.extras.ExtraModel.Service> extras() {
        return extraRepo.loadAll();
    }

    @GetMapping("/api/next-progressive")
    @ResponseBody
    public Map<String, Object> nextProgressive(@RequestParam String date, Authentication auth) {
        AppUser me = current.get(auth);
        LocalDate d = LocalDate.parse(date);
        return Map.of("progressive", offers.nextProgressive(d, me.initials()), "initials", me.initials() == null ? "" : me.initials());
    }

    @GetMapping("/api/{id}")
    @ResponseBody
    public Map<String, Object> load(@PathVariable long id, Authentication auth) {
        checkAccess(id, auth);
        return Map.of("header", offers.header(id), "request", offers.loadRequest(id));
    }

    @PostMapping("/api/calc")
    @ResponseBody
    public CalcResponse calc(@RequestBody OfferRequest req) {
        return offers.calculate(req);
    }

    @PostMapping("/api/save")
    @ResponseBody
    @PreAuthorize("hasAnyAuthority('P_CREATE_OFFERS','P_EDIT_OFFERS')")
    public Map<String, Object> save(@RequestBody OfferRequest req, Authentication auth) {
        AppUser me = current.get(auth);
        if (req.id() != null) {
            checkAccess(req.id(), auth);
            if (!(me.administrator() || me.canEditOffers())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non sei abilitato a modificare le offerte");
            }
        } else if (!(me.administrator() || me.canCreateOffers())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non sei abilitato a creare offerte");
        }
        long id = offers.save(req, me);
        return Map.of("id", id, "number", offers.header(id).number());
    }

    // ------------------------------------------------------------------ sicurezza di accesso all'offerta
    private void checkAccess(long id, Authentication auth) {
        AppUser me = current.get(auth);
        Long owner = offers.ownerOf(id);
        if (owner == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Offerta inesistente");
        }
        if (!(me.administrator() || me.canViewAllOffers() || owner.equals(me.id()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Non puoi accedere a questa offerta");
        }
    }
}
