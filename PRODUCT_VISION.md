# PRODUCT_VISION – LifeOS

## Zielbild
LifeOS ist ein persönliches Betriebssystem für den Alltag: eine einzige, zusammenhängende
Verwaltungszentrale für Finanzen, Termine, Projekte, Besitz, Weiterbildung und persönliche Dokumente.
Nicht eine Sammlung von Apps, sondern **ein Datenraum**, in dem jedes Objekt mit jedem anderen
verbunden sein kann – ohne doppelt zu existieren.

## Für wen
Für eine Person (mich), die ihr Leben ernsthaft organisiert: Haushaltskasse und Verträge im Griff,
private Projekte mit echten Kosten, Fahrzeuge und Werkzeuge mit Wartungshistorie, Weiterbildung zum
Industriemeister Mechatronik mit Prüfungsterminen und Lernfortschritt.

## Lebensbereiche
| Bereich | Kern | Phase |
|---|---|---|
| Finanzen | Haushaltskasse, Konten, Kategorien, Budgets, Fixkosten, Verträge, Abos, Sparziele, Belege, Langzeitstatistik | 1–3 |
| Alltag | Kalender, Aufgaben, Erinnerungen, Wiederholungen | 5 |
| Projekte | Vorhaben, Aufgaben, Materiallisten, Kosten (verknüpfte Buchungen), Meilensteine | 4 |
| Besitz | Fahrzeuge, Werkzeuge, Geräte, Garantien, Wartungen, Reparaturen | 4 |
| Weiterbildung | Industriemeister Mechatronik: Fächer, Themen, Lernzeiten, Prüfungen, Material | 6 |
| Archiv | Dokumente, Notizen, Fotos, Quittungen, Volltextsuche | 5 |
| Erweiterungen | Dokumentenerkennung, automatische Zuordnung, Integrationen, KI (optional) | 7+ |

## Produktprinzipien
1. **Eine Wahrheit.** Jede Information existiert genau einmal; Sichten sind Verknüpfungen.
2. **Exakt und prüfbar.** Jede angezeigte Zahl lässt sich aus gespeicherten Rohdaten nachrechnen.
3. **Lokal und privat.** Offline-First, verschlüsselt, ohne Konto, ohne Tracking. Netzwerk nur, wenn ich es ausdrücklich will.
4. **Futuristisch im Ausdruck, schlicht in der Bedienung.** Die Lichtlinie trägt Bedeutung; Bedienwege bleiben vertraut.
5. **Ehrlich.** Keine Attrappen, keine Fake-Statistiken, keine leeren Versprechen in der Oberfläche.
6. **Langlebig.** Daten überstehen jedes Update; Schemaänderungen sind migriert und getestet.

## Nicht-Ziele (bewusst)
- Kein Chat-Assistent als Hauptoberfläche.
- Keine Bankanbindung (FinTS/PSD2) in den ersten Phasen – Risiko und Aufwand stehen in keinem Verhältnis zum Nutzen für Phase 1–4.
- Kein Mehrbenutzer-/Familienbetrieb, keine Cloud-Synchronisierung (Datenmodell ist dafür aber vorbereitet: UUIDs, Zeitstempel).
- Keine Mehrwährungs-Umrechnung bis zu einer eigenen Phase.

## Erfolgskriterien
- Eine Buchung in < 10 Sekunden erfasst (Betrag, Kategorie, Speichern).
- Monatsübersicht öffnet ohne spürbare Wartezeit (< 300 ms bei 10 000 Buchungen, Ziel für Phase 2-Messung).
- Null Datenverlust über alle Updates (Migrationstests).
- Integritätsprüfung meldet dauerhaft 0 Abweichungen.
