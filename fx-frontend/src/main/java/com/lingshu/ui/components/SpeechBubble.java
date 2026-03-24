package com.lingshu.ui.components;

import com.lingshu.core.ThemeManager;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.util.Duration;

/**
 * 语言气泡自定义组件
 * 封装毛玻璃外观、指向尾巴及打字机模拟逻辑。
 */
public class SpeechBubble extends VBox {

    private final Label speechLabel;
    private final Polygon bubbleTail;
    private Timeline typewriterTimeline;

    public SpeechBubble() {
        this.setAlignment(Pos.CENTER);
        this.setMouseTransparent(true);
        this.setOpacity(0);

        // 1. 气泡主体内容
        speechLabel = new Label("");
        speechLabel.setWrapText(true);
        speechLabel.setMaxWidth(240);
        speechLabel.getStyleClass().add("bubble-text");
        speechLabel.setFont(Font.font("Microsoft YaHei", FontWeight.BOLD, 15));
        speechLabel.setAlignment(Pos.CENTER);

        VBox labelContainer = new VBox(speechLabel);
        labelContainer.getStyleClass().add("bubble-container");
        labelContainer.setPadding(new Insets(15, 20, 15, 20));
        labelContainer.setMaxSize(280, 180);
        labelContainer.setAlignment(Pos.CENTER);

        // 2. 气泡指向尾巴
        bubbleTail = new Polygon();
        bubbleTail.getPoints().addAll(0.0, 0.0, 12.0, 15.0, -12.0, 15.0);
        bubbleTail.setRotate(180);
        
        // 绑定尾巴的颜色到全局主题
        updateTailStyle();
        ThemeManager.getInstance().themeColorProperty().addListener((obs, old, color) -> updateTailStyle());
        ThemeManager.getInstance().themeModeProperty().addListener((obs, old, mode) -> updateTailStyle());

        // 3. 组装组件
        this.getChildren().addAll(labelContainer, bubbleTail);
        
        // 4. 注入全局主题 CSS 变量
        ThemeManager.getInstance().applyTheme(this);
    }

    private void updateTailStyle() {
        boolean isLight = ThemeManager.getInstance().getThemeMode() == ThemeManager.ThemeMode.LIGHT;
        bubbleTail.setFill(isLight ? Color.web("#FFFFFF", 0.9) : Color.web("#191919", 0.85));
        bubbleTail.setStroke(Color.web(ThemeManager.getInstance().getThemeColor(), 0.5));
        bubbleTail.setStrokeWidth(1.5);
    }

    /**
     * 流式文本播放功能
     * @param fullText 完整的待播放文本
     */
    public void streamText(String fullText) {
        if (typewriterTimeline != null) typewriterTimeline.stop();

        this.setOpacity(1);
        this.setMouseTransparent(false);
        speechLabel.setText("");

        typewriterTimeline = new Timeline();
        for (int i = 0; i <= fullText.length(); i++) {
            final int index = i;
            KeyFrame keyFrame = new KeyFrame(Duration.millis(50 * i), e -> {
                speechLabel.setText(fullText.substring(0, index));
            });
            typewriterTimeline.getKeyFrames().add(keyFrame);
        }

        typewriterTimeline.setOnFinished(e -> {
            Timeline hideTimeline = new Timeline(new KeyFrame(Duration.seconds(8), event -> {
                this.setOpacity(0);
                this.setMouseTransparent(true);
            }));
            hideTimeline.play();
        });

        typewriterTimeline.play();
    }
}
