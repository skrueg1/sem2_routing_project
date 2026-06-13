package app;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.leastfixedpoint.json.JSONReader;

import engine.Point;
import javafx.scene.paint.Color;

public class ShapeLoader {
    public List<Shape> shapes = new ArrayList<>();

    public void loadGeoJsonShape(String path, Color color) {
        System.out.println("Loading GeoJSON from: " + path);
        try {
            String jsonText = Files.readString(Paths.get(path));

            JSONReader reader = new JSONReader(new java.io.StringReader(jsonText));

            Object rootObj = reader.read();

            if (!(rootObj instanceof Map)) {
                throw new RuntimeException("Invalid GeoJSON root");
            }

            Map<?, ?> root = (Map<?, ?>) rootObj;
            List<?> features = (List<?>) root.get("features");

            for (Object obj : features) {
                Map<?, ?> feature = (Map<?, ?>) obj;
                Map<?, ?> geometry = (Map<?, ?>) feature.get("geometry");

                String type = (String) geometry.get("type");

                if ("Polygon".equals(type)) {
                    parsePolygon(geometry, color);
                }
            }

        } catch (IOException | RuntimeException e) {
        }
        System.out.println("Loaded " + shapes.size() + " shapes.");
    }

    private void parsePolygon(Map<?, ?> geometry, Color color) {

        List<?> coords = (List<?>) geometry.get("coordinates");
        List<?> outerRing = (List<?>) coords.get(0);

        Shape shape = new Shape();
        shape.points = new ArrayList<>();
        shape.color = color;

        for (Object p : outerRing) {
            List<?> point = (List<?>) p;

            double lon = ((Number) point.get(0)).doubleValue();
            double lat = ((Number) point.get(1)).doubleValue();

            shape.points.add(new Point(lat, lon));
        }

        shapes.add(shape);
    }
}