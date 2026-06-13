package app;

import java.util.List;

import engine.Point;
import javafx.scene.paint.Color;

public class Shape {
    public List<Point> points;
    public Color color;
    
    /// checks if a point is inside the poly
    public boolean contains(double lat, double lon) {
        int n = points.size();
        boolean inside = false;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Point pi = points.get(i);
            Point pj = points.get(j);
            if ((pi.lat > lat) != (pj.lat > lat) &&
                (lon < (pj.lon - pi.lon) * (lat - pi.lat) / (pj.lat - pi.lat) + pi.lon)) {
                inside = !inside;
            }
        }
        return inside;
    }
}
