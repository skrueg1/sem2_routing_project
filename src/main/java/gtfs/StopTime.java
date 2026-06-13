package gtfs;

public class StopTime {
    public String trip_id;
    public String arrival_time;
    public String departure_time;
    public String stop_id;
    public int stop_sequence;
    public String stop_headsign;
    public String pickup_type;
    public String drop_off_time;
    public String shape_dist_traveled;
    
    public StopTime(String trip_id, String arrival_time, String departure_time, String stop_id, int stop_sequence, String stop_headsign, String pickup_type, String drop_off_type, String shape_dist_traveled) {
        this.trip_id = trip_id;
        this.arrival_time = arrival_time;
        this.departure_time = departure_time;
        this.stop_id = stop_id;
        this.stop_sequence = stop_sequence;
        this.stop_headsign = stop_headsign;
        this.pickup_type = pickup_type;
        this.drop_off_time = drop_off_type;
        this.shape_dist_traveled = shape_dist_traveled;
    }
}
