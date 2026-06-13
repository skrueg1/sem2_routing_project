package gtfs;

public class Calendar {
    public String service_id;
    public int monday;
    public int tuesday;
    public int wednesday;
    public int thursday;
    public int friday;
    public int saturday;
    public int sunday;
    public String start_date;
    public String end_date;
    
    public Calendar(String service_id, int monday, int tuesday, int wednesday, int thursday, int friday, int saturday, int sunday, String start_date, String end_date) {
        this.service_id = service_id;
        this.monday = monday;
        this.tuesday = tuesday;
        this.wednesday = wednesday;
        this.thursday = thursday;
        this.friday = friday;
        this.saturday = saturday;
        this.sunday = sunday;
        this.start_date = start_date;
        this.end_date = end_date;
    }
}
