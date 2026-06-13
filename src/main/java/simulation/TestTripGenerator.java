package simulation;

import engine.Point;
import gtfs.GTFSDataset;
import gtfs.Stop;
import utility.RouteUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class TestTripGenerator {
    private static GTFSDataset dataset;
    private static List<Stop> stopsList;
    private static List<TestTrip> testTrips;
    private static final Random RANDOM = new Random(11);

    // Returns random Stop entry as a Point
    private static Point randomPoint() {
        Stop randomStop = stopsList.get(RANDOM.nextInt(stopsList.size()));
        return new Point(randomStop.stop_lat, randomStop.stop_lon);
    }

    // Returns a Point within 7-10km of the given starting point
    private static Point findEndPoint(Point startPoint) {

        Point endPoint = randomPoint();
        double distance = 0;

        while (true) {

            // make sure endpoint is within 7-10km of startpoint
            distance = RouteUtils.HaversineDistance(startPoint.lat, startPoint.lon, endPoint.lat, endPoint.lon);

            // return if so
            if (distance >= 7 && distance <= 10) return endPoint;

            // redo if not
            endPoint = randomPoint();
        }

    }

    public static void main(String[] args) throws IOException {

        double distance = 0;
        int tripID = 0;

        dataset = GTFSDataset.loadFromZip("data/copenhagen_inner_gtfs.zip");
        stopsList = new ArrayList<Stop>(dataset.stops.values());
        testTrips = new ArrayList<>();

        // Adds 1000 TestTrips to testTrips by finding valid start and end points
        while (testTrips.size() < 1000) {

            tripID++;
            Point startPoint = randomPoint();
            Point endPoint = findEndPoint(startPoint);
            distance = RouteUtils.HaversineDistance(startPoint.lat, startPoint.lon, endPoint.lat, endPoint.lon);
            testTrips.add(new TestTrip(tripID, startPoint, endPoint));

            System.out.println("/// Test Trip #" + tripID + " ///");
            System.out.println("Start: " + startPoint.lat + ", " + startPoint.lon);
            System.out.println("End : " + endPoint.lat + ", " + endPoint.lon);
            System.out.println("Distance: " + distance);
            System.out.println();

        }

        // Save them as CSV for later use
        try (PrintWriter writer = new PrintWriter("data/Simulations/test_trips.csv")) {
            writer.println("tripID,startLat,startLon,endLat,endLon");
            for (TestTrip trip : testTrips) {
                writer.printf(
                        "%d,%f,%f,%f,%f%n",
                        trip.getID(),
                        trip.getStartingPoint().lat,
                        trip.getStartingPoint().lon,
                        trip.getEndingPoint().lat,
                        trip.getEndingPoint().lon
                );
            }
        }

    }
}
