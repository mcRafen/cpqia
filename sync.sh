#!/bin/bash
# Allinea i sorgenti con GitHub: registra le modifiche (commit) e le pubblica (push).
# Uso:  ./sync.sh "descrizione delle modifiche"      (senza descrizione usa data e ora)
set -e
cd "$(dirname "$0")"
MSG="${1:-Aggiornamento $(date '+%Y-%m-%d %H:%M')}"
git add -A
if git diff --cached --quiet; then
  echo "Nessuna modifica da registrare."
else
  git commit -m "$MSG"
fi
git pull --rebase origin main || true     # porta qui eventuali modifiche fatte altrove
git push origin main
echo "Fatto: sorgenti allineati con GitHub."
