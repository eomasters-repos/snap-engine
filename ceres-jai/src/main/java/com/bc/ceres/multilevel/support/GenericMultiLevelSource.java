package com.bc.ceres.multilevel.support;

import com.bc.ceres.multilevel.MultiLevelSource;

import java.awt.image.RenderedImage;

/**
 * A {@code GenericMultiLevelSource} is a {@link MultiLevelSource} computing its
 * images at a given resolution level from a number of source images of the same level.
 * <p>Subclasses will have to to implement {@link #createImage(java.awt.image.RenderedImage[], int)}.
 *
 * @author Norman Fomferra
 * @version $revision$ $date$
 */
public abstract class GenericMultiLevelSource extends AbstractMultiLevelSource {
    private final MultiLevelSource[] multiLevelSources;

    protected GenericMultiLevelSource(MultiLevelSource multiLevelSource) {
        this(new MultiLevelSource[]{multiLevelSource});
    }

    protected GenericMultiLevelSource(MultiLevelSource[] multiLevelSources) {
        super(multiLevelSources[0].getModel());
        this.multiLevelSources = multiLevelSources.clone();
    }

    public MultiLevelSource[] getMultiLevelSources() {
        return multiLevelSources.clone();
    }

    @Override
    protected RenderedImage createImage(int level) {
        RenderedImage[] sourceImages = new RenderedImage[multiLevelSources.length];
        for (int i = 0; i < multiLevelSources.length; i++) {
            sourceImages[i] = multiLevelSources[i].getImage(level);
        }
        return createImage(sourceImages, level);
    }

    protected abstract RenderedImage createImage(RenderedImage[] sourceImages, int level);
}
