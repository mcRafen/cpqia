package it.cpqia.service;

import it.cpqia.domain.AppUser;
import it.cpqia.domain.OfferSummary;
import it.cpqia.extras.ExtraCalculator;
import it.cpqia.extras.ExtraModel;
import it.cpqia.extras.ExtraRepository;
import it.cpqia.pricing.PricingModel.App;
import it.cpqia.pricing.PricingModel.Driver;
import it.cpqia.pricing.PricingModel.Module;
import it.cpqia.pricing.PricingModel.Result;
import it.cpqia.pricing.PricingService;
import it.cpqia.pricing.RuleChecker;
import it.cpqia.pricing.RuleChecker.LineSelection;
import it.cpqia.pricing.RuleChecker.Violation;
import it.cpqia.repo.CatalogRepository;
import it.cpqia.repo.CustomerRepository;
import it.cpqia.terms.TermsConfig;
import it.cpqia.terms.TermsModel.PaymentTerms;
import it.cpqia.terms.TermsRepository;
import it.cpqia.trash.TrashKind;
import it.cpqia.trash.TrashService;
import it.cpqia.service.OfferDtos.CalcResponse;
import it.cpqia.service.OfferDtos.LineRequest;
import it.cpqia.service.OfferDtos.LineResult;
import it.cpqia.service.OfferDtos.ModuleQty;
import it.cpqia.service.OfferDtos.OfferHeader;
import it.cpqia.service.OfferDtos.OfferRequest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class OfferService {

    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final DateTimeFormatter NUM_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JdbcClient jdbc;
    private final CatalogRepository catalog;
    private final PricingService pricing;
    private final RuleChecker rules;
    private final ExtraCalculator extraCalc;
    private final ExtraRepository extraRepo;
    private final TermsConfig termsConfig;
    private final TermsRepository termsRepo;
    private final CustomerRepository customers;
    private final TrashService trash;
    private final it.cpqia.galileo.GalileoService galileo;
    private final OfferRevisionService revisions;

    public OfferService(JdbcClient jdbc, CatalogRepository catalog, PricingService pricing, RuleChecker rules,
                        ExtraCalculator extraCalc, ExtraRepository extraRepo, TermsConfig termsConfig,
                        TermsRepository termsRepo, CustomerRepository customers, TrashService trash,
                        it.cpqia.galileo.GalileoService galileo, OfferRevisionService revisions) {
        this.revisions = revisions;
        this.galileo = galileo;
        this.trash = trash;
        this.termsConfig = termsConfig;
        this.termsRepo = termsRepo;
        this.customers = customers;
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.pricing = pricing;
        this.rules = rules;
        this.extraCalc = extraCalc;
        this.extraRepo = extraRepo;
    }

    /** vero se nella riga e' selezionato almeno un modulo con gruppo di prezzo (moltiplicato dagli utenti): solo allora si chiedono gli utenti */
    public static boolean perUserSelected(App app, LineRequest l) {
        if (l.modules() == null) {
            return false;
        }
        for (ModuleQty mq : l.modules()) {
            if (mq.moduleId() == null || mq.qty() == null || mq.qty() <= 0) {
                continue;
            }
            Module m = app.module(mq.moduleId());
            if (m != null && m.groupId() != null && !"LINK".equals(m.section()) && !m.included()) {
                return true;
            }
        }
        return false;
    }

    /** numero offerta: YYYYMMDD + "." + progressivo + iniziali (es. 20261022.3ER) */
    public static String buildNumber(java.time.LocalDate date, int progressive, String initials) {
        return date.format(NUM_DATE) + "." + progressive + (initials == null ? "" : initials.toUpperCase());
    }

    /**
     * iniziali dell'agente dalla descrizione in anagrafica agenti, scritta «COGNOME NOME»: prima lettera del nome e del cognome
     * (es. «ROSSI MARIO» → MR; cognomi composti: l'ultima parola e' il nome, la prima il cognome). Una sola parola: la sua iniziale.
     */
    public static String agentInitials(String description) {
        if (description == null || description.isBlank()) {
            return "";
        }
        String[] w = description.trim().split("\\s+");
        String first = w[0].substring(0, 1);
        String last = w[w.length - 1].substring(0, 1);
        return (w.length == 1 ? first : last + first).toUpperCase();
    }

    /** iniziali per il numero dell'offerta: quelle dell'agente dell'account, in mancanza quelle dell'utente collegato */
    public String initialsFor(Long customerId, AppUser user) {
        String ini = "";
        if (customerId != null) {
            ini = agentInitials(customers.agentInfo(customerId)[1]);
        }
        if (ini.isBlank() && user != null && user.initials() != null) {
            ini = user.initials().trim().toUpperCase();
        }
        return ini;
    }

    // --------------------------------------------------------------------- calcolo
    public CalcResponse calculate(OfferRequest req) {
        List<LineSelection> selections = new ArrayList<>();
        List<LineResult> results = new ArrayList<>();
        BigDecimal totLic = BigDecimal.ZERO;
        BigDecimal totMan = BigDecimal.ZERO;
        if (req.lines() != null) {
            for (LineRequest l : req.lines()) {
                App app = catalog.find(l.applicationId()).orElseThrow(
                        () -> new IllegalArgumentException("Applicazione inesistente: " + l.applicationId()));
                String mode = l.modeCode() != null ? l.modeCode() : app.defaultMode();
                Map<Long, Integer> sel = new LinkedHashMap<>();
                if (l.modules() != null) {
                    for (ModuleQty mq : l.modules()) {
                        if (mq.moduleId() != null && mq.qty() != null && mq.qty() > 0) {
                            sel.put(mq.moduleId(), mq.qty());
                        }
                    }
                }
                // utenti / macchine contano solo se e' selezionato almeno un modulo con gruppo di prezzo (moduli per utente); solo moduli a prezzo fisso: vale il prezzo
                Map<Long, Integer> drv = l.drivers() == null || !perUserSelected(app, l) ? Map.of() : l.drivers();
                Result r = pricing.calculate(app, mode, sel, drv);
                selections.add(new LineSelection(app, mode, sel, drv));
                BigDecimal licTot = r.licenseWithOnetime();
                results.add(new LineResult(app.id(), r.license(), r.onetime(), licTot, r.maintenance(), r.rentMonthly(), r.rows(), r.annual()));
                totLic = totLic.add(licTot);
                totMan = totMan.add(r.maintenance());
            }
        }
        BigDecimal disc = req.discountPct() == null ? BigDecimal.ZERO : req.discountPct();
        if (disc.signum() < 0 || disc.compareTo(HUNDRED) > 0) {
            throw new IllegalArgumentException("Lo sconto deve essere compreso tra 0 e 100");
        }
        BigDecimal factor = BigDecimal.ONE.subtract(disc.divide(HUNDRED));
        List<Violation> violations = rules.check(selections);
        BigDecimal netLic = PricingService.round(totLic.multiply(factor));
        // «Manutenzione su netto scontato» (flag dell'offerta): la manutenzione segue lo sconto; altrimenti resta sul listino
        BigDecimal netMan = !Boolean.FALSE.equals(req.maintOnNet()) ? PricingService.round(totMan.multiply(factor)) : totMan;
        // margine commerciale = netto licenze software + netto manutenzione software
        // servizi accessori (solo se richiesti): importi una tantum / canone e scelte mancanti
        ExtraModel.Totals extraTotals = null;
        if (Boolean.TRUE.equals(req.optExtraServices())) {
            List<String> errs = new ArrayList<>();
            extraTotals = extraCalc.calculate(req.extras(), errs);
            violations = new ArrayList<>(violations);
            for (String m : errs) {
                violations.add(new Violation(0L, null, "ERROR", m));
            }
        }
        return new CalcResponse(results, totLic, totMan, disc, netLic, netMan, netLic.add(netMan), extraTotals, violations);
    }

    // --------------------------------------------------------------------- salvataggio
    @Transactional
    public long save(OfferRequest req, AppUser user) {
        if (req.customerId() == null) {
            throw new IllegalArgumentException("Selezionare il cliente");
        }
        if (req.offerDate() == null) {
            throw new IllegalArgumentException("Indicare la data dell'offerta");
        }
        if (actDiscount(req).signum() > 0 && req.activityDiscountUntil() == null) {
            throw new IllegalArgumentException("Attività sistemistiche: indicare la data di validità dello sconto");
        }
        if (req.projectTitle() == null || req.projectTitle().isBlank()) {
            throw new IllegalArgumentException("Indicare il titolo del progetto");
        }
        if (req.progressive() == null || req.progressive() < 1) {
            throw new IllegalArgumentException("Indicare il numero progressivo (>= 1)");
        }
        boolean otherElements = Boolean.TRUE.equals(req.optPowerSystem()) || Boolean.TRUE.equals(req.optSmitechServices())
                || req.galileoApplicationId() != null
                || (!"NONE".equals(servicesMode(req)) && req.activities() != null && !req.activities().isEmpty());
        if ((req.lines() == null || req.lines().isEmpty()) && !(otherElements && Boolean.FALSE.equals(req.optDepartmental()))) {
            throw new IllegalArgumentException("Aggiungere almeno un'applicazione all'offerta (oppure scegliere altri elementi senza Dipartimentali)");
        }
        String numberInitials = initialsFor(req.customerId(), user);
        if (numberInitials.isBlank()) {
            throw new IllegalArgumentException("Mancano le iniziali per il numero offerta: l'account non ha un agente con nome e cognome e il tuo profilo non ha le iniziali (impostale in Profilo)");
        }
        // Dipartimentali: utenti (e ogni altra grandezza) partono da 0 e vanno indicati per ogni applicazione (SMITECH: dagli utenti di Power / Galileo)
        if (req.lines() != null) {
            for (LineRequest l : req.lines()) {
                App app = catalog.find(l.applicationId()).orElse(null);
                if (app == null || "SMITECH".equalsIgnoreCase(app.family()) || "SMITECH".equalsIgnoreCase(app.code())) {
                    continue;
                }
                // un solo tipo di utenti: obbligatorio; piu' tipi: almeno uno dei due. Solo se e' selezionato un modulo per utente (prezzo fisso: niente utenti)
                if (app.drivers().isEmpty() || !perUserSelected(app, l)) {
                    continue;
                }
                boolean any = false;
                for (Driver d : app.drivers()) {
                    Integer q = l.drivers() == null ? null : l.drivers().get(d.id());
                    if (q != null && q >= 1) {
                        any = true;
                    }
                }
                if (!any) {
                    Driver d = app.drivers().get(0);
                    throw new IllegalArgumentException(app.name() + ": indica " + (app.drivers().size() == 1
                            ? "il numero di " + (d.unitLabel() == null || d.unitLabel().isBlank() ? d.label() : d.unitLabel()).toLowerCase()
                            : "almeno uno tra " + String.join(" e ", app.drivers().stream().map(x -> x.label().toLowerCase()).toList())));
                }
            }
        }
        CalcResponse calc = calculate(req);
        for (Violation v : calc.violations()) {
            if ("ERROR".equals(v.severity())) {
                throw new IllegalArgumentException(v.message());
            }
        }
        if (user.agentRestricted() && customers.findById(req.customerId(), user).isEmpty()) {
            throw new IllegalArgumentException("Account non consentito: puoi usare solo i tuoi prospect e i clienti del tuo agente");
        }
        String customerType = customers.findById(req.customerId()).map(c -> c.type()).orElse("PROSPECT");
        PaymentTerms terms = req.terms();
        if (terms == null) {
            terms = termsConfig.defaultsFor(customerType);
        }
        List<String> termErrors = termsConfig.validate(terms, customerType);
        if (!termErrors.isEmpty()) {
            throw new IllegalArgumentException(String.join("; ", termErrors));
        }
        String number = buildNumber(req.offerDate(), req.progressive(), numberInitials);
        long offerId;
        try {
            if (req.id() == null) {
                offerId = jdbc.sql("""
                        insert into offer (number, offer_date, progressive, initials, customer_id, created_by, project_title,
                            contact_ref, discount_pct, total_license, total_maintenance,
                            opt_maintenance_additional, opt_extra_services, opt_final_summary, distance_km, nearest_sede, travel_rate_id, activity_discount_pct, opt_activity_detail, maint_on_net,
                            opt_power_system, galileo_application_id, opt_smitech_services, opt_departmental,
                            power_model_id, power_ram_gb, power_disk, power_lto8, power_eth10, power_ibmi_users, power_ibmi_unlimited,
                            power_cable, power_transfer, power_transfer_ref, power_edition, power_os, power_maint_months,
                            power_list_price, power_net_price, power_disk_count, opt_use_rfq, opt_services_days, app_totals_mode, rfq_sheet_total, services_mode)
                        values (:num, :dt, :prog, :ini, :cust, :by, :title, :ref, :disc, :tl, :tm, :o1, :o2, :o3, :dk, :ns, :tr, :ad, :adt, :mon, :pw, :gal, :sm, :dep, :pm, :pram, :pdisk, :plto, :peth, :pusr, :punl, :pcab, :ptr, :ptref, :ped, :pos, :pmm, :plp, :pnp, :pdc, :rfq, :sdy, :apt, :rst, :svm) returning id""")
                        .param("num", number).param("dt", req.offerDate()).param("prog", req.progressive())
                        .param("ini", numberInitials).param("cust", req.customerId()).param("by", user.id())
                        .param("title", req.projectTitle()).param("ref", blankToNull(req.contactRef()))
                        .param("disc", calc.discountPct()).param("tl", calc.totalLicense()).param("tm", calc.totalMaintenance())
                        .param("o1", flag(req.optMaintenanceAdditional())).param("o2", flag(req.optExtraServices()))
                        .param("o3", flag(req.optFinalSummary()))
                        .param("dk", req.distanceKm()).param("ns", blankToNull(req.nearestSede())).param("tr", req.travelRateId())
                        .param("ad", actDiscount(req)).param("adt", flag(req.optActivityDetail())).param("mon", !Boolean.FALSE.equals(req.maintOnNet()))
                        .param("pw", flag(req.optPowerSystem())).param("gal", req.galileoApplicationId())
                        .param("sm", flag(req.optSmitechServices())).param("dep", !Boolean.FALSE.equals(req.optDepartmental()))
                        .param("pm", powerModel(req)).param("pram", powerOn(req) ? req.powerRamGb() : null).param("pdisk", powerOn(req) ? req.powerDisk() : null)
                        .param("plto", powerOn(req) && Boolean.TRUE.equals(req.powerLto8())).param("peth", powerOn(req) && Boolean.TRUE.equals(req.powerEth10()))
                        .param("pusr", powerOn(req) && !unlimited(req) ? req.powerIbmiUsers() : null).param("punl", powerOn(req) && unlimited(req))
                        .param("pcab", powerOn(req) ? blankToNull(req.powerCable()) : null).param("ptr", powerOn(req) ? transfer(req) : "NO")
                        .param("ptref", powerOn(req) && !"NO".equals(transfer(req)) ? blankToNull(req.powerTransferRef()) : null)
                        .param("ped", powerOn(req) ? edition(req) : "SOLUTION").param("pos", powerOn(req) ? os(req) : "7.5")
                        .param("pmm", powerOn(req) ? maintMonths(req) : 36)
                        .param("plp", powerOn(req) ? maintTotal(req, true) : null).param("pnp", powerOn(req) ? maintTotal(req, false) : null)
                        .param("pdc", powerOn(req) ? diskCount(req) : 2).param("rfq", rfq(req)).param("sdy", !Boolean.FALSE.equals(req.optServicesDays()))
                        .param("apt", totalsMode(req)).param("svm", servicesMode(req)).param("rst", rfq(req) ? req.rfqSheetTotal() : null)
                        .query(Long.class).single();
            } else {
                offerId = req.id();
                OfferHeader existing = header(offerId);
                if (jdbc.sql("""
                        select count(*) from offer n where n.deleted_at is null
                          and n.revision > (select revision from offer where id = :id)
                          and coalesce(n.revision_of, n.id) = (select coalesce(revision_of, id) from offer where id = :id)""")
                        .param("id", offerId).query(Long.class).single() > 0) {
                    throw new IllegalArgumentException("Questa revisione è stata superata da una più recente: modifica l'ultima revisione");
                }
                // il numero conserva le iniziali assegnate alla creazione dell'offerta (agente dell'account)
                number = buildNumber(req.offerDate(), req.progressive(), existing.initials());
                jdbc.sql("""
                        update offer set number = :num, offer_date = :dt, progressive = :prog, customer_id = :cust,
                            project_title = :title, contact_ref = :ref, discount_pct = :disc, total_license = :tl,
                            total_maintenance = :tm, opt_maintenance_additional = :o1, opt_extra_services = :o2,
                            opt_final_summary = :o3, distance_km = :dk, nearest_sede = :ns, travel_rate_id = :tr, activity_discount_pct = :ad, opt_activity_detail = :adt, maint_on_net = :mon, opt_power_system = :pw, galileo_application_id = :gal,
                            opt_smitech_services = :sm, opt_departmental = :dep,
                            power_model_id = :pm, power_ram_gb = :pram, power_disk = :pdisk, power_lto8 = :plto, power_eth10 = :peth, power_ibmi_users = :pusr, power_ibmi_unlimited = :punl,
                            power_cable = :pcab, power_transfer = :ptr, power_transfer_ref = :ptref, power_edition = :ped, power_os = :pos, power_maint_months = :pmm, power_list_price = :plp, power_net_price = :pnp, power_disk_count = :pdc, services_mode = :svm, opt_use_rfq = :rfq, opt_services_days = :sdy, app_totals_mode = :apt, rfq_sheet_total = :rst,
                            updated_at = now() where id = :id""")
                        .param("num", number).param("dt", req.offerDate()).param("prog", req.progressive())
                        .param("cust", req.customerId()).param("title", req.projectTitle())
                        .param("ref", blankToNull(req.contactRef())).param("disc", calc.discountPct())
                        .param("tl", calc.totalLicense()).param("tm", calc.totalMaintenance())
                        .param("o1", flag(req.optMaintenanceAdditional())).param("o2", flag(req.optExtraServices()))
                        .param("o3", flag(req.optFinalSummary())).param("dk", req.distanceKm()).param("ns", blankToNull(req.nearestSede()))
                        .param("tr", req.travelRateId()).param("ad", actDiscount(req)).param("adt", flag(req.optActivityDetail())).param("mon", !Boolean.FALSE.equals(req.maintOnNet()))
                        .param("pw", flag(req.optPowerSystem())).param("gal", req.galileoApplicationId())
                        .param("sm", flag(req.optSmitechServices())).param("dep", !Boolean.FALSE.equals(req.optDepartmental()))
                        .param("pm", powerModel(req)).param("pram", powerOn(req) ? req.powerRamGb() : null).param("pdisk", powerOn(req) ? req.powerDisk() : null)
                        .param("plto", powerOn(req) && Boolean.TRUE.equals(req.powerLto8())).param("peth", powerOn(req) && Boolean.TRUE.equals(req.powerEth10()))
                        .param("pusr", powerOn(req) && !unlimited(req) ? req.powerIbmiUsers() : null).param("punl", powerOn(req) && unlimited(req))
                        .param("pcab", powerOn(req) ? blankToNull(req.powerCable()) : null).param("ptr", powerOn(req) ? transfer(req) : "NO")
                        .param("ptref", powerOn(req) && !"NO".equals(transfer(req)) ? blankToNull(req.powerTransferRef()) : null)
                        .param("ped", powerOn(req) ? edition(req) : "SOLUTION").param("pos", powerOn(req) ? os(req) : "7.5")
                        .param("pmm", powerOn(req) ? maintMonths(req) : 36)
                        .param("plp", powerOn(req) ? maintTotal(req, true) : null).param("pnp", powerOn(req) ? maintTotal(req, false) : null)
                        .param("pdc", powerOn(req) ? diskCount(req) : 2).param("rfq", rfq(req)).param("sdy", !Boolean.FALSE.equals(req.optServicesDays()))
                        .param("apt", totalsMode(req)).param("svm", servicesMode(req)).param("rst", rfq(req) ? req.rfqSheetTotal() : null)
                        .param("id", offerId).update();
                jdbc.sql("delete from offer_line where offer_id = :id").param("id", offerId).update();
                jdbc.sql("delete from offer_activity where offer_id = :id").param("id", offerId).update();
                jdbc.sql("delete from offer_profile_rate where offer_id = :id").param("id", offerId).update();
                jdbc.sql("delete from offer_power_maint where offer_id = :id").param("id", offerId).update();
                jdbc.sql("delete from offer_power_product where offer_id = :id").param("id", offerId).update();
            }
        } catch (DuplicateKeyException e) {
            throw new IllegalArgumentException("Esiste gia' un'offerta con numero " + number
                    + ": scegliere un altro progressivo");
        }
        jdbc.sql("update offer set opt_hours_detail = :f, activity_discount_until = :u where id = :id").param("f", flag(req.optHoursDetail()))
                .param("u", actDiscount(req).signum() > 0 ? req.activityDiscountUntil() : null).param("id", offerId).update();
        checkGalileo(req.galileoApplicationId());
        checkPower(req);
        termsRepo.save(offerId, terms);
        extraRepo.saveSelection(offerId, Boolean.TRUE.equals(req.optExtraServices()) ? req.extras() : null);
        int sort = 0;
        List<LineRequest> reqLines = req.lines() == null ? List.of() : req.lines();
        for (int i = 0; i < reqLines.size(); i++) {
            LineRequest l = reqLines.get(i);
            LineResult lr = calc.lines().get(i);
            App app = catalog.find(l.applicationId()).orElseThrow();
            String mode = l.modeCode() != null ? l.modeCode() : app.defaultMode();
            long lineId = jdbc.sql("""
                    insert into offer_line (offer_id, application_id, mode_code, sort_order, license_amount, maintenance_amount)
                    values (:o, :a, :m, :s, :la, :ma) returning id""")
                    .param("o", offerId).param("a", app.id()).param("m", mode).param("s", sort += 10)
                    .param("la", lr.licenseTotal()).param("ma", lr.maintenance()).query(Long.class).single();
            if (l.drivers() != null && perUserSelected(app, l)) {
                for (Map.Entry<Long, Integer> e : l.drivers().entrySet()) {
                    if (e.getValue() != null && e.getValue() > 0) {
                        jdbc.sql("insert into offer_line_driver (line_id, driver_id, qty) values (:l, :d, :q)")
                                .param("l", lineId).param("d", e.getKey()).param("q", e.getValue()).update();
                    }
                }
            }
            if (l.modules() != null) {
                for (ModuleQty mq : l.modules()) {
                    if (mq.moduleId() == null || mq.qty() == null || mq.qty() < 1) {
                        continue;
                    }
                    Module m = app.module(mq.moduleId());
                    if (m == null || "LINK".equals(m.section())) {
                        continue;
                    }
                    jdbc.sql("insert into offer_line_module (line_id, module_id, qty, unit_price) values (:l, :m, :q, :p)")
                            .param("l", lineId).param("m", m.id()).param("q", m.qtyEnabled() ? mq.qty() : 1)
                            .param("p", PricingService.priceOf(m, mode)).update();
                }
            }
        }
        saveProfileRates(offerId, req);
        saveActivities(offerId, req);
        savePowerProducts(offerId, req);
        savePowerMaint(offerId, req);
        galileo.save(offerId, req.galileoApplicationId(), req.galileo());      // configurazione e prezzo Galileo (una per offerta)
        // licenze e manutenzione dell'offerta = Dipartimentali + configurazione Galileo
        jdbc.sql("update offer set total_license = :tl + coalesce((select list_total from offer_galileo where offer_id = :o), 0), "
                + "total_maintenance = :tm + coalesce((select maintenance from offer_galileo where offer_id = :o), 0) where id = :o")
                .param("tl", calc.totalLicense()).param("tm", calc.totalMaintenance()).param("o", offerId).update();
        rememberProspectTravel(req);
        revisions.recordChanges(offerId);                 // modifiche rispetto alla revisione precedente (nessuna per la prima)
        return offerId;
    }

    /**
     * Prospect: alla prima offerta con una voce di trasferta, la scelta viene registrata nell'anagrafica del prospect
     * (se non ne ha gia' una). Per i clienti vale sempre l'anagrafica, che non viene toccata dalle offerte.
     */
    private void rememberProspectTravel(OfferRequest req) {
        if (req.travelRateId() == null || req.customerId() == null) {
            return;
        }
        jdbc.sql("update account set travel_rate_id = :t where id = :c and type = 'PROSPECT' and travel_rate_id is null "
                + "and exists (select 1 from travel_rate where id = :t and deleted_at is null)")
                .param("t", req.travelRateId()).param("c", req.customerId()).update();
    }

    private static String transfer(OfferRequest req) {
        String t = req.powerTransfer() == null || req.powerTransfer().isBlank() ? "NO" : req.powerTransfer().trim().toUpperCase();
        return t;
    }

    private static int diskCount(OfferRequest req) {
        return req.powerDiskCount() == null ? 2 : req.powerDiskCount();
    }

    private static int maintMonths(OfferRequest req) {
        if (req.powerMaint() != null && !req.powerMaint().isEmpty()) {          // il periodo piu' breve tra quelli selezionati
            return req.powerMaint().stream().filter(m -> m != null && m.months() != null).mapToInt(OfferDtos.PowerMaintReq::months).min().orElse(36);
        }
        return req.powerMaintMonths() == null ? 36 : req.powerMaintMonths();
    }

    /** totale degli importi di manutenzione (tutti i periodi, Power + LTO8); senza righe vale il campo storico */
    private static BigDecimal maintTotal(OfferRequest req, boolean list) {
        if (req.powerMaint() == null || req.powerMaint().isEmpty()) {
            return list ? req.powerListPrice() : req.powerNetPrice();
        }
        BigDecimal t = BigDecimal.ZERO;
        for (OfferDtos.PowerMaintReq m : req.powerMaint()) {
            BigDecimal v = m == null ? null : (list ? m.listPrice() : m.netPrice());
            t = t.add(v == null ? BigDecimal.ZERO : v);
        }
        return t;
    }

    /** periodi di manutenzione: obbligatori Power per ogni periodo e LTO8 (se attivo); importi tutti obbligatori */
    private static void checkPowerMaint(OfferRequest req) {
        List<OfferDtos.PowerMaintReq> ls = req.powerMaint() == null ? List.of() : req.powerMaint();
        if (ls.isEmpty()) {
            throw new IllegalArgumentException("Power System: seleziona la manutenzione SW/HW e indica gli importi");
        }
        boolean lto = Boolean.TRUE.equals(req.powerLto8());
        java.util.Set<String> seen = new java.util.HashSet<>();
        java.util.Set<Integer> periods = new java.util.TreeSet<>();
        for (OfferDtos.PowerMaintReq m : ls) {
            if (m == null || m.months() == null || !List.of(36, 48, 60).contains(m.months())) {
                throw new IllegalArgumentException("Power System: la manutenzione SW/HW può essere di 36, 48 o 60 mesi");
            }
            if (m.kind() == null || !List.of("POWER", "LTO8").contains(m.kind()) || ("LTO8".equals(m.kind()) && !lto)) {
                throw new IllegalArgumentException("Power System: sezione di manutenzione non valida");
            }
            if (m.listPrice() == null || m.netPrice() == null) {
                throw new IllegalArgumentException("Power System: manutenzione " + m.months() + " mesi (" + m.kind() + "): indica prezzo di listino e netto scontato");
            }
            if (m.listPrice().signum() < 0 || m.netPrice().signum() < 0) {
                throw new IllegalArgumentException("Power System: gli importi non possono essere negativi");
            }
            if (!seen.add(m.months() + m.kind())) {
                throw new IllegalArgumentException("Power System: importi duplicati per la manutenzione " + m.months() + " mesi");
            }
            periods.add(m.months());
        }
        // il prezzo (listino e netto scontato) di un periodo piu' breve non puo' superare quello di un periodo piu' lungo (per Power e per LTO8)
        for (String kind : List.of("POWER", "LTO8")) {
            for (boolean list : new boolean[]{true, false}) {
                java.util.TreeMap<Integer, java.math.BigDecimal> byMonths = new java.util.TreeMap<>();
                for (OfferDtos.PowerMaintReq m : ls) {
                    if (kind.equals(m.kind())) {
                        byMonths.put(m.months(), list ? m.listPrice() : m.netPrice());
                    }
                }
                Integer prevM = null;
                java.math.BigDecimal prevP = null;
                for (var e : byMonths.entrySet()) {
                    if (prevP != null && prevP.compareTo(e.getValue()) > 0) {
                        throw new IllegalArgumentException("Power System: manutenzione " + ("LTO8".equals(kind) ? "LTO8" : "Power") + ": il prezzo " + (list ? "di listino" : "netto scontato")
                                + " a " + prevM + " mesi non può essere maggiore di quello a " + e.getKey() + " mesi");
                    }
                    prevM = e.getKey();
                    prevP = e.getValue();
                }
            }
        }
        for (Integer p : periods) {
            if (!seen.contains(p + "POWER") || (lto && !seen.contains(p + "LTO8"))) {
                throw new IllegalArgumentException("Power System: per la manutenzione " + p + " mesi indica gli importi di tutte le sezioni (Power" + (lto ? " e LTO8" : "") + ")");
            }
        }
    }

    private void savePowerMaint(long offerId, OfferRequest req) {
        if (!powerOn(req) || req.powerMaint() == null) {
            return;
        }
        for (OfferDtos.PowerMaintReq m : req.powerMaint()) {
            jdbc.sql("insert into offer_power_maint (offer_id, months, kind, list_price, net_price) values (:o, :m, :k, :l, :n)")
                    .param("o", offerId).param("m", m.months()).param("k", m.kind())
                    .param("l", m.listPrice().setScale(2, java.math.RoundingMode.HALF_UP)).param("n", m.netPrice().setScale(2, java.math.RoundingMode.HALF_UP)).update();
        }
    }

    private List<OfferDtos.PowerMaintReq> loadPowerMaint(long offerId) {
        return jdbc.sql("select months, kind, list_price, net_price from offer_power_maint where offer_id = :id order by months, kind desc").param("id", offerId)
                .query((rs, n) -> new OfferDtos.PowerMaintReq(rs.getInt("months"), rs.getString("kind"), rs.getBigDecimal("list_price"), rs.getBigDecimal("net_price"))).list();
    }

    private static String os(OfferRequest req) {
        return req.powerOs() == null || req.powerOs().isBlank() ? "7.5" : req.powerOs().trim();
    }

    private static String edition(OfferRequest req) {
        return req.powerEdition() == null || req.powerEdition().isBlank() ? "SOLUTION" : req.powerEdition().trim().toUpperCase();
    }

    /** prodotti IBM i: ogni prodotto attivo deve avere un valore (1, 2, 3, 4 o MIGRATO) */
    private void checkPowerProducts(OfferRequest req) {
        List<Long> activeIds = jdbc.sql("select id from ibmi_product where deleted_at is null and active").query(Long.class).list();
        java.util.Map<Long, String> chosen = new java.util.HashMap<>();
        if (req.powerProducts() != null) {
            for (OfferDtos.PowerProductSel p : req.powerProducts()) {
                if (p.productId() == null || !activeIds.contains(p.productId())) {
                    throw new IllegalArgumentException("Power System: prodotto IBM i non valido");
                }
                if (p.value() == null || !List.of("1", "2", "3", "4", "MIGRATO").contains(p.value())) {
                    throw new IllegalArgumentException("Power System: valore non valido per un prodotto IBM i (1, 2, 3, 4 o Migrato)");
                }
                chosen.put(p.productId(), p.value());
            }
        }
        for (Long id : activeIds) {
            if (!chosen.containsKey(id)) {
                throw new IllegalArgumentException("Power System: seleziona il valore di ogni prodotto IBM i");
            }
        }
    }

    private void savePowerProducts(long offerId, OfferRequest req) {
        if (!powerOn(req) || req.powerProducts() == null) {
            return;
        }
        for (OfferDtos.PowerProductSel p : req.powerProducts()) {
            jdbc.sql("insert into offer_power_product (offer_id, product_id, code, description, value) "
                    + "select :o, id, code, description, :v from ibmi_product where id = :p")
                    .param("o", offerId).param("p", p.productId()).param("v", p.value()).update();
        }
    }

    private List<OfferDtos.PowerProductSel> loadPowerProducts(long offerId) {
        return jdbc.sql("select product_id, value, code, description from offer_power_product where offer_id = :id order by code")
                .param("id", offerId).query((rs, n) -> new OfferDtos.PowerProductSel(rs.getLong("product_id"), rs.getString("value"),
                        rs.getString("code"), rs.getString("description"))).list();
    }

    private static boolean unlimited(OfferRequest req) {
        return Boolean.TRUE.equals(req.powerIbmiUnlimited());
    }

    private static boolean powerOn(OfferRequest req) {
        return Boolean.TRUE.equals(req.optPowerSystem());
    }

    private Long powerModel(OfferRequest req) {
        return powerOn(req) ? req.powerModelId() : null;
    }

    /** con l'elemento Power System: modello esistente e attivo, RAM e disco tra i valori ammessi, utenti IBM i >= 0 */
    private void checkPower(OfferRequest req) {
        if (!powerOn(req)) {
            return;
        }
        long n = req.powerModelId() == null ? 0 : jdbc.sql("select count(*) from power_model where id = :id and deleted_at is null")
                .param("id", req.powerModelId()).query(Long.class).single();
        if (n == 0) {
            throw new IllegalArgumentException("Power System: scegliere il modello");
        }
        if (req.powerRamGb() == null || !List.of(32, 64, 96, 128, 256, 384, 512).contains(req.powerRamGb())) {
            throw new IllegalArgumentException("Power System: scegliere la RAM (32, 64, 96, 128, 256, 384 o 512 GB)");
        }
        if (req.powerDisk() == null || !List.of("1.6TB", "3.2TB", "6.4TB").contains(req.powerDisk())) {
            throw new IllegalArgumentException("Power System: scegliere il disco (1.6TB, 3.2TB o 6.4TB)");
        }
        // tagli supportati dal modello (se l'anagrafica li indica)
        var opts = jdbc.sql("select ram_options, disk_options from power_model where id = :id").param("id", req.powerModelId())
                .query((rs, i) -> new String[]{rs.getString(1), rs.getString(2)}).single();
        if (!opts[0].isBlank() && !List.of(opts[0].split(",")).contains(String.valueOf(req.powerRamGb()))) {
            throw new IllegalArgumentException("Power System: il modello scelto non supporta " + req.powerRamGb() + " GB di RAM");
        }
        if (!opts[1].isBlank() && !List.of(opts[1].split(",")).contains(req.powerDisk())) {
            throw new IllegalArgumentException("Power System: il modello scelto non supporta il disco " + req.powerDisk());
        }
        if (Boolean.TRUE.equals(req.powerLto8()) && req.powerCable() != null && !req.powerCable().isBlank()) {
            throw new IllegalArgumentException("Power System: LTO8 e cavo di collegamento sono alternativi");
        }
        if (req.powerCable() != null && !req.powerCable().isBlank() && !List.of("LTO6", "LTO7").contains(req.powerCable())) {
            throw new IllegalArgumentException("Power System: cavo di collegamento non valido");
        }
        if (!List.of("NO", "TRANSFER", "CONVERSION").contains(transfer(req))) {
            throw new IllegalArgumentException("Power System: Transfer-Conversion non valido");
        }
        if (!"NO".equals(transfer(req)) && (req.powerTransferRef() == null || req.powerTransferRef().isBlank())) {
            throw new IllegalArgumentException("Power System: indica i riferimenti del Power da cui trasferire le licenze");
        }
        if (diskCount(req) < 1 || diskCount(req) > 24) {
            throw new IllegalArgumentException("Power System: il numero di dischi deve essere tra 1 e 24");
        }
        checkPowerProducts(req);
        checkPowerMaint(req);
        if (!List.of("7.5", "7.6").contains(os(req))) {
            throw new IllegalArgumentException("Power System: sistema operativo non valido (7.5 o 7.6)");
        }
        if (!List.of("SOLUTION", "EXPRESS").contains(edition(req))) {
            throw new IllegalArgumentException("Power System: Edition non valida");
        }
        // utenti IBM i licenziati: obbligatori (numero >= 1) oppure ILLIMITATI
        if (!unlimited(req) && (req.powerIbmiUsers() == null || req.powerIbmiUsers() < 1)) {
            throw new IllegalArgumentException("Power System: indica il numero di utenti IBM i licenziati (almeno 1) oppure ILLIMITATI");
        }
    }

    /** il mercato Galileo scelto deve essere un'applicazione attiva della famiglia GALILEO */
    private void checkGalileo(Long appId) {
        if (appId == null) {
            return;
        }
        long n = jdbc.sql("select count(*) from application where id = :id and family = 'GALILEO' and deleted_at is null")
                .param("id", appId).query(Long.class).single();
        if (n == 0) {
            throw new IllegalArgumentException("Il mercato Galileo scelto non è valido");
        }
    }

    private static String totalsMode(OfferRequest req) {
        String m = req.appTotalsMode() == null ? "NO" : req.appTotalsMode().trim().toUpperCase();
        return java.util.List.of("NO", "HOURS", "AMOUNT", "BOTH").contains(m) ? m : "NO";
    }

    /** NONE | MANUAL | RFQ (senza il campo, le richieste vecchie derivano dal flag «Usa RFQ») */
    private static String servicesMode(OfferRequest req) {
        String m = req.servicesMode() == null ? "" : req.servicesMode().trim().toUpperCase();
        if (java.util.List.of("NONE", "MANUAL", "RFQ").contains(m)) {
            return m;
        }
        return Boolean.TRUE.equals(req.optUseRfq()) ? "RFQ" : "MANUAL";
    }

    private static boolean rfq(OfferRequest req) {
        return "RFQ".equals(servicesMode(req));
    }

    private static BigDecimal actDiscount(OfferRequest req) {
        BigDecimal d = req.activityDiscountPct() == null ? BigDecimal.ZERO : req.activityDiscountPct();
        if (d.signum() < 0 || d.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("Sconto sulle attività: valore tra 0 e 100");
        }
        return d;
    }

    /** tariffe orarie modificate nell'offerta (pagina Contenuti): prevalgono su account e listino */
    private void saveProfileRates(long offerId, OfferRequest req) {
        if (req.profileRates() == null) {
            return;
        }
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (OfferDtos.ProfileRateReq r : req.profileRates()) {
            if (r == null || r.profileId() == null || r.amount() == null || !seen.add(r.profileId())) {
                continue;
            }
            if (r.amount().signum() < 0 || r.amount().compareTo(new BigDecimal("99999.99")) > 0) {
                throw new IllegalArgumentException("Tariffa oraria: valore tra 0 e 99.999,99");
            }
            jdbc.sql("insert into offer_profile_rate (offer_id, profile_id, amount) select :o, p.id, :a from professional_profile p where p.id = :p and p.deleted_at is null")
                    .param("o", offerId).param("p", r.profileId()).param("a", r.amount().setScale(2, java.math.RoundingMode.HALF_UP)).update();
        }
    }

    private List<OfferDtos.ProfileRateReq> loadProfileRates(long offerId) {
        return jdbc.sql("select profile_id, amount from offer_profile_rate where offer_id = :id order by profile_id").param("id", offerId)
                .query((rs, n) -> new OfferDtos.ProfileRateReq(rs.getLong("profile_id"), rs.getBigDecimal("amount"))).list();
    }

    /** righe di attivita' sistemistica: applicazione presente nell'offerta, profilo esistente (tariffa riletta dal profilo), ore > 0 */
    private void saveActivities(long offerId, OfferRequest req) {
        if (req.activities() == null || "NONE".equals(servicesMode(req))) {
            return;
        }
        java.util.Set<Long> apps = new java.util.HashSet<>();
        if (req.lines() != null) {
            req.lines().forEach(l -> apps.add(l.applicationId()));
        }
        int sort = 0;
        for (OfferDtos.ActivityReq a : req.activities()) {
            String desc = a.description() == null ? "" : a.description().trim();
            boolean imported = rfq(req) && Boolean.TRUE.equals(a.rfqImport());      // le righe RFQ possono riferirsi ad applicazioni non (ancora) tra i Dipartimentali
            boolean notFound = imported && (a.applicationId() == null || a.applicationId() == 0);      // gruppo «NON TROVATO» dell'import RFQ
            boolean manual = !imported && "MANUAL".equals(servicesMode(req));
            boolean freeGroup = manual && (a.applicationId() == null || a.applicationId() == 0);        // servizi manuali in un gruppo senza applicazione
            if (!notFound && !freeGroup && (a.applicationId() == null || (!imported && !apps.contains(a.applicationId())))) {
                throw new IllegalArgumentException("Attività riferita a un'applicazione non presente nell'offerta");
            }
            if (desc.isEmpty() || desc.length() > 250) {
                throw new IllegalArgumentException("Attività: descrizione obbligatoria (max 250 caratteri)");
            }
            if (a.hours() == null || a.hours().signum() <= 0 || a.hours().compareTo(new BigDecimal("9999")) > 0) {
                throw new IllegalArgumentException("Attività «" + desc + "»: ore tra 0,01 e 9999");
            }
            Object[] prof = jdbc.sql("select p.name, coalesce(o.amount, x.rate, p.amount) as amount from professional_profile p "
                    + "left join account_profile_rate x on x.profile_id = p.id and x.account_id = :acc "
                    + "left join offer_profile_rate o on o.profile_id = p.id and o.offer_id = :off where p.id = :id and p.deleted_at is null")
                    .param("id", a.profileId()).param("acc", req.customerId()).param("off", offerId).query((rs, n) -> new Object[]{rs.getString("name"), rs.getBigDecimal("amount")}).optional()
                    .orElseThrow(() -> new IllegalArgumentException("Attività «" + desc + "»: profilo professionale non valido"));
            String grp = (imported || manual) && a.rfqGroup() != null && !a.rfqGroup().isBlank() ? a.rfqGroup().trim() : null;
            if (grp != null && grp.length() > 250) {
                grp = grp.substring(0, 250);
            }
            jdbc.sql("insert into offer_activity (offer_id, application_id, sort_order, description, profile_id, profile_name, hours, rate, rfq_import, rfq_group, days) "
                    + "values (:o, :a, :s, :d, :p, :pn, :h, :r, :ri, :rg, :dd)")
                    .param("o", offerId).param("a", notFound || freeGroup ? null : a.applicationId()).param("s", sort += 10).param("d", desc).param("p", a.profileId())
                    .param("pn", prof[0]).param("h", a.hours().setScale(2, java.math.RoundingMode.HALF_UP)).param("r", prof[1])
                    .param("ri", imported).param("rg", grp)
                    .param("dd", imported && a.days() != null ? a.days().setScale(2, java.math.RoundingMode.HALF_UP) : null).update();
        }
    }

    // --------------------------------------------------------------------- lettura
    public OfferHeader header(long id) {
        return jdbc.sql("""
                select id, number, offer_date, progressive, initials, customer_id, project_title, contact_ref,
                       discount_pct, status, opt_maintenance_additional, opt_extra_services, opt_final_summary,
                       distance_km, nearest_sede, travel_rate_id, activity_discount_pct, opt_activity_detail, maint_on_net,
                       opt_power_system, galileo_application_id, opt_smitech_services, opt_departmental,
                       power_model_id, power_ram_gb, power_disk, power_lto8, power_eth10, power_ibmi_users, power_ibmi_unlimited,
                       power_cable, power_transfer, power_transfer_ref, power_edition, power_os, power_maint_months, power_list_price, power_net_price, power_disk_count, opt_use_rfq, opt_services_days, app_totals_mode, rfq_sheet_total, services_mode
                from offer where id = :id and deleted_at is null""")
                .param("id", id).query(OfferHeader.class).optional()
                .orElseThrow(() -> new IllegalArgumentException("Offerta inesistente"));
    }

    public Long ownerOf(long id) {
        return jdbc.sql("select created_by from offer where id = :id and deleted_at is null").param("id", id).query(Long.class).optional().orElse(null);
    }

    private record LineRow(long id, long applicationId, String modeCode) {
    }

    private record DrvRow(long lineId, long driverId, int qty) {
    }

    private record ModRow(long lineId, long moduleId, int qty) {
    }

    /** ricostruisce la richiesta (per riaprire l'offerta nel wizard) */
    public OfferRequest loadRequest(long id) {
        OfferHeader h = header(id);
        List<LineRow> lines = jdbc.sql("select id, application_id, mode_code from offer_line where offer_id = :id order by sort_order, id")
                .param("id", id).query(LineRow.class).list();
        List<LineRequest> out = new ArrayList<>();
        for (LineRow l : lines) {
            Map<Long, Integer> drivers = new LinkedHashMap<>();
            for (DrvRow d : jdbc.sql("select line_id, driver_id, qty from offer_line_driver where line_id = :l")
                    .param("l", l.id()).query(DrvRow.class).list()) {
                drivers.put(d.driverId(), d.qty());
            }
            List<ModuleQty> mods = new ArrayList<>();
            for (ModRow m : jdbc.sql("select line_id, module_id, qty from offer_line_module where line_id = :l")
                    .param("l", l.id()).query(ModRow.class).list()) {
                mods.add(new ModuleQty(m.moduleId(), m.qty()));
            }
            out.add(new LineRequest(l.applicationId(), l.modeCode(), drivers, mods));
        }
        return new OfferRequest(h.id(), h.offerDate(), h.progressive(), h.customerId(), h.projectTitle(), h.contactRef(),
                h.discountPct(), out, h.optMaintenanceAdditional(), h.optExtraServices(), h.optFinalSummary(),
                h.optExtraServices() ? extraRepo.loadSelection(id) : ExtraModel.Selection.empty(), termsRepo.load(id),
                h.distanceKm(), h.nearestSede(), h.travelRateId(), loadActivities(id), h.activityDiscountPct(), h.optActivityDetail(), h.maintOnNet(),
                h.optPowerSystem(), h.galileoApplicationId(), h.optSmitechServices(), h.optDepartmental(),
                h.powerModelId(), h.powerRamGb(), h.powerDisk(), h.powerLto8(), h.powerEth10(), h.powerIbmiUsers(), h.powerIbmiUnlimited(),
                h.powerCable(), h.powerTransfer(), h.powerTransferRef(), h.powerEdition(), h.powerOs(), h.powerMaintMonths(), h.powerListPrice(), h.powerNetPrice(), loadPowerProducts(id), h.powerDiskCount(), h.optUseRfq(), h.optServicesDays(), h.appTotalsMode(), h.rfqSheetTotal(), galileo.load(id), h.servicesMode(), loadProfileRates(id), loadPowerMaint(id), jdbc.sql("select opt_hours_detail from offer where id = :id").param("id", id).query(Boolean.class).optional().orElse(false),
                jdbc.sql("select activity_discount_until from offer where id = :id").param("id", id).query((rs, n) -> rs.getObject(1, java.time.LocalDate.class)).optional().orElse(null));
    }

    private List<OfferDtos.ActivityReq> loadActivities(long offerId) {
        return jdbc.sql("select application_id, description, profile_id, hours, rate, profile_name, rfq_group, rfq_import, days from offer_activity where offer_id = :id order by sort_order, id")
                .param("id", offerId).query((rs, n) -> new OfferDtos.ActivityReq(rs.getLong("application_id"), rs.getString("description"),
                        rs.getLong("profile_id"), rs.getBigDecimal("hours"), rs.getBigDecimal("rate"), rs.getString("profile_name"),
                        rs.getString("rfq_group"), rs.getBoolean("rfq_import"), rs.getBigDecimal("days"))).list();
    }

    public List<OfferSummary> list(AppUser user) {
        String sql = """
                select o.id, o.number, o.offer_date, c.company_name, c.code as customer_code, o.project_title, o.status,
                       o.total_license, o.total_maintenance, o.discount_pct,
                       coalesce(u.full_name, u.email) as owner_name, o.created_by, o.updated_at
                from offer o join account c on c.id = o.customer_id join app_user u on u.id = o.created_by
                where o.deleted_at is null
                """;
        if (user.administrator() || user.canViewAllOffers()) {
            return jdbc.sql(sql + " order by o.offer_date desc, o.id desc").query(OfferSummary.class).list();
        }
        return jdbc.sql(sql + " and o.created_by = :u order by o.offer_date desc, o.id desc")
                .param("u", user.id()).query(OfferSummary.class).list();
    }

    /** sposta l'offerta nel Cestino (cancellazione logica) */
    @Transactional
    public void delete(long id, it.cpqia.domain.AppUser user) {
        trash.delete(TrashKind.OFFER, id, user);
    }

    /** suggerisce il prossimo progressivo per la data e le iniziali indicate */
    public int nextProgressive(java.time.LocalDate date, String initials) {
        Integer max = jdbc.sql("select coalesce(max(progressive), 0) from offer where offer_date = :d and initials = :i and deleted_at is null")
                .param("d", date).param("i", initials == null ? "" : initials.toUpperCase()).query(Integer.class).single();
        return max + 1;
    }

    private static boolean flag(Boolean b) {
        return b != null && b;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
