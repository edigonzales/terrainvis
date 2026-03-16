# Developer Notes

Package-Root: `ch.so.agi.terrainvis`

## Architektur

Das Projekt ist in diese Pakete gegliedert:

- `cli`: Picocli-basierte Kommandozeile
- `config`: gemeinsame Laufzeitkonfiguration sowie Occlusion-spezifische Konfiguration
- `render`: Style-Parsing, Farbverläufe und Layer-Komposition für generische Single-Band-Raster
- `raster`: GeoTools/ImageIO-Ext-Zugriff auf lokale GeoTIFFs und Remote-COGs
- `tiling`: BBOX-Snapping, Buffer-Berechnung und Tile-Planung
- `core`: CPU-Raytracing, deterministic sampling, BVH und Tile-Verarbeitung
- `rvt`: RVT-Produkte, Parameter, Defaults und Rendering-Kerne
- `output`: GeoTIFF-Schreiber für Single-Band- und N-Band-Outputs
- `pipeline`: Orchestrierung der parallelen Tile-Verarbeitung für Occlusion und RVT
- `util`: Konsolenlogger und Heartbeat-Scheduler

## Ablauf eines Runs

1. `MainCommand` dispatcht in die Familien `occlusion`, `rvt` oder `render`.
2. `GeoToolsCogRasterSource` liest Metadaten des Eingangsraster und validiert:
   - Single-Band
   - Meter-Einheiten
   - Nordorientierung
   - keine Rotation/kein Shear
3. `TilePlanner` schneidet die BBOX auf den Rasterbereich zu, snappt auf Pixelgrenzen und erzeugt row-major `TileRequest`s.
4. Die passende Pipeline verarbeitet Tiles parallel über ein Fixed-Thread-Pool:
   - `OcclusionPipeline` für `exact` und `horizon`
   - `RvtPipeline` für Relief-Visualisierungen
   - `RenderPipeline` für generische Farbverläufe und Layer-Komposition
5. Für jedes Tile:
   - gepuffertes Fenster lesen
   - nur den Kernbereich auf Daten prüfen
   - bei Daten:
     - `exact`: BVH aus gepufferten Säulen erzeugen und Kernpixel per Raytracing berechnen
     - `horizon`: lokalen DEM-Pyramidenaufbau erzeugen und Kernpixel per Richtungs-Horizontprofil berechnen
     - `rvt`: Produkt- bzw. Kompositionskern auf dem gepufferten DEM ausführen
     - `render`: pro Layer Werte lesen, auf `0..1` stretchen, farblich interpolieren und via `normal`/`multiply` komponieren
   - Ergebnis schreiben

## Logging

- `ConsoleLogger` schreibt serialisierte Plain-Text-Logs mit Zeitstempel und Level-Präfix.
- `INFO` ist für normale Fortschrittsmeldungen gedacht.
- `VERBOSE` wird nur über `--verbose` aktiviert und deckt Detailmeldungen pro Tile sowie Reader-/Writer-Initialisierung ab.
- Die Pipeline startet zusätzlich einen Heartbeat im 30-Sekunden-Intervall, solange Arbeit in Flight ist.

## Raster-I/O

- Remote-Reads verwenden `GeoTiffReader` zusammen mit `CogSourceSPIProvider` und `HttpRangeReader`.
- Das aktuelle Lesen basiert auf einer lazy Coverage und `RenderedImage.getData(Rectangle)`, damit nur die tatsächlich benötigten Bereiche materialisiert werden.
- Jeder Worker nutzt eine eigene Reader-Session; GeoTools-Reader werden nicht zwischen Threads geteilt.
- `render` validiert alle Inputs vorab auf identisches CRS und arbeitet ohne Buffer (`buffer=0`); gleiche Grids werden direkt gelesen, andere Grids je nach Layer-Resampling zur Laufzeit ausgerichtet.

## Geometrie und Tracing

- Jede gültige DSM-Zelle wird als vertikale Säule modelliert:
  - Footprint: halbe Pixelgrösse in X/Y
  - Höhe: `elevation * exaggeration`
  - Unterkante: `z = 0`
- Die BVH wird pro gepuffertem Tile aufgebaut.
- Berechnet werden nur gültige Kernpixel, nicht der gesamte Buffer.
- Die Sampling-Strategie ist deterministisch und an Tile-ID, Pixelindex und Rayindex gebunden.
- Der Horizon-Mode verwendet statt BVH/Rays ein diskretes Richtungsprofil mit mehrstufiger Distanzabtastung und einer bias-kompatiblen Sichtbarkeitsfunktion.

## Einzeldatei-Modus

- `SingleFileAccumulator` hält ein temp-dateibasiertes Float-Raster (`DiskBackedFloatImage`).
- `SingleFileRasterAccumulator` erweitert dieses Prinzip für beliebige Bandzahlen.
- Jedes berechnete Tile schreibt nur seinen Kernbereich in dieses Raster.
- Am Ende wird daraus genau ein GeoTIFF geschrieben.

## RVT-Architektur

- `RvtRunConfig` kombiniert `CommonRunConfig`, Produkt-ID und produktspezifische Parameter.
- `RvtDefaults` kapselt Bandzahl, Byte-Stretching, VAT-Presets und den Mindest-Buffer pro Produkt.
- `RvtRenderer` berechnet RVT-Produkte tileweise und cached gemeinsame Zwischenprodukte wie:
  - `SlopeAspect`
  - SVF-/ASVF-/Openness-Familie
  - VAT-Zwischenstufen
- N-Band-Outputs laufen über `RasterBlock`, `RasterTileResult` und die generischen Writer.

## Render-Architektur

- `RenderStyle` beschreibt einen Layer-Stack aus Single-Band-Rastern, Value-Stretch, Farb-Ramp, Blend-Modus, optionalem Alpha-Raster und optionalem Layer-Resampling.
- `RenderPipeline` öffnet pro eindeutigem Input genau eine `GeoToolsCogRasterSource` und teilt sie thread-sicher über die bestehenden threadlokalen Sessions.
- `RenderComposer` arbeitet rein per Pixel auf dem Kern-Tile:
  - nutzt pro Layer entweder eine Legacy-Zwei-Punkt-Ramp oder eine vorbereitete Mehr-Stop-Ramp
  - interpoliert linear zwischen benachbarten Ramp-Stops und klemmt ausserhalb auf den ersten bzw. letzten Stop
  - berechnet Alpha aus Stop-Alpha bzw. Legacy-Ramp, `opacity`, optionalem Alpha-Raster und NoData
  - komponiert in Layer-Reihenfolge als `normal` oder `multiply`
- `RenderPipeline` nutzt für gridgleiche Layer weiterhin pixelgenaues Window-Reading; Layer mit gleichem CRS, aber anderer Auflösung oder anderem Extent werden pro Layer entweder bilinear, per nearest neighbour oder per interner `max`-Aggregation auf das Grid des ersten Layers ausgerichtet.
- `render` schreibt immer `uint8` als RGB oder RGBA; vollständig transparente Tiles gelten als `skipped`.
- `RenderGridAligner` aggregiert ein feineres Single-Band-Raster per `max` auf das Grid eines gröberen Referenzrasters und schreibt ein `float32`-GeoTIFF.

## Bekannte Grenzen

- Nur projizierte CRS mit Meter-Einheiten
- Keine rotierte/sheared Rastergeometrie
- Keine Multi-Band-Inputs
- `render` v1 unterstützt nur Single-Band-Inputs und separate Single-Band-Alpha-Raster, keine eingebetteten Alpha- oder RGB/RGBA-Inputs
- `render compose` verlangt für Laufzeit-Alignment gleiches CRS; Auflösung und Extent dürfen abweichen, Output-Grid bleibt aber immer das Grid des ersten Layers
- `render compose` verlangt für `resampling=max` zusätzlich einen kompatiblen Grid-Ursprung und einen ganzzahligen Auflösungsfaktor zum ersten Layer
- `render align-grid` verlangt weiterhin identisches CRS, identischen Extent und einen ganzzahligen Auflösungsfaktor zwischen Input und Referenz
- Keine GPU-Implementierung
- Remote-`http(s)` bleibt auf range-fähige COG-/GeoTIFF-Quellen beschränkt
- `exact` splittet Threads zwischen parallelen Tiles und paralleler Zeilenberechnung innerhalb eines Tiles auf
- `horizon` parallelisiert nur über Tiles; die Berechnung innerhalb eines Tiles bleibt einstufig
- `occlusion horizon` approximiert nur den No-Bounce-Fall (`maxBounces=0`)
- Die RVT-Implementierungen orientieren sich fachlich an `RVT_py`, sind aber nicht auf bitgenaue Reproduktion optimiert

## Tests

- Unit-Tests prüfen Tiling, Sampling, Beleuchtung, BVH und Skip-Logik.
- Integrationstests erzeugen ein Test-GeoTIFF, servieren es über einen lokalen HTTP-Range-Server und prüfen Remote-Subset-Reads.
- End-to-End-Tests decken tiled output, `--startTile` und Einzeldatei-Mosaikierung ab.
- Zusätzliche End-to-End-Tests decken Root-CLI, VAT, Multi-Hillshade, MSTP und Remote-SVF ab.
- Zusätzliche Render-Tests decken Style-Validierung, Farb-Ramps, Alpha-/Blend-Logik, Tiled-vs-Single-Mosaik und Remote-Layer ab.

## Nützliche Befehle

```bash
./gradlew compileJava
./gradlew test
./gradlew run --args="--help"
```
