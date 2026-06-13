package utility;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import app.Shape;
import app.ShapeLoader;
import gtfs.GTFSDataset;
import gtfs.Route;
import gtfs.Stop;
import gtfs.StopTime;
import gtfs.Trip;
import javafx.scene.paint.Color;

public class GTFSFilterScript {

    public static void main(String[] args) throws Exception {

        System.out.println("Loading dataset...");

        GTFSDataset dataset = GTFSDataset.loadFromZip("data/GTFS.zip");

        System.out.println("Dataset loaded.");

        ShapeLoader shapeLoader = new ShapeLoader();
                // load boundary shape
        shapeLoader.loadGeoJsonShape("data/copenhagenFullBoundary.geojson", Color.LIGHTSEAGREEN);

        // first, filter stops to only those inside the administrative area        
        Map<String, Stop> filteredStops = new HashMap<>();
        for (Stop stop : dataset.stops.values()) {
            if (!isWithinBoundary(stop, shapeLoader.shapes))
                continue;

            filteredStops.put(stop.stop_id, stop);
        }

        System.out.println("Copenhagen stops: " + filteredStops.size());

        // then, add stop times, and save trip ids that reference the stops
        Map<String, List<StopTime>> filteredStopTimes = new HashMap<>();
        Set<String> cphTripIds = new HashSet<>();
        for (Map.Entry<String, List<StopTime>> entry : dataset.stopTimes.entrySet()) {

            String tripId = entry.getKey();
            List<StopTime> times = entry.getValue();

            List<StopTime> filtered = new ArrayList<>();

            for (StopTime st : times) {
                if (filteredStops.containsKey(st.stop_id)) {
                    filtered.add(st);
                    cphTripIds.add(tripId);
                }
            }

            if (!filtered.isEmpty()) {
                filteredStopTimes.put(tripId, filtered);
            }
        }

        System.out.println("Copenhagen trips: " + cphTripIds.size());

        // add the trips from the filtered trip ids
        Map<String, Trip> filteredTrips = new HashMap<>();
        for (String tripId : cphTripIds) {
            Trip t = dataset.trips.get(tripId);
            if (t != null) {
                filteredTrips.put(tripId, t);
            }
        }

        System.out.println("Filtered trips: " + filteredTrips.size());
        
        // lastly, add the routes that are referenced by the trips
        Map<String, Route> filteredRoutes = new HashMap<>();
        for (Trip trip : filteredTrips.values()) {
            Route r = dataset.routes.get(trip.route_id);
            if (r != null) {
                filteredRoutes.put(r.route_id, r);
            }
        }

        System.out.println("Filtered routes: " + filteredRoutes.size());

        GTFSDataset filteredDataset = new GTFSDataset();

        filteredDataset.stops = filteredStops;
        filteredDataset.trips = filteredTrips;
        filteredDataset.routes = filteredRoutes;
        filteredDataset.stopTimes = filteredStopTimes;
        filteredDataset.agency = dataset.agency;
        
        // probably should filter calendars too, but it doesnt
        // matter for now, its just a bit more data
        filteredDataset.calendar = dataset.calendar;

        System.out.println("Copenhagen GTFS dataset ready.");
        
        GTFSExporter.exportToZip(filteredDataset, "data/copenhagen_gtfs.zip");
        System.out.println("Export complete: copenhagen_gtfs.zip");
    }
    
    private static boolean isWithinBoundary(Stop stop, List<Shape> shapes) {
        for (Shape shape : shapes) {
            if (shape.contains(stop.stop_lat, stop.stop_lon)) {
                return true;
            }
        }
        return false;
    }
}