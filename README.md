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

## Verhalten und Annahmen

- Remote-`http(s)` ist auf range-lesbare COG-/GeoTIFF-Quellen ausgelegt.
- Wenn weder `--buffer-px` noch `--buffer-m` gesetzt ist, bleibt für Occlusion der bisherige Default `tileSize / 3` aktiv; RVT-Produkte ziehen zusätzlich ihren eigenen Mindest-Buffer aus dem Produktbedarf.
- Tiles ohne Daten im Kernbereich werden nicht berechnet.
- `occlusion horizon` unterstützt nur `maxBounces=0`.
- `vat` defaultet auf `combined`.
- `uint8` für RVT nutzt produktspezifische Default-Stretches nach RVT-Vorbild; `float32` schreibt rohe Produktwerte.
- `render compose` arbeitet ohne Buffer, liest nur Kern-Tiles und schreibt immer `uint8` als RGB oder RGBA.
- `render compose` unterstützt v1 nur lineare 2-Farb-Ramps, `normal` und `multiply`, sowie optional separate Alpha-Raster.

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
