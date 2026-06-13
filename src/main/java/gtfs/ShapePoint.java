package gtfs;

public class ShapePoint {
    public String shape_id;
    public double lat;
    public double lon;
    public int sequence;

    public ShapePoint(String shape_id, double lat, double lon, int sequence) {
        this.shape_id = shape_id;
        this.lat = lat;
        this.lon = lon;
        this.sequence = sequence;
    }
}
