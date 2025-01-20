package com.bc.ceres.glevel.support;

import com.bc.ceres.glevel.MultiLevelModel;

import javax.media.jai.operator.FileLoadDescriptor;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.RenderedImage;
import java.io.File;

/**
 * A file based image pyramid. For each level an image file named
 * {@code <basename>.<level>.<extension>} is ecpected to exist in
 * the directory given by {@code location}.
 *
 * @author Norman Fomferra
 * @version $Revision$ $Date$
 * @since BEAM 4.6
 */
public class FileMultiLevelSource extends AbstractMultiLevelSource {
    private final File location;
    private final String basename;
    private final String extension;

    public FileMultiLevelSource(File location, String basename, String extension, MultiLevelModel multiLevelModel) {
        super(multiLevelModel);
        this.location = location;
        this.basename = basename;
        this.extension = extension;
    }

    public File getLocation() {
        return location;
    }

    public String getBasename() {
        return basename;
    }

    public String getExtension() {
        return extension;
    }

    @Override
    public RenderedImage createImage(int level) {
        final StringBuilder sb = new StringBuilder(basename);
        sb.append('.');
        sb.append(level);
        sb.append('.');
        sb.append(extension);
        final String fileName = sb.toString();
        final File file = new File(location, fileName);
        return FileLoadDescriptor.create(file.getPath(), null, true, null).getRendering();
    }

    public static FileMultiLevelSource create(File location, String extension, AffineTransform imageToModelTransform, int levelCount) {
        final com.bc.ceres.glevel.support.DefaultMultiLevelModel defaultMultiLevelModel = new com.bc.ceres.glevel.support.DefaultMultiLevelModel(levelCount, imageToModelTransform, null);
        final FileMultiLevelSource levelImageSource = new FileMultiLevelSource(location, location.getName(), extension, defaultMultiLevelModel);
        Rectangle2D modelBounds = DefaultMultiLevelModel.getModelBounds(imageToModelTransform, levelImageSource.getImage(0));
        defaultMultiLevelModel.setModelBounds(modelBounds);
        return levelImageSource;
    }
}
