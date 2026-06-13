package app;

import java.util.List;

import engine.Point;
import engine.RouteStep;
import engine.RoutingEngine;
import javafx.animation.AnimationTimer;
import java.time.LocalDate;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

public class MapCanvas extends Canvas {

    private double zoomMin = .1;
    private double zoomMax = 1000.0;
    private double zoomSensitivity = 0.005;
    private double interpolationSpeed = 0.15;
    
    private double offsetX = 0;
    private double offsetY = 0;
    private double scale = 1.0;

    private double targetOffsetX = 0;
    private double targetOffsetY = 0;
    private double targetScale = 1.0;
    
    private double lastMouseX;
    private double lastMouseY;
    
    private RoutingEngine engine;
    private MapDrawer drawer;

    private Point startPoint = null;
    private Point endPoint = null;

    private double mouseDownX;
    private double mouseDownY;
    private boolean isDragging = false;

    // addming a mode to switch between "stop closure" and "routing"
    public enum Mode {
        ROUTING,
        HEATMAP,
        CLOSE_STOP
    }

    private Mode mode = Mode.ROUTING;

    // heatmap data cached for rendering
    private java.util.Map<String, Integer> heatmapData = null;

//    public void setMode(Mode mode) {
//        this.mode = mode;
//    }

    // changing it so that it hides / clears the heatmap if we're not on it
    public void setMode(Mode mode) {
        this.mode = mode;

        if (mode != Mode.HEATMAP) {
            heatmapData = null;
            drawer.setHeatmapData(null);
            draw();
        }
    }

    public void requestHeatmap(String startTime, int dayOfWeek) {
        // compute center point in world coordinates
        double centerX = getWidth() / 2.0;
        double centerY = getHeight() / 2.0;

        double worldX = (centerX - offsetX) / scale;
        double worldY = (centerY - offsetY) / scale;

        Point center = drawer.pixelToLatLon(worldX, worldY);

        try {
            // I'm setting the center value as default
            String start = center.lat + "," + center.lon;
            heatmapData = engine.getHeatmapData(start, startTime, dayOfWeek);
            drawer.setHeatmapData(heatmapData);
            draw();
        } catch (Exception ex) {
            System.err.println("Failed to request heatmap: " + ex.getMessage());
        }
    }

    private java.util.function.Consumer<List<RouteStep>> onRouteCalculated;

    public void setOnRouteCalculated(java.util.function.Consumer<List<RouteStep>> callback) {
        this.onRouteCalculated = callback;
    }

    private java.util.function.Consumer<Point> onStartPointSelected;
    private java.util.function.Consumer<Point> onEndPointSelected;

    public void setOnStartPointSelected(java.util.function.Consumer<Point> callback) {
        this.onStartPointSelected = callback;
    }

    public void setOnEndPointSelected(java.util.function.Consumer<Point> callback) {
        this.onEndPointSelected = callback;
    }

    // Francesco: Adding this to track the selected stop for stop closure in the GUI
    private java.util.function.Consumer<String> onStopSelected;

    public void setOnStopSelected(java.util.function.Consumer<String> handler) {
        this.onStopSelected = handler;
    }

    public MapCanvas(double width, double height, RoutingEngine engine) {
        super(width, height);

        drawer = new MapDrawer(engine, width, height);
        
        this.engine = engine;
        setupInteractions();
        animator.start();
        zoom();
        draw();
    }

    private void zoom() {
        targetScale = 3.0;
        targetOffsetX = -1000;
        targetOffsetY = -600;
    }

    public void draw() {
        GraphicsContext gc = getGraphicsContext2D();

        gc.setFill(Colors.OUTSIDE_MAP_COLOR);
        gc.fillRect(0, 0, getWidth(), getHeight());

        gc.save();
        
        // move the center to the offset
        gc.translate(offsetX, offsetY);
        
        // scale the canvas
        gc.scale(scale, scale);

        // draw the map with the custom drawer
        drawer.draw(gc, getWidth(), getHeight(), scale);

        gc.restore();
    }

    // animator for smooth scaling and moving
    private final AnimationTimer animator = new AnimationTimer() {
        @Override
        public void handle(long now) {
            offsetX = lerp(offsetX, targetOffsetX, interpolationSpeed);
            offsetY = lerp(offsetY, targetOffsetY, interpolationSpeed);
            scale = lerp(scale, targetScale, interpolationSpeed);

            draw();
        }
    };
    
    private void setupInteractions() {
        setOnMousePressed(this::handleMousePressed);
        setOnMouseDragged(this::handleMouseDragged);
        setOnMouseReleased(this::handleMouseReleased);

        setOnScroll(e -> {
            double oldTargetScale = targetScale;
            double zoomMultiplier = Math.exp(e.getDeltaY() * zoomSensitivity);

            double mouseX = e.getX();
            double mouseY = e.getY();

            double worldX = (mouseX - targetOffsetX) / oldTargetScale;
            double worldY = (mouseY - targetOffsetY) / oldTargetScale;

            targetScale *= zoomMultiplier;
            targetScale = Math.max(zoomMin, Math.min(targetScale, zoomMax));

            targetOffsetX = mouseX - worldX * targetScale;
            targetOffsetY = mouseY - worldY * targetScale;
        });


        widthProperty().addListener(e -> draw());
        heightProperty().addListener(e -> draw());
    }

    private void handleMousePressed(MouseEvent e) {
        lastMouseX = e.getX();
        lastMouseY = e.getY();

        mouseDownX = e.getX();
        mouseDownY = e.getY();
        isDragging = false;
    }

    private void handleMouseDragged(MouseEvent e) {
        double dx = e.getX() - lastMouseX;
        double dy = e.getY() - lastMouseY;

        targetOffsetX += dx;
        targetOffsetY += dy;

        lastMouseX = e.getX();
        lastMouseY = e.getY();

        double movedX = e.getX() - mouseDownX;
        double movedY = e.getY() - mouseDownY;

        if (Math.sqrt(movedX * movedX + movedY * movedY) > 5) {
            isDragging = true;
        }
    }

    private void handleMouseReleased(MouseEvent e) {
        if (!isDragging) {
            handleClick(e);
        }
    }
    
    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private void handleClick(MouseEvent e) {

        //right click clears everything
        if (e.getButton() == MouseButton.SECONDARY) {
            clearAll();
            return;
        }

        double worldX = (e.getX() - offsetX) / scale;
        double worldY = (e.getY() - offsetY) / scale;

        Point clicked = drawer.pixelToLatLon(worldX, worldY);

        String stopId = findNearestStopId(clicked);

        if (stopId == null) {
            onStopSelected.accept(null);
        }
        else if (stopId != null && onStopSelected != null) {
            onStopSelected.accept(stopId);
        }

//        if (mode == Mode.HEATMAP) {
//            // compute clicked lat/lon and request heatmap centered there
//            Point center = clicked;
//            String start = center.lat + "," + center.lon;
//            int dayOfWeek = LocalDate.now().getDayOfWeek().getValue() - 1;
//            try {
//                heatmapData = engine.getHeatmapData(start, "09:00", dayOfWeek);
//                drawer.setHeatmapData(heatmapData);
//                draw();
//            } catch (Exception ex) {
//                System.err.println("Heatmap request failed: " + ex.getMessage());
//            }
//            return;
//        }

        // previously it automatically triggered engine.getHeatmapData(start, "09:00", dayOfWeek);
        // and drawer.setHeatmapData(heatmapData); , but now it just fills in the coordinates awaiting user
        // confirmation.
        if (mode == Mode.HEATMAP) {

            startPoint = clicked;

            if (onStartPointSelected != null) {
                onStartPointSelected.accept(startPoint);
            }

            drawer.setStartPoint(startPoint);

            draw();
            return;
        }

        //left clicks -> place start first, then end, then reset
        if (startPoint == null) {
            startPoint = clicked;
            drawer.setStartPoint(startPoint);
            drawer.setRoute(null);
            drawer.setEndPoint(null);
            endPoint = null;
            if (onStartPointSelected != null) {
                onStartPointSelected.accept(startPoint);
            }

        } else if (endPoint == null) {
            endPoint = clicked;
            drawer.setEndPoint(endPoint);
            if (onEndPointSelected != null) {
                onEndPointSelected.accept(endPoint);
            }

            List<RouteStep> steps = engine.findPath(
                startPoint.lat + "," + startPoint.lon,
                endPoint.lat + "," + endPoint.lon,
                "09:00"
            );

            drawer.setRoute(steps);

            if (onRouteCalculated != null) {
                onRouteCalculated.accept(steps);
            }

        } else {
            startPoint = clicked;
            endPoint = null;

            drawer.setStartPoint(startPoint);
            drawer.setEndPoint(null);
            drawer.setRoute(null);
            if (onStartPointSelected != null) {
                onStartPointSelected.accept(startPoint);
            }
            if (onEndPointSelected != null) {
                onEndPointSelected.accept(null);
            }
        }

        draw();
    }

    // when a user clicks somewhere, it gets the nearest stop
    private String findNearestStopId(Point p) {

        double bestDist = Double.MAX_VALUE;
        String bestId = null;

        for (var stop : engine.getDataset().stops.values()) {

            double dx = stop.stop_lat - p.lat;
            double dy = stop.stop_lon - p.lon;

            double dist = dx * dx + dy * dy; // fast squared distance

            if (dist < bestDist) {
                bestDist = dist;
                bestId = stop.stop_id;
            }
        }

        // optional threshold (VERY recommended)
        if (bestDist > 0.0001) {
            return null;
        }

        return bestId;
    }

    public void generateHeatmap(String start, String startTime) {

        int dayOfWeek =
                LocalDate.now().getDayOfWeek().getValue() - 1;

        try {
            heatmapData =
                    engine.getHeatmapData(start, startTime, dayOfWeek);

            drawer.setHeatmapData(heatmapData);

            draw();

        } catch (Exception ex) {
            System.err.println("Heatmap request failed: " + ex.getMessage());
        }
    }

    public void clearAll() {
        startPoint = null;
        endPoint = null;

        drawer.setStartPoint(null);
        drawer.setEndPoint(null);
        drawer.setRoute(null);

        if (onStartPointSelected != null) {
            onStartPointSelected.accept(null);
        }
        if (onEndPointSelected != null) {
            onEndPointSelected.accept(null);
        }

        draw();
    }
 
    public void showRoute(List<RouteStep> steps) {
        drawer.setRoute(steps);
        draw();
    }
}