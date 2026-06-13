package engine;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RouteStep {
    public String mode;
    public Map<String, Object> to;
    public int duration;
    public String startTime;
    public String stopName;
    public RouteInfo route;
    public String shapeId;

    public List<Map<String, Object>> waypoints = new ArrayList<>();

    //walking
    public RouteStep(Map<String, Object> to, int duration, String startTime) {
        this.mode = "walk";
        this.to = to;
        this.duration = duration;
        this.startTime = startTime;
    }

    //riding
    public RouteStep(Map<String, Object> to, int duration, String startTime, String stopName, RouteInfo route) {
        this.mode = "ride";
        this.to = to;
        this.duration = duration;
        this.startTime = startTime;
        this.stopName = stopName;
        this.route = route;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("mode", mode);
        map.put("to", to);
        map.put("duration", duration);
        map.put("startTime", startTime);
        if(mode.equals("ride")) {
            map.put("stop", stopName);
            map.put("route", route.toMap());
        }
        return map;
    }

    @Override
    public String toString() {
        return "RouteStep{" +
                "mode='" + mode + '\'' +
                ", duration=" + duration +
                ", startTime='" + startTime + '\'' +
                ", stopName='" + stopName + '\'' +
                ", to=" + to +
                '}';
    }
}
