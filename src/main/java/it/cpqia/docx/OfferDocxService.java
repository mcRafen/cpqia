package it.cpqia.docx;

import it.cpqia.domain.Customer;
import it.cpqia.extras.ExtraCalculator;
import it.cpqia.extras.ExtraModel;
import it.cpqia.extras.ExtraRepository;
import it.cpqia.pricing.PricingModel.App;
import it.cpqia.pricing.PricingModel.Driver;
import it.cpqia.pricing.PricingModel.Module;
import it.cpqia.repo.CatalogRepository;
import it.cpqia.repo.CustomerRepository;
import it.cpqia.service.OfferDtos.OfferHeader;
import it.cpqia.terms.TermsConfig;
import it.cpqia.terms.TermsModel.PaymentTerms;
import it.cpqia.terms.TermsRepository;
import org.apache.poi.ooxml.POIXMLDocumentPart;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.PackageRelationship;
import org.apache.poi.openxml4j.opc.TargetMode;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.impl.xb.xmlschema.SpaceAttribute;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTPPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTRow;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTSectPr;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTc;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTText;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.Set;
import java.util.HashSet;

/**
 * Genera l'offerta Word assemblando, nell'ordine, i file modello:
 * <pre>
 *   offer-template.docx   copertina ({{TITOLO}}, {{CLIENTE}}, {{INDIRIZZO1/2}}, {{PIVA}}, {{RIF}}, {{DATA}}, {{NUMERO}})
 *   licenze.docx          tabella licenze (righe prototipo {{L_*}}) e totali ({{T_*}}, {{N_*}})
 *   attivita.docx   varie.docx   pagamenti.docx   condizioni-gdpr.docx
 * </pre>
 * Prima di ogni parte agganciata viene inserita un'interruzione di pagina (non serve dopo la copertina, che termina
 * gia' con un salto sezione "pagina successiva"). Se un file contiene anche le sezioni delle parti successive
 * (divisione incompleta), viene tagliato al primo titolo "Titolo 1" che appartiene a una parte successiva.
 * I modelli sono letti da cpqia.template-dir (se impostato e il file esiste) altrimenti dal classpath offer-template/.
 * Gli importi usano lo snapshot salvato nell'offerta, non il listino corrente.
 */
@Service
public class OfferDocxService {

    private static final Locale IT = Locale.ITALY;
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ITALIAN);
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final JdbcClient jdbc;
    private final CatalogRepository catalog;
    private final CustomerRepository customers;
    private final String templateDir;
    private final it.cpqia.power.PowerDocumentService powerDocs;
    private final ExtraRepository extraRepo;
    private final ExtraCalculator extraCalc;
    private final TermsConfig termsConfig;
    private final TermsRepository termsRepo;

    public OfferDocxService(JdbcClient jdbc, CatalogRepository catalog, CustomerRepository customers,
                            ExtraRepository extraRepo, ExtraCalculator extraCalc, TermsConfig termsConfig, TermsRepository termsRepo,
                            it.cpqia.power.PowerDocumentService powerDocs,
                            @Value("${cpqia.template-dir:}") String templateDir) {
        this.powerDocs = powerDocs;
        this.jdbc = jdbc;
        this.catalog = catalog;
        this.customers = customers;
        this.extraRepo = extraRepo;
        this.extraCalc = extraCalc;
        this.termsConfig = termsConfig;
        this.termsRepo = termsRepo;
        this.templateDir = templateDir == null ? "" : templateDir.trim();
    }

    /** parti agganciate dopo la copertina: chiave, nomi di file accettati (il primo e' lo standard) e titolo con cui iniziano */
    private record Part(String key, List<String> files, List<String> titles) {
    }

    /** ordine di assemblaggio; servizi-accessori e riepilogo-finale sono inclusi solo se richiesti nella testata offerta */
    private static final List<Part> PARTS = List.of(
            new Part("licenze", List.of("licenze.docx"), List.of("LICENZE")),
            // sezione Power System (solo con l'elemento Power System): documento del modello + tabella hardware; nessun titolo da tagliare
            new Part("power-model", List.of(), List.of()),
            new Part("power-hardware", List.of("Power/tabelle-power-hardware.docx"), List.of()),
            new Part("power-software", List.of("Power/tabelle-power-software.docx"), List.of()),
            new Part("power-manutenzione", List.of("Power/tabelle-power-manutenzione.docx"), List.of()),
            new Part("power-licenze", List.of("Power/tabelle-power-chiavi di licenza.docx"), List.of()),
            new Part("power-solution", List.of("Power/tabelle-power-solution edition.docx"), List.of()),
            new Part("power-migrazione", List.of("Power/tabelle-power-migrazione programmi.docx"), List.of()),
            new Part("power-transfer", List.of("Power/tabelle-power-transfer.docx"), List.of()),
            new Part("power-nvme", List.of("Power/tabelle-power-caratteristiche nvme.docx"), List.of()),
            new Part("power-smaltimento", List.of("Power/tabelle-power-smaltimento.docx"), List.of()),
            new Part("attivita", List.of("attivita.docx", "attività.docx"), List.of("ATTIVITA")),
            new Part("manutenzione", List.of("manutenzione.docx"), List.of("MANUTENZIONE")),
            new Part("servizi-accessori", List.of("servizi-accessori.docx"), List.of("SERVIZI ACCESSORI")),
            new Part("riepilogo-finale", List.of("riepilogo-finale.docx"), List.of("RIEPILOGO")),
            new Part("varie", List.of("varie.docx"), List.of("VARIE")),
            new Part("pagamenti", List.of("pagamenti.docx"), List.of("MODALITA")),
            new Part("condizioni-gdpr", List.of("condizioni-gdpr.docx", "condizioni e gdpr.docx"), List.of("CONCLUSIONI")));

    /** apre il primo file esistente tra i nomi indicati: prima in cpqia.template-dir, poi nel classpath */
    private InputStream openTemplate(List<String> names) throws IOException {
        if (!templateDir.isEmpty()) {
            for (String name : names) {
                Path p = Path.of(templateDir, name);
                if (Files.isRegularFile(p)) {
                    return Files.newInputStream(p);
                }
            }
        }
        for (String name : names) {
            ClassPathResource r = new ClassPathResource("offer-template/" + name);
            if (r.exists()) {
                return r.getInputStream();
            }
        }
        throw new IOException("Modello Word non trovato: " + names.get(0));
    }

    private record LineRow(long id, long applicationId, String modeCode, BigDecimal licenseAmount, BigDecimal maintenanceAmount) {
    }

    private record ModRow(long moduleId, int qty) {
    }

    private record DrvRow(long driverId, int qty) {
    }

    /** riga già pronta per la tabella */
    record TableLine(List<String> codes, List<String> descriptions, String license, String maintenance, String seats) {
    }

    public byte[] generate(OfferHeader h) throws IOException {
        Customer c = customers.findById(h.customerId()).orElseThrow();
        List<LineRow> lines = jdbc.sql("""
                select id, application_id, mode_code, license_amount, maintenance_amount
                from offer_line where offer_id = :id order by sort_order, id""")
                .param("id", h.id()).query(LineRow.class).list();

        List<TableLine> tableLines = new ArrayList<>();
        BigDecimal totLic = BigDecimal.ZERO;
        BigDecimal totMan = BigDecimal.ZERO;
        for (LineRow l : lines) {
            App app = catalog.find(l.applicationId()).orElseThrow();
            List<String> codes = new ArrayList<>();
            List<String> descr = new ArrayList<>();
            // moduli selezionati, nell'ordine del catalogo
            sortLike(app, codes, descr, l.id());
            Map<Long, Integer> drivers = new LinkedHashMap<>();
            for (DrvRow d : jdbc.sql("select driver_id, qty from offer_line_driver where line_id = :l")
                    .param("l", l.id()).query(DrvRow.class).list()) {
                drivers.put(d.driverId(), d.qty());
            }
            tableLines.add(new TableLine(codes, descr, eur(l.licenseAmount()), eur(l.maintenanceAmount()), seatsText(app, drivers)));
            totLic = totLic.add(l.licenseAmount());
            totMan = totMan.add(l.maintenanceAmount());
        }

        // Galileo: listino di vendita e manutenzione della configurazione entrano nelle licenze e nei totali
        record GalRow(BigDecimal list, BigDecimal maint, int users, String label, String market) {
        }
        GalRow gal = jdbc.sql("select og.list_total, og.maintenance, og.users, gp.label, a.name as market from offer_galileo og "
                + "join galileo_profile gp on gp.id = og.profile_id join application a on a.id = gp.application_id where og.offer_id = :id")
                .param("id", h.id()).query((rs, n) -> new GalRow(rs.getBigDecimal("list_total"), rs.getBigDecimal("maintenance"), rs.getInt("users"),
                        rs.getString("label"), rs.getString("market"))).optional().orElse(null);
        if (gal != null && gal.list() != null) {
            BigDecimal gm = gal.maint() == null ? BigDecimal.ZERO : gal.maint();
            tableLines.add(new TableLine(List.of("GALILEO"), List.of(gal.market() + " – " + gal.label()), eur(gal.list()), eur(gm), gal.users() + " utenti"));
            totLic = totLic.add(gal.list());
            totMan = totMan.add(gm);
        }

        PowerRow power = h.optPowerSystem() && h.powerModelId() != null ? powerModel(h.powerModelId()) : null;
        Map<String, String> tokens = new LinkedHashMap<>();
        tokens.put("{{TITOLO}}", h.projectTitle() == null ? "" : h.projectTitle());
        tokens.put("{{CLIENTE}}", c.companyName());
        tokens.put("{{CLIENTE_BREVE}}", shortName(c.companyName()));
        tokens.put("{{INDIRIZZO1}}", c.addressLine1());
        tokens.put("{{INDIRIZZO2}}", c.addressLine2());
        tokens.put("{{PIVA}}", c.vatNumber());
        tokens.put("{{RIF}}", h.contactRef() == null ? "" : h.contactRef());
        tokens.put("{{DATA}}", h.offerDate().format(LONG_DATE));
        tokens.put("{{NUMERO}}", h.number());
        if (power != null) {                                // segnaposto utilizzabili nei documenti Word della sezione Power System
            tokens.put("{{PW_MODELLO}}", power.model());
            tokens.put("{{PW_TIPO}}", power.type());
            tokens.put("{{PW_CODICE}}", power.model() + " " + power.type());
            tokens.put("{{PW_DESCRIZIONE}}", power.description());
            tokens.put("{{PW_CORE}}", String.valueOf(power.cores()));
            tokens.put("{{PW_MONTAGGIO}}", power.mounting());
            tokens.put("{{PW_GHZ}}", power.ghz());
            tokens.put("{{PW_CPW}}", new java.text.DecimalFormat("#,##0", java.text.DecimalFormatSymbols.getInstance(Locale.ITALY)).format(power.cpw()));
            tokens.put("{{PW_RAM}}", h.powerRamGb() == null ? "" : h.powerRamGb() + " GB");
            tokens.put("{{PW_DISCO}}", h.powerDisk() == null ? "" : h.powerDisk());
            tokens.put("{{PW_N_DISCHI}}", String.valueOf(h.powerDiskCount()));
            tokens.put("{{PW_PREZZO_LISTINO}}", h.powerListPrice() == null ? "" : eur(h.powerListPrice()));
            tokens.put("{{PW_PREZZO_NETTO}}", h.powerNetPrice() == null ? "" : eur(h.powerNetPrice()));
            tokens.put("{{PW_MESI_MANUTENZIONE}}", maintPeriodsText(h));
            boolean tr = transferOn(h);
            tokens.put("{{TRANSFER-CONVERSION}}", tr ? ("CONVERSION".equalsIgnoreCase(h.powerTransfer()) ? "Conversion" : "Transfer") : "");
            tokens.put("{{MACCHINA_DONOR}}", h.powerTransferRef() == null ? "" : h.powerTransferRef().trim());
            tokens.put("{{SOFTWARE_IBM}}", tr ? migratedSoftware(h.id()) : "");
            tokens.put("{{PW_UTENTI}}", h.powerIbmiUnlimited() ? "ILLIMITATI" : (h.powerIbmiUsers() == null ? "" : String.valueOf(h.powerIbmiUsers())));
        }

        // modalita' di fatturazione e pagamento (tabella di pagamenti.docx)
        PaymentTerms terms = termsRepo.load(h.id());
        tokens.put("{{PAG_LIC_FATT}}", termsConfig.billLicText(terms));
        tokens.put("{{PAG_LIC_PAG}}", termsConfig.payText(terms.payLic(), terms.payLicOther()));
        tokens.put("{{PAG_CAN_FATT}}", termsConfig.billFeeText(terms));
        tokens.put("{{PAG_CAN_PAG}}", termsConfig.payText(terms.payFee(), terms.payFeeOther()));
        tokens.put("{{PAG_ATT_FATT}}", termsConfig.billActText(terms));
        tokens.put("{{PAG_ATT_PAG}}", termsConfig.payText(terms.payAct(), terms.payActOther()));

        BigDecimal factor = BigDecimal.ONE.subtract(h.discountPct().divide(HUNDRED));
        boolean hasDiscount = h.discountPct().signum() != 0;

        try (InputStream in = openTemplate(List.of("offer-template.docx"));
             XWPFDocument doc = new XWPFDocument(in)) {

            // "Rif." senza valore: elimina la riga
            if (h.contactRef() == null || h.contactRef().isBlank()) {
                for (int i = 0; i < doc.getBodyElements().size(); i++) {
                    if (doc.getBodyElements().get(i) instanceof XWPFParagraph p && p.getText().contains("{{RIF}}")) {
                        doc.removeBodyElement(i);
                        break;
                    }
                }
            }
            replaceEverywhere(doc, tokens);
            stripTrailingBlankParagraphs(doc);
            ensureSectionBreakAfterCover(doc);

            // valori netti (dopo lo sconto) per il riepilogo finale
            BigDecimal netLic = totLic.multiply(factor).setScale(2, java.math.RoundingMode.HALF_UP);
            // «Manutenzione su netto scontato»: se no, la manutenzione resta sul valore di listino delle licenze
            BigDecimal netMan = h.maintOnNet() ? totMan.multiply(factor).setScale(2, java.math.RoundingMode.HALF_UP) : totMan;
            ExtraModel.Selection extraSel = h.optExtraServices() ? extraRepo.loadSelection(h.id()) : ExtraModel.Selection.empty();
            ExtraModel.Totals extraTotals = h.optExtraServices() ? extraCalc.calculate(extraSel, null) : null;
            BigDecimal activitiesTotal = null;
            List<ActRow> activityRows = loadActivityRows(h);

            String prevKey = "";                               // hardware e software Power seguono il documento precedente senza salto pagina
            for (int idx = 0; idx < PARTS.size(); idx++) {
                Part pt = PARTS.get(idx);
                if (("servizi-accessori".equals(pt.key()) && !h.optExtraServices())
                        || ("riepilogo-finale".equals(pt.key()) && !h.optFinalSummary())
                        || (pt.key().startsWith("power-") && (!h.optPowerSystem() || power == null))
                        || ("power-solution".equals(pt.key()) && !"SOLUTION".equalsIgnoreCase(h.powerEdition()))
                        || (("power-migrazione".equals(pt.key()) || "power-transfer".equals(pt.key())) && !transferOn(h))
                        || ("licenze".equals(pt.key()) && tableLines.isEmpty())                  // senza Dipartimentali non ci sono licenze
                        || ("attivita".equals(pt.key()) && (activityRows.isEmpty() || "NONE".equals(h.servicesMode())))) {      // nessuna attività configurata: la sezione non c'è
                    continue;
                }
                // documento Word collegato al modello Power (cartella Power); se manca o non e' leggibile la sezione si salta
                // «Modello offerta Light» (= riepilogo finale): le attività usano attivita-light.docx (con tariffe e valori), altrimenti attivita.docx (con figura professionale)
                List<String> partFiles = "attivita".equals(pt.key()) && h.optFinalSummary() ? List.of("attivita-light.docx", "attivita.docx") : pt.files();
                InputStream pin = "power-model".equals(pt.key()) ? powerDocs.open(power.wordFile()).orElse(null) : openTemplate(partFiles);
                if (pin == null) {
                    continue;
                }
                try (InputStream partIn = pin;
                     XWPFDocument part = new XWPFDocument(partIn)) {
                    List<IBodyElement> elems;
                    switch (pt.key()) {
                        case "licenze" -> {
                            fillLicenceTable(part.getTables().get(0), tableLines);
                            fillTotals(part.getTables().get(1), eur(totLic), eur(totMan), eur(netLic), eur(netMan), hasDiscount);
                            replaceEverywhere(part, tokens);
                            elems = trimToOwnPart(part.getBodyElements(), idx);
                        }
                        case "attivita" -> {
                            activitiesTotal = fillActivityTables(part, activityRows, h.activityDiscountPct(), h.optActivityDetail(), h.optServicesDays(), h.appTotalsMode(), tokens);
                            replaceEverywhere(part, tokens);
                            elems = trimToOwnPart(part.getBodyElements(), idx);
                        }
                        case "power-model" -> {
                            replaceEverywhere(part, tokens);
                            elems = part.getBodyElements();
                        }
                        case "power-hardware" -> {
                            fillPowerHardware(part, power, h);
                            replaceEverywhere(part, tokens);
                            elems = part.getBodyElements();
                        }
                        case "power-manutenzione" -> {
                            fillPowerMaintenance(part, h);              // una riga di durata e i totali (listino e netto) per ogni periodo selezionato
                            replaceEverywhere(part, tokens);            // {{PW_PREZZO_LISTINO}} e {{PW_PREZZO_NETTO}} nella tabella dei totali
                            elems = part.getBodyElements();
                        }
                        case "power-licenze" -> {
                            replaceEverywhere(part, tokens);            // {{PW_MESI_MANUTENZIONE}}: durata della subscription
                            elems = part.getBodyElements();
                        }
                        case "power-migrazione", "power-transfer" -> {
                            replaceEverywhere(part, tokens);            // {{TRANSFER-CONVERSION}}, {{MACCHINA_DONOR}}, {{SOFTWARE_IBM}}
                            elems = part.getBodyElements();
                        }
                        case "power-nvme", "power-smaltimento" -> {
                            replaceEverywhere(part, tokens);
                            elems = part.getBodyElements();
                        }
                        case "power-solution" -> {
                            replaceEverywhere(part, tokens);
                            elems = part.getBodyElements();
                        }
                        case "power-software" -> {
                            fillPowerSoftware(part, power, h);
                            replaceEverywhere(part, tokens);
                            elems = part.getBodyElements();
                        }
                        case "servizi-accessori" -> elems = prepareExtraServices(part, extraSel, extraTotals, tokens);
                        case "riepilogo-finale" -> {
                            fillFinalSummary(part, netLic, activitiesTotal, netMan, h.optMaintenanceAdditional(), tokens);
                            elems = trimToOwnPart(part.getBodyElements(), idx);
                        }
                        default -> {
                            replaceEverywhere(part, tokens);
                            elems = trimToOwnPart(part.getBodyElements(), idx);
                        }
                    }
                    if ("power-model".equals(pt.key()) || "power-hardware".equals(pt.key()) || "power-software".equals(pt.key())
                            || "power-manutenzione".equals(pt.key()) || "power-licenze".equals(pt.key())
                            || "power-solution".equals(pt.key()) || "power-migrazione".equals(pt.key())
                            || "power-transfer".equals(pt.key()) || "power-nvme".equals(pt.key())) {
                        elems = dropTrailingBlanks(elems);          // paragrafi vuoti finali dei documenti: a separare le tabelle basta una riga vuota
                    }
                    boolean hardware = ("power-hardware".equals(pt.key()) && "power-model".equals(prevKey))
                            || ("power-software".equals(pt.key()) && "power-hardware".equals(prevKey))
                            || ("power-licenze".equals(pt.key()) && "power-manutenzione".equals(prevKey))
                            || "power-solution".equals(pt.key()) || "power-migrazione".equals(pt.key())
                            || "power-nvme".equals(pt.key()) || "power-smaltimento".equals(pt.key());       // power-transfer: pagina nuova     // la manutenzione hardware va in una pagina nuova; le chiavi di licenza la seguono con una riga vuota
                    if (hardware) {
                        doc.createParagraph();            // tabella hardware di seguito al documento del modello: solo una riga vuota, nessun salto pagina
                        if ("power-licenze".equals(pt.key()) || "power-solution".equals(pt.key())
                                || "power-migrazione".equals(pt.key()) || "power-nvme".equals(pt.key()) || "power-smaltimento".equals(pt.key())) {
                            doc.createParagraph();        // documenti successivi (licenze, solution, migrazione, transfer): due righe vuote dopo il documento precedente
                        }
                    }
                    appendPart(doc, part, elems, !hardware, pt.key().startsWith("power-"));
                    prevKey = pt.key();
                    // seconda tabella del riepilogo finale: solo con "aggiungi servizi accessori" (stessa pagina, nessun salto)
                    if ("riepilogo-finale".equals(pt.key()) && h.optExtraServices() && extraTotals != null) {
                        try (InputStream sin = openTemplate(List.of("riepilogo-finale-servizi-accessori.docx"));
                             XWPFDocument sa = new XWPFDocument(sin)) {
                            fillExtrasSummaryTable(sa.getTables().get(0), extraTotals);
                            replaceEverywhere(sa, tokens);
                            appendPart(doc, sa, new ArrayList<>(sa.getBodyElements()), false);
                        }
                    }
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.write(out);
            return out.toByteArray();
        }
    }

    // ------------------------------------------------------------------ assemblaggio dei file modello
    private static final String NS_R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships";
    private static final Pattern W_T = Pattern.compile("<w:t(?:\\s[^>]*)?>([^<]*)</w:t>");

    /** testo di tutti gli elementi w:t del paragrafo (comprese le caselle di testo) */
    private static String allText(CTP p) {
        StringBuilder sb = new StringBuilder();
        Matcher m = W_T.matcher(p.xmlText());
        while (m.find()) {
            sb.append(m.group(1));
        }
        return sb.toString().trim();
    }

    private static boolean hasSectPr(CTP p) {
        return p.getPPr() != null && p.getPPr().isSetSectPr();
    }

    /** paragrafo senza testo, immagini/forme e salto sezione (eventualmente con solo un'interruzione di pagina) */
    private static boolean isBlankOrBreak(IBodyElement e) {
        if (!(e instanceof XWPFParagraph p)) {
            return false;
        }
        CTP ctp = p.getCTP();
        if (hasSectPr(ctp) || !allText(ctp).isEmpty()) {
            return false;
        }
        String xml = ctp.xmlText();
        return !xml.contains("<w:drawing") && !xml.contains("<w:pict") && !xml.contains("<mc:AlternateContent");
    }

    private static boolean isHeading1(IBodyElement e) {
        if (!(e instanceof XWPFParagraph p)) {
            return false;
        }
        String st = p.getStyleID();
        return "Heading1".equals(st) || "Titolo1".equals(st);
    }

    private static String norm(String s) {
        return s.toUpperCase(Locale.ROOT).replace('À', 'A').replace("’", "").replace("'", "")
                .replaceAll("[^A-Z ]", "").trim();
    }

    /** taglia la parte al primo titolo che appartiene a una parte successiva (e toglie i paragrafi vuoti che lo precedono) */
    private static List<IBodyElement> trimToOwnPart(List<IBodyElement> elems, int ownIdx) {
        List<String> later = new ArrayList<>();
        for (int i = ownIdx + 1; i < PARTS.size(); i++) {
            for (String t : PARTS.get(i).titles()) {
                later.add(norm(t));
            }
        }
        for (int i = 0; i < elems.size(); i++) {
            IBodyElement e = elems.get(i);
            if (isHeading1(e)) {
                String n = norm(allText(((XWPFParagraph) e).getCTP()));
                for (String l : later) {
                    if (n.startsWith(l)) {
                        int j = i;
                        while (j > 0 && isBlankOrBreak(elems.get(j - 1))) {
                            j--;
                        }
                        return new ArrayList<>(elems.subList(0, j));
                    }
                }
            }
        }
        return elems;
    }

    /** toglie dal modello base i paragrafi finali vuoti / con solo salto pagina: l'interruzione la inserisce l'assemblaggio */
    private static void stripTrailingBlankParagraphs(XWPFDocument doc) {
        while (!doc.getBodyElements().isEmpty()) {
            int last = doc.getBodyElements().size() - 1;
            if (!isBlankOrBreak(doc.getBodyElements().get(last))) {
                break;
            }
            doc.removeBodyElement(last);
        }
    }

    /**
     * La copertina ha un piè di pagina diverso dal resto: deve terminare con un'interruzione di sezione (pagina successiva),
     * dopo la quale vengono agganciate le parti (che usano intestazione/piè di pagina dell'ultima sezione del modello).
     * Se il modello non ne ha una (copertina e corpo nella stessa sezione) la inserisce, copiando le impostazioni di sezione.
     */
    private static void ensureSectionBreakAfterCover(XWPFDocument doc) {
        for (IBodyElement e : doc.getBodyElements()) {
            if (e instanceof XWPFParagraph p && hasSectPr(p.getCTP())) {
                return;
            }
        }
        CTSectPr body = doc.getDocument().getBody().getSectPr();
        if (body == null) {
            return;
        }
        XWPFParagraph p = doc.createParagraph();
        p.getCTP().addNewPPr().setSectPr((CTSectPr) body.copy());
    }

    /** accoda gli elementi della parte al documento con interruzione di pagina iniziale, ricollegando immagini e link */
    private void appendPart(XWPFDocument main, XWPFDocument part, List<IBodyElement> elems, boolean pageBreak) {
        appendPart(main, part, elems, pageBreak, false);
    }

    /** stripSections: toglie le interruzioni di sezione interne (documenti caricati dall'utente, con intestazioni proprie) */
    private void appendPart(XWPFDocument main, XWPFDocument part, List<IBodyElement> elems, boolean pageBreak, boolean stripSections) {
        if (elems.isEmpty()) {
            return;
        }
        List<IBodyElement> mainEls = main.getBodyElements();
        IBodyElement last = mainEls.isEmpty() ? null : mainEls.get(mainEls.size() - 1);
        boolean afterSectionBreak = last instanceof XWPFParagraph lp && hasSectPr(lp.getCTP());
        boolean needBreak = pageBreak && !afterSectionBreak;
        Map<String, String> relMap = new HashMap<>();
        boolean first = true;
        for (IBodyElement e : elems) {
            if (e instanceof XWPFParagraph src) {
                XWPFParagraph np = main.createParagraph();
                np.getCTP().set(src.getCTP().copy());
                if (stripSections && np.getCTP().isSetPPr() && np.getCTP().getPPr().isSetSectPr()) {
                    np.getCTP().getPPr().unsetSectPr();
                }
                // segnalibri (ancore del sommario dei singoli file): id duplicati tra le parti, non servono
                while (np.getCTP().sizeOfBookmarkStartArray() > 0) {
                    np.getCTP().removeBookmarkStart(0);
                }
                while (np.getCTP().sizeOfBookmarkEndArray() > 0) {
                    np.getCTP().removeBookmarkEnd(0);
                }
                remapRelationships(np.getCTP(), part, main, relMap);
                if (first && needBreak) {
                    np.setPageBreak(true);
                }
            } else if (e instanceof XWPFTable src) {
                if (first && needBreak) {
                    main.createParagraph().setPageBreak(true);
                }
                XWPFTable nt = main.createTable();
                nt.getCTTbl().set(src.getCTTbl().copy());
                remapRelationships(nt.getCTTbl(), part, main, relMap);
            } else {
                continue;
            }
            first = false;
        }
    }

    /** riscrive gli id di relazione (immagini, collegamenti ipertestuali) dal pacchetto della parte a quello principale */
    private void remapRelationships(XmlObject obj, XWPFDocument src, XWPFDocument dst, Map<String, String> cache) {
        remapNode(obj.getDomNode(), src, dst, cache);
    }

    /** visita il DOM del frammento: ogni attributo r:embed / r:id / r:link / r:pict e' un riferimento a una relazione */
    private void remapNode(org.w3c.dom.Node node, XWPFDocument src, XWPFDocument dst, Map<String, String> cache) {
        if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE && node.getAttributes() != null) {
            org.w3c.dom.NamedNodeMap attrs = node.getAttributes();
            for (int i = 0; i < attrs.getLength(); i++) {
                org.w3c.dom.Node a = attrs.item(i);
                if (!NS_R.equals(a.getNamespaceURI())) {
                    continue;
                }
                String name = a.getLocalName();
                if (!"embed".equals(name) && !"id".equals(name) && !"link".equals(name) && !"pict".equals(name)) {
                    continue;
                }
                String old = a.getNodeValue();
                String mapped = cache.get(old);
                if (mapped == null) {
                    mapped = copyRelationship(old, src, dst);
                    if (mapped != null) {
                        cache.put(old, mapped);
                    }
                }
                if (mapped != null && !mapped.equals(old)) {
                    a.setNodeValue(mapped);
                }
            }
        }
        for (org.w3c.dom.Node ch = node.getFirstChild(); ch != null; ch = ch.getNextSibling()) {
            remapNode(ch, src, dst, cache);
        }
    }

    private String copyRelationship(String oldId, XWPFDocument src, XWPFDocument dst) {
        try {
            PackageRelationship rel = src.getPackagePart().getRelationship(oldId);
            if (rel == null) {
                return null;
            }
            if (rel.getTargetMode() == TargetMode.EXTERNAL) {
                return dst.getPackagePart().addExternalRelationship(rel.getTargetURI().toString(), rel.getRelationshipType()).getId();
            }
            POIXMLDocumentPart target = src.getRelationById(oldId);
            if (target instanceof XWPFPictureData pic) {
                return dst.addPictureData(pic.getData(), pic.getPictureType());
            }
        } catch (InvalidFormatException e) {
            throw new IllegalStateException("Relazione non copiabile: " + oldId, e);
        }
        return null;   // intestazioni e piè di pagina, ecc.: non servono nel corpo
    }

    /** i moduli letti dal DB non hanno ordine: li riordina secondo il catalogo */
    private void sortLike(App app, List<String> codes, List<String> descr, long lineId) {
        // l'ordine del catalogo (sort_order) e' gia' l'ordine di app.modules(): ricostruisce le liste in tale ordine
        List<ModRow> rows = jdbc.sql("select module_id, qty from offer_line_module where line_id = :l")
                .param("l", lineId).query(ModRow.class).list();
        Map<Long, Integer> qty = new LinkedHashMap<>();
        for (ModRow r : rows) {
            qty.put(r.moduleId(), r.qty());
        }
        codes.clear();
        descr.clear();
        for (Module m : app.modules()) {
            Integer q = qty.get(m.id());
            if (q == null) {
                continue;
            }
            String suffix = q > 1 ? " (x " + q + ")" : "";
            codes.add(m.codeForPrint());
            descr.add(m.codeForPrint() + " - " + m.description().trim() + suffix);
        }
    }

    private static String seatsText(App app, Map<Long, Integer> drivers) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Driver d : app.drivers()) {
            Integer q = drivers.get(d.id());
            if (q == null || q <= 0) {
                continue;
            }
            if (!first) {
                sb.append(" - ");
            }
            if (first && "UTENTI".equals(d.code())) {
                sb.append("Licenze Posti di Lavoro n. ").append(q);
            } else {
                sb.append(d.label()).append(" n. ").append(q);
            }
            first = false;
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ tabelle
    private void fillLicenceTable(XWPFTable table, List<TableLine> lines) {
        CTRow protoA = table.getRow(1).getCtRow();
        CTRow protoB = table.getRow(2).getCtRow();
        for (TableLine tl : lines) {
            CTRow a = cloneRow(table, protoA);
            CTRow b = cloneRow(table, protoB);
            fillCellLines(a.getTcArray(0), tl.codes());
            fillCellLines(a.getTcArray(1), tl.descriptions());
            setCellText(a.getTcArray(2), tl.license());
            setCellText(a.getTcArray(3), tl.maintenance());
            setCellText(b.getTcArray(1), tl.seats());
        }
        table.removeRow(2);   // righe prototipo (le copie sono state aggiunte in coda)
        table.removeRow(1);
    }

    /** aggiunge in coda una copia della riga prototipo; si lavora direttamente sul CT (XWPFTable.getRows() e' di sola lettura) */
    private CTRow cloneRow(XWPFTable table, CTRow proto) {
        CTRow live = table.getCTTbl().addNewTr();
        live.set(proto.copy());
        return live;
    }

    private void fillTotals(XWPFTable table, String lic, String man, String netLic, String netMan, boolean hasDiscount) {
        setCellText(table.getRow(0).getCell(1).getCTTc(), lic);
        setCellText(table.getRow(0).getCell(2).getCTTc(), man);
        if (hasDiscount) {
            setCellText(table.getRow(1).getCell(1).getCTTc(), netLic);
            setCellText(table.getRow(1).getCell(2).getCTTc(), netMan);
        } else if (table.getNumberOfRows() > 1) {
            table.removeRow(1);
        }
    }

    private static void fillCellLines(CTTc tc, List<String> values) {
        CTP proto = tc.getPArray(0);
        while (tc.sizeOfPArray() > 1) {
            tc.removeP(1);
        }
        if (values.isEmpty()) {
            setParagraphText(proto, "");
            return;
        }
        setParagraphText(proto, values.get(0));
        for (int i = 1; i < values.size(); i++) {
            CTP np = tc.addNewP();
            np.set(proto.copy());
            setParagraphText(np, values.get(i));
        }
    }

    private static void setCellText(CTTc tc, String text) {
        while (tc.sizeOfPArray() > 1) {
            tc.removeP(1);
        }
        setParagraphText(tc.getPArray(0), text);
    }

    private static void setParagraphText(CTP p, String text) {
        CTR r;
        if (p.sizeOfRArray() == 0) {
            r = p.addNewR();
        } else {
            r = p.getRArray(0);
        }
        while (p.sizeOfRArray() > 1) {
            p.removeR(1);
        }
        while (r.sizeOfTArray() > 1) {
            r.removeT(1);
        }
        CTText t = r.sizeOfTArray() == 0 ? r.addNewT() : r.getTArray(0);
        t.setStringValue(text);
        t.setSpace(SpaceAttribute.Space.PRESERVE);
    }

    // ------------------------------------------------------------------ segnaposto semplici
    private void replaceEverywhere(XWPFDocument doc, Map<String, String> tokens) {
        replaceEverywhere(doc, literalReplacer(tokens));
    }

    private static UnaryOperator<String> literalReplacer(Map<String, String> tokens) {
        return s -> {
            String r = s;
            for (Map.Entry<String, String> e : tokens.entrySet()) {
                if (r.contains(e.getKey())) {
                    r = r.replace(e.getKey(), e.getValue());
                }
            }
            return r;
        };
    }

    private void replaceEverywhere(XWPFDocument doc, UnaryOperator<String> fn) {
        for (XWPFParagraph p : doc.getParagraphs()) {
            replaceInParagraph(p, fn);
        }
        for (XWPFTable t : doc.getTables()) {
            replaceInTable(t, fn);
        }
        for (XWPFHeader h : doc.getHeaderList()) {
            for (XWPFParagraph p : h.getParagraphs()) {
                replaceInParagraph(p, fn);
            }
        }
        for (XWPFFooter f : doc.getFooterList()) {
            for (XWPFParagraph p : f.getParagraphs()) {
                replaceInParagraph(p, fn);
            }
        }
    }

    private void replaceInTable(XWPFTable table, UnaryOperator<String> fn) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph p : cell.getParagraphs()) {
                    replaceInParagraph(p, fn);
                }
                for (XWPFTable nested : cell.getTables()) {
                    replaceInTable(nested, fn);
                }
            }
        }
    }

    private void replaceInParagraph(XWPFParagraph p, UnaryOperator<String> fn) {
        for (XWPFRun run : p.getRuns()) {
            for (CTText t : run.getCTR().getTList()) {
                String s = t.getStringValue();
                if (s == null || !s.contains("{{")) {
                    continue;
                }
                t.setStringValue(fn.apply(s));
                t.setSpace(SpaceAttribute.Space.PRESERVE);
            }
        }
    }

    // ------------------------------------------------------------------ sezione Power System
    private record PowerRow(String model, String type, int cores, String mounting, String description, String ghz, int cpw, String wordFile,
                            String processorGroup) {
    }

    private PowerRow powerModel(long id) {
        return jdbc.sql("select model, type, cores, mounting, coalesce(nullif(description, ''), 'Model ' || model || ' ' || cores || ' core ' || mounting) as description, "
                + "coalesce(ghz, '') as ghz, cpw, word_file, processor_group from power_model where id = :id").param("id", id).query(PowerRow.class).optional().orElse(null);
    }

    /** toglie i paragrafi vuoti (o con solo salto pagina) in coda all'elenco */
    private static List<IBodyElement> dropTrailingBlanks(List<IBodyElement> elems) {
        int n = elems.size();
        while (n > 0 && isBlankOrBreak(elems.get(n - 1))) {
            n--;
        }
        return new ArrayList<>(elems.subList(0, n));
    }

    private record HwLine(String code, List<String> description, String qty) {
    }

    /**
     * Tabella «Software di sistema» di tabelle-power-software.docx (prototipo {{PWS_CODE}} {{PWS_DESC}} {{PWS_QTY}}):
     * prima riga «Gruppo SW <gruppo> (<n> core)» + «Versione software IBM <sistema operativo>», poi un prodotto IBM i per riga
     * (codice, descrizione, valore scelto nelle Opzioni Software dell'offerta).
     */
    private record MaintAmt(int months, String kind, BigDecimal list, BigDecimal net) {
    }

    /**
     * tabelle-power-manutenzione.docx: la tabella della durata ha una riga «MANUTENZIONE HARDWARE 7x24 – N MESI» e quella dei totali le righe
     * «TOTALE POWER SYSTEM» (listino) e «TOTALE POWER SYSTEM A VOI RISERVATO» (netto). Per ogni periodo selezionato si ripete la riga della durata e
     * si aggiungono le righe dei totali di Power e, con LTO8, di LTO8; con piu' periodi le diciture riportano «– N MESI».
     */
    private void fillPowerMaintenance(XWPFDocument part, OfferHeader h) {
        List<MaintAmt> ls = jdbc.sql("select months, kind, list_price, net_price from offer_power_maint where offer_id = :id order by months, kind desc")
                .param("id", h.id()).query((rs, n) -> new MaintAmt(rs.getInt("months"), rs.getString("kind"), rs.getBigDecimal("list_price"), rs.getBigDecimal("net_price"))).list();
        if (ls.isEmpty()) {
            return;                                                  // offerta senza periodi: restano i segnaposto con i totali storici
        }
        List<Integer> periods = ls.stream().map(MaintAmt::months).distinct().sorted().toList();
        boolean multi = periods.size() > 1;
        XWPFTable tMonths = null, tTot = null;
        int monthsIdx = -1, listIdx = -1, netIdx = -1;
        for (XWPFTable t : part.getTables()) {
            CTRow[] trs = t.getCTTbl().getTrArray();
            for (int i = 0; i < trs.length; i++) {
                String tx = rowText(trs[i]);
                if (tx.contains("{{PW_MESI_MANUTENZIONE}}") && tMonths == null) {
                    tMonths = t;
                    monthsIdx = i;
                }
                if (tx.contains("{{PW_PREZZO_LISTINO}}") && tTot == null) {
                    tTot = t;
                    listIdx = i;
                }
                if (tx.contains("{{PW_PREZZO_NETTO}}") && t == tTot) {
                    netIdx = i;
                }
            }
        }
        if (tMonths != null) {
            CTRow proto = (CTRow) tMonths.getCTTbl().getTrArray(monthsIdx).copy();
            tMonths.removeRow(monthsIdx);
            for (Integer m : periods) {
                CTRow r = tMonths.getCTTbl().addNewTr();
                r.set(proto.copy());
                setCellText(r.getTcArray(0), cellText(r.getTcArray(0)).replace("{{PW_MESI_MANUTENZIONE}}", String.valueOf(m)));
            }
        }
        if (tTot != null && listIdx >= 0 && netIdx >= 0) {
            CTRow protoList = (CTRow) tTot.getCTTbl().getTrArray(listIdx).copy();
            CTRow protoNet = (CTRow) tTot.getCTTbl().getTrArray(netIdx).copy();
            String labelList = cellText(protoList.getTcArray(0)), labelNet = cellText(protoNet.getTcArray(0));
            for (int i = Math.max(listIdx, netIdx); i >= Math.min(listIdx, netIdx); i--) {
                tTot.removeRow(i);
            }
            for (Integer m : periods) {
                String suffix = multi ? " – " + m + " MESI" : "";
                for (MaintAmt a : ls) {
                    if (a.months() != m) {
                        continue;
                    }
                    boolean lto = "LTO8".equals(a.kind());
                    totalRow(tTot, protoList, (lto ? labelList.replace("POWER SYSTEM", "LTO8") : labelList) + suffix, a.list());
                    totalRow(tTot, protoNet, (lto ? labelNet.replace("POWER SYSTEM", "LTO8") : labelNet) + suffix, a.net());
                }
            }
        }
    }

    private void totalRow(XWPFTable t, CTRow proto, String label, BigDecimal value) {
        CTRow r = t.getCTTbl().addNewTr();
        r.set(proto.copy());
        setCellText(r.getTcArray(0), label);
        setCellText(r.getTcArray(r.sizeOfTcArray() - 1), eur(value));
    }

    private static String cellText(CTTc tc) {
        StringBuilder sb = new StringBuilder();
        for (CTP p : tc.getPArray()) {
            sb.append(allText(p));
        }
        return sb.toString();
    }

    /** periodi di manutenzione SW/HW selezionati: «36», «36 e 48», «36, 48 e 60» (senza righe: il periodo storico dell'offerta) */
    private String maintPeriodsText(OfferHeader h) {
        List<Integer> ms = jdbc.sql("select distinct months from offer_power_maint where offer_id = :id order by months").param("id", h.id()).query(Integer.class).list();
        if (ms.isEmpty()) {
            return String.valueOf(h.powerMaintMonths());
        }
        if (ms.size() == 1) {
            return String.valueOf(ms.get(0));
        }
        return ms.subList(0, ms.size() - 1).stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", ")) + " e " + ms.get(ms.size() - 1);
    }

    private static boolean transferOn(OfferHeader h) {
        return h.powerTransfer() != null && !h.powerTransfer().isBlank() && !"NO".equalsIgnoreCase(h.powerTransfer());
    }

    /** descrizioni dei prodotti IBM i impostati «Migrato» nelle Opzioni Software, in elenco discorsivo («A, B e C») */
    private String migratedSoftware(Long offerId) {
        List<String> d = jdbc.sql("select o.description from offer_power_product o join ibmi_product p on p.id = o.product_id "
                + "where o.offer_id = :id and o.value = 'MIGRATO' order by p.sort_order, o.code").param("id", offerId)
                .query((rs, n) -> rs.getString(1)).list();
        if (d.isEmpty()) return "";
        if (d.size() == 1) return d.get(0);
        return String.join(", ", d.subList(0, d.size() - 1)) + " e " + d.get(d.size() - 1);
    }

    private void fillPowerSoftware(XWPFDocument part, PowerRow p, OfferHeader h) {
        List<HwLine> lines = new ArrayList<>();
        lines.add(new HwLine("", List.of("Gruppo SW " + (p.processorGroup() == null ? "" : p.processorGroup()) + " (" + p.cores() + " core)",
                "Versione software IBM " + (h.powerOs() == null ? "" : h.powerOs())), ""));
        List<String[]> products = jdbc.sql("select o.code, o.description, o.value from offer_power_product o join ibmi_product p on p.id = o.product_id "
                + "where o.offer_id = :id order by p.sort_order, o.code").param("id", h.id())
                .query((rs, n) -> new String[]{rs.getString(1), rs.getString(2), rs.getString(3)}).list();
        for (String[] pr : products) {
            lines.add(new HwLine(pr[0], List.of(pr[1]), "MIGRATO".equals(pr[2]) ? "Migrato" : pr[2]));
        }
        fillProtoTable(part, "{{PWS_DESC}}", "tabelle-power-software.docx", lines);
    }

    /** sostituisce la riga prototipo (contenente il segnaposto) con una riga per ogni voce: codice / descrizione (anche su piu' righe) / quantita' */
    private void fillProtoTable(XWPFDocument part, String descToken, String fileName, List<HwLine> lines) {
        XWPFTable table = null;
        int protoIdx = -1;
        for (XWPFTable t : part.getTables()) {
            CTRow[] trs = t.getCTTbl().getTrArray();
            for (int i = 0; i < trs.length; i++) {
                if (rowText(trs[i]).contains(descToken)) {
                    table = t;
                    protoIdx = i;
                }
            }
            if (table != null) {
                break;
            }
        }
        if (table == null) {
            throw new IllegalStateException(fileName + ": manca la riga prototipo con " + descToken);
        }
        CTRow proto = (CTRow) table.getCTTbl().getTrArray(protoIdx).copy();
        table.removeRow(protoIdx);
        for (HwLine l : lines) {
            CTRow r = table.getCTTbl().addNewTr();
            r.set(proto.copy());
            setCellText(r.getTcArray(0), l.code());
            fillCellLines(r.getTcArray(1), l.description());
            setCellText(r.getTcArray(2), l.qty());
        }
    }

    /**
     * Tabella «Hardware di sistema» di tabelle-power-hardware.docx: riga prototipo con {{PWH_CODE}} {{PWH_DESC}} {{PWH_QTY}}.
     * Righe: sistema, RAM, dischi NVMe in mirroring, tape LTO 8 oppure cavo di collegamento, cavi vari, DVD, adattatore 1 Gbps,
     * adattatore 10 Gbps (se scelto), utenti IBM i in subscription.
     */
    private void fillPowerHardware(XWPFDocument part, PowerRow p, OfferHeader h) {
        List<HwLine> lines = new ArrayList<>();
        List<String> sys = new ArrayList<>();
        // la descrizione automatica parte gia' da «Model »: non si ripete il prefisso
        sys.add(p.description().toLowerCase(Locale.ROOT).startsWith("model ") ? p.description() : "Model " + p.description());
        if (!p.ghz().isBlank()) {
            sys.add(p.ghz());
        }
        sys.add("Prestazioni  CPW " + new java.text.DecimalFormat("#,##0", java.text.DecimalFormatSymbols.getInstance(Locale.ITALY)).format(p.cpw()));
        lines.add(new HwLine(p.model() + " " + p.type(), sys, "1"));
        lines.add(new HwLine("", List.of("Memoria RAM GB"), h.powerRamGb() == null ? "" : String.valueOf(h.powerRamGb())));
        lines.add(new HwLine("", List.of("Dischi NVMe da " + (h.powerDisk() == null ? "" : h.powerDisk()) + " in mirroring"), String.valueOf(h.powerDiskCount())));
        if (h.powerLto8()) {
            lines.add(new HwLine("", List.of("Tape LTO 8 e scheda sas"), "1"));
        } else if (h.powerCable() != null && !h.powerCable().isBlank()) {
            lines.add(new HwLine("", List.of("Cavo di collegamento per " + h.powerCable()), "1"));
        }
        lines.add(new HwLine("", List.of("Cavi vari"), "1"));
        lines.add(new HwLine("", List.of("DVD ROM (esterno)"), "1"));
        lines.add(new HwLine("", List.of("Adattatore Ethernet 4 porte 1 Gbps"), p.cores() == 8 ? "2" : "1"));
        if (h.powerEth10()) {
            lines.add(new HwLine("", List.of("Adattatore Ethernet 2 porte 10GB + 4 transceiver 10gbit ottici"), "2"));
        }
        lines.add(new HwLine("", List.of("Utenti licenziati IN SUBSCRIPTION (compreso software maintenance)"),
                h.powerIbmiUnlimited() ? "ILLIMITATI" : (h.powerIbmiUsers() == null ? "" : String.valueOf(h.powerIbmiUsers()))));

        fillProtoTable(part, "{{PWH_DESC}}", "tabelle-power-hardware.docx", lines);
    }

    // ------------------------------------------------------------------ attivita' sistemistiche (attivita.docx)
    private record ActRow(long applicationId, String appName, String description, String profile, BigDecimal hours, BigDecimal rate, String group, BigDecimal days) {
    }

    /** righe del passo «Servizi», raggruppate per applicazione nell'ordine delle licenze */
    private List<ActRow> loadActivityRows(OfferHeader h) {
        List<ActRow> out = new ArrayList<>();
        jdbc.sql("""
                select a.application_id, a.description, a.profile_name, a.hours, a.rate, a.rfq_group, a.days, a.rfq_import
                from offer_activity a
                left join offer_line l on l.offer_id = a.offer_id and l.application_id = a.application_id
                where a.offer_id = :id
                order by (select min(x.sort_order) from offer_activity x where x.offer_id = a.offer_id and x.application_id is not distinct from a.application_id),
                         a.sort_order, a.id""")
                .param("id", h.id()).query((rs, n) -> {
                    long appId = rs.getLong("application_id");
                    return new ActRow(appId, appId == 0 ? (rs.getBoolean("rfq_import") ? "NON TROVATO" : "") : catalog.find(appId).map(x -> "GALILEO".equalsIgnoreCase(x.family()) ? "GALILEO" : x.name()).orElse(""), rs.getString("description"),
                            rs.getString("profile_name"), rs.getBigDecimal("hours"), rs.getBigDecimal("rate"), rs.getString("rfq_group"),
                            rs.getBigDecimal("days") != null ? rs.getBigDecimal("days")
                                    : rs.getBigDecimal("hours").divide(new BigDecimal("8"), 2, java.math.RoundingMode.HALF_UP));
                }).list().forEach(out::add);
        return out;
    }

    private static String hoursText(BigDecimal h) {
        // ore con separatore delle migliaia e fino a 2 decimali (formato italiano)
        java.text.DecimalFormat f = new java.text.DecimalFormat("#,##0.##", java.text.DecimalFormatSymbols.getInstance(Locale.ITALY));
        return f.format(h);
    }

    /** testo di tutte le celle di una riga */
    private static String rowText(CTRow tr) {
        StringBuilder sb = new StringBuilder();
        for (CTTc tc : tr.getTcArray()) {
            for (CTP p : tc.getPArray()) {
                sb.append(allText(p));
            }
        }
        return sb.toString();
    }

    /**
     * Compila la tabella di attivita.docx. Il modello contiene due righe prototipo: la fascia grigia con {{ATT_APP}} (nome
     * dell'applicazione) e la riga attività con {{ATT_DESC}} {{ATT_ORE}} {{ATT_TARIFFA}} {{ATT_VALORE}}; la riga
     * «TOTALE IMPORTO STIMATO» ha {{ATT_TOT_ORE}} e {{ATT_TOT}}, che si valorizzano con `tokens`.
     * Le tariffe sono quelle scontate (sconto % sulle tariffe orarie). Restituisce il totale stimato.
     */
    /** riga di sub-totale dell'applicazione: ore e importo */
    /** la riga di titolo (applicazione o gruppo) resta nella pagina della riga successiva: mai un titolo solo in fondo a una pagina */
    private static void keepWithNext(CTRow row) {
        for (CTTc tc : row.getTcArray()) {
            for (CTP p : tc.getPArray()) {
                CTPPr pr = p.isSetPPr() ? p.getPPr() : p.addNewPPr();
                if (!pr.isSetKeepNext()) {
                    pr.addNewKeepNext();
                }
            }
        }
    }

    private CTRow addSubtotal(XWPFTable table, CTRow proto, String label, BigDecimal hours, BigDecimal total) {
        return addSubtotal(table, proto, label, hours, total, true, true);
    }

    private CTRow addSubtotal(XWPFTable table, CTRow proto, String label, BigDecimal hours, BigDecimal total, boolean showQty, boolean showAmount) {
        CTRow r = table.getCTTbl().addNewTr();
        r.set(proto.copy());
        setCellText(r.getTcArray(0), label);
        setCellText(r.getTcArray(1), showQty ? hoursText(hours) : "");
        int n = r.sizeOfTcArray();                      // attivita.docx (senza Light) ha 3 celle nella riga dei totali, attivita-light.docx 4
        if (n > 2) {
            setCellText(r.getTcArray(2), "");
        }
        if (n > 3) {
            setCellText(r.getTcArray(3), showAmount ? eur(total) : "");
        }
        return r;
    }

    /**
     * Totale dell'applicazione. Con una sola sezione (un solo gruppo) c'e' sempre: ore/gg e importo, salvo diversa scelta del menu;
     * con piu' sezioni dipende dal menu «Aggiungi totale attività per applicazione»: NO | HOURS | AMOUNT | BOTH.
     */
    private void appTotalRow(XWPFTable table, CTRow sub, String mode, int groups, String appName, BigDecimal qty, BigDecimal total, boolean hoursOnly) {
        String m = mode == null ? "NO" : mode;
        if ("NO".equals(m)) {
            if (groups > 1) {
                return;
            }
            m = "BOTH";                                                  // una sola sezione: il totale va sempre messo
        }
        if (hoursOnly) {
            m = "HOURS";                                                 // attivita.docx (senza Light): nessun importo nelle righe
        }
        addSubtotal(table, sub, "Totale " + appName, qty, total, !"AMOUNT".equals(m), !"HOURS".equals(m));
    }

    private BigDecimal fillActivityTables(XWPFDocument part, List<ActRow> acts, BigDecimal discountPct, boolean detail, boolean daysMode, String appTotalsMode, Map<String, String> tokens) {
        BigDecimal factor = BigDecimal.ONE.subtract((discountPct == null ? BigDecimal.ZERO : discountPct).divide(HUNDRED));
        XWPFTable table = null;
        int bandIdx = -1;
        int lineIdx = -1;
        int subIdx = -1;
        int appIdx = -1;                 // riga {{APP_TITLE}}: nome dell'applicazione (opzionale)
        for (XWPFTable t : part.getTables()) {
            CTRow[] trs = t.getCTTbl().getTrArray();
            for (int i = 0; i < trs.length; i++) {
                String tx = rowText(trs[i]);
                if (tx.contains("{{ATT_APP}}")) {
                    bandIdx = i;
                }
                if (tx.contains("{{APP_TITLE}}")) {
                    appIdx = i;
                }
                if (tx.contains("{{ATT_DESC}}")) {
                    lineIdx = i;
                }
                if (tx.contains("{{ATT_SUB_ORE}}")) {
                    subIdx = i;
                }
            }
            if (bandIdx >= 0 && lineIdx >= 0 && subIdx >= 0) {
                table = t;
                break;
            }
            bandIdx = -1;
            lineIdx = -1;
            subIdx = -1;
            appIdx = -1;
        }
        // attivita.docx (Light spento): ultima colonna con la figura professionale e nessun prezzo in riga
        boolean figLayout = table != null && rowText(table.getCTTbl().getTrArray(lineIdx)).contains("{{ATT_FIG_PROF}}");
        if (table == null) {
            throw new IllegalStateException("attivita.docx: mancano le righe prototipo con {{ATT_APP}}, {{ATT_DESC}} e {{ATT_SUB_ORE}}");
        }
        // intestazione: «STIMA» a capo e {{ATT_STIMA}} = «GG» (servizi espressi in giorni) oppure «ORE»
        tokens.put("{{ATT_STIMA}}", daysMode ? "GG" : "ORE");
        tokens.put("{{ATT_INT_TARIFFA}}", detail ? "TARIFFA" : "");      // la parola TARIFFA compare solo con «Dettaglio importi attività»
        // copie staccate dei prototipi, poi si tolgono dal modello (indici dal basso)
        CTRow band = (CTRow) table.getCTTbl().getTrArray(bandIdx).copy();
        CTRow appRow = appIdx >= 0 ? (CTRow) table.getCTTbl().getTrArray(appIdx).copy() : band;      // senza {{APP_TITLE}}: fascia unica come prima
        CTRow line = (CTRow) table.getCTTbl().getTrArray(lineIdx).copy();
        CTRow sub = (CTRow) table.getCTTbl().getTrArray(subIdx).copy();
        int[] idx = appIdx >= 0 ? new int[]{bandIdx, lineIdx, subIdx, appIdx} : new int[]{bandIdx, lineIdx, subIdx};
        java.util.Arrays.sort(idx);
        for (int i = idx.length - 1; i >= 0; i--) {
            table.removeRow(idx[i]);
        }
        // gruppi (titoli) di ogni applicazione: i totali dei gruppi ci sono solo se sono piu' di uno
        Map<Long, java.util.Set<String>> groupsOf = new LinkedHashMap<>();
        for (ActRow a : acts) {
            java.util.Set<String> gs = groupsOf.computeIfAbsent(a.applicationId(), k -> new java.util.LinkedHashSet<>());
            if (a.group() != null && !a.group().isBlank()) {
                gs.add(a.group());
            }
        }
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO;                 // totale a tariffe di listino (prima dello sconto sui servizi)
        BigDecimal hours = BigDecimal.ZERO;
        BigDecimal subTotal = BigDecimal.ZERO;
        BigDecimal subHours = BigDecimal.ZERO;
        Long current = null;
        String currentName = "";
        String curGroup = null;                          // titolo del gruppo (solo righe importate da RFQ)
        BigDecimal grpTotal = BigDecimal.ZERO;
        BigDecimal grpHours = BigDecimal.ZERO;
        for (ActRow a : acts) {
            String grp = a.group() == null || a.group().isBlank() ? null : a.group();
            boolean newApp = current == null || current.longValue() != a.applicationId();
            if (curGroup != null && (newApp || !curGroup.equals(grp))) {
                if (groupsOf.get(current).size() > 1 || currentName.isBlank()) {   // con un solo gruppo basta il totale dell'applicazione (senza applicazione: sempre il totale del gruppo)
                    addSubtotal(table, sub, "Totale", grpHours, grpTotal, true, !figLayout);   // totale del titolo
                }
                curGroup = null;
            }
            if (newApp) {
                if (current != null) {
                    if (!currentName.isBlank()) {
                        appTotalRow(table, sub, appTotalsMode, groupsOf.get(current).size(), currentName, subHours, subTotal, figLayout);
                    }
                }
                current = a.applicationId();
                currentName = a.appName();
                subTotal = BigDecimal.ZERO;
                subHours = BigDecimal.ZERO;
                if (!a.appName().isBlank()) {                             // servizi manuali senza applicazione: nessun titolo applicazione
                    CTRow b = table.getCTTbl().addNewTr();
                    b.set(appRow.copy());                                 // riga {{APP_TITLE}}: nome dell'applicazione
                    setCellText(b.getTcArray(0), a.appName());
                    keepWithNext(b);
                }
            }
            if (grp != null && !grp.equals(curGroup)) {
                curGroup = grp;
                grpTotal = BigDecimal.ZERO;
                grpHours = BigDecimal.ZERO;
                CTRow gb = table.getCTTbl().addNewTr();
                gb.set(band.copy());
                setCellText(gb.getTcArray(0), grp);                       // livello aggiuntivo: titolo sotto l'applicazione
                keepWithNext(gb);
            }
            BigDecimal netRate = a.rate().multiply(factor).setScale(2, java.math.RoundingMode.HALF_UP);
            BigDecimal value = a.hours().multiply(netRate).setScale(2, java.math.RoundingMode.HALF_UP);
            // il dettaglio delle attivita' c'e' sempre; i valori (gg/ore, tariffa, totale riga) solo con «Dettaglio importi attività»
            CTRow r = table.getCTTbl().addNewTr();
            r.set(line.copy());
            if (figLayout) {
                fillCellLines(r.getTcArray(0), List.of(a.description()));             // la figura professionale ha la sua colonna, non va nella descrizione
                setCellText(r.getTcArray(1), hoursText(daysMode ? a.days() : a.hours()));
                setCellText(r.getTcArray(2), a.profile() == null ? "" : a.profile());
            } else {
                boolean withProfile = detail && a.profile() != null && !a.profile().isBlank();
                fillCellLines(r.getTcArray(0), withProfile ? List.of(a.description(), a.profile()) : List.of(a.description()));
                setCellText(r.getTcArray(1), detail ? hoursText(daysMode ? a.days() : a.hours()) : "");
                setCellText(r.getTcArray(2), detail ? eur(netRate) : "");
                setCellText(r.getTcArray(3), detail ? eur(value) : "");
            }
            gross = gross.add(a.hours().multiply(a.rate()).setScale(2, java.math.RoundingMode.HALF_UP));
            grpTotal = grpTotal.add(value);
            grpHours = grpHours.add(daysMode ? a.days() : a.hours());
            subTotal = subTotal.add(value);
            subHours = subHours.add(daysMode ? a.days() : a.hours());
            total = total.add(value);
            hours = hours.add(daysMode ? a.days() : a.hours());
        }
        if (curGroup != null && (groupsOf.get(current).size() > 1 || currentName.isBlank())) {
            addSubtotal(table, sub, "Totale", grpHours, grpTotal, true, !figLayout);
        }
        if (current != null) {
            if (!currentName.isBlank()) {
                appTotalRow(table, sub, appTotalsMode, groupsOf.get(current).size(), currentName, subHours, subTotal, figLayout);
            }
        }
        tokens.put("{{ATT_TOT_ORE}}", hoursText(hours) + (daysMode ? " gg" : " ore"));
        tokens.put("{{ATT_TOT}}", eur(total));
        fillActivityTotals(part, hoursText(hours) + (daysMode ? " gg" : " ore"), gross, total, discountPct != null && discountPct.signum() > 0);
        return total;
    }

    /**
     * Tabella dei totali delle attivita'. Con sconto sui servizi: riga «STIMA TOTALE ATTIVITÀ DI PROGETTO» (ore e totale lordo) e
     * riga «TOTALE NETTO». Senza sconto: solo la riga con il totale, che diventa «TOTALE».
     */
    private static void fillActivityTotals(XWPFDocument part, String hoursText, BigDecimal gross, BigDecimal net, boolean discounted) {
        for (XWPFTable t : part.getTables()) {
            int estIdx = -1;
            int netIdx = -1;
            CTRow[] trs = t.getCTTbl().getTrArray();
            for (int i = 0; i < trs.length; i++) {
                String tx = rowText(trs[i]).toUpperCase(Locale.ROOT);
                if (tx.startsWith("STIMA TOTALE") && tx.contains("{{ATT_TOT")) {
                    estIdx = i;
                } else if (tx.startsWith("TOTALE NETTO") && tx.contains("{{ATT_TOT")) {
                    netIdx = i;
                }
            }
            if (netIdx < 0) {
                continue;
            }
            CTRow netRow = trs[netIdx];
            int last = netRow.sizeOfTcArray() - 1;
            setCellText(netRow.getTcArray(last), eur(net));
            if (discounted && estIdx >= 0) {
                CTRow est = trs[estIdx];
                setCellText(est.getTcArray(1), hoursText);
                setCellText(est.getTcArray(est.sizeOfTcArray() - 1), eur(gross));
            } else {
                if (!discounted) {
                    setCellText(netRow.getTcArray(0), "TOTALE");
                }
                if (estIdx >= 0) {
                    t.removeRow(estIdx);
                }
            }
            return;
        }
    }

    // ------------------------------------------------------------------ riepilogo finale e servizi accessori
    private static final Pattern EURO_AMOUNT = Pattern.compile("(\\d{1,3}(?:\\.\\d{3})+|\\d+),(\\d{2})");

    /** importo "TOTALE IMPORTO STIMATO" della tabella delle attivita' sistemistiche (attivita.docx), null se assente */
    private static BigDecimal findActivitiesTotal(XWPFDocument part) {
        for (XWPFTable t : part.getTables()) {
            for (XWPFTableRow r : t.getRows()) {
                List<XWPFTableCell> cs = r.getTableCells();
                if (cs.isEmpty() || !cs.get(0).getText().trim().toUpperCase(Locale.ROOT).startsWith("TOTALE IMPORTO STIMATO")) {
                    continue;
                }
                for (int i = cs.size() - 1; i >= 0; i--) {
                    Matcher m = EURO_AMOUNT.matcher(cs.get(i).getText());
                    if (m.find()) {
                        return new BigDecimal(m.group(1).replace(".", "") + "." + m.group(2));
                    }
                }
            }
        }
        return null;
    }

    /** elimina dalle tabelle le righe che contengono il segnaposto indicato */
    private static void removeRowsContaining(XWPFDocument doc, String token) {
        for (XWPFTable t : doc.getTables()) {
            for (int i = t.getRows().size() - 1; i >= 0; i--) {
                StringBuilder sb = new StringBuilder();
                for (XWPFTableCell c : t.getRow(i).getTableCells()) {
                    sb.append(c.getText());
                }
                if (sb.toString().contains(token)) {
                    t.removeRow(i);
                }
            }
        }
    }

    /**
     * Riepilogo dei valori: licenze (netto), attivita' di progetto (dal totale di attivita.docx), totale stima investimento,
     * canone di manutenzione (netto) con l'eventuale dicitura "IN AGGIUNTA ALL'ATTUALE".
     * POWER SYSTEM e PACCHETTO GIORNATE DI FOLLOW UP non sono gestiti dall'applicazione: le righe vengono tolte.
     */
    private void fillFinalSummary(XWPFDocument part, BigDecimal netLic, BigDecimal activities, BigDecimal netMan,
                                  boolean additional, Map<String, String> baseTokens) {
        removeRowsContaining(part, "{{RF_POWER}}");
        removeRowsContaining(part, "{{RF_FOLLOWUP}}");
        if (activities == null) {
            removeRowsContaining(part, "{{RF_ATTIVITA}}");
        }
        BigDecimal total = netLic.add(activities == null ? BigDecimal.ZERO : activities);
        Map<String, String> t = new LinkedHashMap<>(baseTokens);
        t.put("{{RF_LICENZE}}", eur(netLic));
        t.put("{{RF_ATTIVITA}}", activities == null ? "" : eur(activities));
        t.put("{{RF_TOTALE}}", eur(total));
        t.put("{{RF_MANUT}}", eur(netMan));
        t.put("{{RF_MANUT_NOTE}}", additional ? " IN AGGIUNTA ALL'ATTUALE" : "");
        replaceEverywhere(part, t);
    }

    /** tabella di riepilogo dei servizi accessori scelti: una riga per servizio (riga prototipo {{SA_*}} replicata) */
    private void fillExtrasSummaryTable(XWPFTable table, ExtraModel.Totals totals) {
        CTRow proto = table.getRow(1).getCtRow();
        for (ExtraModel.Line l : totals.lines()) {
            CTRow r = cloneRow(table, proto);
            setCellText(r.getTcArray(0), l.name());
            setCellText(r.getTcArray(1), l.includedText() != null ? l.includedText()
                    : (l.onetime().signum() > 0 ? eur(l.onetime()) : "-"));
            setCellText(r.getTcArray(2), l.includedText() != null ? "-"
                    : (l.adHoc() && l.annual().signum() == 0 ? l.adHocText()
                    : (l.annual().signum() > 0 ? eur(l.annual()) + (l.adHoc() ? " + " + l.adHocText() : "") : "-")));
        }
        table.removeRow(1);   // riga prototipo
    }

    private static final Pattern SECTION_MARK = Pattern.compile("\\[\\[SA:(\\w+)\\]\\]");
    private static final Pattern EXTRA_TOKEN = Pattern.compile("\\{\\{([XPQ]):([A-Z_]+):([^:}]+)(?::([A-Z]+))?(?::(S))?\\}\\}");

    /**
     * servizi-accessori.docx: compila i segnaposto (X sulle righe scelte, importi, quantita') e tiene solo le sezioni
     * (marcatori [[SA:CODICE]]) dei servizi selezionati; FIRMAE inizia su una nuova pagina se segue altre sezioni.
     */
    private List<IBodyElement> prepareExtraServices(XWPFDocument part, ExtraModel.Selection sel, ExtraModel.Totals totals,
                                                    Map<String, String> baseTokens) {
        Map<String, ExtraModel.Option> byKey = new HashMap<>();
        Map<String, ExtraModel.Group> groups = new HashMap<>();
        for (ExtraModel.Service s : extraRepo.loadAll()) {
            for (ExtraModel.Group g : s.groups()) {
                groups.put(g.code(), g);
                for (ExtraModel.Option o : g.options()) {
                    byKey.put(g.code() + "|" + o.rowKey(), o);
                }
            }
        }
        Map<Long, Integer> chosen = new HashMap<>();
        for (ExtraModel.OptionQty oq : sel.options()) {
            if (oq.optionId() != null) {
                chosen.put(oq.optionId(), oq.qty() == null ? 1 : oq.qty());
            }
        }
        UnaryOperator<String> literal = literalReplacer(baseTokens);
        replaceEverywhere(part, (UnaryOperator<String>) s -> {
            Matcher m = EXTRA_TOKEN.matcher(literal.apply(s));
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(resolveExtraToken(m, byKey, groups, chosen)));
            }
            m.appendTail(sb);
            return sb.toString();
        });

        Set<String> selected = new HashSet<>();
        for (ExtraModel.Line l : totals.lines()) {
            selected.add(l.serviceCode());
        }
        List<IBodyElement> out = new ArrayList<>();
        String current = null;
        boolean breakNext = false;
        int emitted = 0;
        for (IBodyElement e : part.getBodyElements()) {
            if (e instanceof XWPFParagraph p) {
                Matcher m = SECTION_MARK.matcher(allText(p.getCTP()));
                if (m.matches()) {
                    current = m.group(1);
                    breakNext = false;
                    if (selected.contains(current)) {
                        breakNext = "FIRMAE".equals(current) && emitted > 0;
                        emitted++;
                    }
                    continue;
                }
            }
            if (current != null && !selected.contains(current)) {
                continue;
            }
            if (breakNext && e instanceof XWPFParagraph bp) {
                bp.setPageBreak(true);
                breakNext = false;
            }
            out.add(e);
        }
        return out;
    }

    private String resolveExtraToken(Matcher m, Map<String, ExtraModel.Option> byKey, Map<String, ExtraModel.Group> groups,
                                     Map<Long, Integer> chosen) {
        String type = m.group(1);
        String group = m.group(2);
        String row = m.group(3);
        String field = m.group(4);
        boolean suffix = m.group(5) != null;
        ExtraModel.Option o = byKey.get(group + "|" + row);
        if ("P".equals(type) && "TOTAL".equals(field)) {
            BigDecimal sum = BigDecimal.ZERO;
            ExtraModel.Group g = groups.get(group);
            if (g != null) {
                for (ExtraModel.Option x : g.options()) {
                    sum = sum.add(x.onetime() == null ? BigDecimal.ZERO : x.onetime()).add(x.annual() == null ? BigDecimal.ZERO : x.annual());
                }
            }
            return eur(sum);
        }
        if (o == null) {
            return "";
        }
        switch (type) {
            case "X":
                return chosen.containsKey(o.id()) ? "X" : "";
            case "Q":
                return chosen.containsKey(o.id()) && o.qtyEnabled() ? String.valueOf(chosen.get(o.id())) : "____________";
            default:
                if ("LABEL".equals(field)) {
                    return o.label();
                }
                if ("ONETIME".equals(field)) {
                    return money(o.onetime(), suffix);
                }
                if (o.adHoc()) {
                    return o.adHocText() == null ? "" : o.adHocText();
                }
                return money("ANNUAL".equals(field) ? o.annual() : o.extraUnit(), suffix);
        }
    }

    /** "€ 1.320,00" oppure, con suffix, "1.320 €" / "0,80 €" (come nelle tabelle del modello); vuoto se assente o zero */
    private static String money(BigDecimal v, boolean suffix) {
        if (v == null || v.signum() == 0) {
            return "";
        }
        if (!suffix) {
            return eur(v);
        }
        NumberFormat nf = NumberFormat.getInstance(IT);
        boolean whole = v.stripTrailingZeros().scale() <= 0;
        nf.setMinimumFractionDigits(whole ? 0 : 2);
        nf.setMaximumFractionDigits(whole ? 0 : 2);
        return nf.format(v) + " €";
    }

    // ------------------------------------------------------------------ utilita'
    static String eur(BigDecimal v) {
        NumberFormat nf = NumberFormat.getInstance(IT);
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return "€ " + nf.format(v.setScale(2, java.math.RoundingMode.HALF_UP));
    }

    /**
     * Nome del file Word: RAGIONESOCIALE (senza spazi, punti e altri caratteri non alfanumerici, maiuscolo) + "-" + data offerta AAAAMMGG + "-" + titolo
     * dell'offerta in maiuscolo. Esempio: EFORMASRL-20261104-PROGETTO DQ.docx
     */
    public String fileName(OfferHeader h) {
        String company = customers.findById(h.customerId()).map(Customer::companyName).orElse("");
        String compact = noAccents(company).replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ROOT);
        String date = h.offerDate() == null ? "" : h.offerDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        String title = noAccents(h.projectTitle() == null ? "" : h.projectTitle()).replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ").replaceAll("\\s+", " ").trim().toUpperCase(Locale.ROOT);
        if (title.length() > 120) {
            title = title.substring(0, 120).trim();
        }
        StringBuilder sb = new StringBuilder(compact.isEmpty() ? "OFFERTA" : compact);
        if (!date.isEmpty()) {
            sb.append('-').append(date);
        }
        if (!title.isEmpty()) {
            sb.append('-').append(title);
        }
        return sb + ".docx";
    }

    private static String noAccents(String s) {
        return java.text.Normalizer.normalize(s == null ? "" : s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
    }

    /** ragione sociale senza forma giuridica: "EFORMA SRL" -> "EFORMA" */
    static String shortName(String company) {
        String s = company == null ? "" : company.trim();
        String cleaned = s.replaceAll("(?i)[\\s,]+(s\\.?\\s?r\\.?\\s?l\\.?\\s?s?\\.?|s\\.?\\s?p\\.?\\s?a\\.?|s\\.?\\s?n\\.?\\s?c\\.?|s\\.?\\s?a\\.?\\s?s\\.?|s\\.?\\s?c\\.?\\s?a\\.?\\s?r\\.?\\s?l\\.?)$", "");
        return cleaned.isBlank() ? s : cleaned;
    }
}
