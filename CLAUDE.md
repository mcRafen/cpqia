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

## Come lavorare
- Dopo ogni modifica indicare chiaramente cosa non è stato provato (Java non compilato, Word non generato).
- Allineare con git: `./sync.sh "messaggio"` (commit + pull --rebase + push). Prima di iniziare su un altro PC: `git pull`.
