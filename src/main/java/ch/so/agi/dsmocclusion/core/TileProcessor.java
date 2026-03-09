package ch.so.agi.dsmocclusion.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import ch.so.agi.dsmocclusion.config.LightingParameters;
import ch.so.agi.dsmocclusion.raster.RasterMetadata;
import ch.so.agi.dsmocclusion.tiling.PixelWindow;
import ch.so.agi.dsmocclusion.tiling.TileRequest;
import ch.so.agi.dsmocclusion.tiling.TileWindow;
import ch.so.agi.dsmocclusion.util.NoData;

public final class TileProcessor {
    private static final double EPSILON = 1.0e-6;

    private final RaySampler raySampler = new RaySampler();

    public TileComputationResult process(
            TileRequest request,
            RasterMetadata metadata,
            float[] bufferedValues,
            LightingParameters lightingParameters,
            double exaggeration,
            int threads) {
        TileWindow window = request.window();
        PixelWindow core = window.coreWindow();
        PixelWindow buffered = window.bufferedWindow();
        float[] result = new float[core.width() * core.height()];

        boolean hasCoreData = false;
        for (int y = 0; y < core.height(); y++) {
            for (int x = 0; x < core.width(); x++) {
                int bufferedIndex = index(window.coreOffsetX() + x, window.coreOffsetY() + y, buffered.width());
                float value = bufferedValues[bufferedIndex];
                if (NoData.isValid(value, metadata.noDataValue())) {
                    hasCoreData = true;
                    break;
                }
            }
            if (hasCoreData) {
                break;
            }
        }

        if (!hasCoreData) {
            fill(result, metadata.noDataValue());
            return new TileComputationResult(request, result, true, 0);
        }

        CpuBvh bvh = new CpuBvh(
                bufferedValues,
                buffered.width(),
                buffered.height(),
                metadata.resolutionX(),
                metadata.resolutionY(),
                exaggeration,
                metadata.noDataValue());

        LightingModel.PreparedLighting preparedLighting = LightingModel.prepare(lightingParameters);
        int workerCount = Math.max(1, Math.min(threads, core.height()));
        int validPixelCount = workerCount == 1
                ? traceRows(0, core.height(), request, metadata, bufferedValues, lightingParameters, exaggeration, result, bvh, preparedLighting)
                : traceRowsInParallel(workerCount, request, metadata, bufferedValues, lightingParameters, exaggeration, result, bvh, preparedLighting);

        return new TileComputationResult(request, result, false, validPixelCount);
    }

    private int traceRowsInParallel(
            int workerCount,
            TileRequest request,
            RasterMetadata metadata,
            float[] bufferedValues,
            LightingParameters lightingParameters,
            double exaggeration,
            float[] result,
            CpuBvh bvh,
            LightingModel.PreparedLighting preparedLighting) {
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        List<Future<Integer>> futures = new ArrayList<>();
        int chunkHeight = Math.max(1, (request.window().coreWindow().height() + workerCount - 1) / workerCount);
        try {
            for (int startY = 0; startY < request.window().coreWindow().height(); startY += chunkHeight) {
                int fromY = startY;
                int toY = Math.min(request.window().coreWindow().height(), fromY + chunkHeight);
                futures.add(executor.submit(() -> traceRows(
                        fromY,
                        toY,
                        request,
                        metadata,
                        bufferedValues,
                        lightingParameters,
                        exaggeration,
                        result,
                        bvh,
                        preparedLighting)));
            }

            int validPixels = 0;
            for (Future<Integer> future : futures) {
                validPixels += future.get();
            }
            return validPixels;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Tile processing interrupted", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException("Tile processing failed", cause);
        } finally {
            executor.shutdownNow();
        }
    }

    private int traceRows(
            int fromY,
            int toY,
            TileRequest request,
            RasterMetadata metadata,
            float[] bufferedValues,
            LightingParameters lightingParameters,
            double exaggeration,
            float[] result,
            CpuBvh bvh,
            LightingModel.PreparedLighting preparedLighting) {
        TileWindow window = request.window();
        PixelWindow core = window.coreWindow();
        PixelWindow buffered = window.bufferedWindow();
        TileTracingContext tracingContext = new TileTracingContext(bvh.newTraversalContext(), preparedLighting);
        int validPixels = 0;

        for (int localY = fromY; localY < toY; localY++) {
            for (int localX = 0; localX < core.width(); localX++) {
                int sourceX = window.coreOffsetX() + localX;
                int sourceY = window.coreOffsetY() + localY;
                int bufferedIndex = index(sourceX, sourceY, buffered.width());
                float value = bufferedValues[bufferedIndex];
                int coreIndex = index(localX, localY, core.width());
                if (NoData.isNoData(value, metadata.noDataValue())) {
                    result[coreIndex] = (float) metadata.noDataValue();
                    continue;
                }

                validPixels++;
                int originColumnIndex = bvh.columnIndexForPixel(sourceX, sourceY);
                double traced = tracePixel(
                        request.id(),
                        coreIndex,
                        sourceX * metadata.resolutionX(),
                        sourceY * metadata.resolutionY(),
                        value * exaggeration,
                        originColumnIndex,
                        bvh,
                        tracingContext,
                        lightingParameters);
                result[coreIndex] = (float) traced;
            }
        }
        return validPixels;
    }

    private double tracePixel(
            int tileId,
            int pixelIndex,
            double originX,
            double originY,
            double originZ,
            int originColumnIndex,
            CpuBvh bvh,
            TileTracingContext tracingContext,
            LightingParameters lightingParameters) {
        double total = 0.0;
        if (lightingParameters.maxBounces() == 0) {
            for (int rayIndex = 0; rayIndex < lightingParameters.raysPerPixel(); rayIndex++) {
                Vector3 direction = raySampler.primaryDirection(tileId, pixelIndex, rayIndex, lightingParameters);
                total += tracePrimaryRay(
                        originX,
                        originY,
                        originZ,
                        originColumnIndex,
                        direction,
                        bvh,
                        tracingContext);
            }
        } else {
            for (int rayIndex = 0; rayIndex < lightingParameters.raysPerPixel(); rayIndex++) {
                Vector3 direction = raySampler.primaryDirection(tileId, pixelIndex, rayIndex, lightingParameters);
                total += traceRay(tileId, pixelIndex, rayIndex, originX, originY, originZ, originColumnIndex, direction, bvh, tracingContext, lightingParameters);
            }
        }
        double average = total / lightingParameters.raysPerPixel();
        return average + lightingParameters.ambientPower();
    }

    private double tracePrimaryRay(
            double originX,
            double originY,
            double originZ,
            int originColumnIndex,
            Vector3 direction,
            CpuBvh bvh,
            TileTracingContext tracingContext) {
        double currentOriginX = originX + direction.x() * EPSILON;
        double currentOriginY = originY + direction.y() * EPSILON;
        double currentOriginZ = originZ + direction.z() * EPSILON;
        boolean occluded = bvh.hasAnyHit(
                currentOriginX,
                currentOriginY,
                currentOriginZ,
                direction.x(),
                direction.y(),
                direction.z(),
                originColumnIndex,
                tracingContext.traversalContext());
        if (occluded) {
            return 0.0;
        }
        return LightingModel.visibleContribution(direction, tracingContext.preparedLighting(), 1.0);
    }

    private double traceRay(
            int tileId,
            int pixelIndex,
            int rayIndex,
            double originX,
            double originY,
            double originZ,
            int originColumnIndex,
            Vector3 direction,
            CpuBvh bvh,
            TileTracingContext tracingContext,
            LightingParameters lightingParameters) {
        Vector3 currentDirection = direction;
        double currentOriginX = originX + currentDirection.x() * EPSILON;
        double currentOriginY = originY + currentDirection.y() * EPSILON;
        double currentOriginZ = originZ + currentDirection.z() * EPSILON;
        double radianceFactor = 1.0;
        int currentOriginColumn = originColumnIndex;

        for (int bounce = 0; bounce <= lightingParameters.maxBounces(); bounce++) {
            Hit hit = bvh.findNearestHit(
                    currentOriginX,
                    currentOriginY,
                    currentOriginZ,
                    currentDirection.x(),
                    currentDirection.y(),
                    currentDirection.z(),
                    currentOriginColumn,
                    tracingContext.traversalContext());
            if (hit == null) {
                return LightingModel.visibleContribution(currentDirection, tracingContext.preparedLighting(), radianceFactor);
            }
            if (bounce == lightingParameters.maxBounces()) {
                return 0.0;
            }
            radianceFactor *= lightingParameters.materialReflectance();
            currentDirection = raySampler.bounceDirection(tileId, pixelIndex, rayIndex, bounce, hit.normal());
            currentOriginX = hit.point().x() + currentDirection.x() * EPSILON;
            currentOriginY = hit.point().y() + currentDirection.y() * EPSILON;
            currentOriginZ = hit.point().z() + currentDirection.z() * EPSILON;
            currentOriginColumn = -1;
        }
        return 0.0;
    }

    private void fill(float[] values, double fillValue) {
        float value = (float) fillValue;
        for (int i = 0; i < values.length; i++) {
            values[i] = value;
        }
    }

    private int index(int x, int y, int width) {
        return y * width + x;
    }

    private record TileTracingContext(CpuBvh.TraversalContext traversalContext, LightingModel.PreparedLighting preparedLighting) {
    }
}
