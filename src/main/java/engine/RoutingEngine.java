package engine;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.leastfixedpoint.json.JSONReader;
import com.leastfixedpoint.json.JSONSyntaxError;
import com.leastfixedpoint.json.JSONWriter;

import gtfs.GTFSDataset;

public class RoutingEngine {

    /**
     * Defining the JSON reader and writer for handling input and output,
     * as well as a variable to hold the GTFS dataset once it's loaded.
     **/
    private JSONReader requestReader = new JSONReader(new InputStreamReader(System.in));
    private JSONWriter<OutputStreamWriter> responseWriter = new JSONWriter<>(new OutputStreamWriter(System.out));
    private RouteFinder routeFinder = null;

    private GTFSDataset dataset;

    // Constants defined here
    private static final double walkingSpeedKmh = 5.0;

    // Main method (entry point) that creates an instance of the RoutingEngine and
    // starts it.
    public static void main(String[] args) throws IOException {
        new RoutingEngine().run();
    }

    /**
     * The run method continuously reads JSON requests from standard input,
     * processes them, and writes JSON responses to standard output. It handles
     * processes them, and writes JSON responses to standard output. It handles
     * different types of requests such as "ping", "load", and "routeFrom".
     * If it encounters invalid JSON or an unknown request type, it responds
     * with an error message. The loop continues until the end of input is detected.
     *-----------------------------------------------------------------------------
     * ANOTHER THING: THIS IS ONLY USED FOR TESTING, THE GUI INVOKED PUBLIC METHODS
     * ACCORDINGLY.
     */
    public void run() throws IOException {
        System.err.println("Starting");

        while (true) {
            Object json;

            try {
                json = requestReader.read();
            } catch (JSONSyntaxError e) {
                sendError("Bad JSON input");
                break;
            } catch (EOFException e) {
                System.err.println("End of input detected");
                break;
            }

            /**
             * Here we check if the parsed JSON is a Map (so a JSON object)
             * and then process it based on its contents. We look for specific
             * keys to determine the type of request and call the appropriate
             * handler method. If the request doesn't match any known type, we
             * respond with an error message.
             **/
            if (json instanceof Map<?, ?>) {
                Map<?, ?> request = (Map<?, ?>) json;

                if (request.containsKey("ping")) {
                    sendOk(Map.of("pong", request.get("ping")));
                    continue;
                }

                if (request.containsKey("load")) {
                    handleLoad(request);
                    continue;
                }

                if (request.containsKey("routeFrom")) {
                    handleRoute(request);
                    continue;
                }
                // ... process other requests here
            }

            sendError("Bad request");
        }
    }

    /**
     * Example: {"load":"dummy.zip"}
     * Attempts to load the GTFS dataset from the specified path.
     * If successful, responds with "loaded". If it fails, responds
     * with an error message.
     **/
    private void handleLoad(Map<?, ?> request) throws IOException {
        String path = (String) request.get("load");

        try {
            dataset = GTFSDataset.loadFromZip(path);
            routeFinder = new GTFSRouteFinder(dataset);
            sendOk("loaded"); // Respond with "loaded" if the dataset is successfully loaded
        } catch (IOException e) {
            sendError("Failed to load GTFS dataset: " + e.getMessage());
        }
    }

    // Extracted logic from handleRoute to be reused by findPath to store return value
    private List<RouteStep> findRoute(double lat1, double lon1, double lat2, double lon2, String startTime, int dayOfWeek) {
        if (dataset == null) {
            throw new IllegalStateException("No dataset loaded");
        }
        Point fromPoint = new Point(lat1, lon1);
        Point toPoint = new Point(lat2, lon2);
        return routeFinder.findRoute(fromPoint, toPoint, startTime, dayOfWeek);
    }

    /**
     * Example:
     * {"routeFrom":{"lat":52.52,"lon":13.40},"to":{"lat":52.50,"lon":13.45},"startingAt":"09:00","dayOfWeek":0}
     * This method calculates a simple walking route between the specified start and
     * end coordinates.
     * It uses the Haversine formula to calculate the distance and estimates the
     * duration based on a walking speed of 5 km/h.
     * The response includes the mode of transportation, the destination
     * coordinates, the estimated duration, and the starting time.
     **/
    private void handleRoute(Map<?, ?> request) throws IOException {
        if (dataset == null || routeFinder == null) {
            sendError("No dataset loaded");
            return;
        }

        try {
            Map<?, ?> from = (Map<?, ?>) request.get("routeFrom");
            Map<?, ?> to = (Map<?, ?>) request.get("to");
            String startTime = (String) request.get("startingAt");
            Number dayNum = (Number) request.get("dayOfWeek");

            // number of routes to return, in our case we want 3 routes
            int numRoutes = 3;

            if (from == null || to == null || startTime == null) {
                sendError("Bad request");
                return;
            }

            double lat1 = ((Number) from.get("lat")).doubleValue();
            double lon1 = ((Number) from.get("lon")).doubleValue();
            double lat2 = ((Number) to.get("lat")).doubleValue();
            double lon2 = ((Number) to.get("lon")).doubleValue();

            int dayOfWeek = dayNum != null ? dayNum.intValue() : (LocalDate.now().getDayOfWeek().getValue() - 1); // Default
                                                                                                                  // to
                                                                                                                  // current
                                                                                                                  // day
                                                                                                                  // if
                                                                                                                  // not
                                                                                                                  // specified

            if (!isValidUserTime(startTime)) {
                sendError("Bad request");
                return;
            }

            if (dayOfWeek < 0 || dayOfWeek > 6) {
                sendError("Bad request");
                return;
            }

            Point fromPoint = new Point(lat1, lon1);
            Point toPoint = new Point(lat2, lon2);

            // Get multiple routes
            List<List<RouteStep>> allRoutes = routeFinder.findRoutes(fromPoint, toPoint, startTime, numRoutes,
                    dayOfWeek);

            if (allRoutes == null || allRoutes.isEmpty()) {
                sendError("No route found");
                return;
            }

            // Format as array of routes
            List<Map<String, Object>> responseList = new ArrayList<>();
            for (List<RouteStep> steps : allRoutes) {
                List<Map<String, Object>> routeSteps = steps.stream()
                        .map(RouteStep::toMap)
                        .toList();
                responseList.add(Map.of("route", routeSteps));
            }

            sendOk(responseList);

        } catch (Exception e) {
            sendError("Bad request");
        }
    }

    /**
    /**
     * This function simply calculates the distance between
     * two geographic coordinates using the Haversine formula.
     *
     * this isn't even used?
     *
    private static double distance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
     **/

    /**
     * This function is invoked by the various methods to send
     * a successful response back to the client. It takes an object
     * as a parameter, wraps it in a Map with the key "ok", and
     * writes it as JSON to standard output.
     **/
    private void sendOk(Object value) throws IOException {
        responseWriter.write(Map.of("ok", value));
        responseWriter.getWriter().write('\n');
        responseWriter.getWriter().flush();
    }

    /**
     * Similarly, this function is invoked when an error occurs, to send an
     * error message back to the client. It takes a string message
     * as a parameter, wraps it in a Map with the key "error", and
     * writes it as JSON to standard output.
     **/
    private void sendError(String message) throws IOException {
        responseWriter.write(Map.of("error", message));
        responseWriter.getWriter().write('\n');
        responseWriter.getWriter().flush();
    }

    /**
     * -------------------------------------------------------------------
     * Below are a series of public methods which are used primarily
     * by the JavaFX GUI. Each method calls or returns respective private
     * functions / variables.
     * -------------------------------------------------------------------
     */

    public GTFSDataset getDataset() {return dataset;}
    public void setDataset(GTFSDataset dataset) {this.dataset = dataset;}

    public void loadDataset(String path) {
        try {
            Map<String, Object> request = Map.of("load", path);
            handleLoad(request);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load dataset", e);
        }
    }

    // {"routeFrom":{"lat":52.52,"lon":13.40},"to":{"lat":52.50,"lon":13.45},"startingAt":"09:00"}
    // not in use since it only returns a single path - deprecated function
    public List<RouteStep> findPath(String start, String end, String departure) {
        int dayOfWeek = 1; // set to 1 as placeholder for now

        try {
            String[] startParts = start.split(",");
            String[] endParts = end.split(",");

            double startLat = Double.parseDouble(startParts[0].trim());
            double startLon = Double.parseDouble(startParts[1].trim());

            double endLat = Double.parseDouble(endParts[0].trim());
            double endLon = Double.parseDouble(endParts[1].trim());

            return findRoute(startLat, startLon, endLat, endLon, departure, dayOfWeek);

        } catch (Exception e) {
            throw new RuntimeException("Failed to compute route", e);
        }
    }

    // Heatmap
    public Map<String, Integer> getHeatmapData(String start, String startTime, int dayOfWeek) {
        String[] parts = start.split(",");

        double lat = Double.parseDouble(parts[0].trim());
        double lon = Double.parseDouble(parts[1].trim());
        
        return routeFinder.getHeatmapData(new Point(lat, lon), startTime, dayOfWeek);
    }

    // the new way to show 3 route steps instead of just 1
    public List<List<RouteStep>> findPaths(String start, String end, String departure, int dayOfWeek, int limit) {

        String[] startParts = start.split(",");
        String[] endParts = end.split(",");

        double startLat = Double.parseDouble(startParts[0].trim());
        double startLon = Double.parseDouble(startParts[1].trim());

        double endLat = Double.parseDouble(endParts[0].trim());
        double endLon = Double.parseDouble(endParts[1].trim());

        return routeFinder.findRoutes(
                new Point(startLat, startLon),
                new Point(endLat, endLon),
                departure,
                limit,
                dayOfWeek
        );
    }
    private static boolean isValidUserTime(String time) {
        if (time == null || !time.matches("\\d{2}:\\d{2}")) {
            return false;
        }

        String[] parts = time.split(":");
        int hours = Integer.parseInt(parts[0]);
        int minutes = Integer.parseInt(parts[1]);

        return hours >= 0 && hours < 24 && minutes >= 0 && minutes < 60;
    }

    public void closeStop(String stopId) {

        if (dataset == null) {
            return;
        }

        dataset.closeStop(stopId);

        // IMPORTANT:
        // rebuild graph so routes update immediately
        routeFinder = new GTFSRouteFinder(dataset);
    }

    public void openStop(String stopId) {

        if (dataset == null) {
            return;
        }

        dataset.openStop(stopId);

        // rebuild graph
        routeFinder = new GTFSRouteFinder(dataset);
    }

    public boolean isStopClosed(String stopId) {

        if (dataset == null) {
            return false;
        }

        return dataset.isStopClosed(stopId);
    }
}