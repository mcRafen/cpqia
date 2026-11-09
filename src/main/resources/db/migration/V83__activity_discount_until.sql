-- Riepilogo > Attivita' sistemistiche: data di validita' dello sconto sulle tariffe (obbligatoria se c'e' lo sconto); compare nel documento attivita.docx
ALTER TABLE offer ADD COLUMN activity_discount_until DATE;
