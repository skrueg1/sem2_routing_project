package gtfs;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import csv.CSVParser;
import csv.CSVRecord;

public class GTFSDataset {
    public Agency agency;
    public Map<String, Stop> stops = new HashMap<>();
    public Map<String, Route> routes = new HashMap<>();
    public Map<String, Trip> trips = new HashMap<>();
    public Map<String, List<StopTime>> stopTimes = new HashMap<>(); // list of stoptimes because they share trip_ids
    public Map<String, Calendar> calendar = new HashMap<>();
    public Map<String, List<ShapePoint>> shapes = new HashMap<>();

    /// Load GTFS dataset from a zip file
    public static GTFSDataset loadFromZip(String path) throws IOException {
        GTFSDataset dataset = new GTFSDataset();
        
        try (ZipFile zip = new ZipFile(path)) {
            dataset.loadAgency(zip);
            dataset.loadStops(zip);
            dataset.loadRoutes(zip);
            dataset.loadTrips(zip);
            dataset.loadStopTimes(zip);
            dataset.loadCalendar(zip);
            dataset.loadShapes(zip);

        }
        
        return dataset;
    }

    public GTFSDataset() {}
    
    // methods below are not very dry, but we dont need abstractions here
    private void loadAgency(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("agency.txt");
        if (entry == null) return;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8));
            CSVParser parser = new CSVParser(reader)) {

            for (CSVRecord r : parser) {

                Agency a = new Agency(
                    r.get("agency_id"),
                    r.get("agency_name"),
                    r.get("agency_url"),
                    r.get("agency_timezone")
                );

                this.agency = a;
                
                // one agency for now
                break;
            }
        }
    }
    
    private void loadStops(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("stops.txt");
        if (entry == null) return;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8));
            CSVParser parser = new CSVParser(reader)) {

            for (CSVRecord r : parser) {
                Stop stop = new Stop(
                    r.get("stop_id"),
                    r.get("stop_name"),
                    safeGet(r, "stop_desc"),
                    Double.parseDouble(r.get("stop_lat")),
                    Double.parseDouble(r.get("stop_lon")),
                    safeGet(r, "zone_id"),
                    safeGet(r, "stop_url")
                );

                stops.put(stop.stop_id, stop);
            }
        }
    }

    private void loadRoutes(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("routes.txt");
        if (entry == null) return;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8));
            CSVParser parser = new CSVParser(reader)) {

            for (CSVRecord r : parser) {

                Route route = new Route(
                    r.get("route_id"),
                    r.get("agency_id"),
                    r.get("route_short_name"),
                    r.get("route_long_name"),
                    safeGet(r, "route_desc"),
                    Integer.parseInt(r.get("route_type")),
                    safeGet(r, "route_url")
                );

                routes.put(route.route_id, route);
            }
        }
    }

    private void loadTrips(ZipFile zip) throws IOException {

        ZipEntry entry = zip.getEntry("trips.txt");
        if (entry == null) return;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8));
            CSVParser parser = new CSVParser(reader)) {

            for (CSVRecord r : parser) {

                Trip trip = new Trip(
                    r.get("route_id"),
                    r.get("service_id"),
                    r.get("trip_id"),
                    safeGet(r, "trip_headsign"),
                    safeGet(r, "direction_id"),
                    safeGet(r, "block_id"),
                    safeGet(r, "shape_id")
                );

                trips.put(trip.trip_id, trip);
            }
        }
    }
    
    private void loadStopTimes(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("stop_times.txt");
        if (entry == null) return;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8));
            CSVParser parser = new CSVParser(reader)) {

            for (CSVRecord r : parser) {

                StopTime st = new StopTime(
                    r.get("trip_id"),
                    r.get("arrival_time"),
                    r.get("departure_time"),
                    r.get("stop_id"),
                    Integer.parseInt(r.get("stop_sequence")),
                    safeGet(r, "stop_headsign"),
                    safeGet(r, "pickup_type"),
                    safeGet(r, "drop_off_time"),
                    safeGet(r, "shape_dist_traveled")
                );

                stopTimes
                    .computeIfAbsent(st.trip_id, k -> new ArrayList<>())
                    .add(st);
            }
        }

        // sort each trip’s stop times
        for (List<StopTime> list : stopTimes.values()) {
            list.sort(Comparator.comparingInt(a -> a.stop_sequence));
        }
    }

    private void loadCalendar(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("calendar.txt");
        if (entry == null) return;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8));
            CSVParser parser = new CSVParser(reader)) {

            for (CSVRecord r : parser) {

                Calendar c = new Calendar(
                    r.get("service_id"),
                    Integer.parseInt(r.get("monday")),
                    Integer.parseInt(r.get("tuesday")),
                    Integer.parseInt(r.get("wednesday")),
                    Integer.parseInt(r.get("thursday")),
                    Integer.parseInt(r.get("friday")),
                    Integer.parseInt(r.get("saturday")),
                    Integer.parseInt(r.get("sunday")),
                    r.get("start_date"),
                    r.get("end_date")
                );

                calendar.put(c.service_id, c);
            }
        }
    }

    private void loadShapes(ZipFile zip) throws IOException {
        ZipEntry entry = zip.getEntry("shapes.txt");
        if (entry == null) return;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8));
            CSVParser parser = new CSVParser(reader)) {

            for (CSVRecord r : parser) {
                ShapePoint sp = new ShapePoint(
                    r.get("shape_id"),
                    Double.parseDouble(r.get("shape_pt_lat")),
                    Double.parseDouble(r.get("shape_pt_lon")),
                    Integer.parseInt(r.get("shape_pt_sequence"))
                );
                shapes.computeIfAbsent(sp.shape_id, k -> new ArrayList<>())
                    .add(sp);
            }
        }

        // sort by sequence
        for (List<ShapePoint> list : shapes.values()) {
            list.sort(Comparator.comparingInt(s -> s.sequence));
        }
    }

    // mark a stop as closed
    public void closeStop(String stopId) {

        Stop stop = stops.get(stopId);

        if (stop != null) {
            stop.closed = true;
        }
    }

    // reopen a stop
    public void openStop(String stopId) {

        Stop stop = stops.get(stopId);

        if (stop != null) {
            stop.closed = false;
        }
    }

    // check if stop is closed
    public boolean isStopClosed(String stopId) {

        Stop stop = stops.get(stopId);

        return stop != null && stop.closed;
    }
    
    // this gets optional fields without crashing if they are missing
    // sometimes e.g. stop_headsign is missing from stop_times.txt
    // use this instead of r.get() for optional fields
    private String safeGet(CSVRecord r, String key) {
        try {
            return r.get(key);
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            return null;
        }
    }
}