# Anomalie del listino JGalileo 5.0 (foglio Excel) e come sono trattate

Il motore replica le formule dei fogli. Dove il foglio contiene incongruenze, il comportamento del foglio è stato **mantenuto**, così i totali coincidono con quelli di Excel (verificato con LibreOffice su 212 scenari). Sono tutte correggibili dall'amministrazione (per esempio impostando la regola di addebito del modulo), ma la scelta spetta al responsabile del listino.

## Moduli con prezzo ma senza formula di addebito (non incidono sul totale)

- **ALIMENTARE VINICOLO**: SMIFTEPL (3000)
- **COMMERCIALE**: SMIFTEPL (3000)
- **CONSOCIATA ALIMENTARE VINICOLO**: SMIFTEPL (3000)
- **CONSOCIATA COMMERCIALE**: SMIFTEPL (3000), SMILE (854)
- **CONSOCIATA HOLDING AMMINISTRAZI**: SMIFTEPL (3000), SMILE (854)
- **CONSOCIATA PROD SU COMMESSA**: SMIFTEPL (3000)
- **HOLDING AMMINISTRATIVA**: SMIFTEPL (3000)
- **MIG. AZ. CONSOCIATA COMMERCIALE**: SMIFTEPL (3000)
- **MIG. AZ. CONSOCIATA PRODUZIONE**: SMIFTEPL (3000)
- **MIGRAZIONE AZ. COMMERCIALE**: SMIFTEPL (3000)
- **MIGRAZIONE AZ. PRODUZIONE**: SMIFTEPL (3000)

## Moduli fuori dall'intervallo di somma della colonna E (non addebitati come «migrati/in comune»)

- **MIG. AZ. CONSOCIATA PRODUZIONE**: 20 moduli (SMIFBE, SMIFCZ, SMIFDE, SMIFES, SMIFFR, SMIFGB, SMIFHR, SMIFHUN, SMIFNL, SMIFPL, SMIFRO, SMIFRU, SMIFSM, SMIFTERO, SMIFTR, SMIFUS, SMIFYG, SMIFYS, SMISAFRO, SMISVK)
- **MIGRAZIONE AZ. PRODUZIONE**: 20 moduli (SMIFBE, SMIFCZ, SMIFDE, SMIFES, SMIFFR, SMIFGB, SMIFHR, SMIFHUN, SMIFNL, SMIFPL, SMIFRO, SMIFRU, SMIFSM, SMIFTERO, SMIFTR, SMIFUS, SMIFYG, SMIFYS, SMISAFRO, SMISVK)

## Altre incongruenze

- CONSOCIATA HOLDING AMMINISTRAZI: SMIAM (riga 25) colonna E addebita il prezzo della riga 28 (360) invece del proprio 760; replicato
- CONSOCIATA HOLDING AMMINISTRAZI: SMIAM (riga 25) colonna G addebita il prezzo della riga 28 (360) invece del proprio 760; replicato
- CONSOCIATA HOLDING AMMINISTRAZI: SMIFT (riga 41) colonna E addebita il prezzo della riga 45 (900) invece del proprio 1500; replicato
- CONSOCIATA HOLDING AMMINISTRAZI: SMIFT (riga 41) colonna G addebita il prezzo della riga 45 (900) invece del proprio 1500; replicato
- CONSOCIATA HOLDING AMMINISTRAZI: SMIFYL (riga 26) colonna E addebita il prezzo della riga 29 (360) invece del proprio 500; replicato
- CONSOCIATA HOLDING AMMINISTRAZI: SMIFYL (riga 26) colonna G addebita il prezzo della riga 29 (360) invece del proprio 500; replicato
- CONSOCIATA HOLDING AMMINISTRAZI: SMIRA (riga 27) colonna E addebita il prezzo della riga 25 (760) invece del proprio 504; replicato
- CONSOCIATA HOLDING AMMINISTRAZI: SMIRA (riga 27) colonna G addebita il prezzo della riga 25 (760) invece del proprio 504; replicato
- MODA: sotto i 6 utenti il foglio riduce il prezzo (utenti negativi nella 2a fascia: 1 utente = 60%, 2 = 70%, 3 = 80%, 4 = 90% del prezzo base); replicato
- Vincoli di SMICP: nel foglio AGGIUNTA NUOVI MODULI 'e SMIAS', nei fogli di mercato anche REQUIRES SMICG SMIFY (usato AGGIUNTA NUOVI MODULI)
- Vincoli di SMICZ: nel foglio AGGIUNTA NUOVI MODULI 'e ECM SMICG SMIGD SMIGDP', nei fogli di mercato anche REQUIRES SMIBB SMIDG (usato AGGIUNTA NUOVI MODULI)
- Vincoli di SMIFF: nel foglio AGGIUNTA NUOVI MODULI 'e SMIFT SMIGA', nei fogli di mercato anche REQUIRES SMIRA (usato AGGIUNTA NUOVI MODULI)
- Vincoli di SMIGF: nel foglio AGGIUNTA NUOVI MODULI 'o SMIGA SMILE', nei fogli di mercato anche REQUIRES SMIGA SMILE (usato AGGIUNTA NUOVI MODULI)
- Vincoli di SMIHB: nel foglio AGGIUNTA NUOVI MODULI 'e SMIEF SMIEP SMIFF', nei fogli di mercato anche REQUIRES SMICG (usato AGGIUNTA NUOVI MODULI)
- Vincoli di SMIJEVA: nel foglio AGGIUNTA NUOVI MODULI 'e SMIMG SMIVI', nei fogli di mercato anche REQUIRES SMIJEVT SMIMG SMIVI (usato AGGIUNTA NUOVI MODULI)
- Vincoli di SMIMA: nel foglio AGGIUNTA NUOVI MODULI 'e SMIBB SMIDG', nei fogli di mercato anche REQUIRES SMIFT SMIGA SMILE SMIMG SMIPK SMIPM SMISP (usato AGGIUNTA NUOVI MODULI)
- Vincoli di SMIPF: nel foglio AGGIUNTA NUOVI MODULI 'e SMIRA', nei fogli di mercato anche REQUIRES SMIEF SMIEP SMIFF (usato AGGIUNTA NUOVI MODULI)
- Vincoli di SMIUM: nel foglio AGGIUNTA NUOVI MODULI 'o SMIFT SMISP SMIMG SMIPM SMIGA SMILE SMIPK', nei fogli di mercato anche REQUIRES SMIFT SMIGA SMILE SMIMG SMIPK SMIPM SMISP (usato AGGIUNTA NUOVI MODULI)

## Comportamenti dei fogli replicati

- **Consociata COMMERCIALE e HOLDING**: con release V11 lo sconto del 30% sui moduli non viene applicato (le altre consociate lo applicano).
- **Migrazione consociata COMMERCIALE**: il totale è diviso per 2; nella migrazione consociata PRODUZIONE no.
- **Aggiunta moduli (N5 = 2)** nelle consociate/migrazioni di produzione: nel foglio la formula dà #VALUE! (cella configurazione vuota); qui la configurazione non è addebitata e i moduli base sono addebitati singolarmente (comportamento previsto).
- **MODA**: sotto i 6 utenti il prezzo scende in proporzione (1 utente = 60%, 2 = 70%, 3 = 80%, 4 = 90%).
- **IMMOBILIARE**: prezzo fisso fino a 3 utenti; oltre il foglio rimanda a HOLDING AMMINISTRATIVA. La cella del totale nel foglio (L20) mostra per errore il numero di utenti: si usa C30.
- **Scadenza del listino**: nel foglio RIEPILOGO (5.0, scadenza 31/12/2026); dopo la scadenza i fogli azzerano i totali, l'applicazione mostra un avviso.
