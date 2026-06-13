package app;

import java.io.IOException;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import engine.RouteStep;
import engine.RoutingEngine;
import gtfs.GTFSDataset;
import gtfs.Stop;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

public class MainController {
    // ------------ INPUTS ------------
    @FXML private TextField startingLocation;
    @FXML private TextField departureTime;
    @FXML private TextField endLocation;
    @FXML private Button goButton;
    @FXML private Button routingButton;
    @FXML private Button heatmapButton;
    @FXML private Button outOfOrderButton;

    // ------------ ROUTE CARDS ------------
    @FXML private VBox route1;
    @FXML private Label route1Time;
    @FXML private VBox route2;
    @FXML private Label route2Time;
    @FXML private VBox route3;
    @FXML private Label route3Time;

    @FXML private Label walkStep;
    @FXML private Label busStep;
    @FXML private Label tramStep;
    @FXML private Label trainStep;

    @FXML private Label outOfOrderStopName;

    // ------------ PANELS ------------
    @FXML private VBox routeStepsContainer;
    @FXML private Pane mapContainer;
    @FXML private ScrollPane routeStepsScroll;
    @FXML private VBox oooPopup;

    //  ------------ INITIALIZE  ------------
    private RoutingEngine engine;
    private List<List<RouteStep>> currentRoutes;
    private MapCanvas mapCanvas;
    private List<RouteStep> currentSteps;
    private enum MapMode { ROUTING, HEATMAP }
    private MapMode currentMode = MapMode.ROUTING;
    private String selectedStopId = null;

    @FXML
    public void initialize() throws IOException {
        System.out.println("Loading dataset...");

        engine = new RoutingEngine();
        engine.loadDataset("data/copenhagen_gtfs.zip");

        // for drawing routes
        GTFSDataset originalDataset = GTFSDataset.loadFromZip("data/GTFS.zip");
        engine.getDataset().shapes = originalDataset.shapes;


        setupMapCanvas(engine);
        clipRoundedCorners(routeStepsScroll);
        clipRoundedCorners(mapContainer);

        setMode(currentMode);
        routingButton.setOnAction(e -> setMode(MapMode.ROUTING));
        heatmapButton.setOnAction(e -> setMode(MapMode.HEATMAP));

    }
    
    private void setupMapCanvas(RoutingEngine engine)
    {
        mapCanvas = new MapCanvas(800, 600, engine);
        
        // resize the canvas with the pane
        mapCanvas.widthProperty().bind(mapContainer.widthProperty());
        mapCanvas.heightProperty().bind(mapContainer.heightProperty());

        mapCanvas.setOnRouteCalculated(steps -> {
            currentSteps = steps;
            showRoute(0);
        });

        mapCanvas.setOnStartPointSelected(point -> {
            if (point != null) {
                startingLocation.setText(point.lat + "," + point.lon);
            } else {
                startingLocation.clear();
            }

            // clears all the data from the previous routes
            currentRoutes = null;
            endLocation.clear();

            route1.getStyleClass().remove("active");
            route2.getStyleClass().remove("active");
            route3.getStyleClass().remove("active");
            
            routeStepsContainer.getChildren().clear();

            route1.setDisable(true);
            route2.setDisable(true);
            route3.setDisable(true);
        });

        mapCanvas.setOnEndPointSelected(point -> {
            if (point != null) {
                endLocation.setText(point.lat + "," + point.lon);
            } else {
                endLocation.clear();
            }
        });

        // called when user selects a stop on the canvas in close-stop mode
        mapCanvas.setOnStopSelected(stopId -> {
            selectedStopId = stopId;

            if (stopId != null) {
                outOfOrderStopName.setText("Stop: " + stopId);
                outOfOrderButton.setVisible(true);
                outOfOrderButton.setManaged(true);
            }
            else {
                outOfOrderButton.setVisible(false);
            }
        });

        mapContainer.getChildren().add(mapCanvas);
    }
    
    private void clipRoundedCorners(Region region)
    {
        Rectangle clip = new Rectangle();
        clip.setArcHeight(32);
        clip.setArcWidth(32);

        clip.widthProperty().bind(region.widthProperty());
        clip.heightProperty().bind(region.heightProperty());

        region.setClip(clip);
    }

    @FXML
    private void handleRoutingMode() {
        setMode(MapMode.ROUTING);
    }

    @FXML
    private void handleHeatmapMode() {
        setMode(MapMode.HEATMAP);
    }

    @FXML
    private void handleGoButton() {
        if (currentMode == MapMode.HEATMAP) {

            String start = startingLocation.getText();
            String departure = departureTime.getText();

            if (!start.contains(",")) {
                showInputError();
                return;
            }

            if (departure == null || departure.isBlank()) {
                departure = LocalTime.now()
                        .format(DateTimeFormatter.ofPattern("HH:mm"));

                departureTime.setText(departure);
            }

            mapCanvas.generateHeatmap(start, departure);
            return;
        }

        String start = startingLocation.getText();
        String end = endLocation.getText();
        String departure = departureTime.getText();

        if (!start.contains(",") || !end.contains(",")) {
            showInputError();
            throw new IllegalArgumentException("Coordinates must be lat,lon");
        }

        if (departure == null || departure.isBlank()) {
            departure = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            departureTime.setText(departure);
        }

        currentRoutes = engine.findPaths(start, end, departure, 1, 3);

        updateRouteCards(currentRoutes);

        if (currentRoutes != null && !currentRoutes.isEmpty()) {
            showRoute(0);

            VBox[] cards = {route1, route2, route3};
            for (VBox c : cards) c.getStyleClass().remove("active");
            route1.getStyleClass().add("active");
        }
    }

    private void updateRouteCards(List<List<RouteStep>> routes) {

        VBox[] cards = {route1, route2, route3};
        Label[] times = {route1Time, route2Time, route3Time};

        for (int i = 0; i < cards.length; i++) {

            VBox card = cards[i];

            card.getStyleClass().remove("active");

            card.setOnMouseClicked(null);

            if (i < routes.size()) {

                List<RouteStep> route = routes.get(i);

                card.getStyleClass().add("routeCard");
                card.setDisable(false);
                card.setOpacity(1.0);

                times[i].setText(formatTimeLabel(route));

                int index = i;
                card.setOnMouseClicked(e -> {
                    showRoute(index);

                    for (VBox c : cards) c.getStyleClass().remove("active");
                    card.getStyleClass().add("active");
                });

            } else {

                card.setDisable(true);
                card.setOpacity(0.4);
                times[i].setText("");
            }
        }
    }

    private void showRoute(int index) {

        routeStepsContainer.getChildren().clear();

        if (currentRoutes == null || index >= currentRoutes.size()) return;

        List<RouteStep> route = currentRoutes.get(index);

        mapCanvas.showRoute(route);

        for (RouteStep step : route) {

            HBox row = new HBox(10);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("routeStepRow");

            ImageView icon = iconFor(step.mode);

            VBox iconBox = new VBox(icon);
            iconBox.getStyleClass().add("stepIcon");
            iconBox.setAlignment(Pos.CENTER);

            Label label = new Label(buildStepText(step));
            label.getStyleClass().add("stepText");
            label.setWrapText(true);
            label.setMaxWidth(400);
            HBox.setHgrow(label, Priority.ALWAYS);

            label.setMaxWidth(Double.MAX_VALUE);

            row.getChildren().addAll(icon, label);

            routeStepsContainer.getChildren().add(row);
        }
    }

    private String buildStepText(RouteStep step) {

        String base = step.duration + " min • " + step.startTime;

        if (step.stopName != null && !step.stopName.equals("null")) {
            base += " → " + step.stopName;
        }

        return base;
    }

    private void setStepText(String fxId, String text) {
        Label label = (Label) routeStepsContainer.lookup("#" + fxId);

        if (label != null) {
            label.setText(text.isEmpty() ? "-" : text);
        }
    }

    private String formatTimeLabel(List<RouteStep> route) {

        if (route == null || route.isEmpty()) {
            return "";
        }

        RouteStep last = route.get(route.size() - 1);

        java.time.LocalTime start =
                java.time.LocalTime.parse(last.startTime);

        java.time.LocalTime arrival =
                start.plusMinutes(last.duration);

        return arrival.toString();
    }

    private ImageView iconFor(String mode) {

        String file = switch (mode == null ? "walk" : mode.toLowerCase()) {
            case "walk" -> "person-walking-arrow-right-solid.png";
            case "ride", "bus" -> "bus-simple-solid.png";
            case "tram" -> "train-tram-solid.png";
            case "train" -> "train-solid.png";
            default -> "person-walking-arrow-right-solid.png";
        };

        String path = "/icons/" + file;

        var stream = getClass().getResourceAsStream(path);

        Image image;

        if (stream == null) {
            System.out.println("Missing icon: " + path);
            image = new Image("https://via.placeholder.com/22");
        } else {
            image = new Image(stream);
        }

        ImageView view = new ImageView(image);
        view.setFitWidth(22);
        view.setFitHeight(22);
        view.setPreserveRatio(true);

        return view;
    }

    private void showInputError(){

        startingLocation.getStyleClass().add("inputError");
        goButton.getStyleClass().add("goError");
        if (currentMode == MapMode.ROUTING) endLocation.getStyleClass().add("inputError");
        PauseTransition pause = new PauseTransition(Duration.seconds(5));
        pause.setOnFinished(e -> {
            startingLocation.getStyleClass().remove("inputError");
            if (currentMode == MapMode.ROUTING) endLocation.getStyleClass().remove("inputError");
            goButton.getStyleClass().remove("goError");
        });
        pause.play();

    }

    private void setMode(MapMode mode){
        currentMode = mode;

        // clear visual state
        routingButton.getStyleClass().remove("active");
        heatmapButton.getStyleClass().remove("active");

        if (mode == MapMode.ROUTING) {
            routingButton.getStyleClass().add("active");
            endLocation.setEditable(true);
            endLocation.setOpacity(1);
            route1.setVisible(true);
            route2.setVisible(true);
            route3.setVisible(true);
            routeStepsScroll.setVisible(true);
            if (mapCanvas != null) mapCanvas.setMode(MapCanvas.Mode.ROUTING);
        }

        else if (mode == MapMode.HEATMAP) {
            heatmapButton.getStyleClass().add("active");

            // Clear routing inputs
            startingLocation.clear();
            endLocation.clear();
            departureTime.clear();

            // Clear route data
            currentRoutes = null;
            currentSteps = null;

            // Clear route cards
            route1Time.setText("");
            route2Time.setText("");
            route3Time.setText("");

            route1.getStyleClass().remove("active");
            route2.getStyleClass().remove("active");
            route3.getStyleClass().remove("active");

            routeStepsContainer.getChildren().clear();

            endLocation.setEditable(false);
            endLocation.setOpacity(0.5);

            route1.setVisible(false);
            route2.setVisible(false);
            route3.setVisible(false);
            routeStepsScroll.setVisible(false);

            if (mapCanvas != null) {
                mapCanvas.clearAll(); // clears start/end markers and route
                mapCanvas.setMode(MapCanvas.Mode.HEATMAP);
            }
        }
    }

    @FXML
    private void handleOutOfOrderButton(){
        oooPopup.setVisible(true);
        oooPopup.setManaged(true);
    }

    @FXML
    private void handleOOOCancel(){
        oooPopup.setVisible(false);
        oooPopup.setManaged(false);
    }

    @FXML
    private void handleOOOConfirm(){
        if (selectedStopId != null) {
            engine.closeStop(selectedStopId);
            mapCanvas.draw(); // refresh
            System.out.println("Closed stop: " + selectedStopId);
        }
        oooPopup.setVisible(false);
        oooPopup.setManaged(false);
    }

}
