package gtfs;

public class Stop {
    public String stop_id;
    public String stop_name;
    public String stop_desc;
    public double stop_lat;
    public double stop_lon;
    public String zone_id;
    public String stop_url;
    public boolean closed = false; // added this to track if a stop has been closed

    public Stop(String stop_id, String stop_name, String stop_desc, double stop_lat, double stop_lon, String zone_id, String stop_url) {
        this.stop_id = stop_id;
        this.stop_name = stop_name;
        this.stop_desc = stop_desc;
        this.stop_lat = stop_lat;
        this.stop_lon = stop_lon;
        this.zone_id = zone_id;
        this.stop_url = stop_url;
    }
}
