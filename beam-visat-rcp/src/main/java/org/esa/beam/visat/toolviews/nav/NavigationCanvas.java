/*
 * Created at 12.07.2004 06:45:55 Copyright (c) 2004 by Norman Fomferra
 */
package org.esa.beam.visat.toolviews.nav;

import com.bc.ceres.glayer.Layer;
import com.bc.ceres.glayer.support.ImageLayer;
import com.bc.ceres.grender.Viewport;
import com.bc.ceres.grender.support.BufferedImageRendering;
import com.bc.ceres.grender.support.DefaultViewport;
import org.esa.beam.framework.ui.product.ProductSceneView;
import org.esa.beam.util.logging.BeamLogManager;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

public class NavigationCanvas extends JPanel {

    private NavigationToolView navigationWindow;
    private BufferedImage thumbnailImage;
    private BufferedImageRendering imageRendering;

    /**
     * currently visible model area in view coordinates
     */
    private Rectangle visibleArea;
    /**
     * model area in view coordinates
     */
    private Rectangle area;
    private boolean updatingImageDisplay;

    public NavigationCanvas(NavigationToolView navigationWindow) {
        super(null, true);
        this.navigationWindow = navigationWindow;
        visibleArea = new Rectangle(0, 0, 0, 0);
        area = new Rectangle(0, 0, 0, 0);
        final MouseHandler mouseHandler = new MouseHandler();
        addMouseListener(mouseHandler);
        addMouseMotionListener(mouseHandler);
    }

    public boolean isUpdatingImageDisplay() {
        return updatingImageDisplay;
    }

    public Rectangle getVisibleArea() {
        return visibleArea;
    }

    public void setVisibleArea(Rectangle slider) {
        Rectangle oldSlider = this.visibleArea;
        this.visibleArea = new Rectangle(slider);
        firePropertyChange("visibleArea", oldSlider, this.visibleArea);
    }

    /**
     * Causes this container to lay out its components. Most programs should not
     * call this method directly, but should invoke the <code>validate</code>
     * method instead.
     *
     * @see java.awt.LayoutManager#layoutContainer
     * @see #setLayout
     * @see #validate
     * @since JDK1.1
     */
    @Override
    public void doLayout() {
        super.doLayout();
        updateImage();
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (isOpaque()) {
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
        }
        if (thumbnailImage != null) {
            final AffineTransform transform = AffineTransform.getTranslateInstance(area.x, area.y);
            ((Graphics2D) g).drawRenderedImage(thumbnailImage, transform);

            g.setColor(new Color(getForeground().getRed(), getForeground().getGreen(),
                                 getForeground().getBlue(), 82));
            g.fillRect(visibleArea.x, visibleArea.y, visibleArea.width, visibleArea.height);
            g.setColor(getForeground());
            g.draw3DRect(visibleArea.x - 1, visibleArea.y - 1, visibleArea.width + 2, visibleArea.height + 2, true);
            g.draw3DRect(visibleArea.x, visibleArea.y, visibleArea.width, visibleArea.height, false);
        }
    }

    private void updateImageDisplay() {
        final ProductSceneView view = navigationWindow.getCurrentView();
        if (view != null && thumbnailImage != null) {
            // todo - ask nf to explain why offset is needed (rq)
            final double x = visibleArea.x - area.x;
            final double y = visibleArea.y - area.y;
            final Point2D point = new Point.Double(x, y);

            imageRendering.getViewport().getViewToModelTransform().transform(point, point);
            updatingImageDisplay = true;
            navigationWindow.setModelOffset(point.getX(), point.getY());
            updatingImageDisplay = false;
        }
    }

    public void updateImage() {
        final ProductSceneView view = navigationWindow.getCurrentView();
        if (view != null) {
            final Insets insets = getInsets();
            int imageWidth = getWidth() - (insets.left + insets.right);
            int imageHeight = getHeight() - (insets.top + insets.bottom);
            final double imageRatio = (double) imageWidth / (double) imageHeight;
            final Rectangle2D tma = getRotatedModelBounds(view);
            final double modelRatio = tma.getWidth() / tma.getHeight();
            if (imageRatio < modelRatio) {
                imageHeight = (int) Math.round(imageWidth / modelRatio);
            } else {
                imageWidth = (int) Math.round(imageHeight * modelRatio);
            }
            if (imageWidth > 0 && imageHeight > 0) {
                if (thumbnailImage == null || thumbnailImage.getWidth() != imageWidth
                        || thumbnailImage.getHeight() != imageHeight) {
                    thumbnailImage = new BufferedImage(imageWidth, imageHeight,
                                                       BufferedImage.TYPE_3BYTE_BGR);
                    imageRendering = new BufferedImageRendering(thumbnailImage);
                }
                updateImageContent();
                updateSliderArea();
                updateSlider();
            } else {
                thumbnailImage = null;
            }
        } else {
            thumbnailImage = null;
        }
        // needed to update if last ProductSceneView is closed
        updateUI();
    }

    public void updateSlider() {
        // TODO IMAGING 4.5
        final ProductSceneView view = navigationWindow.getCurrentView();
        if (updatingImageDisplay || view == null) {
            return;
        }
        visibleArea.setRect(getViewportThumbnailBounds(view, area));
        repaint();
    }

    private void updateSliderArea() {
        if (thumbnailImage == null) {
            return;
        }
        area.x = 0;
        area.y = 0;
        area.width = thumbnailImage.getWidth();
        area.height = thumbnailImage.getHeight();
        if (thumbnailImage.getWidth() < getWidth()) {
            area.x = (getWidth() - thumbnailImage.getWidth()) / 2;
        }
        if (thumbnailImage.getHeight() < getHeight()) {
            area.y = (getHeight() - thumbnailImage.getHeight()) / 2;
        }
    }

    private void updateImageContent() {
        // Will to this totally different later!!! (Use max. level image of
        // ImageLayerModel).
        final ProductSceneView view = navigationWindow.getCurrentView();
        if (view == null || thumbnailImage == null) {
            return;
        }
        renderThumbnail(view);
    }

    private void renderThumbnail(ProductSceneView view) {
        final Graphics2D graphics = imageRendering.getGraphics();
        graphics.setBackground(getBackground());
        graphics.clearRect(0, 0, thumbnailImage.getWidth(), thumbnailImage.getHeight());

        configureThumbnailViewport(view, imageRendering.getViewport());
        view.getRootLayer().accept(new Layer.Visitor() {
            @Override
            public void visit(Layer layer) {
                if (layer instanceof ImageLayer) {
                    layer.render(imageRendering);
                }
            }
        });
    }

    private Rectangle getViewportThumbnailBounds(ProductSceneView view, Rectangle thumbnailArea) {
        Viewport tnViewport = new DefaultViewport(thumbnailArea);
        configureThumbnailViewport(view, tnViewport);
        Viewport vwViewport = view.getLayerCanvas().getViewport();

        AffineTransform vwV2M = vwViewport.getViewToModelTransform();
        AffineTransform tnM2V = tnViewport.getModelToViewTransform();

        Rectangle vwViewBounds = vwViewport.getBounds();
        Point2D vwViewOffset = vwViewBounds.getLocation();
        Point2D vwModelOffset = vwV2M.transform(vwViewOffset, null);
        Point2D tnViewOffset = tnM2V.transform(vwModelOffset, null);
        double scale = Math.sqrt(vwV2M.getDeterminant()) * Math.sqrt(tnM2V.getDeterminant());
        return new Rectangle((int) Math.floor(tnViewOffset.getX()),
                             (int) Math.floor(tnViewOffset.getY()),
                             (int) Math.floor(vwViewBounds.width * scale),
                             (int) Math.floor(vwViewBounds.height * scale));
    }

    private void configureThumbnailViewport(ProductSceneView view, Viewport thumbnailViewport) {
        thumbnailViewport.setMaxZoomFactor(-1);
        thumbnailViewport.zoom(getRotatedModelBounds(view));
        thumbnailViewport.moveViewDelta(thumbnailViewport.getBounds().x,
                                        thumbnailViewport.getBounds().y);
        thumbnailViewport.rotate(view.getOrientation());
    }

    private Rectangle2D getRotatedModelBounds(ProductSceneView view) {
        final Rectangle2D modelBounds = view.getModelBounds();
        final double orientation = view.getOrientation();
        if (orientation != 0.0) {
            final AffineTransform t = new AffineTransform();
            t.rotate(orientation, modelBounds.getCenterX(), modelBounds.getCenterY());
            return t.createTransformedShape(modelBounds).getBounds2D();
        }
        return modelBounds;
    }

    /**
     * This method ignores the given parameter. It is an empty implementation to
     * prevent from setting borders on this canvas.
     *
     * @param border is ignored
     */
    @Override
    public void setBorder(Border border) {
        if (border != null) {
            BeamLogManager.getSystemLogger()
                    .warning(
                            "NavigationCanvas.setBorder() called with "
                                    + border.getClass().getCanonicalName());
            BeamLogManager.getSystemLogger().warning("borders not allowed");
        }
    }

    private class MouseHandler extends MouseInputAdapter {

        private Point _pickPoint;
        private Point _sliderPoint;

        @Override
        public void mousePressed(MouseEvent e) {
            _pickPoint = e.getPoint();
            if (!visibleArea.contains(_pickPoint)) {
                visibleArea.x = _pickPoint.x - visibleArea.width / 2;
                visibleArea.y = _pickPoint.y - visibleArea.height / 2;
                repaint();
            }
            _sliderPoint = visibleArea.getLocation();
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            updateImageDisplay();
            repaint();
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            visibleArea.x = _sliderPoint.x + (e.getX() - _pickPoint.x);
            visibleArea.y = _sliderPoint.y + (e.getY() - _pickPoint.y);
            updateImageDisplay();
            repaint();
        }
    }
}
