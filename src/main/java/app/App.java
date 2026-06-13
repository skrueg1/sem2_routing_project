package app;

import java.io.IOException;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage stage) throws IOException {

        Font.loadFont(getClass().getResourceAsStream("/Jost.ttf"), 14);

        // ========== Load FXML Root ========== //
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/main.fxml"));
        Parent root = loader.load();

        MainController controller = loader.getController();

        Scene scene = new Scene(root, 1000, 700);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        stage.setTitle("Routing Engine");
        stage.setScene(scene);
        stage.setResizable(false);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
