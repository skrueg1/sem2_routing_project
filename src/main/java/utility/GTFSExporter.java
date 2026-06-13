package utility;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import gtfs.Agency;
import gtfs.Calendar;
import gtfs.GTFSDataset;
import gtfs.Route;
import gtfs.Stop;
import gtfs.StopTime;
import gtfs.Trip;

public class GTFSExporter {

    public static void exportToZip(GTFSDataset ds, String outputPath) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(outputPath))) {
            writeAgency(ds, zip);
            writeStops(ds, zip);
            writeRoutes(ds, zip);
            writeTrips(ds, zip);
            writeStopTimes(ds, zip);
            writeCalendar(ds, zip);
        }
    }

    private static void writeAgency(GTFSDataset ds, ZipOutputStream zip) throws Exception {
        zip.putNextEntry(new ZipEntry("agency.txt"));

        PrintWriter out = writer(zip);

        Agency a = ds.agency;

        out.println("agency_id,agency_name,agency_url,agency_timezone");
        out.printf("%s,%s,%s,%s%n",
            safe(a.agency_id),
            safe(a.agency_name),
            safe(a.agency_url),
            safe(a.agency_timezone));

        out.flush();
        zip.closeEntry();
    }

    private static void writeStops(GTFSDataset ds, ZipOutputStream zip) throws Exception {
        zip.putNextEntry(new ZipEntry("stops.txt"));

        PrintWriter out = writer(zip);

        out.println("stop_id,stop_name,stop_desc,stop_lat,stop_lon,zone_id,stop_url");

        for (Stop s : ds.stops.values()) {
            out.printf("%s,%s,%s,%f,%f,%s,%s%n",
                safe(s.stop_id),
                safe(s.stop_name),
                safe(s.stop_desc),
                s.stop_lat,
                s.stop_lon,
                safe(s.zone_id),
                safe(s.stop_url));
        }

        out.flush();
        zip.closeEntry();
    }

    private static void writeRoutes(GTFSDataset ds, ZipOutputStream zip) throws Exception {
        zip.putNextEntry(new ZipEntry("routes.txt"));

        PrintWriter out = writer(zip);

        out.println("route_id,agency_id,route_short_name,route_long_name,route_desc,route_type,route_url");

        for (Route r : ds.routes.values()) {
            out.printf("%s,%s,%s,%s,%s,%d,%s%n",
                safe(r.route_id),
                safe(r.agency_id),
                safe(r.route_short_name),
                safe(r.route_long_name),
                safe(r.route_desc),
                r.route_type,
                safe(r.route_url));
        }

        out.flush();
        zip.closeEntry();
    }

    private static void writeTrips(GTFSDataset ds, ZipOutputStream zip) throws Exception {
        zip.putNextEntry(new ZipEntry("trips.txt"));

        PrintWriter out = writer(zip);

        out.println("route_id,service_id,trip_id,trip_headsign,direction_id,block_id,shape_id");

        for (Trip t : ds.trips.values()) {
            out.printf("%s,%s,%s,%s,%s,%s,%s%n",
                safe(t.route_id),
                safe(t.service_id),
                safe(t.trip_id),
                safe(t.trip_headsign),
                safe(t.direction_id),
                safe(t.block_id),
                safe(t.shape_id));
        }

        out.flush();
        zip.closeEntry();
    }

    private static void writeStopTimes(GTFSDataset ds, ZipOutputStream zip) throws Exception {
        zip.putNextEntry(new ZipEntry("stop_times.txt"));

        PrintWriter out = writer(zip);

        out.println("trip_id,arrival_time,departure_time,stop_id,stop_sequence,stop_headsign,pickup_type,drop_off_time,shape_dist_traveled");

        for (Map.Entry<String, List<StopTime>> e : ds.stopTimes.entrySet()) {
            for (StopTime st : e.getValue()) {

                out.printf("%s,%s,%s,%s,%d,%s,%s,%s,%s%n",
                    safe(st.trip_id),
                    safe(st.arrival_time),
                    safe(st.departure_time),
                    safe(st.stop_id),
                    st.stop_sequence,
                    safe(st.stop_headsign),
                    safe(st.pickup_type),
                    safe(st.drop_off_time),
                    safe(st.shape_dist_traveled));
            }
        }

        out.flush();
        zip.closeEntry();
    }

    private static void writeCalendar(GTFSDataset ds, ZipOutputStream zip) throws Exception {
        zip.putNextEntry(new ZipEntry("calendar.txt"));

        PrintWriter out = writer(zip);

        out.println("service_id,monday,tuesday,wednesday,thursday,friday,saturday,sunday,start_date,end_date");

        for (Calendar c : ds.calendar.values()) {
            out.printf("%s,%d,%d,%d,%d,%d,%d,%d,%s,%s%n",
                safe(c.service_id),
                c.monday,
                c.tuesday,
                c.wednesday,
                c.thursday,
                c.friday,
                c.saturday,
                c.sunday,
                safe(c.start_date),
                safe(c.end_date));
        }

        out.flush();
        zip.closeEntry();
    }

    private static PrintWriter writer(ZipOutputStream zip) {
        return new PrintWriter(
                new OutputStreamWriter(zip, StandardCharsets.UTF_8),
                true
        );
    }

    private static String safe(String s) {
        return s == null ? "" : s.replace(",", " ");
    }
}