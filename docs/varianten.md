```
./gradlew run --args="render compose --style /Users/stefan/sources/terrainvis/docs/style-variante-a.json --bbox 2592000,1213000,2645000,1262000 --output-mode tile-files --output /Users/stefan/tmp/dsm_variante_a --tile-size 1000 --with-alpha" -Dorg.gradle.jvmargs=-"Xmx4g"
```

```
conda env list
```

```
conda activate gdal
```

```
gdalbuildvrt basis.vrt *.tif
```

```
gdal_translate basis.vrt basis.tif -of COG -co NUM_THREADS=ALL_CPUS -co COMPRESS=LZW -co BIGTIFF=YES -co OVERVIEWS=IGNORE_EXISTING -co RESAMPLING=AVERAGE

gdal_translate basis.vrt basis-deflate-2.tif -of COG -co NUM_THREADS=ALL_CPUS -co COMPRESS=DEFLATE -co PREDICTOR=2 -co BIGTIFF=YES -co OVERVIEWS=IGNORE_EXISTING -co RESAMPLING=AVERAGE
```


--------


```
./gradlew run --args="render compose --style /Users/stefan/sources/dsm-occlusion/docs/style-variante-b.json --bbox 2592000,1213000,2645000,1262000 --output-mode tile-files --output /Users/stefan/tmp/dsm_variante_b --tile-size 1000 --with-alpha" -Dorg.gradle.jvmargs=-"Xmx4g"
```


```
gdalbuildvrt basis.vrt *.tif
```

```
gdal_translate basis.vrt basis.tif -of COG -co NUM_THREADS=ALL_CPUS -co COMPRESS=JPEG -co BIGTIFF=YES -co OVERVIEWS=IGNORE_EXISTING -co RESAMPLING=AVERAGE

gdal_translate basis.vrt basis.tif -of COG -co NUM_THREADS=ALL_CPUS -co COMPRESS=LZW -co BIGTIFF=YES -co OVERVIEWS=IGNORE_EXISTING -co RESAMPLING=AVERAGE

gdal_translate basis.vrt basis-deflate-2.tif -of COG -co NUM_THREADS=ALL_CPUS -co COMPRESS=DEFLATE -co PREDICTOR=2 -co BIGTIFF=YES -co OVERVIEWS=IGNORE_EXISTING -co RESAMPLING=AVERAGE
```

--------
```
./gradlew run --args="render compose --style /Users/stefan/sources/terrainvis/docs/style-variante-c.json --bbox 2592000,1213000,2645000,1262000 --output-mode tile-files --output /Users/stefan/tmp/dsm_variante_c --tile-size 1000 --with-alpha" -Dorg.gradle.jvmargs=-"Xmx4g"

./gradlew run --args="render compose --style /Users/stefan/sources/terrainvis/docs/style-variante-c-2.json --bbox 2592000,1213000,2645000,1262000 --output-mode tile-files --output /Users/stefan/tmp/dsm_variante_c_2 --tile-size 1000 --with-alpha" -Dorg.gradle.jvmargs=-"Xmx4g"
```



---------