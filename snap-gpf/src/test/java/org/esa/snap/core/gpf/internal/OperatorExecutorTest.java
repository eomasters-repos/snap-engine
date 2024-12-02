/*
 * Copyright (C) 2010 Brockmann Consult GmbH (info@brockmann-consult.de)
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.snap.core.gpf.internal;

import com.bc.ceres.core.ProgressMonitor;
import org.esa.snap.core.datamodel.Band;
import org.esa.snap.core.datamodel.Product;
import org.esa.snap.core.datamodel.ProductData;
import org.esa.snap.core.gpf.Operator;
import org.esa.snap.core.gpf.OperatorException;
import org.esa.snap.core.gpf.Tile;
import org.esa.snap.core.gpf.annotations.SourceProduct;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.media.jai.*;
import java.awt.*;
import java.awt.image.Raster;
import java.util.*;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class OperatorExecutorTest {

    private static class RecordingTileScheduler implements TileScheduler {

        TileScheduler delegate;
        List<String> recordedCalls = Collections.synchronizedList(new ArrayList<>());
        List<Point> requestedTileIndices = Collections.synchronizedList(new ArrayList<>());

        RecordingTileScheduler(TileScheduler delegate) {
            this.delegate = delegate;
        }

        @Override
        public void cancelTiles(TileRequest request, Point[] tileIndices) {
            delegate.cancelTiles(request, tileIndices);
        }

        @Override
        public int getParallelism() {
            return delegate.getParallelism();
        }

        @Override
        public int getPrefetchParallelism() {
            return delegate.getPrefetchParallelism();
        }

        @Override
        public int getPrefetchPriority() {
            return delegate.getPrefetchPriority();
        }

        @Override
        public int getPriority() {
            return delegate.getPriority();
        }

        @Override
        public void prefetchTiles(PlanarImage target, Point[] tileIndices) {
            delegate.prefetchTiles(target, tileIndices);
        }

        @Override
        public Raster scheduleTile(OpImage target, int tileX, int tileY) {
            return delegate.scheduleTile(target, tileX, tileY);
        }

        @Override
        public Raster[] scheduleTiles(OpImage target, Point[] tileIndices) {
            return delegate.scheduleTiles(target, tileIndices);
        }

        @Override
        public TileRequest scheduleTiles(PlanarImage target, Point[] tileIndices,
                                         TileComputationListener[] tileListeners) {
            // this is the only method we invoke, thus the only method we need to record,
            // everything else is out of our control
            recordedCalls.add("scheduleTiles");
            requestedTileIndices.addAll(Arrays.asList(tileIndices));
            return delegate.scheduleTiles(target, tileIndices, tileListeners);
        }

        @Override
        public void setParallelism(int parallelism) {
            delegate.setParallelism(parallelism);
        }

        @Override
        public void setPrefetchParallelism(int parallelism) {
            delegate.setPrefetchParallelism(parallelism);
        }

        @Override
        public void setPrefetchPriority(int priority) {
            delegate.setPrefetchPriority(priority);
        }

        @Override
        public void setPriority(int priority) {
            delegate.setPriority(priority);
        }
    }

    private static class TestOP extends Operator {

        @SourceProduct
        Product source;

        public TestOP(Product source) {
            this.source = source;
        }

        @Override
        public void initialize() throws OperatorException {
            Product targetProduct = new Product("target", "target", 100, 100);
            for (Band srcBand : source.getBands()) {
                Band band = targetProduct.addBand(srcBand.getName(), srcBand.getDataType());
                band.setSourceImage(srcBand.getSourceImage());
            }

            setTargetProduct(targetProduct);
        }

    }

    private static class TestStackOP extends Operator {

        @SourceProduct
        Product source;

        public TestStackOP(Product source) {
            this.source = source;
        }

        @Override
        public void initialize() throws OperatorException {
            Product targetProduct = new Product("target", "target", 100, 100);
            for (Band srcBand : source.getBands()) {
                targetProduct.addBand(srcBand.getName(), srcBand.getDataType());
            }
            setTargetProduct(targetProduct);
        }

        @Override
        public void computeTileStack(Map<Band, Tile> targetTiles, Rectangle targetRectangle, ProgressMonitor pm) throws OperatorException {
            for (Map.Entry<Band, Tile> bandTileEntry : targetTiles.entrySet()) {
                Band targetBand = bandTileEntry.getKey();
                Band sourceBand = source.getBand(targetBand.getName());
                Raster sourceData = sourceBand.getSourceImage().getData(targetRectangle);
                Tile tile = bandTileEntry.getValue();
                for (Tile.Pos pos : tile) {
                    tile.setSample(pos.x, pos.y, sourceData.getSample(pos.x, pos.y, 0) + 1 );
                }

            }
        }
    }

    private TileScheduler defaultTileScheduler;
    private RecordingTileScheduler recordingTileScheduler;

    @Before
    public void setUp() throws Exception {
        defaultTileScheduler = JAI.getDefaultInstance().getTileScheduler();
        recordingTileScheduler = new RecordingTileScheduler(defaultTileScheduler);
        JAI.getDefaultInstance().setTileScheduler(recordingTileScheduler);
    }

    @After
    public void tearDown() throws Exception {
        JAI.getDefaultInstance().setTileScheduler(defaultTileScheduler);
    }

    @Test
    public void testOneTile() {
        Product sourceProduct = createSourceProduct();
        Operator op = new TestOP(sourceProduct);
        OperatorExecutor operatorExecutor = OperatorExecutor.create(op);
        operatorExecutor.execute(ProgressMonitor.NULL);

        assertEquals(1, recordingTileScheduler.recordedCalls.size());
        assertEquals("scheduleTiles", recordingTileScheduler.recordedCalls.get(0));

        assertEquals(1, recordingTileScheduler.requestedTileIndices.size());
        assertEquals(new Point(0, 0), recordingTileScheduler.requestedTileIndices.get(0));
    }


    @Test
    public void testManyTilesOneBand() {
        Product sourceProduct = createSourceProduct();
        sourceProduct.setPreferredTileSize(50, 50);
        Operator op = new TestOP(sourceProduct);
        OperatorExecutor operatorExecutor = OperatorExecutor.create(op);
        operatorExecutor.execute(ProgressMonitor.NULL);

        assertEquals(4, recordingTileScheduler.recordedCalls.size());

        assertEquals(4, recordingTileScheduler.requestedTileIndices.size());
        assertEquals(new Point(0, 0), recordingTileScheduler.requestedTileIndices.get(0));
        assertEquals(new Point(1, 0), recordingTileScheduler.requestedTileIndices.get(1));
        assertEquals(new Point(0, 1), recordingTileScheduler.requestedTileIndices.get(2));
        assertEquals(new Point(1, 1), recordingTileScheduler.requestedTileIndices.get(3));
    }

    @Test
    public void testManyTilesTwoBands() {
        Product sourceProduct = createSourceProduct();
        Band bandB = sourceProduct.addBand("b", ProductData.TYPE_INT8);
        bandB.setRasterData(createDataFor(bandB));
        bandB.setSynthetic(true);
        sourceProduct.setPreferredTileSize(50, 50);
        Operator op = new TestOP(sourceProduct);
        OperatorExecutor operatorExecutor = OperatorExecutor.create(op);
        operatorExecutor.execute(ProgressMonitor.NULL);

        assertEquals(8, recordingTileScheduler.recordedCalls.size());

        assertEquals(8, recordingTileScheduler.requestedTileIndices.size());
        assertEquals(new Point(0, 0), recordingTileScheduler.requestedTileIndices.get(0));
        assertEquals(new Point(1, 0), recordingTileScheduler.requestedTileIndices.get(1));
        assertEquals(new Point(0, 0), recordingTileScheduler.requestedTileIndices.get(2));
        assertEquals(new Point(1, 0), recordingTileScheduler.requestedTileIndices.get(3));
        assertEquals(new Point(0, 1), recordingTileScheduler.requestedTileIndices.get(4));
        assertEquals(new Point(1, 1), recordingTileScheduler.requestedTileIndices.get(5));
        assertEquals(new Point(0, 1), recordingTileScheduler.requestedTileIndices.get(6));
        assertEquals(new Point(1, 1), recordingTileScheduler.requestedTileIndices.get(7));
    }

    @Test
    public void testManyTilesTwoBands_ColumnBandOrder() {
        Product sourceProduct = createSourceProduct();
        Band bandB = sourceProduct.addBand("b", ProductData.TYPE_INT8);
        bandB.setRasterData(createDataFor(bandB));
        bandB.setSynthetic(true);
        sourceProduct.setPreferredTileSize(50, 50);
        Operator op = new TestOP(sourceProduct);
        OperatorExecutor operatorExecutor = OperatorExecutor.create(op);
        operatorExecutor.execute(OperatorExecutor.ExecutionOrder.SCHEDULE_ROW_COLUMN_BAND, ProgressMonitor.NULL);

        // 4 tiles * 2 bands = 8
        assertEquals(8, recordingTileScheduler.requestedTileIndices.size());
        // each tiles must be scheduled 2 times. One time for each band
        assertEquals(2, recordingTileScheduler.requestedTileIndices.stream().filter(point -> new Point(0, 0).equals(point)).count());
        assertEquals(2, recordingTileScheduler.requestedTileIndices.stream().filter(point -> new Point(1, 0).equals(point)).count());
        assertEquals(2, recordingTileScheduler.requestedTileIndices.stream().filter(point -> new Point(0, 1).equals(point)).count());
        assertEquals(2, recordingTileScheduler.requestedTileIndices.stream().filter(point -> new Point(1, 1).equals(point)).count());

        // 8x scheduleTiles
        assertEquals(8, recordingTileScheduler.recordedCalls.size());

    }

    @Test
    public void testManyTilesTwoBands_withTileStackOp() {
        Product sourceProduct = createSourceProduct();
        Band bandB = sourceProduct.addBand("b", ProductData.TYPE_INT8);
        bandB.setRasterData(createDataFor(bandB));
        bandB.setSynthetic(true);
        sourceProduct.setPreferredTileSize(50, 50);
        Operator op = new TestStackOP(sourceProduct);
        OperatorExecutor operatorExecutor = OperatorExecutor.create(op);
        operatorExecutor.execute(ProgressMonitor.NULL);

        // 4 tiles * 2 bands = 8
        assertEquals(8, recordingTileScheduler.requestedTileIndices.size());
        // each tiles must be scheduled 2 times. One time for each band
        assertEquals(2, recordingTileScheduler.requestedTileIndices.stream().filter(point -> new Point(0, 0).equals(point)).count());
        assertEquals(2, recordingTileScheduler.requestedTileIndices.stream().filter(point -> new Point(1, 0).equals(point)).count());
        assertEquals(2, recordingTileScheduler.requestedTileIndices.stream().filter(point -> new Point(0, 1).equals(point)).count());
        assertEquals(2, recordingTileScheduler.requestedTileIndices.stream().filter(point -> new Point(1, 1).equals(point)).count());

        // 8x scheduleTiles
        assertEquals(8, recordingTileScheduler.recordedCalls.size());

    }

    private Product createSourceProduct() {
        Product product = new Product("source", "source", 100, 100);
        Band bandA = product.addBand("a", ProductData.TYPE_INT8);
        bandA.setRasterData(createDataFor(bandA));
        bandA.setSynthetic(true);
        return product;
    }


    private static ProductData createDataFor(Band dataBand) {
        final int width = dataBand.getRasterWidth();
        final int height = dataBand.getRasterHeight();
        final ProductData data = ProductData.createInstance(dataBand.getDataType(), width * height);
        for (int y = 0; y < height; y++) {
            final int line = y * width;
            for (int x = 0; x < width; x++) {
                data.setElemIntAt(line + x, x * y);
            }
        }
        return data;
    }
}
