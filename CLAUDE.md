# CPQIA – note per chi lavora sul progetto (Claude incluso)

Applicazione web in italiano (look Google Material) che compone offerte commerciali in .docx attraverso una configurazione guidata (wizard).
Rispondere e scrivere testi dell'interfaccia sempre in italiano.

## Stack e build
- Spring Boot 3.5.x WAR, Java 25, Thymeleaf + CSS/JS propri (`src/main/resources/static`: `wizard.js`, `wizard-core.js`, `grid.js`, `app.js`, `m3.css`, `palettes.css`), PostgreSQL + Flyway, Apache POI 6 per i Word.
- Build e prova: `./go.sh` (clean package + spring-boot:run). Configurazione locale e segreti in `config/application.properties` (non versionato).
- Chi scrive il codice con Claude di solito NON ha Java 17+/Maven/PostgreSQL nell'ambiente di Claude: il Java va compilato e provato dall'utente. Il JavaScript si controlla con `node --check`.

## Regole del progetto
- Migrazioni Flyway `V<n>__*.sql` consecutive (c'è un test); mai modificare una migrazione già rilasciata, aggiungerne una nuova.
- I modelli Word sono in `src/main/resources/offer-template/` (la cartella `documenti/` contiene le copie di lavoro dell'utente). Il WAR li sincronizza all'avvio (`TemplateUpgradeService`). I modelli delle attività si preparano con `python3 tools/prepare_activity_templates.py`.
- Tema grafico: palette Laguna (predefinita), Ametista, Foresta, Terra; i colori sono generati da `tools/gen_palette.py` → `palettes.css` (non modificare a mano).
- Listini: Dipartimentali (`tools/` + migrazioni V1-V58), JGalileo (`tools/galileo/`, `gen_seed*.py`, motore `GalileoPricing`).
- Deploy su Tomcat: cancellare sia `webapps/cpqia.war` sia la cartella `webapps/cpqia` prima di copiare il nuovo WAR (vedi `deploy/INSTALL.md`).
- Repository privato: non committare segreti (Google OAuth ecc.).

## Decisioni e comportamenti da ricordare
Offerta e revisioni
- Numero offerta `YYYYMMDD.<progressivo><iniziali>`: le iniziali sono nome+cognome dell'agente dell'account (`agent.description` è «COGNOME NOME»); se manca l'agente, quelle dell'utente.
- Revisione = nuova offerta collegata (stesso numero, `revision`+1, V76). Le revisioni superate sono di sola lettura, con «Genera Word» sempre abilitato e «Promuovi ad attuale» dallo storico. La griglia mostra solo l'ultima revisione; la matita apre `/offers/{id}/open`. Il confronto N revisioni è una finestra modale; le modifiche tra revisioni sono in `offer_revision_change`.
- Titolo progetto obbligatorio se non si può calcolare in automatico dalle applicazioni. Con sconto sulle attività la «Data validità» è obbligatoria.

Dipartimentali e SMITECH
- Gli utenti partono da 0 e si chiedono solo se è selezionato un modulo con gruppo di prezzo «Moduli per utente»; con più tipi di utenti basta uno. Con soli moduli a prezzo fisso conta il prezzo e il campo non c'è. La pagina blocca l'uscita finché mancano.
- Utenti dei servizi SMITECH = il maggiore tra utenti Power e Galileo, non modificabili. Fasce SMISMP nella scheda del modulo SMISMP; classe P05/P10 dal gruppo processore del Power.
- BU in ordine alfabetico, chip senza colori propri (seguono il tema).

Galileo
- Importi arrotondati all'intero (sempre con 2 decimali); la riduzione % «moduli aggiuntivi» viene dall'anagrafica applicazione; riga «Importo moduli aggiuntivi».
- Scenario MODULI AGGIUNTIVI (applicazione `CRM_JGAL_MODULI`, profilo `AGG`, V82 generata da `tools/galileo/gen_seed_agg.py`): utenti facoltativi, dipendenze solo da verificare (avvisi, non bloccanti), Light acceso e bloccato, altre opzioni oscurate tranne «Servizi» (solo Manuale), nessun riepilogo finale, licenze con `licenze-elenco-semplice.docx` (una riga per modulo, riga «Posti di lavoro» senza importi solo se ci sono utenti), nota sul consuntivo se non ci sono servizi.

Documento Word
- «Modello offerta Light» acceso: `attivita-light.docx` + `attivita-appendice.docx`, nessun riepilogo finale, mai il profilo professionale; contano «Dettaglio importi attività» e «Totali applicazioni». Spento: `attivita.docx` (tariffe da listino, sviluppo progetto, sconto/data validità GG/MM/AAAA, totali, «Servizi extra progetto» con conversioni solo per prospect, personalizzazioni, follow up) e riepilogo finale; vale «Dettaglio ore per singola attività».
- Licenze: ordine righe Galileo, SMITECH, poi ordine di selezione; `moduli.docx` (famiglia/area/modulo) prima della tabella; con Light spento titolo «Importi licenze software». Riepilogo: riga «POWER SYSTEM» con i netti Power+LTO8 di ogni durata separati da « ~ »; totale stima investimento vuoto con più durate.
- SMIDRI (Power HA/DR con soluzione SMIDRI): box «SMIDRI» sotto Opzioni Hardware (listino dal modulo SMITECH con codice esatto `SMIDRI`, SMISMP abbinato dal codice esatto `SMISMP-SMIDRI`, per classe P05/P10 del Power di Produzione; valori al cliente obbligatori). Nel Word il documento `Smitech/SMIDRI.docx` va dopo la sezione Power System e prima di ATTIVITA' SISTEMISTICHE; con SMISMP abbinato seguono `Smitech/SMISMP.docx` e, con un capo riga, `Smitech/SMISMP-TOTALI-CON-SMIDRI.docx`; segnaposto `{{CLASSE_POWER}}`, `{{SMIDRI_LISTINO}}`, `{{SMIDRI_NETTO}}`, `{{SMISMP-SMIDRI_LISTINO}}`, `{{SMISMP-SMIDRI_NETTO}}`. Riga «Canone SMIDRI/SMISMP» in pagamenti.docx (`{{SMIDRI/SMISMP}}`, `{{PAG_SMIDRI_SMISMP_FATT}}`, `{{PAG_SMIDRI_SMISMP_PAG}}`).
- Riepilogo finale (solo con Light spento): con SMIDRI e/o SMISMP in offerta compare `riepilogo-finale-servizi-smitech.docx` (`{{SMITECH_NAME}}`, `{{SMITECH_CAN}}`: descrizione e canone annuo al cliente) prima di `riepilogo-finale-servizi-accessori.docx`.
- Power manutenzione: listino e netto a 36 mesi non possono superare 48 (e 48 non il 60), per Power e LTO8; una riga vuota tra le durate; la tabella manutenzione è di seguito, senza salto pagina.
- Le tabelle a tutta pagina dei modelli attività sono 200 unità più strette per non uscire dal margine destro.

Altro
- Esportazione dati delle griglie abilitata per utente e per griglia (V80). ATOKA: chiave API cifrata, la prima connessione la fa un amministratore dal clic sulla P.IVA (V77). Preferenze di tema e palette salvate per utente (V78).

## Come lavorare
- Dopo ogni modifica indicare chiaramente cosa non è stato provato (Java non compilato, Word non generato).
- Allineare con git: `./sync.sh "messaggio"` (commit + pull --rebase + push). Prima di iniziare su un altro PC: `git pull`.
