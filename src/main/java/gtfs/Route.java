package gtfs;

public class Route {
    public String route_id;
    public String agency_id;
    public String route_short_name;
    public String route_long_name;
    public String route_desc;
    public int route_type;
    public String route_url;
    
    public Route(String route_id, String agency_id, String route_short_name, String route_long_name, String route_desc, int route_type, String route_url) {
        this.route_id = route_id;
        this.agency_id = agency_id;
        this.route_short_name = route_short_name;
        this.route_long_name = route_long_name;
        this.route_desc = route_desc;
        this.route_type = route_type;
        this.route_url = route_url;
    }
}
