# CPQIA – Configuratore di offerte commerciali

Applicazione web (interfaccia Google Material) per creare offerte commerciali in formato Word (`.docx`) configurando
applicazioni e moduli del listino dipartimentali.

**Stack:** Java 25 · Spring Boot 3.5 (WAR) · Thymeleaf + CSS/JS Material 3 senza dipendenze esterne · PostgreSQL + Flyway ·
Apache POI (Word) · Tomcat 10.1+ multi-contesto · login Google e Apple (OIDC).

## Menu e Impostazioni

Il menu laterale è alleggerito (icone neutre, voci più compatte) e contiene Offerte, Account, Cestino e un'unica voce **Impostazioni** (icona ingranaggio). Le ex voci di *Amministrazione* sono ora **schede in alto** nella pagina Impostazioni: Applicazioni e moduli, Tabelle tariffe, Modelli Power System, Prodotti IBM i (permesso «gestione catalogo»); Utenti e permessi, Sedi Sanmarco, Comuni, Tipo azienda e indirizzo, Integrazioni (solo administrator). Le schede sono mostrate in ciascuna pagina (frammento `settingsTabs` in `fragments/layout.html`).

## Agenti e Sync Agenti

Tabella `agent` (codice, descrizione; migrazione `V49`) e scheda **Agenti** in *Impostazioni* (solo lettura, solo administrator). La tabella è alimentata dalla **Sync Agenti** (*Impostazioni > Integrazioni*, scheda accanto a **Sync Clienti**, che è l'ex «Agente di sincronizzazione clienti»): legge dal server IBM i il file `SMTAB01L` (libreria predefinita `INF90DAT`) con `XTIPTB = '01AG'`, scarta i record con `**` alle posizioni 9 e 10 di `XDATTB`; si importano solo i codici (`XCODTB`) interamente numerici che iniziano per 3 (gli altri contano tra gli scartati); `XCODTB` = codice, 20 caratteri di `XDATTB` a partire dalla posizione 9 (posizioni 9–28) = descrizione. La lettura è completa a ogni esecuzione (nuovi / aggiornati / invariati / scartati nello storico); pianificazione giornaliera o a intervallo come per i clienti, esecuzione manuale con *Esegui ora*; usa la stessa connessione IBM i (`AgentSyncService`, `agent_sync_config`, `agent_sync_run`). Gli agenti non più presenti in IBM i non vengono cancellati. Nella griglia Agenti, selezionate una o più righe compare **Elimina** (solo administrator): conferma con risposta predefinita *No*, poi eliminazione definitiva (`POST /api/grid/agents/delete-many`, `GridSpec.bulkDelete`); un agente eliminato ma ancora presente in IBM i ricompare alla sincronizzazione successiva.

## Codice agente degli account e account Cliente

La Sync Clienti importa anche **CDAGCA** (codice agente) di `CGANA01J` in `account.agent_code` (migrazione `V50`; la stessa migrazione azzera la data dell'ultima importazione, così la prossima esecuzione rilegge tutti i clienti e valorizza il codice agente degli account già presenti). Nella scheda dell'account (prospect e cliente) compaiono **Codice agente** e **Agente** (descrizione decodificata dalla tabella Agenti), e la griglia Account ha le colonne *Cod. agente* e *Agente*. Per gli account di tipo **Cliente** l'anagrafica è in sola lettura (scheda e modifica in cella, anche per l'administrator, `CustomerController.save` e hook della griglia): si modificano solo le **tariffe dedicate** e la **voce di trasferta**.

## Data ultima importazione (Sync Clienti e Sync Agenti)

Nelle schede *Sync Clienti* e *Sync Agenti* (Integrazioni) il campo **Data ultima importazione** è sempre visibile. Nella Sync Clienti è **modificabile** e si salva con «Salva pianificazione»: la sincronizzazione importa i clienti inseriti o modificati da quella data; **svuotandola** la prossima esecuzione rilegge tutti i clienti (importazione totale). Nella Sync Agenti (migrazione `V51`) la data è solo informativa, perché la lettura degli agenti è sempre completa.

## Agente dell'utente e visibilità degli account

Nell'anagrafica utenti (scheda e griglia, migrazione `V52`) il campo **Codice agente** si sceglie dalla tabella Agenti; **Agente** (descrizione) è calcolato. Un utente con codice agente vede soltanto i **prospect creati da lui** e i **clienti con lo stesso codice agente**: vale per l'elenco e la griglia Account, la ricerca account del configuratore, l'apertura/modifica della scheda e il salvataggio di un'offerta (`AppUser.agentRestricted`, `CustomerRepository.visibleTo/search`, scope della griglia). Gli utenti senza agente vedono tutto, come prima. Le offerte restano soggette ai permessi esistenti («Vede tutte»).

## Usa RFQ e Importa da RFQ

Nella pagina *Elementi*, prima di Power System, il flag **Usa RFQ** (`offer.opt_use_rfq`, migrazione `V48`). Se selezionato la pagina **Servizi** segue subito *Elementi* (il percorso di avanzamento si riordina) e le tabelle ad inserimento manuale delle attività sono sostituite da due funzioni:

* **Importa da RFQ**: chiede l'URL di un documento Google Sheets e legge il **primo foglio** con l'account Google dell'utente (OAuth, sola lettura, scope `spreadsheets.readonly`; il ritorno passa dallo stesso `/google-sheets/callback` già registrato per l'esportazione, quindi non servono nuovi redirect URI; serve però abilitare *Google Sheets API* e lo scope nella schermata di consenso). Se l'utente non ha accesso al foglio, o l'indirizzo è errato, compare il messaggio con la possibilità di accedere con un altro account Google. Struttura attesa: colonna **B** = applicazione (vale fino al cambio), **C** = titolo del gruppo sulle righe con **I = «T»** (e descrizione dell'attività sulle righe di dettaglio), **D** = ore → campo *Ore*, **E** = giorni → campo *gg* (le ore sono i giorni × 8; il totale giorni del titolo in E decide quali gruppi importare), **F** = figura professionale (abbinata ai *Profili professionali* delle Tabelle Tariffe, per nome senza maiuscole/accenti), **G** scartata. Si prendono solo i gruppi con ore (E del titolo) > 0; le righe di dettaglio con ore > 0 sono le attività; le righe «T» sono solo titoli (la figura professionale in F si legge soltanto dalle righe di dettaglio). Le tariffe sono quelle dell'**account** (se presenti, altrimenti di listino). «APP SERVICE» si cerca come **WAT** (`RfqService.ALIASES`). Un nome del tipo «App ICM» si cerca come **ICM** (si toglie il prefisso «App»). Nei Servizi e nel Word il titolo dell'applicazione Galileo è **GALILEO** (non la tipologia). Nel Word le attività seguono l'**ordine di inserimento** delle righe in offerta (le applicazioni nell'ordine in cui compare la loro prima riga, poi le righe nell'ordine salvato). L'applicazione **JGALILEO** (o GALILEO) del foglio si cerca per **famiglia GALILEO** nell'anagrafica applicazioni: se ce n'è una sola è quella, altrimenti vale la tipologia Galileo scelta in *Elementi*. Se nella colonna B non c'è nulla (nessuna applicazione indicata sopra il gruppo) le attività vanno nell'applicazione **ALTRO**, che deve esistere nell'anagrafica applicazioni. **Controllo dei totali:** il totale servizi calcolato (ore × tariffe dell'account, a listino) è confrontato con la colonna **H** della riga «TOTALE GENERALE PROGETTO»; se differiscono (oltre 5 centesimi) compare un popup e nella pagina Servizi resta un avviso (il totale del foglio è salvato in `offer.rfq_sheet_total`, migrazione `V57`, e il confronto si rifà anche alla riapertura). La lettura si ferma alla riga con la descrizione **TOTALE GENERALE DI PROGETTO** (anche scritta «TOTALE GENERALE PROGETTO», in una qualunque colonna A:I). Se un'applicazione non si trova in anagrafica, le sue attività vanno in un gruppo **NON TROVATO** in fondo (titolo «NOME DEL FOGLIO – titolo»; `offer_activity.application_id` nullo, migrazione `V54`) e l'importazione lo segnala; se non si trova una figura professionale, o la tipologia Galileo è ambigua, l'importazione è annullata con l'elenco dei nomi non riconosciuti (`RfqService`).
* **Elimina righe importate**: disabilitata finché non ci sono righe importate; le rimuove e riabilita *Importa da RFQ*. Dopo l'importazione *Importa da RFQ* si disabilita.

Le righe importate portano due campi di servizio in `offer_activity`: `rfq_import` e `rfq_group` (titolo). Nella pagina Servizi e nel Word (`attivita.docx`) c'è un livello in più: **applicazione > titolo > attività**, con subtotale per titolo e per applicazione. Attivando/disattivando *Usa RFQ* le attività dell'altra modalità vengono rimosse (con conferma). Le applicazioni delle righe RFQ possono non essere (ancora) tra i Dipartimentali.

## Servizi espressi in gg (Opzioni del documento)

Nel *Riepilogo finale*, box *Opzioni del documento*, l'opzione **Servizi espressi in gg** (`offer.opt_services_days`, migrazione `V55`, predefinita **attiva**). Nel documento (`attivita.docx`) il segnaposto **`{{ATT_STIMA}}`** della seconda colonna dell'intestazione («DESCRIZIONE ATTIVITA’ | …») sta sotto «STIMA» (a capo) e diventa **GG** se l'opzione è attiva, **ORE** altrimenti (intestazione «STIMA / GG» o «STIMA / ORE»); le quantità delle righe, dei totali per titolo/applicazione e del «TOTALE IMPORTO STIMATO» sono i **giorni** (campo gg; per le righe senza giorni, ore ÷ 8) o le **ore** del campo ore. Gli importi restano calcolati sulle ore.
**Dettaglio importi attività** attivo: l'intestazione della terza colonna riporta «TARIFFA» (segnaposto `{{ATT_INT_TARIFFA}}`) e ogni attività è stampata con descrizione, figura professionale, gg/ore, tariffa e totale riga. Spento: la terza cella dell'intestazione è vuota e le attività sono stampate comunque (descrizione) ma **senza valori**. Il nome dell'applicazione usa la riga con **`{{APP_TITLE}}`** di `attivita.docx` (se manca, la fascia `{{ATT_APP}}` come prima); la fascia `{{ATT_APP}}` è usata per il titolo del gruppo di attività (import RFQ). Un gruppo può continuare sulla pagina successiva; si va a pagina nuova solo se in fondo alla pagina resterebbe il **solo titolo** (applicazione o gruppo) senza righe sotto (titoli con «tieni con la successiva»). Totali: ogni applicazione chiude con «**Totale <applicazione>**»; i «Totale» dei singoli gruppi (titoli) compaiono solo se l'applicazione ha più di un gruppo (con un solo gruppo, o senza titoli, c'è solo il totale dell'applicazione). In entrambi i casi compaiono i totali (gg/ore e importo).

## Moduli dell'applicazione: azioni e selezione

Nella tabella *Moduli*: matita e menu a tre puntini (**Elimina**, con conferma) a sinistra; caselle di selezione con «seleziona tutti» e pulsante **Elimina selezionati** (conferma, risposta predefinita *No*; i moduli vanno nel Cestino, `POST …/modules/delete-many`).

## Vincoli dei moduli

Nella scheda del modulo (*Vincoli verso altri moduli*) ogni vincolo si può **modificare** (icona matita: tipo, codici dei moduli, gravità, messaggio) oltre che eliminare; si salva con `POST …/rules/{id}/update`.

## Prezzo base delle applicazioni

La scheda di modifica dell'applicazione elenca, in sola lettura, il prezzo base di ogni gruppo di prezzo e modalità (la griglia Applicazioni non ha la colonna prezzo). I prezzi si impostano nella pagina dell'applicazione (sezione «Utenti, gruppi di prezzo e fasce»).

## Installazione su Tomcat e procedura di setup

Vedi `deploy/INSTALL.md` (requisiti: Java 25, Tomcat 10.1, PostgreSQL 12+). Se il contesto non ha ancora `conf/cpqia/<contesto>.properties` con il database, al deploy del WAR **parte la procedura di setup** (`it.cpqia.setup.SetupServlet`, servlet puro senza Spring, pagina `resources/setup/setup.html`) al posto dell'applicazione: protetta da un codice generato a ogni avvio (file `conf/cpqia/<contesto>.setup-token` e log di Tomcat), raccoglie i parametri di PostgreSQL (host, porta, database, utente, password, SSL, schema, timeout, pool), con verifica della connessione e creazione facoltativa di utente e database tramite un amministratore PostgreSQL, più email del primo amministratore, credenziali Google, chiave di cifratura, cartella modelli e impostazioni libere; scrive il file (permessi 600, backup `.bak`) e ricarica il contesto via JMX (ripiego: data del WAR). Alla ripartenza Flyway crea lo schema.

## Aggiungi totale attività per applicazione

Menu del *Riepilogo finale* (`offer.app_totals_mode`, migrazione `V58`; predefinito **No**): *No*, *Solo totale ore* (gg o ore secondo «Servizi espressi in gg»), *Solo totale importo*, *Totale ore e importo*. Nel Word la riga «Totale <applicazione>» compare secondo la scelta; **se l'applicazione ha una sola sezione (un solo gruppo) il totale c'è sempre** (con «No» mostra ore e importo). Con più sezioni compaiono anche i «Totale» di ogni sezione.

## Aggiornamento del database in produzione

Il WAR contiene tutte le migrazioni Flyway (V1 … V58 e successive); all'avvio `DatabaseUpgradeConfig` applica in ordine quelle mancanti, loggando «aggiornamento da Vx a Vy». Blocca l'avvio se il database è più nuovo del WAR o se una migrazione già applicata è stata alterata. `MigrationsTest` verifica che le migrazioni siano consecutive e senza duplicati. Procedura completa (backup, aggiornamento, ritorno indietro) in `deploy/INSTALL.md`.

## Galileo (listino JGalileo 5.0)

**Dati.** Dal file `listino_jgalileo` (xlsm; foglio RIEPILOGO escluso) lo script `tools/galileo/extract.py` produce `tools/galileo/profiles.json`; `tools/galileo/gen_seed.py` ne ricava la migrazione `V60__seed_galileo.sql` (schema in `V59`). Mercati (applicazioni della famiglia GALILEO): COMMESSA E MACHINERY ← *PRODUZIONE SU COMMESSA*; PRODUZIONE IN SERIE ← *PRODUZ. DI SERIE* e *MIGRAZIONE AZ. PRODUZIONE*; ALIMENTARE E GDO e VINICOLO ← *ALIMENTARE VINICOLO*; MODA; COMMERCIALE (nuova applicazione) ← *COMMERCIALE* e *MIGRAZIONE AZ. COMMERCIALE*; AMMINISTRAZIONE ← *HOLDING AMMINISTRATIVA* e *IMMOBILIARE*. Per ogni mercato ci sono i **profili** (scenari di calcolo): *Nuova configurazione*, *Consociata*, *Migrazione* e *Migrazione consociata* (solo dove il listino li prevede), *Immobiliare* (prezzo fisso fino a 3 utenti). Il catalogo moduli (codice, descrizione, area applicativa, prezzo, vincoli) viene dal foglio *AGGIUNTA NUOVI MODULI* (`module.area`); il flag **CB** (configurazione base, compreso nel prezzo) o **FA** (funzione aggiuntiva) è per profilo (`galileo_module`).

**Calcolo** (`it.cpqia.galileo.GalileoPricing`, replica delle formule e delle macro dei fogli): somma dei moduli aggiuntivi × (1 − 30%) + prezzo della configurazione (V9 o V11, o fisso), poi utenti per fascia come % della base (la prima fascia è inclusa); consociata = prezzo / 2 + moduli nuovi; migrazione = % della configurazione (da carattere 50%, da visuale 30%, da V9 importo fisso) + moduli nuovi + utenti aggiuntivi. Verificato contro il ricalcolo LibreOffice dei fogli: `tools/galileo/validate.py` (212 scenari, in `src/test/resources/galileo/golden.json`) e `GalileoPricingTest`. Le anomalie del listino, mantenute come nel foglio, sono in `tools/galileo/ANOMALIE.md`.

**Offerta.** Con l'elemento *Galileo* e una tipologia che ha un listino compare la pagina **Galileo** (dopo Power System): scenario, release, utenti, moduli per area con stato (In comune/Migrato/Nuovo), totali (listino, manutenzione, noleggio), vincoli sui moduli (`module_rule`, dal foglio *AGGIUNTA NUOVI MODULI*). La configurazione si salva con l'offerta (`offer_galileo`, `offer_galileo_module`) ed è mostrata nel riepilogo; **per ora non entra nei totali dell'offerta né nel documento Word**. API: `/offers/api/galileo/markets|profile/{id}|calc`. Il listino ha una scadenza (31/12/2026): dopo, la pagina mostra un avviso.

**Aggiornare il listino.** Per un nuovo listino Excel: rigenerare `profiles.json` con `extract.py`, rieseguire `validate.py` (per tipo: NEW, CONS, MIG,MIGCONS, IMMOB) e scrivere una **nuova** migrazione di aggiornamento di prezzi, moduli e profili (la migrazione `V60` crea il catalogo iniziale e non si modifica né si rigenera).

## Comportamento griglie (Impostazioni > Comportamento griglie)

Solo administrator. Una riga per ogni griglia dell'applicazione (Offerte, Account, Utenti, Applicazioni, Sedi, Cestino, tabelle tariffe, ecc.) con le caselle **Configurabile**, **Spostamento colonne**, **Ordinamento colonne**, **Pivot**, **Totali**, **Subtotali**. Se *Configurabile* è disattivato la griglia è fissa (niente «Colonne», niente «Viste» salvate) e le altre caselle sono disabilitate. Le impostazioni stanno nella tabella `grid_behavior` (migrazione `V46`; senza riga tutto è abilitato) e arrivano a `grid.js` nel campo `meta.behavior`; le configurazioni di viste salvate incompatibili vengono ripulite al caricamento (`Grid.enforce`). *Subtotali* = totali dei gruppi della pivot; *Totali* = riga dei totali in fondo.

## Stato della verifica (leggere prima)

| Parte | Stato |
|---|---|
| Catalogo estratto dal listino Excel (26 applicazioni, 249 moduli, fasce, vincoli) | generato da script, riproducibile (`tools/`) |
| Motore di calcolo (moduli × utenti, fasce, prezzo fisso, manutenzione 18%, noleggio) | **verificato** contro il ricalcolo LibreOffice del listino: 160 scenari su 19 applicazioni, tutti coincidenti (`src/test/resources/golden.json`) |
| Esempio del tuo Word (DQ 10 utenti = € 8.000 / € 1.440; JPA3 = € 10.500 / € 1.890; totale € 18.500 / € 3.330; netto 20% € 14.800 / € 2.664) | riprodotto esattamente |
| Modello Word con segnaposto + riempimento | verificato con una versione Python equivalente e resa in PDF |
| Logica JS del wizard | testata con node (`node tools/test_wizard_core.js`) e verificata visivamente con API simulate |
| Logica JS delle griglie (filtri, ordinamenti, pivot, totali) | testata con node (`node tools/test_grid_core.js`) e verificata visivamente con API simulate (`tools/make_grid_preview.py`) |
| **Codice Java (Spring Boot), SQL, template Thymeleaf** | **non compilato né eseguito**: l'ambiente di sviluppo non aveva JDK 21, Maven né rete. Rivisto a mano da due letture indipendenti. Aspettarsi qualche correzione al primo `mvn package` / primo avvio |

Primo passo consigliato: `mvn clean package` (esegue anche `PricingGoldenTest` e `RuleCheckerTest`), poi avvio con
`cpqia.dev-login=true` per provare l'interfaccia senza configurare Google/Apple.

## Build e deploy

```bash
mvn clean package            # produce target/cpqia.war
createdb / psql -f deploy/setup-postgres.sql   # database vuoto: lo schema lo crea Flyway al primo avvio
```

**Tomcat multi-contesto.** Ogni WAR è un contesto indipendente e il nome del file è il context path:

| WAR | URL | Configurazione letta |
|---|---|---|
| `cpqia.war` | `/cpqia` | `$CATALINA_BASE/conf/cpqia/cpqia.properties` |
| `cpqia-test.war` | `/cpqia-test` | `$CATALINA_BASE/conf/cpqia/cpqia-test.properties` |

Per una nuova istanza: copiare il WAR con un altro nome, creare il suo file `.properties` (modello in
`deploy/cpqia.properties.example`) e un database dedicato. Ogni istanza ha quindi database, amministratori e credenziali OAuth
propri. Il pool può essere anche JNDI (`deploy/context.xml.example`). Tutti i link dell'applicazione sono relativi al
contesto, quindi il percorso di pubblicazione è libero. Dietro un reverse proxy abilitare `RemoteIpValve` (HTTPS e host corretti per i redirect OAuth).

## Login Google e Apple

* **Google:** creare un ID client OAuth "Applicazione web" e registrare `https://HOST/CONTESTO/login/oauth2/code/google`.
* **Apple:** creare un Services ID con "Sign in with Apple", registrare dominio e `https://HOST/CONTESTO/login/oauth2/code/apple`,
  creare una chiave `.p8`. Il client secret (JWT ES256) viene generato dall'applicazione a ogni richiesta. Apple richiede
  HTTPS e che il cookie di sessione sia `SameSite=None` (vedi `deploy/context.xml.example`).
* **Chi può entrare:** solo chi ha un utente applicativo attivo. `cpqia.bootstrap-admin-email` crea il primo administrator;
  gli altri al primo accesso vengono registrati "non abilitati" e un administrator li abilita in *Utenti e permessi*.

## Utenti e flag di abilitazione (tabella `app_user`)

`administrator` (tutte le funzioni + gestione utenti), `can_create_offers`, `can_edit_offers`, `can_view_all_offers`,
`can_delete_offers`, `can_export_docx`, `can_manage_customers`, `can_manage_catalog`, `can_view_prices`, `active`.
I permessi sono letti al login. `can_view_prices` nasconde i prezzi nell'interfaccia (non è un confine di sicurezza: il documento Word
generato contiene comunque gli importi).

## Funzioni

* **Nuova offerta** (percorso a passi; nel passo applicazioni il selettore delle BU mostra tutte le famiglie con numero di applicazioni e moduli scelti, e il pulsante «Solo BU con moduli selezionati» limita l'elenco alle BU in cui hai scelto dei moduli): *1)* data (default oggi), progressivo, numero offerta
  `AAAAMMGG.<progressivo><iniziali>` (es. `20261022.3ER`), cliente da anagrafica con ricerca o **nuova anagrafica** da finestra dedicata
  (codice cliente automatico e non modificabile; il record creato viene subito selezionato); *2)* tabella applicazioni con selezione
  multipla, numero utenti (abilitato alla selezione), modalità (nuova installazione/migrazione ecc.) e dettaglio moduli nella stessa tabella con
  prezzo di listino, vincoli e prerequisiti (aggiunta automatica); *3)* riepilogo, totali, sconto ("netto a voi riservato"), salvataggio e
  generazione Word.
* **Recupero dati dalla partita IVA:** nella nuova anagrafica la P.IVA è il primo campo; il pulsante *Recupera dati online* controlla la cifra di controllo e interroga il servizio gratuito VIES della Commissione Europea (ragione sociale e indirizzo, spezzato in via/indirizzo/civico/CAP/città/provincia). Segnala se la P.IVA esiste già in anagrafica. Per le imprese italiane VIES non sempre pubblica i dati: in tal caso conferma solo la validità. Il server deve poter raggiungere `ec.europa.eu` (HTTPS); proprietà `cpqia.vat-lookup.*`. Un fornitore a pagamento (es. OpenAPI/Cerved/InfoCamere) si può agganciare in `VatLookupService`.
* **Account (anagrafica clienti/prospect, tabella `account`, rinominata da `customer` con la migrazione `V10`):** tipo, ragione sociale, via (tipo toponimo), indirizzo, n. civico, CAP, città, provincia,
  P.IVA (univoca), codice fiscale; codice `C000001…` automatico.
* **Anagrafica applicazioni e moduli** (*Impostazioni > Applicazioni e moduli*): applicazioni, modalità di prezzo, grandezze (utenti, macchine…),
  gruppi di prezzo e fasce, moduli con prezzi per modalità, vincoli (richiede / richiede uno tra / esclude / nota).
* **Utenti e permessi, profilo (iniziali per il numero offerta).**

## Manutenzione su netto scontato

In fondo al passo **Applicazioni e moduli** il flag **«Manutenzione su netto scontato»** (un solo flag per offerta, vale per tutte le manutenzioni; `V15`, predefinito: selezionato, cioè il comportamento precedente) decide come lo sconto tocca la manutenzione: selezionato, la manutenzione è calcolata sulle licenze **al netto dello sconto** (anche la manutenzione viene scontata); non selezionato, è calcolata sul **valore di listino** delle licenze e lo sconto si applica solo alle licenze. Vale per netto manutenzione, margine commerciale, totali del configuratore e Word (tabella dei totali e riepilogo finale). Si salva con l'offerta.

## Come funziona il listino (dall'Excel)

Ogni foglio è un'applicazione. I moduli "a utente" si sommano in un valore base; la **prima fascia** (es. fino a 10 utenti) è inclusa nel
valore moduli, ogni utente oltre costa `valore moduli × %fascia / 100` (percentuale decrescente per fascia; per JPA3 e JPM-User prezzi unitari
espliciti). I moduli a prezzo fisso si sommano. Manutenzione annua = 18% (noleggio mensile = (listino + 4×manutenzione) / 48). Le macro VBA
sono solo di servizio: azzerano le selezioni e impostano la modalità (nuova installazione / migrazione, View / Sense, ecc.) in celle fisse (`Z4`, `G4`).
Le macro non contengono altra logica di calcolo. I commenti delle celle (prerequisiti, alternative) sono stati trasformati in vincoli.

Fogli esclusi per scelta: `RIEPILOGO` e `JPA` (non più vendibile).

### Anomalie del listino riportate così come sono (da decidere)

* **EPM, moduli "a nuovo":** la % della fascia oltre 41 utenti (cella `O25`) è vuota → 0%. Mantenuto per coincidere con l'Excel; correggibile da Anagrafica.
* **JPM, modulo `SMIEV`:** non rientra in nessuna somma del foglio (`F16 = SUM(F10:F14)`). Importato come modulo a prezzo fisso.
* **ECM3, fasce pagine:** nell'Excel il canone entra nel "listino" e subisce il 18%. Modellato come canone annuo + € 100 una tantum senza 18%.
* **JPA3, Designer in migrazione:** lo sconto (2.500) dipende dalla cella `Z3`, mai valorizzata. Applicato 2.500 in migrazione come da intento.
* **CONNETTORI** (`#REF!`), **BARCODEFLOW** (totale incompleto), **XC** (IF annidati sul canone), **FIRMAE**, **CRC**, **EPM modalità Power BI**:
  importati dai valori delle celle ma non verificabili contro l'Excel; marcati "da verificare" nel catalogo.
* **iOC:** percentuale orfana in `N13` ignorata.

## Modello Word (file separati, assemblati in ordine)

Il documento finale è l'unione dei file in `src/main/resources/offer-template/`, ciascuno agganciato con un'interruzione di pagina:

| # | File | Quando |
|---|---|---|
| 1 | `offer-template.docx` | copertina (sezione 1, piè di pagina proprio): `{{TITOLO}}`, `{{CLIENTE}}`, `{{INDIRIZZO1/2}}`, `{{PIVA}}`, `{{RIF}}`, `{{DATA}}`, `{{NUMERO}}` |
| 2 | `licenze.docx` | sempre: riga prototipo `{{L_*}}` per ogni applicazione, totali `{{T_*}}` / netto `{{N_*}}` |
| 3 | `attivita.docx` | sempre (testo fisso; il suo "TOTALE IMPORTO STIMATO" alimenta il riepilogo finale) |
| 4 | `manutenzione.docx` | sempre, dopo le attività |
| 5 | `servizi-accessori.docx` | solo con il flag "Aggiungi servizi accessori", dopo la manutenzione |
| 6 | `riepilogo-finale.docx` (+ tabella da `riepilogo-finale-servizi-accessori.docx`) | solo con il flag "Aggiungi riepilogo finale", prima di "varie"; la seconda tabella solo se c'è anche il flag servizi accessori |
| 7-9 | `varie.docx`, `pagamenti.docx`, `condizioni-gdpr.docx` | sempre |

**Flag della testata offerta:** "Canone di manutenzione in aggiunta all'attuale" (aggiunge " IN AGGIUNTA ALL'ATTUALE" alla riga *CANONE MANUTENZIONE ANNUALE E ATC* del riepilogo finale), "Aggiungi servizi accessori" (aggiunge il passo di configurazione e le sezioni nel Word), "Aggiungi riepilogo finale".

**Riepilogo finale** (`{{RF_*}}`): licenze software e canone di manutenzione sono i valori **netti** (dopo lo sconto); attività di progetto = totale di `attivita.docx`; totale stima investimento = licenze + attività. Le righe POWER SYSTEM e PACCHETTO GIORNATE DI FOLLOW UP non hanno dati nell'applicazione e vengono tolte.

**Servizi accessori:** il catalogo (Audit, Discovery Xchange, Firmae, CRC con le relative tabelle di fasce) è nelle tabelle `extra_*` ed è stato ricavato da `servizi-accessori.docx` (`tools/gen_extras_seed.py`). Nel Word restano solo le sezioni dei servizi scelti (marcatori `[[SA:...]]`), con la "X" sulla riga scelta di ogni tabella (`{{X:GRUPPO:RIGA}}`) e importi, fasce e quantità compilati dal catalogo (`{{P:...}}`, `{{Q:...}}`). Gli importi dei servizi accessori non rientrano nello sconto né nel margine commerciale. I modelli con segnaposto si rigenerano dagli originali con `tools/make_parts.py` (originali in `tools/originals/`).

Intestazione, piè di pagina e numerazione dopo la copertina sono quelli della sezione finale del modello di copertina; immagini (QR) e collegamenti dei file agganciati sono ricollegati. Se un file contiene anche le sezioni dei file successivi, viene tagliato al primo "Titolo 1" di una parte successiva. Con `cpqia.template-dir=/percorso/cartella` i file si leggono da disco (modificabili con Word senza ricompilare; nomi accettati anche `attività.docx` e `condizioni e gdpr.docx`).

## Tabelle tariffe (Impostazioni > Tabelle tariffe)

Due tabelle di supporto, gestibili con il flag “gestione catalogo”: **Profili professionali** (tariffa oraria: Consulente di organizzazione 125, Capo Progetto 110, Analista 95, Istruttore 90, Sviluppatore 85, ITech 90 €/h) e **Trasferte Italia** (0-25 km 50, 25-100 km 100, 100-200 km 150, 200-400 km 250, > 400 km 350, Isole 400 €). Si possono modificare descrizione, importo e ordine, disattivare, eliminare e aggiungere voci; i valori iniziali sono nella migrazione `V6`. Le voci attive sono lette da `GET /offers/api/rates` (da usare per la futura configurazione delle attività sistemistiche).

## Tema Giorno / Notte / Sistema

Icona a mezzaluna nella barra superiore, accanto al nome utente: *Giorno*, *Notte* o *Sistema* (segue in tempo reale l'impostazione chiaro/scuro del sistema operativo). La scelta è salvata nel browser; il tema scuro è in `m3.css` (`html[data-theme=dark]`).

## Modifiche non salvate

Se si tocca un campo di una scheda (account, utente, applicazione, modulo, profilo), del configuratore offerte o di una cella in modifica nelle griglie e poi si sceglie un'altra funzione (voce di menu, collegamento, Esci, altro modulo), compare il pannello **«Modifiche non salvate»** con **«No, resta qui»** predefinito (Invio, Esc o clic fuori = resta); solo «Sì, abbandona» fa perdere le modifiche. Salvando, il segnale si azzera. Ricerche e filtri (`data-noguard`) e le finestre di dialogo non contano. Per chiusura della scheda, ricarica o indirizzo digitato, i browser consentono solo la loro finestra standard (non personalizzabile).

## Elenchi a discesa

I menu a tendina dei moduli, delle schede di modifica e delle tabelle di configurazione usano un **pannello personalizzato** (angoli arrotondati, ombra, voce selezionata evidenziata con spunta, animazione leggera, tema chiaro/scuro) al posto di quello nativo del browser. Tastiera: frecce, Invio / Spazio per aprire e scegliere, Esc per chiudere, digitando le prime lettere si salta alla voce. Il `<select>` originale resta nascosto nella pagina, quindi valori, validazioni ed eventi non cambiano (`app.js`, `.cs-*` in `m3.css`; `data-native` sul select per escluderlo).

## Cancellazione logica e Cestino

Nessun record viene più cancellato fisicamente: l'eliminazione imposta `deleted_at`/`deleted_by` (offerte, account, utenti, applicazioni, moduli, modalità, grandezze, gruppi, fasce, vincoli, profili, trasferte, sedi; migrazione `V7`). Gli indici univoci (numero offerta, P.IVA, e-mail, codici…) valgono solo sui record non eliminati, quindi un codice eliminato si può riusare.

* **Cestino** (menu per tutti): un utente normale vede solo i record eliminati da lui e può **ripristinarli**; l'administrator vede quelli di tutti (colonna *Eliminato da*), può ripristinare qualunque record ed **eliminarlo definitivamente**.
* **Svuota cestino** (solo administrator): elimina definitivamente tutto il Cestino; i record ancora collegati ad altri dati restano e vengono segnalati. Tutte le conferme dell'applicazione usano un pannello web (non `alert`/`confirm`) con **No** come scelta predefinita.
* Controlli: un account con offerte attive, un'applicazione/modulo usati in offerte, il proprio utente o l'ultimo administrator non si eliminano; un elemento figlio (modulo, fascia…) non si ripristina se il genitore è nel Cestino; il ripristino fallisce con messaggio se esiste già un record attivo con lo stesso codice/numero.

## Griglie dati (Offerte, Account, Utenti, Applicazioni, Tabelle tariffe, Sedi, Cestino)

* **Modifica in griglia:** doppio clic sulla cella (Invio conferma, Esc annulla; le caselle sì/no con un clic). Le celle modificabili dipendono dal permesso dell'utente; ogni modifica è **ricontrollata dal server** (tipo, obbligatorietà, lunghezza, intervalli, regole: offerta *Emessa* non modificabile finché non torna *Bozza*, CAP a 5 cifre, provincia a 2 lettere, nessun auto-declassamento dell'administrator, unicità…). In caso di errore la cella torna al valore precedente.
* **Colonne:** si spostano trascinando l'intestazione; il menu *Colonne* le mostra/nasconde.
* **Ordinamento:** clic sull'intestazione (crescente → decrescente → nessuno); **Maiusc+clic** aggiunge colonne in serie (numeri 1, 2, … accanto alla freccia). I valori vuoti vanno sempre in fondo.
* **Filtri** sotto l'intestazione e ricerca libera: testo (`contiene`, `=esatto`, `!non contiene`, `^inizia con`, `a|b`), numeri (`>100`, `50..120`, `!=0`), date (`2026`, `10/2026`, `>=01/10/2026`, `01/01/2026..31/03/2026`), `vuoto`, elenchi e sì/no a tendina.
* **Pivot:** trascina una o più intestazioni nella barra *Raggruppa per* (o usa l'icona sull'intestazione); l'ordine delle etichette è l'ordine dei livelli e si cambia trascinandole. Ogni gruppo mostra conteggio e **totali delle colonne numeriche**; in fondo c'è il totale generale. La funzione (somma, media, min, max, conteggio) si sceglie per colonna nella riga dei totali. È un raggruppamento a righe con subtotali, non una tabella a doppia entrata.
* **Esporta > Google Sheets:** l'applicazione crea il foglio direttamente sul tuo Google Drive, con titolo «<griglia> – gg/mm/aaaa hh:mm», intestazione in grassetto, riga bloccata, numeri/date veri e riga dei totali (rispetta la selezione). Al primo uso Google chiede l'autorizzazione (scope `drive.file`: l'app accede solo ai file che crea). **Configurazione** nel progetto Google Cloud del login: abilitare **Google Sheets API**; nella schermata di consenso aggiungere lo scope `.../auth/drive.file`; tra gli *URI di reindirizzamento autorizzati* aggiungere `https://HOST/CONTESTO/google-sheets/callback` (in locale `http://localhost:8080/google-sheets/callback`, con il prefisso del contesto se impostato). Google accetta solo HTTPS o localhost: da un indirizzo `http://IP` di rete l'app ripiega sulla copia negli appunti con un foglio nuovo in cui incollare.
* **Righe per pagina:** menu «Righe» nella barra della griglia (10, 15, 25, 50, 100, 200, 500 o Tutte; predefinito 15; con la pivot conta i gruppi di primo livello): è un'impostazione **personale per utente e per griglia** (`V24`, tabella `grid_setting`), si salva subito e non fa parte delle viste salvate. Il paginatore in fondo compare solo se le righe superano la pagina.
* **Scheda di modifica:** nelle griglie con colonne modificabili e senza una pagina di modifica propria (modelli Power System, applicazioni, tabelle tariffe, sedi, tipi azienda/indirizzo…) ogni riga ha il pulsante matita che apre una **scheda con tutti i campi modificabili**; il salvataggio è unico e passa dagli stessi controlli del server della modifica in cella (campi obbligatori, intervalli, unicità).
* **Azioni di riga:** nelle Offerte il pulsante matita (apri e modifica nel configuratore) resta in vista, mentre «Scarica Word» e «Sposta nel Cestino» sono raccolti nel menu a **tre puntini**, separati da una linea. Le altre griglie mantengono i pulsanti in linea; una azione di griglia si porta nel menu con `menuLink(...)` in `GridRegistry`.
* **Azioni di riga:** in ogni griglia si vede solo l'azione principale (la matita di **modifica**; nel Cestino «Ripristina»). Tutte le altre stanno nel **menu a tre puntini** (es. Scarica Word, Nuova offerta per l'account, Apri applicazione), e in fondo, dopo un separatore, le azioni distruttive: «Elimina definitivamente» nel Cestino e **«Sposta nel Cestino»** come ultima voce.
* **Selezione righe:** la prima colonna ha una casella per riga (nell'intestazione: seleziona/deseleziona tutte le righe filtrate; sulle righe di gruppo della pivot: tutte le righe del gruppo). Il contatore «n selezionate» e il pulsante *Deseleziona* compaiono in alto. **Esporta** (Excel, Google Sheets, CSV) considera solo le righe selezionate, se ce ne sono, altrimenti tutte quelle visualizzate; il pulsante diventa «Esporta (n)».
* **Viste salvate** (menu *Viste*): ordine e visibilità colonne, ordinamenti, filtri, raggruppamenti, funzioni di totale, righe per pagina; per utente e per griglia, con vista predefinita applicata all'apertura. Menu *Esporta*: **Excel (.xlsx)** generato dal server (numeri e date veri, intestazione bloccata, filtro automatico, riga Totale con SUBTOTAL), **Google Sheets** (copia i dati negli appunti e apre un foglio nuovo: basta incollare) e **CSV** (`;`, per Excel italiano). Si esportano le righe e le colonne come visualizzate, nell'ordine dei gruppi se c'è la pivot.
* Le griglie sono definite in `GridRegistry` (colonne, permessi, regole di modifica); API `GET/PATCH/POST /api/grid/{chiave}…`; logica client testata con `node tools/test_grid_core.js`.

## Sedi Sanmarco (solo administrator)

Menu *Impostazioni > Sedi Sanmarco*: elenco importato da `Elenco sedi.xlsx` (migrazione `V8`, rigenerabile con `tools/gen_sedi_seed.py`). Progressivo, paese, indirizzo, civico, CAP, provincia: modifica in griglia, *Aggiungi*, eliminazione nel Cestino. Il nome del paese è univoco tra le sedi attive.

## Elementi dell'offerta, Tipo Azienda / Tipo Indirizzo, mercati Galileo

* **Passi dell'offerta:** *Dati iniziali* (data, progressivo, cliente, titolo, riferimento) → **Elementi** → **Dipartimentali** (ex «Applicazioni e moduli») → Servizi → (Servizi accessori) → Riepilogo. La pagina *Elementi* contiene: **Power System**, **Galileo** (casella: se selezionata compare il selettore *Tipologia Galileo*, senza scelta predefinita e **obbligatorio**; elenco = applicazioni con famiglia `GALILEO`, che non compaiono tra i Dipartimentali), **Servizi SMITECH**, **Dipartimentali** (predefinito: selezionato), **Servizi accessori** (aggiunge il passo dedicato) e la card *Opzioni del documento* con «Canone di manutenzione in aggiunta all'attuale» e «Aggiungi riepilogo finale».
* **Dipartimentali:** i passi *Dipartimentali* e *Servizi* (attività) compaiono solo se la casella è selezionata (Servizi anche solo con almeno un'applicazione); togliendola, le applicazioni e le attività già configurate vengono rimosse dopo conferma (predefinito «No, mantieni»). Un'offerta senza Dipartimentali è ammessa se ha almeno un altro elemento (Power System, Galileo, SMITECH); nel Word manca allora la sezione licenze. Le scelte si salvano con l'offerta (`V18`) e compaiono nel Riepilogo. Per ora Power System, Galileo e SMITECH **registrano la scelta ma non aggiungono ancora passi né sezioni al Word**.
* **Power System:** selezionando la casella compare, subito dopo *Elementi* (prima dei Dipartimentali), la pagina **Power System**, in due righe di sezioni. In alto, **Selezione del modello** (75%): modello (anagrafica *Modelli Power System*, solo attivi), **RAM** e **disco** — con i soli tagli supportati dal modello — e **utenti IBM i licenziati** (almeno 1 oppure **ILLIMITATI**); a destra **Importi** (25%): prezzo di listino e prezzo netto in €, obbligatori, con lo sconto implicito. In basso, **Opzioni Hardware** (50%): scheda ethernet 10 gbit, LTO8 e cavo di collegamento LTO6/LTO7 (alternativi), e **Opzioni Software** (50%): manutenzione SW/HW (36/48/60 mesi), sistema operativo (7.5/7.6), Edition (Solution/Express), Transfer-Conversion (con riferimenti obbligatori), e l'**elenco dei prodotti IBM i** dell'anagrafica, ciascuno con un selettore (Migrato, 1, 2, 3, 4) **senza valore predefinito e obbligatorio** (`V42`). Importi, hardware e software sono disabilitati (grigi, con lucchetto) finché la selezione del modello non è completa. Tutto si salva con l'offerta (server con gli stessi controlli) e compare nel riepilogo degli elementi; togliendo la casella Power System la configurazione viene azzerata. Per ora senza sezione nel Word e senza effetto sui totali.
* **Mercati Galileo** (`V17`, da `mercati.xlsx` con `tools/gen_mercati_seed.py`): 11 applicazioni attive, famiglia = 1ª colonna, codice = 2ª, descrizione = 3ª (usata anche come nome). Si gestiscono in *Applicazioni e moduli* come le altre.
* **Account:** il campo *Tipo* è collegato alla tabella **Tipo Azienda** (`company_type`: Prospect, Cliente) e il campo *Via* alla tabella **Tipo Indirizzo** (`address_type`: Via, Viale, Piazza, Piazzale, Corso, Largo, Strada, Vicolo, Contrada, Borgo, Lungomare, Frazione, Località), con vincoli di integrità (`V16`). Le due tabelle si gestiscono in *Impostazioni > Tipo azienda e indirizzo* (solo administrator); i menu a tendina della scheda account, della finestra «nuovo account» e della griglia leggono da queste tabelle. I codici PROSPECT / CLIENTE sono usati dalla logica dell'applicazione e non si modificano.

### Sezione Power System nel documento Word

Con l'elemento Power System selezionato, nel Word compaiono **dopo «LICENZE»** (la prima con interruzione di pagina):
1. il **documento Word collegato al modello** scelto (cartella `Power`, caricato in anagrafica; se il modello non ne ha uno la parte si salta; le interruzioni di sezione interne vengono tolte);
2. **`Power/tabelle-power-hardware.docx`** («Hardware di sistema»), di seguito al documento del modello con una sola riga vuota (nessun salto pagina), tabella con riga prototipo `{{PWH_CODE}}` `{{PWH_DESC}}` `{{PWH_QTY}}` compilata con le righe: sistema (codice = modello + tipo, descrizione = descrizione del modello — senza ripetere «Model » se già presente — poi GHz e «Prestazioni  CPW n», quantità 1); Memoria RAM GB (quantità = solo il numero di GB scelto); Dischi NVMe da <disco> in mirroring (**Numero dischi**, nuovo campo di «Selezione del modello», predefinito 2, `V45`); «Tape LTO 8 e scheda sas» oppure «Cavo di collegamento per LTO6/LTO7» (nessuna riga se non scelti; quantità 1); Cavi vari; DVD ROM (esterno); Adattatore Ethernet 4 porte 1 Gbps (2 per 8 core, altrimenti 1); adattatore 10 Gbps + transceiver (solo se scelto, quantità 2); Utenti licenziati IN SUBSCRIPTION (numero oppure ILLIMITATI).

3. **`Power/tabelle-power-software.docx`** («Software di sistema»), di seguito alla tabella hardware senza salto pagina (riga vuota); prototipo `{{PWS_CODE}}` `{{PWS_DESC}}` `{{PWS_QTY}}`: prima riga con codice vuoto e descrizione «Gruppo SW <gruppo processore> (<n> core)» + a capo «Versione software IBM <sistema operativo>»; poi una riga per ogni **prodotto IBM i** impostato in *Opzioni Software* (codice, descrizione, valore scelto: 1, 2, 3, 4 o Migrato; nell'ordine dell'anagrafica).

4. **`Power/tabelle-power-manutenzione.docx`** («Manutenzione hardware»), **in una pagina nuova** dopo la tabella software: nella tabella dei totali `{{PW_PREZZO_LISTINO}}` («TOTALE POWER SYSTEM») e `{{PW_PREZZO_NETTO}}` («TOTALE POWER SYSTEM A VOI RISERVATO») sono compilati con il prezzo di listino e il prezzo netto impostati nella pagina Power System (formato € 12.500,00). La riga «MANUTENZIONE HARDWARE 7x24 –» è completata con `{{PW_MESI_MANUTENZIONE}}` MESI (36, 48 o 60: *Manutenzione SW/HW* delle Opzioni Software), es. «MANUTENZIONE HARDWARE 7x24 – 36 MESI».
5. **`Power/tabelle-power-chiavi di licenza.docx`**, di seguito alla manutenzione hardware con **due righe vuote** (nessun salto pagina): `{{PW_MESI_MANUTENZIONE}}` (nel testo «ha durata pari a … mesi») è sostituito con i mesi *Manutenzione SW/HW* delle Opzioni Software.
6. **`Power/tabelle-power-solution edition.docx`**, solo se *Edition* = Solution nelle Opzioni Software: di seguito alle chiavi di licenza con due righe vuote (nessun salto pagina).
7. **`Power/tabelle-power-migrazione programmi.docx`** e **`Power/tabelle-power-transfer.docx`**, solo se *Transfer-Conversion* è Transfer o Conversion: la migrazione di seguito con due righe vuote, il documento transfer **in una pagina nuova**. Segnaposto: `{{TRANSFER-CONVERSION}}` («Transfer» o «Conversion»), `{{MACCHINA_DONOR}}` (campo *Riferimenti Power*), `{{SOFTWARE_IBM}}` (descrizioni dei prodotti IBM i impostati «Migrato», es. «A, B e C»).
8. **`Power/tabelle-power-caratteristiche nvme.docx`**, sempre con il Power System, di seguito al documento precedente con due righe vuote (nessun salto pagina).
9. **`Power/tabelle-power-smaltimento.docx`**, sempre con il Power System, di seguito al documento NVMe con due righe vuote (nessun salto pagina).

Segnaposto disponibili in questi documenti (e negli altri): `{{PW_MODELLO}}`, `{{PW_TIPO}}`, `{{PW_CODICE}}`, `{{PW_DESCRIZIONE}}`, `{{PW_CORE}}`, `{{PW_MONTAGGIO}}`, `{{PW_GHZ}}`, `{{PW_CPW}}`, `{{PW_RAM}}`, `{{PW_DISCO}}`, `{{PW_N_DISCHI}}`, `{{PW_UTENTI}}`, `{{PW_PREZZO_LISTINO}}`, `{{PW_PREZZO_NETTO}}`, `{{PW_MESI_MANUTENZIONE}}`. I modelli `tabelle-power-*.docx` sono in `offer-template/Power`; quello hardware è generato dall'originale con `tools/make_power_parts.py` (toglie l'evidenziazione gialla e l'altezza riga fissa). Gli altri `tabelle-power-*` (software, manutenzione, chiavi di licenza, migrazione, solution edition, transfer) sono già nella cartella ma non ancora usati.

### Documento Word del modello Power System

Ogni modello ha il campo **Documento Word** (`V30`, `power_model.word_file`): nella **scheda di modifica** del record (matita) si sceglie un file `.docx` da caricare, oppure si scarica quello esistente. I documenti sono salvati nella sottocartella **`Power`** della cartella dei modelli Word: `cpqia.template-dir/Power` se `cpqia.template-dir` è impostata, altrimenti in sviluppo `src/main/resources/offer-template/Power`; sul server impostare `cpqia.template-dir` (la sottocartella viene creata da sola). Controlli: estensione `.docx`, file Word valido, massimo 25 MB, nome ripulito (nessun percorso); un file con lo stesso nome viene sostituito. Caricando un altro documento per lo stesso modello, **il nuovo sostituisce il precedente** (il vecchio file viene eliminato dal disco se nessun altro modello lo usa). Il collegamento è solo memorizzato: il documento non viene ancora inserito nell'offerta.

## Integrazioni: IBM Power System (IBM i)

*Impostazioni > Integrazioni* (solo administrator): scheda **IBM Power System** per il collegamento JDBC al server IBM i con il driver **JTOpen** (`net.sf.jt400:jt400`, nel `pom.xml`). Si impostano **indirizzo IP o nome del server**, **utente**, **password** (cifrata AES-GCM nel database, mai riletta dal browser; campo vuoto = invariata), **libreria** (schema, passata come `libraries=` e nome predefinito) e l'opzione **SSL**; il pulsante **Verifica connessione** apre una connessione (timeout 20 s) con i valori del modulo (anche non salvati), esegue una query di prova, mostra versione del server e driver e controlla che la libreria esista (`QSYS2.SYSSCHEMAS`); l'ultimo esito è registrato (`V22`). Impostare `cpqia.crypto-key` (frase segreta lunga) nella configurazione: senza, la password è protetta da una chiave predefinita e la pagina lo segnala. Il server deve raggiungere il Power System (porte JDBC/Host Servers: 8470, 8476 per il login e 8471/9471 con SSL). L'integrazione è solo la configurazione e il test: nessun dato viene ancora letto o scritto.

### Agente di sincronizzazione clienti da IBM Power System

Nella pagina *Integrazioni*, card **Agente di sincronizzazione clienti**: esegue (automaticamente o con **Esegui ora**) la lettura del file **CGANA01J** della libreria **INF90DAT** (modificabili) con la connessione IBM i sopra, per i record con `CLFOCP = 'C'`, e li importa negli *Account* come **Cliente**. Pianificazione: ogni giorno a un orario oppure ogni N minuti (5–1440), con attivazione e disattivazione; un controllo ogni minuto lancia l'esecuzione quando è dovuta (`V23`).

* **Quali record:** quelli con data di inserimento (`DT01CA`) o di ultima modifica (`DTMNCA`, formato AAAAMMGG) compresa tra la **data dell'ultima importazione** (inclusa, perché i dati hanno solo la data) e la **data di esecuzione**; alla prima esecuzione tutti. A fine importazione riuscita la data dell'ultima importazione diventa quella di esecuzione. Il filtro sulle date è applicato dall'agente dopo la lettura (così funziona sia con campi numerici sia testuali).
* **Campi importati:** tipo = `CLIENTE`; codice = `CONTCA`; ragione sociale = `DSCOCP`; indirizzo = `INDICA`, con il **numero civico separato** (anche `12/A`, `SNC`) e il toponimo riconosciuto dalla tabella *Tipo Indirizzo* (anche abbreviazioni come `P.ZZA`, `C.SO`, `V.LE`; altrimenti «Via» e testo intero); CAP = `CAPOCA` (completato a 5 cifre); città = `LOCACA`; provincia = `PROVCA`; partita IVA = `PIVACA` (a 11 cifre); codice fiscale = `CDFICA`. Se la partita IVA manca si usa il codice fiscale.
* **Regole di aggiornamento:** la chiave è il codice; se esiste si aggiornano i dati (se cambia l'indirizzo le coordinate si ricalcolano). Un **prospect con la stessa partita IVA** viene promosso a cliente con quel codice. Vengono **saltati** (e elencati in *Dettagli*) i record senza codice o ragione sociale, senza P.IVA e codice fiscale, con P.IVA già usata da un altro account, o con codice nel Cestino.
* Storico delle ultime esecuzioni con letti / nuovi / aggiornati / invariati / saltati.

## Prodotti IBM i

In *Impostazioni*, accanto a **Modelli Power System**, la scheda **Prodotti IBM i** (permesso «gestione catalogo»): anagrafica dei prodotti software IBM i con **codice** (univoco) e **descrizione**, attivo sì/no, come le altre griglie (modifica in cella o con la scheda, Aggiungi, Cestino, filtri, viste, esportazione). Dati iniziali (`V40`): 5722-SS1 Sistema Operativo OS/400; 5773-PVE Licenza Power VM; 5722-XW1 iSeries Access; 5770-WDS IBM WebSphere Development Studio for iSeries. Per ora è solo l'anagrafica, non ancora collegata ai modelli né alle offerte. Nel menu laterale le voci con sottolivelli (come *Modelli Power System*) hanno la freccia **espandi / chiudi**: sono chiuse in partenza, si aprono da sole quando si è in una pagina figlia e la scelta fatta con la freccia viene ricordata dal browser. L'icona del menu *Modelli Power System* è un server rack con pulsante di accensione.

## Anagrafica modelli IBM Power System

*Impostazioni > Modelli Power System* (permesso «gestione catalogo»): griglia con **modello, tipo, core (1, 4 o 8), montaggio (Desk/Rack), descrizione, gruppo processore (P05 / P10, obbligatorio), GHz (testo libero fino a 200 caratteri, `V38`/`V44`), versione (POWER 11 — predefinita — / POWER 10, `V43`), RAM supportata, dischi supportati, versione, CPW, documento Word** e stato attivo. Gestione completa: modifica in cella o con la **scheda** (matita), **Aggiungi**, eliminazione nel Cestino, filtri, ordinamenti, pivot, viste, esportazione come le altre griglie.

* **Gruppo processore dei modelli iniziali** (`V32`): 4 core → **P05**, 8 core → **P10**; i modelli da 1 core passano a **P05** (`V33`, che rende il campo obbligatorio).
* **Descrizione:** proposta come «Model » + modello + core + «core» + montaggio (es. *Model 9824-22A 8 core DESK*); nella scheda segue le modifiche di modello / core / montaggio finché non la si personalizza, e se è vuota si usa quella automatica.
* **RAM supportata** (scelta multipla: 32, 64, 96, 128, 256, 384, 512 GB) e **dischi supportati** (1.6, 3.2, 6.4 TB): definiscono quali tagli offre lo specifico modello; nell'offerta il menu RAM/Disco propone solo quelli (elenco vuoto = tutti) e il server rifiuta tagli non supportati. Migrazioni `V31` (nuovi campi; i modelli esistenti partono con tutti i tagli, da raffinare) e dati iniziali da `Modelli Power.xlsx` (`V26`/`V27`, `tools/gen_power_seed.py`).
* **Offerta:** nella pagina *Elementi* la casella *Power System* fa comparire, dopo Elementi, la pagina **Power System** (scelta del modello con RAM, disco, opzioni LTO8 / ethernet 10 gbit, utenti IBM i): vedi sotto.

## Comuni (ricerca inline nei dati dell'account)

Tabella `comuni` (nome, codice ISTAT, **sigla, provincia e regione**, CAP principale e tutti i CAP, codice catastale). La tabella `province` e la sua gestione sono state eliminate (migrazione `V47`): provincia e regione stanno nei comuni e l'elenco delle province per la ricerca è ricavato da lì. I comuni con CAP si **scaricano da internet** (elenco open data JSON, `cpqia.geodata.url`): all'avvio, in background, se la tabella è vuota, oppure con *Impostazioni > Comuni > Aggiorna da internet* (ripetibile: aggiorna per codice ISTAT/sigla). Il server deve poter raggiungere l'indirizzo; il formato atteso è quello di `matteocontrini/comuni-json` (nome, codice, sigla, provincia, regione, codiceCatastale, cap[]).

Nei moduli account (scheda e finestra «nuovo account» dell'offerta) i campi **Città**, **Prov.** e **CAP** hanno la ricerca inline (frecce, Invio, Esc): scegliendo un comune si compilano la **provincia** e il **CAP** (se il comune ne ha più d'uno, il campo CAP propone l'elenco); scrivendo la provincia si cerca per sigla o nome, e se è già indicata la ricerca dei comuni è limitata a quella provincia. Si può comunque scrivere un valore libero. API: `/geo/api/comuni?q=…&prov=…`, `/geo/api/province?q=…`.

## Servizi: attività sistemistiche

Passo **Servizi** del configuratore, subito dopo le applicazioni e prima dei servizi accessori. Per ogni applicazione dell'offerta (non per i moduli) si aggiungono righe di attività: **descrizione**, **profilo professionale** (tabella *Profili professionali*), **ore**; la **tariffa oraria** è quella del profilo e il **totale riga** (ore × tariffa) è calcolato, con subtotale per applicazione. In fondo il campo **Sconto %** riduce tutte le tariffe orarie (tariffa scontata arrotondata al centesimo × ore); le righe mostrano tariffa e totale anche scontati. La barra inferiore mostra, dopo il margine commerciale, il **totale delle attività a listino / scontato**. Le righe vuote vengono ignorate, quelle incomplete bloccano il salvataggio; se si toglie un'applicazione dall'offerta le sue attività non vengono salvate.

Salvataggio (`V12`): tabella `offer_activity` (applicazione, descrizione, profilo con nome, ore, tariffa) e `offer.activity_discount_pct`. Il server rilegge la tariffa dal profilo a ogni salvataggio e controlla applicazione, profilo e ore. **Nel Word** la sezione «ATTIVITA’ SISTEMISTICHE» (`attivita.docx`) viene compilata con le righe del passo Servizi: per ogni applicazione una fascia grigia con il nome e le sue attività (descrizione e profilo professionale, stima ore, **tariffa scontata**, valore stimato); la riga «TOTALE IMPORTO STIMATO» riporta ore e importo totali, che sono anche l'«Attività di progetto» del riepilogo finale. `attivita.docx` è un modello con segnaposto (come gli altri, rigenerabile da `tools/originals/attivita.docx` con `tools/make_parts.py`): riga-fascia `{{ATT_APP}}`, riga attività `{{ATT_DESC}}` `{{ATT_ORE}}` `{{ATT_TARIFFA}}` `{{ATT_VALORE}}`, totali `{{ATT_TOT_ORE}}` `{{ATT_TOT}}`; le righe prototipo sono sostituite da quelle reali e le note finali restano. Ogni applicazione ha una riga di **sub-totale** (ore e valore, riga `{{ATT_SUB_NAME}}` `{{ATT_SUB_ORE}}` `{{ATT_SUB_VALORE}}`). Il flag **«Dettaglio importi attività»** in fondo al passo Servizi (predefinito: non selezionato, `V13`) decide se le righe riportano ore, tariffa e valore (e il profilo) oppure solo la descrizione, lasciando al sub-totale i numeri. Se non ci sono attività configurate la sezione non compare nel documento (e manca la riga «Attività di progetto» nel riepilogo finale). Il totale non entra nel margine commerciale.

## Tariffe dell'account

Nella scheda dell'account la sezione **Tariffe** contiene: i *Profili professionali* (€/h) con il valore di **listino** e un campo **tariffa dedicata** (vuoto = listino; `V19`, tabella `account_profile_rate`) e il campo **Spese di trasferta**, un'unica voce scelta dalla tabella *Trasferte Italia* (`V20`, `account.travel_rate_id`). Nell'offerta, scegliendo l'account: le **attività sistemistiche** (passo Servizi) usano la tariffa dedicata del profilo se esiste, altrimenti il listino (le righe sono marcate «tariffe cliente»; il server rilegge la tariffa a ogni salvataggio, quindi è la stessa che finisce nei totali e nel Word) e le **Spese di trasferta** dell'offerta sono preimpostate sulla voce assegnata all'account (modificabile; in sua assenza si propone la fascia in base ai km). Cambiando account le righe vengono riprezzate.

## Ordine dei box nel Riepilogo e sconto attività

Riepilogo finale: *Riepilogo offerta* → (servizi accessori) → **Licenze** (ex «Sconto e margine»: sconto %, netto licenze e manutenzione, margine commerciale) → **Attività sistemistiche** → **Trasferta** → **Power System** (solo con l'elemento Power System: prezzo di listino e prezzo netto, con modello e sconto implicito) → **Modalità di fatturazione e pagamento**. Il campo **Sconto %** sulle tariffe orarie delle attività si imposta nel box **Attività sistemistiche** del Riepilogo (sopra ai totali a listino e scontato, che si aggiornano mentre scrivi); nella pagina *Servizi* resta il box «Totali attività» con totale a listino e scontato e un rimando.

## Trasferta: spese di trasferta e distanza dalla sede più vicina

Nel Riepilogo la card *Trasferta* ha il menu **Spese di trasferta** (tabella *Trasferte Italia*) e la **distanza** dalla sede Sanmarco più vicina. Come si determina la voce:

* **Cliente:** la voce è quella dell'**anagrafica account** (campo *Spese di trasferta* della scheda). Nessun calcolo automatico; se in anagrafica non c'è, si può sceglierla a mano nell'offerta (senza modificare l'anagrafica).
* **Prospect con voce già in anagrafica:** si usa quella.
* **Prospect senza voce in anagrafica:** viene **calcolata** in automatico cercando la sede più vicina all'indirizzo del prospect (percorso stradale, fascia in base ai km). **Salvando la prima offerta con una voce di trasferta** impostata, la scelta viene registrata nell'anagrafica del prospect; dalle offerte successive vale quella.

Le **coordinate delle sedi** Sanmarco sono salvate in tabella (`V21`: colonne *Comune*, *Latitudine*, *Longitudine*, con i valori di partenza del centro del comune; modificabili in *Sedi Sanmarco*): non dipendono dalla geocodifica online. Se si modifica l'indirizzo di una sede le coordinate vengono ricalcolate da internet, se si inseriscono a mano restano fisse. La distanza (Nominatim/OpenStreetMap + OSRM, servizi pubblici gratuiti; coordinate in cache, `V9`) è un'informazione che si salva con l'offerta; il pulsante *Ricalcola* la rifà per qualunque account e, per un prospect senza voce in anagrafica, propone di nuovo la fascia. Se il percorso stradale non risponde il valore è una **stima** (linea d'aria × 1,3, `cpqia.geo.straight-line-factor`). Impostare in `cpqia.geo.user-agent` un recapito reale (policy di Nominatim). L'importo della trasferta non entra nei totali né nel margine e non compare ancora nel Word.

## Modalità di fatturazione e pagamento

Nel riepilogo dell'offerta si scelgono, separatamente per **licenze**, **canoni di manutenzione** e **attività sistemistiche**, la fatturazione (licenze: entro 30 gg dall'ordine o data; canoni: entro il mese successivo alla fatturazione delle licenze o data, con flag *Semestrale*; attività: a consuntivo o a pacchetto) e il pagamento (solito in uso, RI.BA. 30 gg dffm, IVA RI.BA. con scadenze dell'imponibile, altro a testo libero; per le attività le opzioni dipendono dalla fatturazione). Con “Data” e “Altro” i campi relativi sono obbligatori. Predefiniti: pagamento RI.BA. 30 gg dffm per un prospect e “solito in uso” per un cliente, finché non si modifica a mano. Elenchi e predefiniti sono in `src/main/resources/offer-terms.json`; i valori compaiono nella tabella di `pagamenti.docx` (`{{PAG_*}}`, il canone semestrale aggiunge “– fatturazione semestrale”).

## Rigenerare il catalogo dal listino

```bash
python3 tools/build_catalog.py "listino.xlsm"     # -> tools/catalog.json
python3 tools/gen_seed.py                         # -> db/migration/V2__seed_catalog.sql
python3 tools/api_shape.py                        # -> src/test/resources/catalog-api.json
python3 tools/validate_engine.py "listino.xlsm"   # ricalcolo LibreOffice + golden.json (richiede soffice)
```
Il catalogo di un database esistente si modifica dall'interfaccia di amministrazione; una nuova versione del listino va introdotta con una nuova migrazione Flyway.

## Struttura

```
pom.xml                         WAR Spring Boot (finalName cpqia)
deploy/                         esempi di configurazione Tomcat/PostgreSQL
tools/                          estrazione catalogo, validazione, template Word, anteprime
src/main/java/it/cpqia/         pricing (motore) · repo · service · security · web · docx · domain
src/main/resources/             db/migration (V1 schema, V2 catalogo) · templates · static · offer-template
src/test/                       test del motore con scenari ricalcolati dall'Excel
```
