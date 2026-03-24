package com.lingshu.ui.stages;

import com.lingshu.core.AppConfig;
import com.lingshu.core.AppConfigService;
import com.lingshu.core.AsrService;
import com.lingshu.core.AudioStreamService;
import com.lingshu.core.ThemeManager;
import com.lingshu.ui.components.SpeechBubble;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import javafx.scene.input.MouseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * AI 挂件主窗口类
 * 管理形象展示、气泡对齐及拖拽交互，并集成语音流服务。
 */
public class MainMascotStage extends Stage {

    private static final Logger logger = LoggerFactory.getLogger(MainMascotStage.class);
    private final ImageView aiView;
    private final SpeechBubble speechBubble;
    private final AudioStreamService audioService;
    private final AsrService asrService;
    private final AppConfigService appConfigService;
    private double xOffset = 0;
    private double yOffset = 0;

    public MainMascotStage() {
        this.initStyle(StageStyle.TRANSPARENT);
        this.setAlwaysOnTop(true);
        this.setTitle("LingShu AI Mascot");

        this.appConfigService = AppConfigService.getInstance();

        this.audioService = new AudioStreamService();
        this.asrService = new AsrService();

        aiView = new ImageView();
        try {
            Image aiImage = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/icon/ai.png")));
            aiView.setImage(aiImage);
            aiView.setPreserveRatio(true);
            aiView.setFitWidth(250);
            aiView.setPickOnBounds(true);
        } catch (Exception e) {
            logger.error("加载 AI 图片失败", e);
        }

        speechBubble = new SpeechBubble();
        speechBubble.setTranslateY(-1.0);

        initDragEvents();
        initAsrCallback();

        StackPane layoutNode = new StackPane(aiView, speechBubble);
        layoutNode.setAlignment(Pos.BOTTOM_CENTER);
        layoutNode.setStyle("-fx-background-color: transparent;");

        StackPane root = new StackPane(layoutNode);
        root.setPadding(new Insets(20));
        root.setPickOnBounds(false); 
        root.setStyle("-fx-background-color: transparent;");

        Scene scene = new Scene(root, 400, 750, Color.TRANSPARENT);
        ThemeManager.getInstance().applyTheme(root);
        ThemeManager.getInstance().loadStylesheet(scene);
        this.setScene(scene);

        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        this.setX(bounds.getMaxX() - 400);
        this.setY(bounds.getMaxY() - 750);

        initAsrFromConfig();
    }

    private void initAsrCallback() {
        asrService.setOnRecognitionResult(text -> {
            Platform.runLater(() -> {
                logger.info("ASR 识别结果: {}", text);
                speechBubble.streamText("你说: " + text);
                
                AppConfig config = appConfigService.load();
                if (config.ttsEnabled()) {
                    audioService.speak(text, "taozi");
                }
            });
        });
    }

    private void initAsrFromConfig() {
        AppConfig config = appConfigService.load();
        if (config.asrEnabled()) {
            logger.info("根据配置启动 ASR 持续监听");
            asrService.startListening();
        }
    }

    private void initDragEvents() {
        aiView.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                xOffset = event.getSceneX();
                yOffset = event.getSceneY();
            }
        });

        aiView.setOnMouseDragged(event -> {
            if (event.getButton() == MouseButton.PRIMARY) {
                this.setX(event.getScreenX() - xOffset);
                this.setY(event.getScreenY() - yOffset);
            }
        });

        aiView.addEventHandler(MouseEvent.MOUSE_ENTERED, e -> logger.debug("鼠标进入 AI 区域"));
        aiView.addEventHandler(MouseEvent.MOUSE_EXITED, e -> logger.debug("鼠标离开 AI 区域"));

//        aiView.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
//            logger.info("Mascot 被点击: {}, Count: {}", event.getButton(), event.getClickCount());
//            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
//                String welcomeText = "你好！我是灵枢 AI 助手，很高兴为你服务。你可以通过拖拽来调整我的位置，右键打开菜单。";
//                logger.info("正在显示气泡并调用语音服务...");
//                speechBubble.streamText(welcomeText);
//
//                AppConfig config = appConfigService.load();
//                if (config.ttsEnabled()) {
//                    audioService.speak(welcomeText, "taozi");
//                }
//            }
//        });
    }

    public ImageView getAiView() {
        return aiView;
    }

    public SpeechBubble getSpeechBubble() {
        return speechBubble;
    }

    public AudioStreamService getAudioService() {
        return audioService;
    }

    public AsrService getAsrService() {
        return asrService;
    }
}
