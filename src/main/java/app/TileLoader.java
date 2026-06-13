package app;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import javafx.scene.image.Image;

public class TileLoader {

    private static final String[] TILE_DIRS = {
        "src/main/resources/tiles"
    };

    private Map<String, Image> cache = new HashMap<>();

    public Image getTile(int zoom, int x, int y) {
        String key = zoom + "/" + x + "/" + y;

        if (cache.containsKey(key)) {
            return cache.get(key);
        }

        // try both possible locations
        for (String dir : TILE_DIRS) {
            String path = dir + "/" + zoom + "/" + x + "/" + y + ".png";
            File file = new File(path);

            if (file.exists()) {
                Image image = new Image(file.toURI().toString());
                cache.put(key, image);
                return image;
            }
        }

        // tile not found 
        return null; 
    }
}