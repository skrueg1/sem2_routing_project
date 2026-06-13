package engine;

import gtfs.*;
import gtfs.Calendar;
import org.junit.jupiter.api.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

//Unit tests for the Routing Engine.

@DisplayName("Routing Engine – Unit Tests")
class RoutingEngineTest {

    // Stop(id, name, desc, lat, lon, zone_id, stop_url) 
    private static Stop makeStop(String id, String name, double lat, double lon) {
        return new Stop(id, name, "", lat, lon, "", "");
    }

    // Agency(agency_id, agency_name, agency_url, agency_timezone) 
    private static Agency makeAgency(String name) {
        return new Agency("AG1", name, "", "Europe/Copenhagen");
    }

    // Route(route_id, agency_id, short, long, desc, type, url) 
    private static Route makeRoute(String id, String shortName, String longName) {
        return new Route(id, "AG1", shortName, longName, "", 3, "");
    }

    // Trip(route_id, service_id, trip_id, headsign, direction_id, block_id, shape_id) 
    private static Trip makeTrip(String tripId, String routeId, String headsign) {
        return new Trip(routeId, "SVC1", tripId, headsign, "0", "", "");
    }


    // Calendar(service_id, mon, tue, wed, thu, fri, sat, sun, start, end) — all days active 
    private static Calendar makeCalendar(String serviceId) {
        return new Calendar(serviceId, 1, 1, 1, 1, 1, 1, 1, "20260101", "20271231");
    }
    // StopTime(trip_id, arrival, departure, stop_id, seq, headsign, pickup, dropoff, dist) 
    private static StopTime makeStopTime(String tripId, String stopId,
                                         String arrival, String departure, int seq) {
        return new StopTime(tripId, arrival, departure, stopId, seq, "", "", "", "");
    }

    // Empty dataset – triggers walk-only routing 
    private static GTFSDataset emptyDataset() {
        GTFSDataset ds = new GTFSDataset();
        ds.stops     = new HashMap<>();
        ds.routes    = new HashMap<>();
        ds.trips     = new HashMap<>();
        ds.stopTimes = new HashMap<>();
        ds.calendar  = new HashMap<>();
        ds.agency    = null;
        return ds;
    }

    private static GTFSDataset twoStopDataset() {
        GTFSDataset ds = new GTFSDataset();

        ds.stops = new HashMap<>();
        ds.stops.put("A", makeStop("A", "Stop Alpha", 55.6415, 12.5415));
        ds.stops.put("B", makeStop("B", "Stop Beta",  55.7485, 12.6185));

        ds.agency = makeAgency("Copenhagen Transit");

        ds.routes = new HashMap<>();
        ds.routes.put("R1", makeRoute("R1", "1", "Alpha to Beta Express"));

        ds.trips = new HashMap<>();
        ds.trips.put("T1", makeTrip("T1", "R1", "Beta"));

        ds.stopTimes = new HashMap<>();
        ds.stopTimes.put("T1", List.of(
            makeStopTime("T1", "A", "09:00", "09:00", 1),
            makeStopTime("T1", "B", "09:20", "09:20", 2)
        ));

        // Calendar: SVC1 runs every day — required by isTripActiveOnDay()
        ds.calendar = new HashMap<>();
        ds.calendar.put("SVC1", makeCalendar("SVC1"));

        return ds;
    }

    // Query points: ~159 min walk apart; stops are only ~2 min walk away
    private static final Point TRANSIT_FROM = new Point(55.6400, 12.5400);
    private static final Point TRANSIT_TO   = new Point(55.7500, 12.6200);


    @Nested
    @DisplayName("Walk-only routing")
    class WalkOnlyTests {

        @Test
        @DisplayName("Returns exactly one step when dataset has no stops")
        void testWalkOnlyEmptyDataset() {
            GTFSRouteFinder finder = new GTFSRouteFinder(emptyDataset());
            List<RouteStep> steps = finder.findRoute(
                new Point(55.6761, 12.5683), new Point(55.6800, 12.5700), "08:00", 0);
            assertEquals(1, steps.size());
            assertEquals("walk", steps.get(0).mode);
        }

        @Test
        @DisplayName("Duration > 0 for non-identical points")
        void testWalkOnlyPositiveDuration() {
            GTFSRouteFinder finder = new GTFSRouteFinder(emptyDataset());
            List<RouteStep> steps = finder.findRoute(
                new Point(55.6761, 12.5683), new Point(55.6980, 12.5500), "09:00", 0);
            assertTrue(steps.get(0).duration > 0);
        }

        @Test
        @DisplayName("Same start and end point gives duration 0")
        void testWalkOnlySamePoint() {
            GTFSRouteFinder finder = new GTFSRouteFinder(emptyDataset());
            Point p = new Point(55.6761, 12.5683);
            List<RouteStep> steps = finder.findRoute(p, p, "10:00", 0);
            assertEquals(0, steps.get(0).duration);
        }

        @Test
        @DisplayName("startTime is preserved in the walk step")
        void testWalkOnlyStartTime() {
            GTFSRouteFinder finder = new GTFSRouteFinder(emptyDataset());
            List<RouteStep> steps = finder.findRoute(
                new Point(55.6761, 12.5683), new Point(55.6800, 12.5700), "14:30", 0);
            assertEquals("14:30", steps.get(0).startTime);
        }

        @Test
        @DisplayName("Destination coordinates appear in the step's 'to' map")
        void testWalkOnlyDestinationCoords() {
            GTFSRouteFinder finder = new GTFSRouteFinder(emptyDataset());
            List<RouteStep> steps = finder.findRoute(
                new Point(55.6761, 12.5683), new Point(55.6900, 12.5800), "08:00", 0);
            Map<String, Object> dest = steps.get(0).to;
            assertNotNull(dest);
            assertEquals(55.6900, (double) dest.get("lat"), 1e-9);
            assertEquals(12.5800, (double) dest.get("lon"), 1e-9);
        }
    }

    @Nested
    @DisplayName("Transit routing")
    class TransitRoutingTests {

        @Test
        @DisplayName("Route is returned (not empty)")
        void testRouteNotEmpty() {
            GTFSRouteFinder finder = new GTFSRouteFinder(twoStopDataset());
            List<RouteStep> steps = finder.findRoute(
                TRANSIT_FROM, TRANSIT_TO, "08:00", 0);
            assertFalse(steps.isEmpty());
        }

        @Test
        @DisplayName("At least one step has mode 'ride'")
        void testRouteContainsRideStep() {
            GTFSRouteFinder finder = new GTFSRouteFinder(twoStopDataset());
            List<RouteStep> steps = finder.findRoute(
                TRANSIT_FROM, TRANSIT_TO, "08:00", 0);
            assertTrue(steps.stream().anyMatch(s -> "ride".equals(s.mode)));
        }

        @Test
        @DisplayName("Ride step has positive duration")
        void testRideStepPositiveDuration() {
            GTFSRouteFinder finder = new GTFSRouteFinder(twoStopDataset());
            List<RouteStep> steps = finder.findRoute(
                TRANSIT_FROM, TRANSIT_TO, "08:00", 0);
            steps.stream()
                 .filter(s -> "ride".equals(s.mode))
                 .forEach(s -> assertTrue(s.duration > 0));
        }

        @Test
        @DisplayName("Transit total time is not slower than pure walking")
        void testTransitNotSlowerThanWalking() {
            GTFSRouteFinder transitFinder = new GTFSRouteFinder(twoStopDataset());
            GTFSRouteFinder walkFinder    = new GTFSRouteFinder(emptyDataset());

            int transitTotal = transitFinder.findRoute(TRANSIT_FROM, TRANSIT_TO, "08:00", 0)
                .stream().mapToInt(s -> s.duration).sum();
            int walkTotal = walkFinder.findRoute(TRANSIT_FROM, TRANSIT_TO, "08:00", 0)
                .stream().mapToInt(s -> s.duration).sum();

            assertTrue(transitTotal <= walkTotal,
                "Transit (" + transitTotal + " min) should not exceed walking (" + walkTotal + " min)");
        }

    }

    @Nested
    @DisplayName("RouteStep serialisation (toMap)")
    class RouteStepSerializationTests {

        @Test
        @DisplayName("Walk step toMap has keys: mode, to, duration, startTime")
        void testWalkStepMapKeys() {
            RouteStep step = new RouteStep(
                Map.of("lat", 55.6, "lon", 12.5), 10, "09:00");
            Map<String, Object> map = step.toMap();
            assertTrue(map.containsKey("mode"));
            assertTrue(map.containsKey("to"));
            assertTrue(map.containsKey("duration"));
            assertTrue(map.containsKey("startTime"));
        }

        @Test
        @DisplayName("Walk step toMap: mode is 'walk'")
        void testWalkStepMode() {
            RouteStep step = new RouteStep(
                Map.of("lat", 55.6, "lon", 12.5), 10, "09:00");
            assertEquals("walk", step.toMap().get("mode"));
        }

        @Test
        @DisplayName("Walk step toMap does NOT contain 'stop' or 'route'")
        void testWalkStepNoRideKeys() {
            RouteStep step = new RouteStep(
                Map.of("lat", 55.6, "lon", 12.5), 10, "09:00");
            Map<String, Object> map = step.toMap();
            assertFalse(map.containsKey("stop"));
            assertFalse(map.containsKey("route"));
        }

        @Test
        @DisplayName("Ride step toMap has keys: mode, to, duration, startTime, stop, route")
        void testRideStepMapKeys() {
            RouteInfo info = new RouteInfo(
                makeAgency("TestAgency"),
                makeRoute("R1", "5", "Test Route"),
                makeTrip("T1", "R1", "North")
            );
            RouteStep step = new RouteStep(
                Map.of("lat", 55.7, "lon", 12.6), 15, "10:00", "Central", info);
            Map<String, Object> map = step.toMap();
            assertTrue(map.containsKey("mode"));
            assertTrue(map.containsKey("to"));
            assertTrue(map.containsKey("duration"));
            assertTrue(map.containsKey("startTime"));
            assertTrue(map.containsKey("stop"));
            assertTrue(map.containsKey("route"));
        }

        @Test
        @DisplayName("Ride step toMap: mode is 'ride'")
        void testRideStepMode() {
            RouteInfo info = new RouteInfo(
                makeAgency("TestAgency"),
                makeRoute("R1", "5", "Test Route"),
                makeTrip("T1", "R1", "North")
            );
            RouteStep step = new RouteStep(
                Map.of("lat", 55.7, "lon", 12.6), 15, "10:00", "Central", info);
            assertEquals("ride", step.toMap().get("mode"));
        }

        @Test
        @DisplayName("Ride step toMap: 'stop' value matches the stop name")
        void testRideStepStopInMap() {
            RouteInfo info = new RouteInfo(
                makeAgency("TestAgency"),
                makeRoute("R1", "5", "Test Route"),
                makeTrip("T1", "R1", "North")
            );
            RouteStep step = new RouteStep(
                Map.of("lat", 55.7, "lon", 12.6), 15, "10:00", "Central Station", info);
            assertEquals("Central Station", step.toMap().get("stop"));
        }
    }

    @Nested
    @DisplayName("RouteInfo serialisation (toMap)")
    class RouteInfoSerializationTests {

        private RouteInfo buildInfo(String agencyName, String shortName,
                                    String longName, String headsign) {
            return new RouteInfo(
                makeAgency(agencyName),
                makeRoute("R1", shortName, longName),
                makeTrip("T1", "R1", headsign)
            );
        }

        @Test
        @DisplayName("toMap contains keys: operator, shortName, longName, headSign")
        void testRouteInfoMapKeys() {
            Map<String, Object> map = buildInfo("Movia", "7A", "Nørreport–Airport", "Airport").toMap();
            assertTrue(map.containsKey("operator"));
            assertTrue(map.containsKey("shortName"));
            assertTrue(map.containsKey("longName"));
            assertTrue(map.containsKey("headSign"));
        }

        @Test
        @DisplayName("operator matches agency_name")
        void testRouteInfoOperator() {
            assertEquals("DSB",
                buildInfo("DSB", "S", "S-Tog Line C", "Klampenborg").toMap().get("operator"));
        }

        @Test
        @DisplayName("shortName matches route_short_name")
        void testRouteInfoShortName() {
            assertEquals("S",
                buildInfo("DSB", "S", "S-Tog Line C", "Klampenborg").toMap().get("shortName"));
        }

        @Test
        @DisplayName("headSign matches trip_headsign")
        void testRouteInfoHeadSign() {
            assertEquals("Klampenborg",
                buildInfo("DSB", "S", "S-Tog Line C", "Klampenborg").toMap().get("headSign"));
        }
    }

    @Nested
    @DisplayName("Overnight/past-midnight GTFS times")
    class OvernightTimeTests {

        private GTFSDataset overnightDataset() {
            GTFSDataset ds = new GTFSDataset();

            ds.stops = new HashMap<>();
            ds.stops.put("A", makeStop("A", "Night Start", 55.676, 12.568));
            ds.stops.put("B", makeStop("B", "Night End",   55.693, 12.601));

            ds.agency = makeAgency("NightBus");

            ds.routes = new HashMap<>();
            ds.routes.put("NR1", makeRoute("NR1", "N1", "Night Route 1"));

            ds.trips = new HashMap<>();
            ds.trips.put("NT1", makeTrip("NT1", "NR1", "Night End"));

            ds.stopTimes = new HashMap<>();
            ds.stopTimes.put("NT1", List.of(
                makeStopTime("NT1", "A", "25:00", "25:00", 1),
                makeStopTime("NT1", "B", "25:15", "25:15", 2)
            ));

            ds.calendar = new HashMap<>();
            ds.calendar.put("SVC1", makeCalendar("SVC1"));

            return ds;
        }

        @Test
        @DisplayName("25:xx times are accepted without exception during graph build")
        void testOvernightTimesNoException() {
            assertDoesNotThrow(() -> new GTFSRouteFinder(overnightDataset()));
        }

        @Test
        @DisplayName("Overnight trip: findRoute at 01:00 returns a result")
        void testOvernightRouteFound() {
            GTFSRouteFinder finder = new GTFSRouteFinder(overnightDataset());
            List<RouteStep> steps = finder.findRoute(
                new Point(55.676, 12.568), new Point(55.693, 12.601), "01:00", 0);
            assertFalse(steps.isEmpty());
        }
    }

    @Nested
    @DisplayName("Haversine distance sanity checks")
    class HaversineTests {

        @Test
        @DisplayName("Short distance (~0.44 km) → walking time between 3 and 10 min")
        void testShortDistance() {
            GTFSRouteFinder finder = new GTFSRouteFinder(emptyDataset());
            List<RouteStep> steps = finder.findRoute(
                new Point(55.6761, 12.5683), new Point(55.6728, 12.5655), "09:00", 0);
            int minutes = steps.stream().mapToInt(s -> s.duration).sum();
            assertTrue(minutes >= 3 && minutes <= 10,
                "Expected ~5 min, got " + minutes);
        }

        @Test
        @DisplayName("Longer distance (~10 km) → walking time between 80 and 150 min")
        void testLongerDistance() {
            GTFSRouteFinder finder = new GTFSRouteFinder(emptyDataset());
            List<RouteStep> steps = finder.findRoute(
                new Point(55.6761, 12.5683), new Point(55.6273, 12.6502), "09:00", 0);
            int minutes = steps.stream().mapToInt(s -> s.duration).sum();
            assertTrue(minutes >= 80 && minutes <= 150,
                "Expected ~110 min, got " + minutes);
        }
    }

}