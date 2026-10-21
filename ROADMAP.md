# ROADMAP – LifeOS

Status: ✅ fertig und geprüft · 🟡 fertig, Prüfung eingeschränkt (siehe Hinweis) · ⬜ offen

> **Stand 2026-10-21 · Version 0.5.0** – alle fünf Prioritäten des Next-Generation-Auftrags sind als
> nutzbare Kernversionen umgesetzt. Nachweise: `CHANGELOG.md`, `TEST_PLAN.md`.

## Versionen dieses Updates
| Version | Inhalt |
|---|---|
| 0.1.0 | Ausgangsstand (Haushaltskasse-Kern) – gesichert in Branch `backup/v0.1.0` |
| 0.2.0 | P1 + P2: CONTINUUM v2, fünf Bereiche, Home-Kommandozentrale, Haushaltskasse vollständig |
| 0.3.0 | P3: Aufgaben, Kalender, Erinnerungen, Projekte mit Kostenverknüpfung |
| 0.4.0 | P4: Fahrzeuge, Inventar, Dokumententresor, Industriemeister |
| 0.5.0 | P5: Suche, Datensicherung, App-Sperre, Widget, LifeMap |

## P1 – Design & Fundament ✅
- ✅ Bestandsaufnahme (`INVENTORY.md`), Sicherungsstand `backup/v0.1.0`, Paketkennung unverändert
- ✅ CONTINUUM v2: Palette laut Auftrag, Space Grotesk / Inter / JetBrains Mono, eigene Glyphen
- ✅ Komponenten: LifeLine, LifeNode, LifePanel, LifeMetric, LifeTimeline, LifeNavigation, LifeProgress, LifeChart, LifeAction, LifeOverlay, Kalender, Zeitwahl
- ✅ Bewegung: Einstiegs-Linie, Lichtimpuls beim Speichern, Diagramm-Morph, Haptik, alles abschaltbar
- ✅ Fünf Hauptbereiche + zentrale Schnellerfassung; nur implementierte Ziele sichtbar
- ✅ Home: Heute, Aufmerksamkeit, Finanzen, Planung, Projekte, Weiterbildung; ein-/ausblenden, sortieren, Dichte Minimal/Standard/Advanced

## P2 – Finanzen ✅
- ✅ Einnahme, Ausgabe, Umbuchung, Erstattung, Korrektur; bearbeiten, löschen mit Rückgängig, Suche/Filter
- ✅ Schnellerfassung mit großer Betragsanzeige, eigener Zifferntastatur, zuletzt genutzte Kategorien
- ✅ Gesamt- und Kategorienbudgets mit sichtbarer Überschreitung (nicht bei 100 % gekappt)
- ✅ Wiederkehrende Zahlungen (monatlich/vierteljährlich/jährlich …) – nie automatisch gebucht
- ✅ Analysen: 6-Monats-Verlauf, Kategorieverteilung, Fixkostenquote, Budgetentwicklung

## P3 – Alltag & Projekte ✅
- ✅ Aufgaben (Priorität, Fälligkeit, Wiederholung, Projekt), Kalender Tag/Woche/Monat, Termine mit Wiederholung
- ✅ Echte Benachrichtigungen (exakte Alarme, Neustart, Zeitumstellung, „Erledigt"/„Später"); erledigte Aufgaben erinnern nicht mehr
- ✅ Projekte: Status, Zeitraum, Fortschritt nach Aufgaben/Meilensteinen/manuell, Projekt-LifeLine
- ✅ Bestehende Buchung einem Projekt zuordnen – ohne Kopie, Änderungen überall sichtbar

## P4 – Persönliche Verwaltung ✅
- ✅ Fahrzeuge: Stammdaten, Historie als LifeLine, geplante Termine (HU), Kosten aus verknüpften Buchungen, Dokumente
- ✅ Inventar: Kauf, Seriennummer, Garantie mit Hinweis, Belege, Dokumente
- ✅ Dokumente: PDF/Bild über Android-Dateiauswahl, verschlüsselt, keine Doppelablage, Vorschau, Zuordnung
- ✅ Industriemeister: Start/Stopp + Nachtrag, Statistik je Fach/Woche/Monat ohne Doppelzählung, Fächer/Themen, Prüfungen mit Zielzeitraum (nicht bestätigt) bzw. bestätigtem Termin, Unterrichtstermine aus der Planung
- ⬜ OCR: Feld `ocr_text` vorbereitet, **keine** Texterkennung eingebaut

## P5 – Premium-Funktionen
- ✅ Zentrale Suche (Titel, Händler, Notiz, Betrag, Dateiname, Aufgabenbeschreibung) mit Sprung zum Eintrag
- ✅ Verschlüsselte Datensicherung + Wiederherstellung auf anderem Gerät (Emulator-Test mit fremden Schlüsseln)
- 🟡 App-Sperre (BiometricPrompt, Gerätesperre) – im Emulator nicht automatisiert geprüft (keine Biometrie)
- 🟡 Widget (nächster Termin, fällige Aufgaben, Schnellerfassung) – gebaut, nicht auf einem Startbildschirm automatisiert geprüft
- ✅ LifeMap: ausgewähltes Objekt + direkte Verbindungen (Links und feste Beziehungen), weiterwandern

## Danach (nicht Teil dieses Updates)
| Thema | Begründung |
|---|---|
| OCR on-device (ML Kit) für Dokumente | erst wenn zuverlässig offline lauffähig |
| Verträge mit Kündigungsfristen (`contract`) | heute über wiederkehrende Zahlungen abgebildet |
| Konten/Kategorien umbenennen & archivieren im UI | Datenmodell vorbereitet |
| Erinnerungen für Fahrzeug-Termine und Prüfungen als Benachrichtigung | heute: Hinweis auf Home |
| Lernaufgaben direkt im Fach (heute: Aufgaben in der Planung) | |
| Mehrfachaufteilung im Buchungseditor (Splits) | Domäne vorhanden |
| Roborazzi-Screenshot-Tests, Tablet-Layouts | heute: echte Emulator-Screenshots (Pixel 6) |
| Release-Signatur mit eigenem Schlüssel, Play-Store | derzeit fester Entwicklungsschlüssel |
