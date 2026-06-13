
package engine;

import java.util.List;
import java.util.Map;

public interface RouteFinder {
    List<RouteStep> findRoute(Point from, Point to, String startTime, int dayOfWeek);// this is for one route

    // for multiple routes
    List<List<RouteStep>> findRoutes(Point from, Point to, String startTime, int numberOfRoutes, int dayOfWeek);

    // For heatmap
    Map<String, Integer> getHeatmapData(Point from, String startTime, int dayOfWeek);
}
