package cs1302.gallery;

import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.GridPane;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.scene.text.Text;
import javafx.geometry.Pos;
import javafx.geometry.Insets;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpClient;
import java.net.URLEncoder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Represents an iTunes Gallery App.
 */
public class GalleryApp extends Application {

    /** HTTP client. */
    public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)           // uses HTTP protocol version 2 where possible
        .followRedirects(HttpClient.Redirect.NORMAL)  // always redirects, except from HTTPS to HTTP
        .build();                                     // builds and returns a HttpClient object

    /** Google {@code Gson} object for parsing JSON-formatted strings. */
    public static Gson GSON = new GsonBuilder()
        .setPrettyPrinting()                          // enable nice output when printing
        .create();                                    // builds and returns a Gson object

    protected static final String DEFAULT_IMG =
        "https://garden.spoonflower.com/c/12984962/p/f/m/EH5aWJbn_LhuOlWypO5l_"
        + "PaHuLwc4GUac9wRjQv8xJ-7aocY_A8rq3E/Solid%20graphite%20dark%20grey.jpg";
    Image defaultImage = new Image(DEFAULT_IMG);

    Queue<Image> display;

    private Stage stage;
    private Scene scene;
    private VBox root;

    /** Container for navigation. */
    HBox navigation;
    Button play;
    boolean playing = false;
    Label label;
    TextField search;
    ComboBox<String> type;
    Button get;
    /** Container for message. */
    HBox messageBox;
    Text message;

    /** Container for gallery. */
    HBox gallery;
    GridPane galleryPane;
    List<ImageView> frames;
    Timeline replacement;

    /** Container for progress bar and message. */
    HBox bottom;
    ProgressBar progress;    Text source;

    private static final String ITUNES_API = "https://itunes.apple.com/search";

    /** ItunesReponse class. */
    public static class ItunesResponse {
        int resultCount;
        ItunesResult[] results;
    } // ItunesResponse

    /** ItunesResult class. */
    public static class ItunesResult {
        String wrapperType;
        String kind;
        String artworkUrl100; // we omit variables for data we're not interested in
    } // ItunesResult

    Alert alert;

    /**
     * Constructs a {@code GalleryApp} object}.
     */
    public GalleryApp() {
        this.stage = null;
        this.scene = null;
        this.root = new VBox();

        /** Construct objects. */
        this.navigation = new HBox();
        this.play = new Button("Play");
        this.label = new Label("Search:");
        this.search = new TextField();
        ObservableList<String> options = FXCollections.observableArrayList(
            "music",
            "movie",
            "podcast",
            "musicVideo",
            "audiobook",
            "shortFilm",
            "tvShow",
            "software",
            "ebook",
            "all"
            );
        this.type = new ComboBox<String>(options);
        this.get = new Button("Get Images");
        this.messageBox = new HBox();
        this.message = new Text("Type in a term, select a media type, then click the button.");

        this.gallery = new HBox();
        this.galleryPane = new GridPane();
        this.frames = new ArrayList<ImageView>();
        this.replacement = new Timeline();

        this.bottom = new HBox();
        this.progress = new ProgressBar();
        this.source = new Text("Images provided by iTunes Search API.");
        alert = new Alert(AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Error");
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setMinWidth(600);
        dialogPane.setMinHeight(200);
    } // GalleryApp

    /** {@inheritDoc} */
    @Override
    public void init() {
        System.out.println("init() called");
        root.getChildren().addAll(navigation, messageBox, gallery, bottom);
        navigation.getChildren().addAll(play, label, search, type, get);
        navigation.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS);
        navigation.setPadding(new Insets(5));
        navigation.setSpacing(5);
        play.setDisable(true);
        search.setText("lana del rey");
        type.setValue("music");
        messageBox.setPadding(new Insets(5));
        messageBox.getChildren().add(message);
        gallery.getChildren().add(galleryPane);
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 5; c++) {
                ImageView imgView = new ImageView(defaultImage);
                imgView.setFitHeight(135);
                imgView.setFitWidth(135);
                galleryPane.add(imgView, c, r);
                frames.add(imgView);
            } // for
        } // for
        bottom.getChildren().addAll(progress, source);
        HBox.setHgrow(progress, Priority.ALWAYS);
        bottom.setPadding(new Insets(5));
        bottom.setSpacing(5);
        bottom.setAlignment(Pos.CENTER_LEFT);
        progress.setProgress(0.0);
        progress.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(progress, Priority.ALWAYS);
        KeyFrame keyFrame = new KeyFrame(Duration.seconds(2), e -> { // new replacement img
            ImageView frame = frames.get(new Random().nextInt(20));
            Image img = display.remove();
            frame.setImage(img);
            display.add(img);
        });
        replacement.setCycleCount(Timeline.INDEFINITE);
        replacement.getKeyFrames().add(keyFrame);
        Runnable task = () -> {
            pause();
            play.setDisable(true);
            get.setDisable(true);
            message.setText("Getting images...");
            this.search();
        };
        this.get.setOnAction(e -> runNow(task)); // actions when get button clicked
        this.play.setOnAction(e -> { // actions when play button clicked
            if (playing == true) {
                pause();
                Platform.runLater(() -> play.setText("Play"));
            } else {
                playing = true;
                replacement.play();
                Platform.runLater(() -> play.setText("Pause"));
            } // else
        });
    } // init

    /** {@inheritDoc} */
    @Override
    public void start(Stage stage) {
        this.stage = stage;
        this.scene = new Scene(this.root);
        this.stage.setOnCloseRequest(event -> Platform.exit());
        this.stage.setTitle("GalleryApp!");
        this.stage.setScene(this.scene);
        this.stage.sizeToScene();
        this.stage.show();
        Platform.runLater(() -> this.stage.setResizable(false));
    } // start

    /** {@inheritDoc} */
    @Override
    public void stop() {
        // feel free to modify this method
        System.out.println("stop() called");
    } // stop

    /**
     * Method to create new thread and execute task.
     * @param target runnable task
     */
    public static void runNow(Runnable target) {
        Thread t = new Thread(target);
        t.setDaemon(true);
        t.start();
    } // runNow

    /**
     * Method to search with user-given query and receive iTunes response.
     */
    public void search() {
        String term = URLEncoder.encode(search.getText(), StandardCharsets.UTF_8);
        String media = URLEncoder.encode(type.getValue(), StandardCharsets.UTF_8);
        String limit = URLEncoder.encode("200", StandardCharsets.UTF_8);
        String query = String.format("?term=%s&media=%s&limit=%s", term, media, limit);
        String uri = ITUNES_API + query;
        HttpRequest request = HttpRequest.newBuilder() //sending request
            .uri(URI.create(uri))
            .build();
        try {
            HttpResponse<String> response = HTTP_CLIENT.send(request, BodyHandlers.ofString());
            if (response.statusCode() != 200) { // checking if response was successfully received
                throw new IOException(response.toString());
            } // if
            String jsonString = response.body();
            ItunesResponse itunesResponse = GSON.fromJson(jsonString, ItunesResponse.class);
            Set<String> urls = new HashSet<String>();
            for (var result : itunesResponse.results) { // adding artworkUrl100 of result obj
                urls.add(result.artworkUrl100);
            } // for
            if (urls.size() <= 20) { // checking # of urls
                throw new IllegalArgumentException(
                    String.format("%d distinct results found," +
                    " but 21 or more are needed.", urls.size()));
            } // if
            Queue<Image> images = new LinkedList<Image>();
            int load = 0;
            for (var url : urls) { //creating image with url, then adding
                images.add(new Image(url));
                load++;
                double progress = (load + 0.0) / urls.size();
                Platform.runLater(() -> this.progress.setProgress(progress));
            } // for
            Platform.runLater(() ->  {
                get.setDisable(false);
                play.setDisable(false);
                message.setText(uri);
            });
            display = images;
            for (int i = 0; i < 20; i++) {
                Image img = display.remove();
                ImageView imgView = frames.get(i);
                display.add(img);
                Platform.runLater(() -> imgView.setImage(img)); //displaying in grid
            } // for
        } catch (IOException | IllegalArgumentException | InterruptedException e) { // pop-up error
            Platform.runLater(() -> {
                alert.setContentText(String.format("URI: %s%n%nException: %s", uri, e.toString()));
                alert.showAndWait();
                message.setText("Last attempt to get images failed...");
                get.setDisable(false);
                progress.setProgress(1);
                if (display != null) {
                    play.setDisable(false);
                    pause();
                } // if
            });
        } // catch
    } // search

    /**
     * Method to pause random replacement.
     */
    public void pause() {
        replacement.stop();
        Platform.runLater(() -> play.setText("Play"));
        playing = false;
    } // pause
} // GalleryApp
