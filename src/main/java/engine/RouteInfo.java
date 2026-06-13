package engine;
import java.util.Map;

public class RouteInfo {
    public String routeId;
    public String tripId;
    public String operator;
    public String shortName;
    public String longName;
    public String headSign;

    public RouteInfo(gtfs.Agency agency, gtfs.Route route, gtfs.Trip trip) {
        this.routeId = route.route_id;
        this.tripId = trip.trip_id;
        this.operator = agency.agency_name;
        this.shortName = route.route_short_name;

        String longNameValue = route.route_long_name;
        this.longName = (longNameValue == null || longNameValue.isEmpty()) ? route.route_short_name : longNameValue;
        
        this.headSign = trip.trip_headsign;
    }

    public Map<String, Object> toMap() {
        return Map.of(
            "routeId", routeId,
            "operator", operator,
            "shortName", shortName,
            "longName", longName,
            "headSign", headSign
        );
    }
    
}

