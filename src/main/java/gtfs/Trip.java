package gtfs;

public class Trip {
    public String route_id;
    public String service_id;
    public String trip_id;
    public String trip_headsign;
    public String direction_id;
    public String block_id;
    public String shape_id;
    
    public Trip(String route_id, String service_id, String trip_id, String trip_headsign, String direction_id, String block_id, String shape_id) {
        this.route_id = route_id;
        this.service_id = service_id;
        this.trip_id = trip_id;
        this.trip_headsign = trip_headsign;
        this.direction_id = direction_id;
        this.block_id = block_id;
        this.shape_id = shape_id;
    }
}
