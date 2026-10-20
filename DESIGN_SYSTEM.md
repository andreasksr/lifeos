# DESIGN_SYSTEM – CONTINUUM

Code: `core/designsystem/` · Tokens: `theme/Continuum.kt` · Komponenten: `component/`

## 1. Idee
Eine einzige, feine **Lichtlinie** zieht sich durch die gesamte App. Sie ist kein Schmuck, sondern
ein Informationsträger. Ihre Bedeutung ergibt sich aus dem Kontext:

| Kontext | Die Linie zeigt | `LineMeaning` |
|---|---|---|
| Zeitleiste | Verlauf der Zeit, Tage als Knoten | `TIME` |
| Geldfluss | Zufluss (Cyan) / Abfluss (Violett) | `INFLOW` / `OUTFLOW` |
| Budget, Projekt, Lernstand | Fortschritt / Auslastung, > 100 % endet in Flare | `PROGRESS` |
| Navigation | Wo ich mich im System befinde | — (LifeNavigation) |
| Verknüpfungen (Phase 4) | Abhängigkeiten zwischen Bereichen | `LINKED`-Knoten |

## 2. Abgrenzung
Kein Material-Dashboard: **keine gefüllten bunten Karten, keine Schatten, keine FABs mit Icons,
keine Pastellflächen.** Struktur entsteht aus Haarlinien, Abständen, Typografie und Licht.
Material3 wird nur technisch genutzt (DatePicker, Textauswahl) und über `MaterialBridge` eingefärbt.

## 3. Tokens
### Farben
| Token | Wert | Verwendung |
|---|---|---|
| `Obsidian` | `#05060A` | Hintergrund |
| `ObsidianRaised` / `High` | `#0A0C12` / `#11141C` | Panel-Füllung (transparent) |
| `Hairline` / `Strong` | Eis 14 % / 27 % | Konturen, Spuren |
| `Ice` | `#EAF2FF` | Primärtext (≈ 18:1 Kontrast) |
| `IceMuted` | `#A3AFC2` | Sekundärtext (≈ 8:1) |
| `IceFaint` | `#6F7A8E` | Tertiär, nur Nebeninfo (≈ 4.6:1) |
| `Cyan` | `#52E5F2` | Zufluss, aktiv, Fokus |
| `Violet` | `#9D7CFF` | Abfluss, Struktur |
| `Flare` | `#FF7AB6` | ausschließlich Überschreitung/Fehler – immer mit Text |
| `Signature` | Cyan → Violett | Lichtlinie, Primäraktion |

Bedeutung wird **nie nur über Farbe** transportiert: Beträge tragen Vorzeichen (`+`, `−`, `⇄`),
Knoten unterscheiden sich in der Form, Überschreitungen haben Text.

### Typografie
| Stil | Einsatz |
|---|---|
| `display` (Light 34) | Seitentitel |
| `title` (20) | Abschnittstitel |
| `body` / `bodyMuted` (15) | Fließtext |
| `label` (11, Versalien, +1.6 Laufweite) | Labels, Abschnitte, Navigation |
| `figures` / `figuresLarge` (Monospace, `tnum`) | **alle Zahlen** – fluchten exakt |

Phase 2: eigene Schriften einbinden (Space Grotesk + JetBrains Mono, OFL) – bis dahin Systemschriften.

### Abstände
4-dp-Raster: `xs 4 · s 8 · m 12 · l 16 · xl 24 · xxl 32 · gutter 20`. Touch-Ziele ≥ 48 dp.
`timelineRail 28 dp` = Spalte, in der die Lichtlinie vertikal verläuft.

### Bewegung (`ContinuumMotion`)
| Token | Dauer | Einsatz |
|---|---|---|
| `quick` | 160 ms | Farbwechsel, Zustände |
| `standard` | 320 ms | Navigation, Knoten-Glühen |
| `emphasis` | 560 ms | Fortschrittslinien füllen sich |
| `pulse` | 4200 ms | wandernder Lichtpuls (nur ausgewählte Linien) |

Regeln: `enabled = false`, wenn Nutzer es abschaltet (**System → Bewegung**) **oder** Android
„Animationen entfernen" aktiv ist. Dann sind alle Dauern 0 und Endlos-Animationen werden nicht
gestartet. Gezeichnet wird im Draw-Pass (`Canvas`/`drawBehind`) – keine Recomposition pro Frame.

## 4. Komponenten
| Komponente | Zweck | Wichtigste Parameter |
|---|---|---|
| `LifeLine` | Die Lichtlinie: voll, Fortschritt, Überschreitung, Puls | `progress`, `meaning`, `pulse`, `progressDescription` |
| `LifeNode` | Knoten auf der Linie | `shape` (DIAMOND = Buchung/Ereignis, RING = Struktur/Tag, DOT, LINKED = Verknüpfung), `tone`, `active` |
| `LifePanel` | Kontur-Container mit abgeschrägter Ecke und Lichtsegment | `title`, `trailing` |
| `LifeMetric` | Kennzahl mit Label, Wert, optional Linie | `label`, `value`, `tone`, `size`, `progress` |
| `LifeTimelineRow` / `LifeTimelineHeader` | Vertikale Zeitleiste in LazyColumns – eine durchgehende Linie über alle Zeilen | `segment`, `nodeShape`, `nodeTone` |
| `LifeNavigation` | Navigation als Linie mit Knoten, gleitender Lichtpunkt | `items`, `selectedKey` |
| `LifeButton` | PRIMARY (Signatur-Kontur), GHOST, DANGER | `style`, `enabled` |
| `LifeTextField` | Grundlinie, die bei Fokus zur Lichtlinie wird | `label`, `error` |
| `LifeSegmented` | Segmentauswahl mit Licht-Unterstrich | `options`, `selected` |
| `LifeChoiceChip` | Auswahl (Kategorie, Konto) | `selected` |
| `LifeTopBar` | Overline + Display-Titel + Aktionen | `overline`, `onBack` |

## 5. Interaktionslogik (konsistent in allen Modulen)
- **Hauptbereiche** über `LifeNavigation` unten; Unterseiten blenden sie aus und haben „←".
- **Liste → Antippen → Bearbeiten.** Jede Zeile in einer Zeitleiste öffnet ihr Objekt.
- **Primäraktion** unten rechts bzw. unten in Formularen; destruktive Aktionen immer zweistufig.
- **Fehler** erscheinen am Feld (Flare-Linie + Text) und werden für Screenreader gesetzt.
- **Leere Zustände** erklären in einem Satz, wie es weitergeht – keine Illustrationen, keine Fake-Daten.

## 6. Barrierefreiheit
Kontraste ≥ 4.5:1 für Text, Rollen (`Button`, `Tab`, `RadioButton`) und `selected`-Zustände gesetzt,
Fortschrittslinien mit `progressBarRangeInfo` + Beschreibung, Metriken als zusammengefasste
`contentDescription`, Überschriften mit `heading()`.

## 7. Status
Komponenten sind implementiert, aber **noch nicht kompiliert oder auf einem Gerät gesehen**
(siehe CHANGELOG). Screenshot-Tests (Paparazzi/Roborazzi) sind für Phase 1b geplant.
