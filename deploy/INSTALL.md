# Installazione di CPQIA su Tomcat

## 1. Cosa installare sul server

| Componente | Versione | Note |
|---|---|---|
| Java (JRE o JDK) | **25** (LTS) | l'applicazione è compilata per Java 25 (Spring Boot 3.5); con Java 21 non parte |
| Apache Tomcat | **10.1.x**, ultima release disponibile (Jakarta EE 10) | Tomcat 9 e precedenti **non** funzionano (namespace `javax`). Il WAR include già tutte le librerie (driver PostgreSQL, Flyway, POI, JTOpen per IBM i): non serve copiare nulla in `lib/` |
| PostgreSQL | **12 o superiore** (consigliato 14+) | anche su un altro server; serve solo che Tomcat lo raggiunga sulla porta 5432 (o quella scelta) |

Non servono LibreOffice, Word, né altri programmi: i documenti Word sono generati dall'applicazione.

**Rete in uscita dal server Tomcat** (HTTPS, porta 443 salvo dove indicato):
- `accounts.google.com`, `oauth2.googleapis.com`, `www.googleapis.com` – accesso con Google;
- `sheets.googleapis.com` – «Importa da RFQ» ed esportazione in Google Sheets;
- `ec.europa.eu` – recupero dati dalla partita IVA (VIES);
- `nominatim.openstreetmap.org`, `router.project-osrm.org` – distanza dalla sede più vicina;
- `raw.githubusercontent.com` – elenco dei comuni (scaricato al primo avvio; si può disattivare con `cpqia.geodata.auto-import=false`);
- il server **IBM i** (porte dei Host Server: 446, 449, 8470–8476; con SSL 9470–9476) per Sync Clienti e Sync Agenti.

**Impostazioni consigliate di Tomcat** (`setenv.sh`): `CATALINA_OPTS="-Xms512m -Xmx1g -Duser.timezone=Europe/Rome -Dfile.encoding=UTF-8"` (le pianificazioni delle sincronizzazioni usano l'ora locale del server).
`unpackWARs="true"` e `autoDeploy="true"` (valori predefiniti) nell'`Host` di `server.xml`.

## 2. Preparare la cartella di configurazione

La configurazione di ogni contesto sta **fuori dal WAR**, in `$CATALINA_BASE/conf/cpqia/<contesto>.properties` (per `cpqia.war`: `conf/cpqia/cpqia.properties`). L'utente con cui gira Tomcat deve poterci scrivere (la procedura di setup crea lì il file):

```
sudo mkdir -p $CATALINA_BASE/conf/cpqia
sudo chown tomcat:tomcat $CATALINA_BASE/conf/cpqia
sudo chmod 750 $CATALINA_BASE/conf/cpqia
```

## 3. Deploy e procedura di setup

1. Copia `cpqia.war` in `$CATALINA_BASE/webapps/` (Tomcat lo distribuisce da solo).
2. Poiché il contesto non è ancora configurato, **al posto dell'applicazione parte la procedura di setup**: apri `http://SERVER:8080/cpqia/`.
3. Inserisci il **codice di setup**, generato a ogni avvio, che trovi nel file `$CATALINA_BASE/conf/cpqia/cpqia.setup-token` e nel log (`catalina.out`, riga «CPQIA setup»). Serve a impedire che altri completino la procedura.
4. **Accesso a PostgreSQL** – server, porta, database, utente, password, modalità SSL (`disable … verify-full`) e, tra le opzioni avanzate, schema, timeout di connessione e dimensione del pool. Il pulsante **Verifica connessione** controlla anche la versione di PostgreSQL, che lo schema sia vuoto (o già di CPQIA) e che l'utente possa creare tabelle.
   Se il database o l'utente non esistono, apri «Il database o l'utente non esistono ancora?», inserisci le credenziali di un amministratore PostgreSQL (non vengono salvate) e premi **Crea utente e database** (database UTF-8, collazione `it_IT.UTF-8` se disponibile).
5. **Applicazione** – email del primo amministratore, client ID e secret Google (nella Google Cloud Console registra gli URI di reindirizzamento mostrati dalla pagina; abilita «Google Sheets API» per l'RFQ), chiave di cifratura (generata: **conservala**), cartella modelli Word facoltativa e altre impostazioni libere `chiave=valore`.
6. **Salva e avvia CPQIA**: viene scritto `conf/cpqia/cpqia.properties` (permessi 600), il contesto si ricarica e al primo avvio Flyway crea tabelle e catalogo (può servire un minuto). Quando l'applicazione è pronta la pagina ti porta al login.

Se il ricaricamento automatico non riesce (messaggio in pagina), ricarica il contesto dal Manager di Tomcat o riavvia Tomcat.

## 4. Dietro un proxy HTTPS (consigliato in produzione)

Google accetta URI di reindirizzamento solo `https://` (o `http://localhost`). Metti Apache/nginx davanti a Tomcat e inoltra `X-Forwarded-Proto/Host/For`; in `server.xml`, nell'`Host`, aggiungi:

```
<Valve className="org.apache.catalina.valves.RemoteIpValve" />
```

Per «Sign in with Apple» usa anche `deploy/context.xml.example` (cookie `SameSite=None`).

## 5. Dopo l'installazione

- Accedi con l'email del primo amministratore; poi *Impostazioni > Integrazioni* per IBM i, Sync Clienti e Sync Agenti.
- **Backup**: il database PostgreSQL (`pg_dump`) e la cartella `conf/cpqia` (contiene password e chiave di cifratura).
- **Aggiornamento della versione in produzione**: il WAR contiene **tutte** le migrazioni del database (V1 … ultima). Procedura:
  1. **Backup**: `pg_dump -Fc -U cpqia cpqia > cpqia_AAAAMMGG.dump` (e copia di `conf/cpqia`).
  2. **Ferma il contesto, elimina la vecchia versione e copia il nuovo WAR**: rimuovi `webapps/cpqia.war` **e la cartella `webapps/cpqia`**, poi copia il nuovo `cpqia.war` (se resta la cartella già espansa Tomcat può continuare a usare le classi della versione precedente e il log mostra uno schema «up to date» alla vecchia versione). La configurazione non cambia e il setup **non** riparte. Per controllare che giri il WAR giusto: passa il mouse sulla versione `(V…)` in alto, compare la data e l'ora di compilazione.
  3. All'avvio Flyway applica in ordine le migrazioni mancanti, partendo dalla versione del database (qualunque sia la versione precedente): nel log di Tomcat leggi «CPQIA database: aggiornamento da V… a V…» e alla fine «schema alla versione V…».
  3a. **Impostazioni**: il contenuto delle tabelle di Impostazioni del database di sviluppo viaggia nel WAR come pacchetto (`settings/settings-pack.json`) e all'avvio viene applicato, una sola volta per versione del pacchetto, al database dell'installazione (riga «CPQIA Impostazioni aggiornate: …» nel log). Si trasportano: applicazioni e moduli (con prezzi, vincoli, modalità), tabelle tariffe, modelli Power System, prodotti IBM i, sedi, tipi azienda e indirizzo, servizi accessori, listino Galileo, comportamento delle griglie. **Non** si trasportano offerte, account, utenti, agenti, comuni, integrazioni e preferenze delle griglie. Per preparare il pacchetto: in sviluppo apri *Impostazioni > Aggiornamento* e premi **Esporta nel progetto**, poi ricompila il WAR. Le righe del pacchetto sostituiscono quelle con lo stesso id; se l'installazione ha un record con lo stesso codice ma id diverso l'applicazione lo segnala nel log e lascia invariate le impostazioni (nessuna modifica parziale).
  3b. **Modelli Word**: tutti i documenti di `offer-template` (anche le sottocartelle `Power` e `Smitech`) sono dentro il WAR. Se non usi `cpqia.template-dir` vengono letti direttamente dal WAR e si aggiornano con lui. Se hai impostato `cpqia.template-dir`, all'avvio l'applicazione allinea quella cartella: copia i modelli mancanti, sostituisce quelli non modificati (copia di sicurezza in `_backup/<data-ora>/`) e lascia i modelli che hai personalizzato o caricato dall'applicazione, salvando accanto la nuova versione come `<nome>.nuova-versione` (leggi nel log «CPQIA modelli Word»).
  4. Se la versione del WAR è **più vecchia** del database, o una migrazione già applicata risulta alterata, l'applicazione **non parte** (errore chiaro nel log): non forzare, usa il WAR corretto oppure ripristina il backup.
  5. **Ritorno indietro**: ripristina il backup (`pg_restore -c`) e rimetti il WAR precedente; le migrazioni non si annullano da sole.
  Regole per chi sviluppa: ogni modifica al database è una **nuova** migrazione `V<n+1>__descrizione.sql`; non modificare mai una migrazione già rilasciata; i numeri devono essere consecutivi (lo verifica `MigrationsTest` al build).
- **Rieseguire il setup**: avvia Tomcat con `-Dcpqia.setup.force=true` (le impostazioni esistenti restano, quelle gestite dalla procedura vengono sostituite; il file precedente è salvato come `.properties.bak`), oppure togli da `conf/cpqia/cpqia.properties` le righe `spring.datasource.*`.
- **Più istanze** (test, clienti): copia il WAR con un altro nome (`cpqia-test.war`): ogni contesto ha la sua configurazione e il suo database.
- Installazioni senza la procedura: copia `deploy/cpqia.properties` in `conf/cpqia/<contesto>.properties` e modificalo a mano (`deploy/setup-postgres.sql` crea utente e database).
