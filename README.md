# terrainvis (Java CPU)

Java-17-Implementierung von `terrainvis` für CPU-basierte DSM-Occlusion, RVT-inspirierte Relief-Visualisierungen und generisches Raster-Rendering.

## Eigenschaften

- Neue Root-CLI mit drei Familien: `occlusion`, `rvt` und `render`.
- Neue Root-CLI-Familie `render` für Farbverläufe und Layer-Komposition.
- Liest lokale GeoTIFFs und Remote-COGs per GeoTools/ImageIO-Ext.
- Verarbeitet nur die angefragte BBOX und liest Rasterdaten etappenweise pro Tile.
- Nutzt gepufferte Tiles, schneidet nach der Berechnung wieder auf das Kern-Tile zurück und schreibt nur Kern-Tiles mit Daten.
- Parallele Tile-Verarbeitung über mehrere CPU-Kerne.
- Single-File- und Tile-Output für `float32` und `uint8`.
- N-Band-Outputs für RVT-Produkte wie MSTP und Multi-Hillshade.
- `render compose` färbt Single-Band-Raster ein und kombiniert mehrere Layer als RGB- oder RGBA-GeoTIFF.
- RVT-Produkte: `slope`, `hillshade`, `multi-hillshade`, `slrm`, `svf`, `asvf`, `positive-openness`, `negative-openness`, `sky-illumination`, `local-dominance`, `msrm`, `mstp`, `vat`.
- VAT als First-Class-Produkt mit Presets `general`, `flat` und `combined`.
- Strukturierte Laufzeit-Logs mit optionalem `--verbose`.

## Voraussetzungen

- Java 17
- Netzwerkzugriff für Remote-COGs
- Eingaberasters im projizierten CRS mit Meter-Einheiten
- Nordorientierter GeoTIFF ohne Rotation/Shear
- Single-Band-DSM
- Für `render`: exakt ausgerichtete Single-Band-Raster; kein Reproject/Resample

## Build

```bash
./gradlew test
./gradlew run --args="--help"
```

## Nutzung

Hilfe:

```bash
./gradlew run --args="--help"
```

Occlusion exact:

```bash
./gradlew run --args="\
  occlusion exact \
  --input https://example.org/dsm.tif \
  --bbox 2590000,1210000,2592000,1212000 \
  --output-mode tile-files \
  --output output_tiles \
  --tile-size 512 \
  --buffer-m 25 \
  -r 128 \
  --threads 8"
```

Occlusion horizon:

```bash
./gradlew run --args="\
  occlusion horizon \
  --input https://example.org/dsm.tif \
  --bbox 2590000,1210000,2592000,1212000 \
  --output-mode single-file \
  --output horizon.tif \
  --tile-size 512 \
  --horizon-directions 32 \
  --horizon-radius-m 50"
```

VAT combined:

```bash
./gradlew run --args="\
  rvt vat \
  --input https://example.org/dsm.tif \
  --bbox 2590000,1210000,2592000,1212000 \
  --terrain combined \
  --output-mode single-file \
  --output vat_combined.tif \
  --tile-size 512"
```

## Render compose Beispiele

`render compose` unterstützt zwei Ramp-Modi pro Layer:
- Legacy: `valueMin`/`valueMax` plus `colorFrom`/`colorTo`
- Mehrere Stops: `stops` mit absoluten Rasterwerten, Farbe und optional `alpha` in `0..1`

Schwarz-Weiss-Ramp für ein Occlusion-Raster mit `0 -> schwarz` und `1 -> weiss`:

```bash
cat > style-bw.json <<'EOF'
{
  "layers": [
    {
      "input": "/path/to/occlusion.tif",
      "valueMin": 0.0,
      "valueMax": 1.0,
      "colorFrom": "#000000",
      "colorTo": "#FFFFFF",
      "blendMode": "normal",
      "opacity": 1.0
    }
  ]
}
EOF

./gradlew run --args="\
  render compose \
  --style style-bw.json \
  --bbox 2592000,1213000,2645000,1262000 \
  --output-mode single-file \
  --output occlusion_bw.tif \
  --tile-size 1024"
```

Grüner Verlauf für ein einzelnes Float-Raster von hellgrün nach dunkelgrün:

```bash
cat > style-green.json <<'EOF'
{
  "layers": [
    {
      "input": "/path/to/forest_index.tif",
      "valueMin": 0.0,
      "valueMax": 1.0,
      "colorFrom": "#CBEA9A",
      "colorTo": "#1F6B2A",
      "blendMode": "normal",
      "opacity": 1.0
    }
  ]
}
EOF

./gradlew run --args="\
  render compose \
  --style style-green.json \
  --bbox 2592000,1213000,2645000,1262000 \
  --output-mode single-file \
  --output forest_green.tif \
  --tile-size 1024"
```

QML-artiger Verlauf mit mehreren Stops und Alpha pro Stop. QGIS-Alpha `255` wird dabei zu `1.0`, `0` zu `0.0`:

```bash
cat > style-vegetation-qml.json <<'EOF'
{
  "layers": [
    {
      "input": "/path/to/vegetation.tif",
      "stops": [
        { "value": 0.0, "color": "#FCFCFC", "alpha": 0.0 },
        { "value": 0.5, "color": "#E5F5E0", "alpha": 1.0 },
        { "value": 36.0, "color": "#00441B", "alpha": 1.0 }
      ],
      "blendMode": "normal",
      "opacity": 1.0
    }
  ]
}
EOF

./gradlew run --args="\
  render compose \
  --style style-vegetation-qml.json \
  --bbox 2592000,1213000,2645000,1262000 \
  --output-mode single-file \
  --output vegetation_qml_rgba.tif \
  --tile-size 1024 \
  --with-alpha"
```

Gebäude-Ramp aus QGIS ebenfalls als Mehr-Stop-Style:

```bash
cat > style-buildings-qml.json <<'EOF'
{
  "layers": [
    {
      "input": "/path/to/buildings.tif",
      "stops": [
        { "value": 0.0, "color": "#FFFFFF", "alpha": 0.0 },
        { "value": 0.5, "color": "#FEE0D2", "alpha": 1.0 },
        { "value": 15.0, "color": "#CE6863", "alpha": 1.0 }
      ],
      "blendMode": "normal",
      "opacity": 1.0
    }
  ]
}
EOF

./gradlew run --args="\
  render compose \
  --style style-buildings-qml.json \
  --bbox 2592000,1213000,2645000,1262000 \
  --output-mode single-file \
  --output buildings_qml_rgba.tif \
  --tile-size 1024 \
  --with-alpha"
```

Zwei Layer mit `multiply`, um ein farbiges Basisrelief mit einem zweiten Layer zu modulieren:

```bash
cat > style-multiply.json <<'EOF'
{
  "layers": [
    {
      "input": "/path/to/base_relief.tif",
      "valueMin": 0.0,
      "valueMax": 1.0,
      "colorFrom": "#F7E6C4",
      "colorTo": "#8B6F47",
      "blendMode": "normal",
      "opacity": 1.0
    },
    {
      "input": "/path/to/occlusion.tif",
      "valueMin": 0.0,
      "valueMax": 1.0,
      "colorFrom": "#FFFFFF",
      "colorTo": "#404040",
      "blendMode": "multiply",
      "opacity": 0.7
    }
  ]
}
EOF

./gradlew run --args="\
  render compose \
  --style style-multiply.json \
  --bbox 2592000,1213000,2645000,1262000 \
  --output-mode single-file \
  --output relief_multiply.tif \
  --tile-size 1024"
```

Separates Alpha-Raster und `--with-alpha`, damit die Ausgabe als RGBA geschrieben wird:

```bash
cat > style-rgba.json <<'EOF'
{
  "layers": [
    {
      "input": "/path/to/thematic_values.tif",
      "alphaInput": "/path/to/transparency.tif",
      "valueMin": 0.0,
      "valueMax": 1.0,
      "colorFrom": "#FFF2B2",
      "colorTo": "#D95F0E",
      "blendMode": "normal",
      "opacity": 1.0
    }
  ]
}
EOF

./gradlew run --args="\
  render compose \
  --style style-rgba.json \
  --bbox 2592000,1213000,2645000,1262000 \
  --output-mode single-file \
  --output thematic_rgba.tif \
  --tile-size 1024 \
  --with-alpha"
```

Occlusion als Basis und Vegetation darüber, wobei das feinere Vegetationsraster zur Laufzeit auf das Grid des ersten Layers resampled wird:

```bash
cat > style-occlusion-vegetation.json <<'EOF'
{
  "layers": [
    {
      "input": "/data/ch.swisstopo.lidar_2023.dsm_occlusion.tif",
      "valueMin": 0.0,
      "valueMax": 1.0,
      "colorFrom": "#000000",
      "colorTo": "#FFFFFF",
      "blendMode": "normal",
      "opacity": 1.0
    },
    {
      "input": "/data/ch.swisstopo.lidar_2023.ndsm_vegetation.tif",
      "valueMin": 0.0,
      "valueMax": 30.0,
      "colorFrom": "#CFE8B4",
      "colorTo": "#1F5A2A",
      "blendMode": "normal",
      "opacity": 0.6
    }
  ]
}
EOF

./gradlew run --args="\
  render compose \
  --style style-occlusion-vegetation.json \
  --bbox 2610260,1227813,2611914,1228790 \
  --output-mode tile-files \
  --output steroids_occlusion_vegetation_rgba \
  --tile-size 1000 \
  --with-alpha \
  --verbose"
```

Das zweite Raster darf dabei eine andere Auflösung und einen kleineren oder grösseren Extent haben, solange das CRS gleich ist. `render compose` richtet es automatisch zur Laufzeit am Grid des ersten Layers aus.

Optionale Grid-Angleichung für ein feineres Single-Band-Raster, wenn ein materialisierter Zwischenoutput gewünscht ist:

```bash
./gradlew run --args="\
  render align-grid \
  --input /path/to/ndsm_vegetation_025m.tif \
  --reference /path/to/dsm_occlusion_05m.tif \
  --output vegetation_aligned_05m.tif \
  --tile-size 1024"
```

MSTP als RGB-GeoTIFF:

```bash
./gradlew run --args="\
  rvt mstp \
  --input https://example.org/dsm.tif \
  --bbox 2590000,1210000,2592000,1212000 \
  --output-mode single-file \
  --output mstp_rgb.tif \
  --output-data-type uint8"
```

## CLI-Struktur

- Gemeinsame Optionen aller Rechenkommandos:
  - `--bbox`
  - `--output-mode single-file|tile-files`
  - `--output`
  - `--tile-size`
  - `--threads`
  - `--start-tile`
  - `--info`
  - `--verbose`
- Occlusion:
  - `--input`
  - `--output-data-type float32|uint8`
  - `--buffer-px` oder `--buffer-m`
  - `occlusion exact` für Raytracing
  - `occlusion horizon` für Horizont-Approximation
- RVT:
  - `--input`
  - `--output-data-type float32|uint8`
  - `--buffer-px` oder `--buffer-m`
  - `rvt slope|hillshade|multi-hillshade|slrm|svf|asvf|positive-openness|negative-openness|sky-illumination|local-dominance|msrm|mstp|vat`
- Render:
  - `render compose --style <style.json> [--with-alpha]`
  - `render align-grid --input <src.tif> --reference <ref.tif> --output <aligned.tif>`

## Verhalten und Annahmen

- Remote-`http(s)` ist auf range-lesbare COG-/GeoTIFF-Quellen ausgelegt.
- Wenn weder `--buffer-px` noch `--buffer-m` gesetzt ist, bleibt für Occlusion der bisherige Default `tileSize / 3` aktiv; RVT-Produkte ziehen zusätzlich ihren eigenen Mindest-Buffer aus dem Produktbedarf.
- Tiles ohne Daten im Kernbereich werden nicht berechnet.
- `occlusion horizon` unterstützt nur `maxBounces=0`.
- `vat` defaultet auf `combined`.
- `uint8` für RVT nutzt produktspezifische Default-Stretches nach RVT-Vorbild; `float32` schreibt rohe Produktwerte.
- `render compose` arbeitet ohne Buffer, liest nur Kern-Tiles und schreibt immer `uint8` als RGB oder RGBA.
- `render compose` unterstützt v1 nur lineare 2-Farb-Ramps, `normal` und `multiply`, sowie optional separate Alpha-Raster.
- `render compose` akzeptiert Layer mit gleichem CRS auch dann, wenn Auflösung oder Extent abweichen; sie werden zur Laufzeit per GeoTools bilinear auf das Grid des ersten Layers resampled. Bereiche ohne Überlappung liefern NoData bzw. Transparenz.
- `render align-grid` bleibt als optionales Hilfswerkzeug für materialisierte Vorverarbeitung erhalten.

## Performance-Hinweise

- CPU-only-Läufe mit grossen Buffern oder vielen Richtungen können langsam sein.
- Für `occlusion exact` sind meist kleinere Werte sinnvoll, zum Beispiel `-r 64..256` und `--tile-size 256..1024`.
- Für grosse BBOXen sollte der Tiled-Modus bevorzugt werden.
- Für schnelle Näherungsläufe eignet sich `occlusion horizon`.
- Für RVT-Produkte mit grossen Nachbarschaften (`svf`, `sky-illumination`, `msrm`, `mstp`) sollte die Tile-Grösse nicht zu klein gewählt werden.

## Tests

Die Test-Suite deckt ab:

- Root-CLI und neue Subcommand-Hilfe
- BBOX-Snapping und Tile-Planung
- Deterministisches Ray-Sampling
- Lichtmodell und BVH-Hits
- NoData-/Skip-Logik
- Remote-Reads per HTTP-Range
- End-to-End-Läufe im Tiled- und Single-File-Modus
- RVT-End-to-End-Läufe für VAT, MSTP, Multi-Hillshade und Remote-SVF
- Render-End-to-End-Läufe für RGB/RGBA, Transparenz-Skip, Start-Tile und Grid-Validierung

```bash
./gradlew test
```
