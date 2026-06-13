package simulation;

import engine.GTFSRouteFinder;
import engine.Point;
import engine.RouteStep;
import gtfs.GTFSDataset;
import gtfs.Route;
import gtfs.Stop;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ClosureImpactAnalyzer {
    private static final String DEFAULT_GTFS = "data/copenhagen_inner_gtfs.zip";
    private static final String DEFAULT_TRIPS = "data/Simulations/test_trips.csv";
    private static final String DEFAULT_ROUTE_OUTPUT = "data/Simulations/route_closure_results.csv";
    private static final String DEFAULT_START_TIME = "08:30";
    private static final int DEFAULT_DAY_OF_WEEK = 0;
    private static final int DEFAULT_TOP_COUNT = 15;
    private static final int DEFAULT_MAX_STOP_CANDIDATES = 20;

    public static void main(String[] args) throws IOException {
        Config config = Config.fromArgs(args);
        GTFSDataset dataset = GTFSDataset.loadFromZip(config.gtfsPath);
        List<TestTrip> trips = loadTrips(config.tripsPath);
        if (config.tripLimit > 0 && config.tripLimit < trips.size()) {
            trips = new ArrayList<>(trips.subList(0, config.tripLimit));
        }

        GTFSRouteFinder baselineFinder = new GTFSRouteFinder(dataset);
        AverageResult baseline = averageDuration(baselineFinder, trips, config.startTime, config.dayOfWeek);
        System.out.printf(Locale.US, "Baseline average travel time: %.2f minutes (%d trips)%n",
                baseline.averageMinutes,
                trips.size());

        List<ClosureResult> routeResults = analyzeRouteClosures(dataset, trips, baseline, config);
        writeRouteResults(config.routeOutputPath, baseline.averageMinutes, routeResults);

        List<ClosureResult> stopResults = List.of();
        if (config.rankStops) {
            Map<String, StopUsage> baselineStopUsage = collectBaselineStopUsage(dataset, baseline.routes);
            List<StopUsage> stopCandidates = new ArrayList<>(baselineStopUsage.values());
                    
            stopResults = analyzeStopClosures(dataset, trips, baseline.averageMinutes, stopCandidates, baseline, config);
            writeStopResults(config.stopOutputPath, baseline.averageMinutes, stopResults);
        }

        printSummary(config, baseline, routeResults, stopResults);
    }

    private static List<TestTrip> loadTrips(String tripsPath) throws IOException {
        List<TestTrip> trips = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(Path.of(tripsPath), StandardCharsets.UTF_8)) {
            String line = reader.readLine();
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split(",");
                int tripId = Integer.parseInt(parts[0]);
                Point start = new Point(Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
                Point end = new Point(Double.parseDouble(parts[3]), Double.parseDouble(parts[4]));
                trips.add(new TestTrip(tripId, start, end));
            }
        }
        return trips;
    }

    private static AverageResult averageDuration(GTFSRouteFinder finder, List<TestTrip> trips, String startTime, int dayOfWeek) {
        int totalMinutes = 0;
        int walkOnlyTrips = 0;
        List<List<RouteStep>> routes = new ArrayList<>();

        for (TestTrip trip : trips) {
            List<RouteStep> route = finder.findRoute(trip.getStartingPoint(), trip.getEndingPoint(), startTime, dayOfWeek);
            routes.add(route);
            totalMinutes += totalDuration(route);
            if (route.stream().noneMatch(step -> "ride".equals(step.mode))) {
                walkOnlyTrips++;
            }
        }

        return new AverageResult(totalMinutes, (double) totalMinutes / trips.size(), walkOnlyTrips, routes);
    }

    private static int totalDuration(List<RouteStep> route) {
        return route.stream().mapToInt(step -> step.duration).sum();
    }

    private static Map<String, StopUsage> collectBaselineStopUsage(GTFSDataset dataset, List<List<RouteStep>> baselineRoutes) {
        Map<String, Stop> stopsByCoordinate = new HashMap<>();
        for (Stop stop : dataset.stops.values()) {
            stopsByCoordinate.put(coordinateKey(stop.stop_lat, stop.stop_lon), stop);
        }

        Map<String, StopUsage> usage = new HashMap<>();
        for (List<RouteStep> route : baselineRoutes) {
            Set<String> stopsUsedInTrip = new HashSet<>();
            for (RouteStep step : route) {
                Object lat = step.to.get("lat");
                Object lon = step.to.get("lon");
                if (!(lat instanceof Number) || !(lon instanceof Number)) {
                    continue;
                }
                Stop stop = stopsByCoordinate.get(coordinateKey(((Number) lat).doubleValue(), ((Number) lon).doubleValue()));
                if (stop != null) {
                    stopsUsedInTrip.add(stop.stop_id);
                }
            }
            for (String stopId : stopsUsedInTrip) {
                Stop stop = dataset.stops.get(stopId);
                usage.computeIfAbsent(stopId, id -> new StopUsage(stop, 0)).usedByTrips++;
            }
        }
        return usage;
    }

    private static String coordinateKey(double lat, double lon) {
        return String.format(Locale.US, "%.6f,%.6f", lat, lon);
    }

    private static Map<String, Integer> collectBaselineRouteUsage(List<List<RouteStep>> baselineRoutes) {
        Map<String, Integer> usage = new HashMap<>();
        for (List<RouteStep> route : baselineRoutes) {
            Set<String> routesUsedInTrip = new HashSet<>();
            for (RouteStep step : route) {
                if ("ride".equals(step.mode) && step.route != null && step.route.routeId != null) {
                    routesUsedInTrip.add(step.route.routeId);
                }
            }
            for (String routeId : routesUsedInTrip) {
                usage.merge(routeId, 1, Integer::sum);
            }
        }
        return usage;
    }

    private static List<ClosureResult> analyzeRouteClosures(
            GTFSDataset dataset,
            List<TestTrip> trips,
            AverageResult baseline,
            Config config) {
        List<ClosureResult> results = new ArrayList<>();
        GTFSRouteFinder finder = new GTFSRouteFinder(dataset);
        Map<String, Integer> routeUsage = collectBaselineRouteUsage(baseline.routes);

        // int routeDone = 0; // can delete later
        for (Route route : dataset.routes.values()) {
            // routeDone++; // can delete later
            // ƒ("Route " + routeDone + "/" + dataset.routes.size() + " — " + route.route_short_name); // can delete later
            if (!config.closeRouteId.isBlank() && !route.route_id.equals(config.closeRouteId)) {
                continue;
            }
            if (config.closeRouteId.isBlank() && !config.includeUnusedRoutes && !routeUsage.containsKey(route.route_id)) {
                continue;
            }
            finder.setClosedRouteIds(Set.of(route.route_id));
            int changedTotalMinutes = baseline.totalMinutes;
            int changedWalkOnlyTrips = baseline.walkOnlyTrips;

            for (int i = 0; i < trips.size(); i++) {
                List<RouteStep> baselineRoute = baseline.routes.get(i);
                if (!routeIdsFor(baselineRoute).contains(route.route_id)) {
                    continue;
                }

                TestTrip trip = trips.get(i);
                int oldDuration = totalDuration(baselineRoute);
                boolean oldWalkOnly = baselineRoute.stream().noneMatch(step -> "ride".equals(step.mode));

                List<RouteStep> changedRoute = finder.findRoute(
                        trip.getStartingPoint(),
                        trip.getEndingPoint(),
                        config.startTime,
                        config.dayOfWeek);
                int newDuration = totalDuration(changedRoute);
                boolean newWalkOnly = changedRoute.stream().noneMatch(step -> "ride".equals(step.mode));

                changedTotalMinutes += newDuration - oldDuration;
                if (oldWalkOnly && !newWalkOnly) {
                    changedWalkOnlyTrips--;
                } else if (!oldWalkOnly && newWalkOnly) {
                    changedWalkOnlyTrips++;
                }
            }

            double changedAverage = (double) changedTotalMinutes / trips.size();
            results.add(new ClosureResult(
                    route.route_id,
                    route.route_short_name,
                    routeUsage.getOrDefault(route.route_id, 0) + " baseline trips used this route",
                    changedAverage,
                    changedAverage - baseline.averageMinutes,
                    changedWalkOnlyTrips));
        }
        finder.setClosedRouteIds(Set.of());
        results.sort(Comparator.comparingDouble(r -> r.deltaMinutes));
        return results;
    }

    private static Set<String> routeIdsFor(List<RouteStep> route) {
        Set<String> routeIds = new HashSet<>();
        for (RouteStep step : route) {
            if ("ride".equals(step.mode) && step.route != null && step.route.routeId != null) {
                routeIds.add(step.route.routeId);
            }
        }
        return routeIds;
    }

    private static void writeRouteResults(String outputPath, double baselineAverage, List<ClosureResult> results) throws IOException {
        Path path = Path.of(outputPath);
        Files.createDirectories(path.getParent());
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8))) {
            writer.println("routeID,routeName,baselineAvgMinutes,closedRouteAvgMinutes,deltaMinutes,walkOnlyTrips,note");
            for (ClosureResult result : results) {
                writer.printf(
                        Locale.US,
                        "%s,%s,%.2f,%.2f,%.2f,%d,%s%n",
                        csv(result.id),
                        csv(result.name),
                        baselineAverage,
                        result.averageMinutes,
                        result.deltaMinutes,
                        result.walkOnlyTrips,
                        csv(result.note));
            }
        }
    }

    private static void writeStopResults(String outputPath, double baselineAverage, List<ClosureResult> results) throws IOException {
        Path path = Path.of(outputPath);
        Files.createDirectories(path.getParent());
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(path, StandardCharsets.UTF_8))) {
            writer.println("stopID,stopName,baselineAvgMinutes,closedStopAvgMinutes,deltaMinutes,walkOnlyTrips,note");
            for (ClosureResult result : results) {
                writer.printf(
                        Locale.US,
                        "%s,%s,%.2f,%.2f,%.2f,%d,%s%n",
                        csv(result.id),
                        csv(result.name),
                        baselineAverage,
                        result.averageMinutes,
                        result.deltaMinutes,
                        result.walkOnlyTrips,
                        csv(result.note));
            }
        }
    }

    private static List<ClosureResult> analyzeStopClosures(
            GTFSDataset dataset,
            List<TestTrip> trips,
            double baselineAverage,
            List<StopUsage> stopCandidates,
            AverageResult baseline,
            Config config) {

        // build stop_id -> set of trip indices that used it
        Map<String, Set<Integer>> stopToTripIndices = new HashMap<>();
        for (StopUsage su : stopCandidates) {
            stopToTripIndices.put(su.stop.stop_id, new HashSet<>());
        }

        // figure out which trips used which stops
        for (int i = 0; i < baseline.routes.size(); i++) {
        for (RouteStep step : baseline.routes.get(i)) {
            if (!"ride".equals(step.mode)) continue;
            String tripId = step.route != null ? step.route.tripId : null;
            if (tripId == null) continue;
            List<gtfs.StopTime> stopTimes = dataset.stopTimes.get(tripId);
            if (stopTimes == null) continue;
            for (gtfs.StopTime st : stopTimes) {
                if (stopToTripIndices.containsKey(st.stop_id)) {
                    stopToTripIndices.get(st.stop_id).add(i);
                }
            }
        }
    }

        List<ClosureResult> results = stopCandidates.parallelStream().map(stopUsage -> {
            Stop stop = stopUsage.stop;
            GTFSRouteFinder finder = new GTFSRouteFinder(dataset);
            finder.setClosedStopIds(Set.of(stop.stop_id));

            int changedTotalMinutes = baseline.totalMinutes;
            int changedWalkOnlyTrips = baseline.walkOnlyTrips;

            Set<Integer> affectedIndices = stopToTripIndices.getOrDefault(stop.stop_id, Set.of());
            for (int i : affectedIndices) {
                TestTrip trip = trips.get(i);
                List<RouteStep> baselineRoute = baseline.routes.get(i);
                int oldDuration = totalDuration(baselineRoute);
                boolean oldWalkOnly = baselineRoute.stream().noneMatch(s -> "ride".equals(s.mode));

                List<RouteStep> newRoute = finder.findRoute(
                    trip.getStartingPoint(), trip.getEndingPoint(),
                    config.startTime, config.dayOfWeek);
                // int newDuration = Math.max(totalDuration(newRoute), oldDuration);
                int newDuration = totalDuration(newRoute);
                boolean newWalkOnly = newRoute.stream().noneMatch(s -> "ride".equals(s.mode));

                changedTotalMinutes += newDuration - oldDuration;
                if (oldWalkOnly && !newWalkOnly) changedWalkOnlyTrips--;
                else if (!oldWalkOnly && newWalkOnly) changedWalkOnlyTrips++;
            }

            double changedAverage = (double) changedTotalMinutes / trips.size();
            return new ClosureResult(
                stop.stop_id, stop.stop_name,
                stopUsage.usedByTrips + " baseline trips used this stop",
                changedAverage, changedAverage - baselineAverage, changedWalkOnlyTrips);
        }).collect(java.util.stream.Collectors.toList());

        results.sort(Comparator.comparingDouble(r -> r.deltaMinutes));
        return results;
    }

    private static void printSummary(
            Config config,
            AverageResult baseline,
            List<ClosureResult> routeResults,
            List<ClosureResult> stopResults) {
        System.out.printf(Locale.US, "Trips file: %s%n", config.tripsPath);
        System.out.printf(Locale.US, "GTFS file : %s%n", config.gtfsPath);
        System.out.printf(Locale.US, "Start/day : %s, dayOfWeek=%d (0=Monday)%n%n", config.startTime, config.dayOfWeek);

        System.out.printf(Locale.US, "Baseline average travel time: %.2f minutes%n", baseline.averageMinutes);
        System.out.printf(Locale.US, "Baseline walk-only trips     : %d%n%n", baseline.walkOnlyTrips);

        if (routeResults.isEmpty()) {
            System.out.printf("No route closures were tested. Try --include-unused-routes=true if no baseline trips used transit.%n");
        } else if (config.closeRouteId.isBlank()) {
            System.out.printf("Most convenient routes to close (smallest average-time increase):%n");
            printResults(routeResults, config.topCount);
        } else {
            System.out.printf("Selected route closure result:%n");
            printResults(routeResults, 1);
        }
        System.out.printf("%nRoute closure CSV written to: %s%n", config.routeOutputPath);

        if (config.rankStops) {
            System.out.printf("%nMost convenient tested stops to close (smallest average-time increase):%n");
            System.out.printf("Stop candidates checked: %d baseline-used stops with lowest usage%n", stopResults.size());
            printResults(stopResults, config.topCount);
            System.out.printf("%nStop closure CSV written to: %s%n", config.stopOutputPath);
        }
    }

    private static void printResults(List<ClosureResult> results, int topCount) {
        System.out.printf("%-18s %-28s %12s %12s %10s%n", "id", "name", "avg min", "delta min", "walk-only");
        for (ClosureResult result : results.stream().limit(topCount).toList()) {
            System.out.printf(
                    Locale.US,
                    "%-18s %-28s %12.2f %12.2f %10d%n",
                    trim(result.id, 18),
                    trim(result.name, 28),
                    result.averageMinutes,
                    result.deltaMinutes,
                    result.walkOnlyTrips);
        }
    }

    private static String trim(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 1);
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static class Config {
        String gtfsPath = DEFAULT_GTFS;
        String tripsPath = DEFAULT_TRIPS;
        String routeOutputPath = DEFAULT_ROUTE_OUTPUT;
        String stopOutputPath = "data/Simulations/stop_closure_results.csv";
        String startTime = DEFAULT_START_TIME;
        int dayOfWeek = DEFAULT_DAY_OF_WEEK;
        int topCount = DEFAULT_TOP_COUNT;
        int maxStopCandidates = DEFAULT_MAX_STOP_CANDIDATES;
        int tripLimit = 0;
        String closeRouteId = "";
        boolean includeUnusedRoutes = false;
        boolean rankStops = false;

        static Config fromArgs(String[] args) {
            Config config = new Config();
            for (String arg : args) {
                String[] parts = arg.split("=", 2);
                if (parts.length != 2) {
                    continue;
                }
                switch (parts[0]) {
                    case "--gtfs" -> config.gtfsPath = parts[1];
                    case "--trips" -> config.tripsPath = parts[1];
                    case "--route-output" -> config.routeOutputPath = parts[1];
                    case "--start" -> config.startTime = parts[1];
                    case "--day" -> config.dayOfWeek = Integer.parseInt(parts[1]);
                    case "--top" -> config.topCount = Integer.parseInt(parts[1]);
                    case "--max-stop-candidates" -> config.maxStopCandidates = Integer.parseInt(parts[1]);
                    case "--trip-limit" -> config.tripLimit = Integer.parseInt(parts[1]);
                    case "--close-route" -> config.closeRouteId = parts[1];
                    case "--include-unused-routes" -> config.includeUnusedRoutes = Boolean.parseBoolean(parts[1]);
                    case "--rank-stops" -> config.rankStops = Boolean.parseBoolean(parts[1]);
                    case "--stop-output" -> config.stopOutputPath = parts[1];
                    default -> {
                    }
                }
            }
            return config;
        }
    }

    private static class AverageResult {
        final int totalMinutes;
        final double averageMinutes;
        final int walkOnlyTrips;
        final List<List<RouteStep>> routes;

        AverageResult(int totalMinutes, double averageMinutes, int walkOnlyTrips, List<List<RouteStep>> routes) {
            this.totalMinutes = totalMinutes;
            this.averageMinutes = averageMinutes;
            this.walkOnlyTrips = walkOnlyTrips;
            this.routes = routes;
        }
    }

    private static class ClosureResult {
        final String id;
        final String name;
        final String note;
        final double averageMinutes;
        final double deltaMinutes;
        final int walkOnlyTrips;

        ClosureResult(String id, String name, String note, double averageMinutes, double deltaMinutes, int walkOnlyTrips) {
            this.id = id;
            this.name = name;
            this.note = note;
            this.averageMinutes = averageMinutes;
            this.deltaMinutes = deltaMinutes;
            this.walkOnlyTrips = walkOnlyTrips;
        }
    }

    private static class StopUsage {
        final Stop stop;
        int usedByTrips;

        StopUsage(Stop stop, int usedByTrips) {
            this.stop = stop;
            this.usedByTrips = usedByTrips;
        }
    }
}


