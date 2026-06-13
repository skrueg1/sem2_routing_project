package app;

import engine.Point;
import engine.RoutingEngine;
import gtfs.Stop;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import java.util.List;
import java.util.Map;
import engine.RouteStep;
import javafx.scene.image.Image;


public class MapDrawer {

    private double stopCircleRadius = 2;
    private double mapPadding = 10;
    
    private double minLon, maxLon, minLat, maxLat;
    private double mapWidth, mapHeight;
    private double canvasWidth, canvasHeight;

    private double scale;
    private double offsetX;
    private double offsetY;

    private RoutingEngine engine;
    private ShapeLoader shapeLoader = new ShapeLoader();

    //the route and two clicked points
    private List<RouteStep> route = null;
    private Point startPoint = null;
    private Point endPoint = null;
    // heatmap data (stopId maps to minutes reachable)
    private java.util.Map<String, Integer> heatmapData = null;

    // tiles
    private int tileZoom = 12;
    private TileLoader tileLoader = new TileLoader();

    public MapDrawer(RoutingEngine engine, double width, double height) {
        this.engine = engine;
        this.canvasWidth = width;
        this.canvasHeight = height;
        calcBoundary();
        updateTransform();
        
        // load boundary shape
        shapeLoader.loadGeoJsonShape("data/copenhagenFullBoundary.geojson", Color.LIGHTSEAGREEN);
    }

    //setters to get the data
    public void setRoute(List<RouteStep> route) {
        this.route = route;
    }

    public void setStartPoint(Point p) {
        this.startPoint = p;
    }

    public void setEndPoint(Point p) {
        this.endPoint = p;
    }

    public void setHeatmapData(java.util.Map<String, Integer> data) {
        this.heatmapData = data;
    }

    // converts pixels to lan/lon
    public Point pixelToLatLon(double pixelX, double pixelY) {
        double lon = (pixelX - offsetX - mapPadding) / scale + minLon;
        double lat = maxLat - (pixelY - offsetY - mapPadding) / scale;
        return new Point(lat, lon);
    }

    public void draw(GraphicsContext gc, double width, double height, double zoom) {
        
        double w = mapWidth * scale + 2 * mapPadding;
        double h = mapHeight * scale + 2 * mapPadding;
        // draw border
        gc.setStroke(Colors.BORDER_COLOR);
        double strokeWidth = 1.0 / zoom;
        gc.strokeRect(offsetX, offsetY, 
            w + strokeWidth, 
            h + strokeWidth);

        // draw tiles as background
        drawTiles(gc, zoom);
        
        // draw bus stops
        // if heatmap exists, draw it as semi-transparent colored circles
        if (heatmapData != null && !heatmapData.isEmpty()) {
            drawHeatmapOverlay(gc, zoom);
        }

        gc.setFill(Colors.STOP_COLOR);
        for (Stop stop : engine.getDataset().stops.values()) {

            // color node based on closed state
            if (stop.closed) {
                gc.setFill(Color.RED);
            } else {
                gc.setFill(Colors.STOP_COLOR);
            }

            double r = stopCircleRadius / zoom;
            double x = convertToCanvasX(stop.stop_lon);
            double y = convertToCanvasY(stop.stop_lat);

            gc.fillOval(x - r, y - r, 2 * r, 2 * r);
        }
        
        // draw more stuff

        // draw the route lines if we have a route
        if (route != null && startPoint != null) {
            drawRoute(gc, zoom);
        }

        // draw the start and end markers
        drawMarker(gc, startPoint, Color.GREEN, zoom);
        drawMarker(gc, endPoint, Color.RED, zoom);
    }

    // This is a function which draws the heatmap as circles with a given color
    // between red (for hard to reach) and green (for easy to reach). 
    private void drawHeatmapOverlay(GraphicsContext gc, double zoom) {
        // find max value to normalize
        int max = heatmapData.values().stream().mapToInt(Integer::intValue).max().orElse(1);
        double zoomAlphaFactor = Math.max(0.2, Math.min(1.0, zoom / 3.0));

        for (java.util.Map.Entry<String, Integer> e : heatmapData.entrySet()) {
            Stop s = engine.getDataset().stops.get(e.getKey());
            if (s == null) continue;

            double intensity = Math.max(0.0, Math.min(1.0, (double) e.getValue() / (double) max));
            double hue = (1.0 - intensity) * 120.0; // 120=green, 0=red
            double alpha = (0.08 + 0.18 * intensity) * zoomAlphaFactor;
            double radius = 8 + 28 * intensity;

            double x = convertToCanvasX(s.stop_lon);
            double y = convertToCanvasY(s.stop_lat);

            gc.setFill(Color.hsb(hue, 0.85, 0.92, alpha));
            double r = radius / zoom;
            gc.fillOval(x - r, y - r, 2 * r, 2 * r);
        }
    }

     private void drawTiles(GraphicsContext gc, double zoom) {
        double w = mapWidth * scale + 2 * mapPadding;
        double h = mapHeight * scale + 2 * mapPadding;
 
        // fallback color if tiles are missing
        gc.setFill(Color.web("#e8e0d8"));
        gc.fillRect(offsetX, offsetY, w, h);
 
        int xTileMin = latLonToTileX(minLon, tileZoom);
        int xTileMax = latLonToTileX(maxLon, tileZoom);
        int yTileMin = latLonToTileY(maxLat, tileZoom);
        int yTileMax = latLonToTileY(minLat, tileZoom);
 
        for (int tx = xTileMin; tx <= xTileMax; tx++) {
            for (int ty = yTileMin; ty <= yTileMax; ty++) {
                Image tile = tileLoader.getTile(tileZoom, tx, ty);
                if (tile == null) continue;
 
                double tileLon = tileXToLon(tx, tileZoom);
                double tileLat = tileYToLat(ty, tileZoom);
                double screenX = convertToCanvasX(tileLon);
                double screenY = convertToCanvasY(tileLat);
 
                double nextLon = tileXToLon(tx + 1, tileZoom);
                double nextLat = tileYToLat(ty + 1, tileZoom);
                double tileW = convertToCanvasX(nextLon) - screenX;
                double tileH = convertToCanvasY(nextLat) - screenY;
 
                gc.drawImage(tile, screenX, screenY, tileW, tileH);
            }
        }
    }

    private int latLonToTileX(double lon, int zoom) {
        return (int) Math.floor((lon + 180.0) / 360.0 * Math.pow(2, zoom));
    }
 
    private int latLonToTileY(double lat, int zoom) {
        double latRad = Math.toRadians(lat);
        return (int) Math.floor(
            (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * Math.pow(2, zoom)
        );
    }
 
    private double tileXToLon(int x, int zoom) {
        return x / Math.pow(2, zoom) * 360.0 - 180.0;
    }
 
    private double tileYToLat(int y, int zoom) {
        double n = Math.PI - 2.0 * Math.PI * y / Math.pow(2, zoom);
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    private void drawRoute(GraphicsContext gc, double zoom) {
        // drawing the route step by step 
        Point previousPoint = startPoint;

        for (RouteStep step : route) {
            // get the destination coordinates
            Map<String, Object> to = step.to;
            double lat = ((Number) to.get("lat")).doubleValue();
            double lon = ((Number) to.get("lon")).doubleValue();
            Point nextPoint = new Point(lat, lon);

            // convert points to canvas pixels
            double x1 = convertToCanvasX(previousPoint.lon);
            double y1 = convertToCanvasY(previousPoint.lat);
            double x2 = convertToCanvasX(nextPoint.lon);
            double y2 = convertToCanvasY(nextPoint.lat);

            if (step.mode.equals("walk")) {
                //gray line for walking
                gc.setStroke(Color.DIMGRAY);
                gc.setLineWidth(3.0 / zoom);
                gc.setLineDashes(8.0 / zoom, 5.0 / zoom);
                gc.strokeLine(x1, y1, x2, y2);
                gc.setLineDashes(null);

            } else if (step.mode.equals("ride")) {
                gc.setStroke(Color.DODGERBLUE);
                gc.setLineWidth(4.0 / zoom);
                
                List<gtfs.ShapePoint> shapePoints = null;
                if (step.route != null && step.shapeId != null) {
                    shapePoints = engine.getDataset().shapes.get(step.shapeId);
                }


                if (shapePoints != null && !shapePoints.isEmpty()) {
                    // drawing based on shape points
                    
                    //finding the closest points
                    double startLat = previousPoint.lat;
                    double startLon = previousPoint.lon;
                    double endLat = ((Number) to.get("lat")).doubleValue();
                    double endLon = ((Number) to.get("lon")).doubleValue();

                    //closest start point
                    int startIdx = 0;
                    double minDistStart = Double.MAX_VALUE;
                    for (int i = 0; i < shapePoints.size(); i++) {
                        gtfs.ShapePoint sp = shapePoints.get(i);
                        double d = Math.pow(sp.lat - startLat, 2) + Math.pow(sp.lon - startLon, 2);
                        if (d < minDistStart) { minDistStart = d; startIdx = i; }
                    }

                    //closest to end point
                    int endIdx = shapePoints.size() - 1;
                    double minDistEnd = Double.MAX_VALUE;
                    for (int i = startIdx; i < shapePoints.size(); i++) {
                        gtfs.ShapePoint sp = shapePoints.get(i);
                        double d = Math.pow(sp.lat - endLat, 2) + Math.pow(sp.lon - endLon, 2);
                        if (d < minDistEnd) { minDistEnd = d; endIdx = i; }
                    }

                    //drawing the shape only inbetween them
                    gtfs.ShapePoint first = shapePoints.get(startIdx);
                    double px = convertToCanvasX(first.lon);
                    double py = convertToCanvasY(first.lat);
                    for (int i = startIdx + 1; i <= endIdx; i++) {
                        gtfs.ShapePoint sp = shapePoints.get(i);
                        double nx = convertToCanvasX(sp.lon);
                        double ny = convertToCanvasY(sp.lat);
                        gc.strokeLine(px, py, nx, ny);
                        px = nx;
                        py = ny;
                    }


                /* technically we do not need to use all the stops inbetween if we are using shapes
                // takes all the stops we take and draws the lines between them
                if (step.waypoints != null && !step.waypoints.isEmpty()) {
                    Point prev = previousPoint;
                    for (Map<String, Object> wp : step.waypoints) {
                        double wlat = ((Number) wp.get("lat")).doubleValue();
                        double wlon = ((Number) wp.get("lon")).doubleValue();
                        double wx1 = convertToCanvasX(prev.lon);
                        double wy1 = convertToCanvasY(prev.lat);
                        double wx2 = convertToCanvasX(wlon);
                        double wy2 = convertToCanvasY(wlat);
                        gc.strokeLine(wx1, wy1, wx2, wy2);
                        prev = new Point(wlat, wlon);
                    }*/
                } else {
                    gc.strokeLine(x1, y1, x2, y2);
                }
                
            } else if (step.mode.equals("wait")) {
                // waiting means we stay in place
                continue;
            }

            // small white dot at each stop
            double r = 5.0 / zoom;
            gc.setFill(Color.WHITE);
            gc.fillOval(x2 - r, y2 - r, 2 * r, 2 * r);

            gc.setStroke(Color.DODGERBLUE);
            gc.setLineWidth(1.5 / zoom);
            gc.strokeOval(x2 - r, y2 - r, 2 * r, 2 * r);

            previousPoint = nextPoint;
        }
    }

    private void drawMarker(GraphicsContext gc, Point point, Color color, double zoom) {
        if (point == null) return;

        double x = convertToCanvasX(point.lon);
        double y = convertToCanvasY(point.lat);
        double r = 8.0 / zoom;

        // white circle slightly bigger
        gc.setFill(Color.WHITE);
        gc.fillOval(x - r - 2.0/zoom, y - r - 2.0/zoom, 2*r + 4.0/zoom, 2*r + 4.0/zoom);

        // colored circle on top
        gc.setFill(color);
        gc.fillOval(x - r, y - r, 2 * r, 2 * r);
    }

    private double convertToCanvasX(double lon) {
        return offsetX + mapPadding + (lon - minLon) * scale;
    }

    private double convertToCanvasY(double lat) {
        return offsetY + mapPadding + (maxLat - lat) * scale;
    }
    
    private void updateTransform() {

        scale = Math.min(
            (canvasWidth - 2 * mapPadding) / mapWidth,
            (canvasHeight - 2 * mapPadding) / mapHeight
        );
        double centerX = 0, centerY = 0;
        
        // get the average location of all stops
        // to center the map based on density
        for (Stop stop : engine.getDataset().stops.values()) {
            double lon = stop.stop_lon;
            double lat = stop.stop_lat;

            centerX += convertToCanvasX(lon);
            centerY += convertToCanvasY(lat);
        }
        centerX /= engine.getDataset().stops.size();
        centerY /= engine.getDataset().stops.size();

        offsetX = canvasWidth / 2 - centerX;
        offsetY = canvasHeight / 2 - centerY;
    }

    // sets the map size by checking the lowest and highest locations, making a rectangle
    private void calcBoundary() {
        minLat = 55.449;
        maxLat = 55.900;
        minLon = 11.953;
        maxLon = 12.920;

        mapWidth = maxLon - minLon;
        mapHeight = maxLat - minLat;
    }
}