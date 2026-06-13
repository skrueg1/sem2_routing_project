package engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import gtfs.Calendar;
import gtfs.GTFSDataset;
import gtfs.Route;
import gtfs.Stop;
import gtfs.StopTime;
import gtfs.Trip;
import utility.RouteUtils;

public class GTFSRouteFinder implements RouteFinder {

    private static final double walkingSpeedKmh = 5.0;
    private static final int MAX_START_STOPS = 50;// How many nearby stops to consider getting on

    private final GTFSDataset dataset;
    private Set<String> closedRouteIds;
    private Set<String> closedStopIds;
    private final Map<String, List<Edge>> graph = new HashMap<>();

    // Constructs the route finder and pre-builds the transit graph from the dataset
    public GTFSRouteFinder(GTFSDataset dataset) {
        this(dataset, Set.of(), Set.of());
    }

    public GTFSRouteFinder(GTFSDataset dataset, Set<String> closedRouteIds, Set<String> closedStopIds) {
        this.dataset = dataset;
        this.closedRouteIds = new HashSet<>(closedRouteIds);
        this.closedStopIds = new HashSet<>(closedStopIds);
        buildGraph();
    }

    public void setClosedRouteIds(Set<String> closedRouteIds) {
        this.closedRouteIds = new HashSet<>(closedRouteIds);
    }

    public void setClosedStopIds(Set<String> closedStopIds) {
        this.closedStopIds = new HashSet<>(closedStopIds);
    }

    @Override
    public List<RouteStep> findRoute(Point from, Point to, String startTime, int dayOfWeek) {
        List<List<RouteStep>> routes = findRoutes(from, to, startTime, 1, dayOfWeek);
        return routes.isEmpty() ? walkOnly(from, to, startTime) : routes.get(0);
    }

    // Finds the fastest route from 'from' to 'to', starting at 'startTime'
    // Returns a list of RouteSteps
    @Override
    public List<List<RouteStep>> findRoutes(Point from, Point to, String startTime, int numberOfRoutes, int dayOfWeek) {
        int startMinutes = parseTimeToMinutes(startTime);
        if (startMinutes < 0)
            startMinutes = 0;

        if (dataset == null || dataset.stops.isEmpty()) {
            List<List<RouteStep>> result = new ArrayList<>();
            result.add(walkOnly(from, to, startTime));
            return result;
        }

        int directWalk = walkingMinutes(from, to);
        List<StopDistance> startStops = findNearestStops(from, MAX_START_STOPS);

        if (startStops.isEmpty()) {
            List<List<RouteStep>> result = new ArrayList<>();
            result.add(walkOnly(from, to, startTime));
            return result;
        }

        // INITIALIZE DIJKSTRA STRUCTURES
        Map<String, Integer> dist = new HashMap<>();
        Map<String, Parent> parent = new HashMap<>();
        PriorityQueue<Node> queue = new PriorityQueue<>(Comparator.comparingInt(n -> n.cost));

        for (StopDistance startStop : startStops) {
//            if (closedStopIds.contains(startStop.stop.stop_id)) continue;
            if (startStop.stop.closed || closedStopIds.contains(startStop.stop.stop_id)) continue;
            int arrivalTime = startMinutes + startStop.walkTime;
            dist.put(startStop.stop.stop_id, arrivalTime);
            parent.put(startStop.stop.stop_id, new Parent(null, null, arrivalTime));
            queue.add(new Node(startStop.stop.stop_id, arrivalTime));
        }

        // Run full Dijkstra to get all possible end stops with their costs
        Map<String, Integer> bestArrivalAtStop = new HashMap<>();

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (current.cost > dist.getOrDefault(current.stopId, Integer.MAX_VALUE)) {
                continue;
            }

            Stop stop = dataset.stops.get(current.stopId);
            if (stop == null)
                continue;

            bestArrivalAtStop.put(current.stopId, current.cost);

            List<Edge> outEdges = graph.getOrDefault(current.stopId, Collections.emptyList());
            if (outEdges.isEmpty())
                continue;

            Edge dummy = new Edge(null, null, current.cost, current.cost, 0, null, null);
            int index = Collections.binarySearch(outEdges, dummy, Comparator.comparingInt(e -> e.departTime));
            if (index < 0)
                index = -(index + 1);

            for (int i = index; i < outEdges.size(); i++) {
                Edge edge = outEdges.get(i);
                if (closedRouteIds.contains(edge.routeId)) continue;
//                if (closedStopIds.contains(edge.to)) continue;

                Stop toStop = dataset.stops.get(edge.to);
                if (toStop != null && (toStop.closed || closedStopIds.contains(edge.to))) continue;

                if (!isTripActiveOnDay(edge.tripId, dayOfWeek)) continue;
                int nextCost = edge.arriveTime;
                if (nextCost < dist.getOrDefault(edge.to, Integer.MAX_VALUE)) {
                    dist.put(edge.to, nextCost);
                    parent.put(edge.to, new Parent(current.stopId, edge, nextCost));
                    queue.add(new Node(edge.to, nextCost));
                }
            }
        }

        List<StopDistance> endStops = findNearestStops(to, MAX_START_STOPS);
        List<RouteWithCost> candidates = new ArrayList<>();

        for (StopDistance endStop : endStops) {
            String stopId = endStop.stop.stop_id;
            Integer arrivalTime = bestArrivalAtStop.get(stopId);
            if (arrivalTime == null) continue;
            Parent p = parent.get(stopId);
            if (p == null || p.edge == null) continue;
            int totalTime = arrivalTime + endStop.walkTime;
            candidates.add(new RouteWithCost(stopId, totalTime, arrivalTime, endStop.walkTime));
        }

        candidates.add(new RouteWithCost(null, startMinutes + directWalk, 0, directWalk));
        candidates.sort(Comparator.comparingInt(r -> r.totalCost));

        List<List<RouteStep>> result = new ArrayList<>();
        Set<String> seenRoutes = new HashSet<>();

        for (RouteWithCost candidate : candidates) {
            if (result.size() >= numberOfRoutes)
                break;

            List<RouteStep> route;
            if (candidate.stopId == null) {
                route = walkOnly(from, to, startTime);
            } else {
                route = buildRoute(from, to, startTime, candidate.stopId, candidate.finalWalk, parent);
            }

            String routeKey = route.toString();
            if (!seenRoutes.contains(routeKey)) {
                seenRoutes.add(routeKey);
                result.add(route);
            }
        }

        if (result.isEmpty()) {
            result.add(walkOnly(from, to, startTime));
        }

        return result;
    }

    // Heatmap
    public Map<String, Integer> getHeatmapData(Point from, String startTime, int dayOfWeek) {
        int startMinutes = parseTimeToMinutes(startTime);
        if (startMinutes < 0) startMinutes = 0;
        System.out.println("Start minutes: " + startMinutes);

        List<StopDistance> startStops = findNearestStops(from, MAX_START_STOPS);

        Map<String, Integer> dist = new HashMap<>();
        PriorityQueue<Node> queue = new PriorityQueue<>(Comparator.comparingInt(n -> n.cost));

        for (StopDistance startStop : startStops) {
            int arrivalTime = startMinutes + startStop.walkTime;
            dist.put(startStop.stop.stop_id, arrivalTime);
            queue.add(new Node(startStop.stop.stop_id, arrivalTime));
        }

        while (!queue.isEmpty()) {
            Node current = queue.poll();
            if (current.cost > dist.getOrDefault(current.stopId, Integer.MAX_VALUE)) continue;

            Stop stop = dataset.stops.get(current.stopId);
            if (stop == null) continue;

            List<Edge> outEdges = graph.getOrDefault(current.stopId, Collections.emptyList());
            if (outEdges.isEmpty()) continue;

            Edge dummy = new Edge(null, null, current.cost, current.cost, 0, null, null);
            int index = Collections.binarySearch(outEdges, dummy, Comparator.comparingInt(e -> e.departTime));
            if (index < 0) index = -(index + 1);

            for (int i = index; i < outEdges.size(); i++) {
                Edge edge = outEdges.get(i);
                if (closedRouteIds.contains(edge.routeId)) continue;
//                if (closedStopIds.contains(edge.to)) continue;
                Stop toStop = dataset.stops.get(edge.to);
                if (toStop != null && toStop.closed) continue;

                if (!isTripActiveOnDay(edge.tripId, dayOfWeek)) continue;
                int nextCost = edge.arriveTime;
                if (nextCost < dist.getOrDefault(edge.to, Integer.MAX_VALUE)) {
                    dist.put(edge.to, nextCost);
                    queue.add(new Node(edge.to, nextCost));
                }
            }
        }

        Map<String, Integer> result = new HashMap<>();
        for (Map.Entry<String, Integer> entry : dist.entrySet()) {
            result.put(entry.getKey(), entry.getValue() - startMinutes);
        }
        return result;
    }

    /**
     * Pre-builds the transit graph from the GTFS stop_times data.
     * Each consecutive pair of stops in a trip becomes a directed edge in the graph.
     * Edges are sorted by departure time per stop to allow efficient binary search during routing.
     */
    private void buildGraph() {
        graph.clear();

        for (List<StopTime> stopList : dataset.stopTimes.values()) {
            for (int i = 0; i + 1 < stopList.size(); i++) {
                StopTime current = stopList.get(i);
                StopTime next = stopList.get(i + 1);

                int depart = parseTimeToMinutes(current.departure_time);
                int arrive = parseTimeToMinutes(next.arrival_time);

                if (depart < 0 || arrive < 0)
                    continue;
                if (arrive < depart)
                    arrive += 24 * 60;

                int duration = arrive - depart;
                if (duration <= 0)
                    continue;

                Trip trip = dataset.trips.get(current.trip_id);
                String routeId = trip == null ? null : trip.route_id;
                Edge edge = new Edge(current.stop_id, next.stop_id, depart, arrive, duration, current.trip_id, routeId);
                graph.computeIfAbsent(current.stop_id, k -> new ArrayList<>()).add(edge);
            }
        }

        for (List<Edge> edges : graph.values()) {
            edges.sort(Comparator.comparingInt(e -> e.departTime));
        }
    }

    // Reconstructs the full route from the Dijkstra parent map.
    private List<RouteStep> buildRoute(Point from, Point to, String startTime, String bestStopId, int bestStopWalk,
            Map<String, Parent> parent) {
        List<Edge> edges = new ArrayList<>();
        String current = bestStopId;
        while (true) {
            Parent p = parent.get(current);
            if (p == null || p.edge == null) break;
            edges.add(p.edge);
            current = p.prevStopId;
        }
        Collections.reverse(edges);

        if (edges.isEmpty()) {
            return walkOnly(from, to, startTime);
        }

        List<RouteStep> steps = new ArrayList<>();

        // Walk from the origin to the boarding stop
        Stop boardingStop = dataset.stops.get(edges.get(0).from);
        int walkingToBoard = walkingMinutes(from, new Point(boardingStop.stop_lat, boardingStop.stop_lon));
        if (walkingToBoard > 0) {
            steps.add(new RouteStep(
                    Map.of("lat", boardingStop.stop_lat, "lon", boardingStop.stop_lon),
                    walkingToBoard,
                    startTime));
        }

        int currentTime = parseTimeToMinutes(startTime);
        if (currentTime < 0) currentTime = 0;
        currentTime += walkingToBoard;

        // Account for waiting at the stop for the first departure
        int firstDeparture = edges.get(0).departTime;
        if (firstDeparture > currentTime) {
            if (!steps.isEmpty()) {
                steps.get(0).duration += firstDeparture - currentTime;
            }
            currentTime = firstDeparture;
        }

        // Group consecutive edges on the same trip into a single ride step
        for (int i = 0; i < edges.size();) {
            Edge first = edges.get(i);
            int cumulativeDuration = first.duration;
            String tripId = first.tripId;
            int j = i + 1;

            // Extend the ride as long as we stay on the same trip
            while (j < edges.size() && edges.get(j).tripId.equals(tripId)) {
                cumulativeDuration += edges.get(j).duration;
                j++;
            }

            Stop arrivalStop = dataset.stops.get(edges.get(j - 1).to);
            Trip trip = dataset.trips.get(tripId);
            Route route = trip == null ? null : dataset.routes.get(trip.route_id);

            RouteInfo info = null;
            if (route != null && trip != null) {
                info = new RouteInfo(dataset.agency, route, trip);
            }

            // Collect waypoints for all stops visited in this ride
            List<Map<String, Object>> waypoints = new ArrayList<>();
            for (int k = i; k < j; k++) {
                Stop s = dataset.stops.get(edges.get(k).to);
                if (s != null) waypoints.add(Map.of("lat", s.stop_lat, "lon", s.stop_lon));
            }

            RouteStep rideStep = new RouteStep(
                    Map.of("lat", arrivalStop.stop_lat, "lon", arrivalStop.stop_lon),
                    cumulativeDuration,
                    formatTime(currentTime),
                    arrivalStop.stop_name,
                    info);
            rideStep.waypoints = waypoints;
            rideStep.shapeId = trip != null ? trip.shape_id : null;
            steps.add(rideStep);

            currentTime += cumulativeDuration;
            i = j;

            if (i < edges.size()) {
                int nextDeparture = edges.get(i).departTime;
                if (nextDeparture > currentTime) {
                    if (!steps.isEmpty()) {
                        steps.get(steps.size() - 1).duration += nextDeparture - currentTime;
                    }
                    currentTime = nextDeparture;
                }
            }
        }

        // Walk from the alighting stop to the destination
        if (bestStopWalk > 0) {
            steps.add(new RouteStep(
                    Map.of("lat", to.lat, "lon", to.lon),
                    bestStopWalk,
                    formatTime(currentTime)));
        }

        return steps;
    }

    private List<RouteStep> walkOnly(Point from, Point to, String startTime) {
        int duration = walkingMinutes(from, to);
        return List.of(new RouteStep(
                Map.of("lat", to.lat, "lon", to.lon),
                duration,
                startTime));
    }

    // Returns the 'maxStops' nearest transit stops to the given point, sorted by walking time.
    private List<StopDistance> findNearestStops(Point point, int maxStops) {
        List<StopDistance> distances = new ArrayList<>();
        for (Stop stop : dataset.stops.values()) {
            if (stop.closed || closedStopIds.contains(stop.stop_id)) continue;
//            if (stop.closed) {
//                System.out.println("Closed stop detected: " + stop.stop_id);
//                System.out.println(closedStopIds.size());
//            }
            int walkTime = walkingMinutes(point, new Point(stop.stop_lat, stop.stop_lon));
            distances.add(new StopDistance(stop, walkTime));
        }
        distances.sort(Comparator.comparingInt(sd -> sd.walkTime));
        if (distances.size() > maxStops) {
            return new ArrayList<>(distances.subList(0, maxStops));
        }
        return distances;
    }

    private int walkingMinutes(Point a, Point b) {
        return (int) Math.round((RouteUtils.HaversineDistance(a.lat, a.lon, b.lat, b.lon) / walkingSpeedKmh) * 60);
    }

    // Parses "HH:MM" or "HH:MM:SS" into minutes since midnight. Returns -1 if malformed.
    private int parseTimeToMinutes(String time) {
        if (time == null) return -1;
        String[] parts = time.split(":");
        if (parts.length < 2) return -1;
        try {
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            if (hours < 0 || minutes < 0 || minutes >= 60) return -1;
            return hours * 60 + minutes;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private String formatTime(int minutes) {
        minutes = Math.max(0, minutes % (24 * 60));
        int hours = minutes / 60;
        int mins = minutes % 60;
        return String.format("%02d:%02d", hours, mins);
    }

    // Checks if the trip operates on the given day of the week (0=Monday, 6=Sunday)
    private boolean isTripActiveOnDay(String tripId, int dayOfWeek) {
        Trip trip = dataset.trips.get(tripId);
        if (trip == null) return false;
        Calendar cal = dataset.calendar.get(trip.service_id);
        if (cal == null) return true; // If no calendar data, assume trip is active
        switch (dayOfWeek) {
            case 0: return cal.monday == 1;
            case 1: return cal.tuesday == 1;
            case 2: return cal.wednesday == 1;
            case 3: return cal.thursday == 1;
            case 4: return cal.friday == 1;
            case 5: return cal.saturday == 1;
            case 6: return cal.sunday == 1;
            default: return false;
        }
    }

    private static class RouteWithCost {
        String stopId;
        int totalCost;
        int arrivalTime;
        int finalWalk;

        RouteWithCost(String stopId, int totalCost, int arrivalTime, int finalWalk) {
            this.stopId = stopId;
            this.totalCost = totalCost;
            this.arrivalTime = arrivalTime;
            this.finalWalk = finalWalk;
        }
    }

    // INTERNAL DATA CLASSES ----------------------------------------

    private static class Edge {
        public final String from;
        public final String to;
        public final int duration;
        public final int departTime;
        public final int arriveTime;
        public final String tripId;
        public final String routeId;

        public Edge(String from, String to, int departTime, int arriveTime, int duration, String tripId, String routeId) {
            this.from = from;
            this.to = to;
            this.departTime = departTime;
            this.arriveTime = arriveTime;
            this.duration = duration;
            this.tripId = tripId;
            this.routeId = routeId;
        }
    }

    private static class StopDistance {
        public final Stop stop;
        public final int walkTime;

        public StopDistance(Stop stop, int walkTime) {
            this.stop = stop;
            this.walkTime = walkTime;
        }
    }

    private static class Node {
        public final String stopId;
        public final int cost;

        public Node(String stopId, int cost) {
            this.stopId = stopId;
            this.cost = cost;
        }
    }

    private static class Parent {
        public final String prevStopId;
        public final Edge edge;
        public final int cost;

        public Parent(String prevStopId, Edge edge, int cost) {
            this.prevStopId = prevStopId;
            this.edge = edge;
            this.cost = cost;
        }
    }
}