package com.lingshu.ui.stages;

import com.lingshu.core.AppConfig;
import com.lingshu.core.AppConfigService;
import com.lingshu.core.ThemeManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * 系统设置窗口。
 */
public class SettingsStage extends Stage {

    private final AppConfigService appConfigService = AppConfigService.getInstance();
    private double xOffset;
    private double yOffset;

    public SettingsStage() {
        this.initStyle(StageStyle.TRANSPARENT);
        this.setAlwaysOnTop(true);
        this.setTitle("LingShu AI - 系统设置");

        AppConfig config = appConfigService.load();
        
        // Initialize ThemeManager state
        ThemeManager.getInstance().setThemeColor(config.themeColor());
        ThemeManager.getInstance().setThemeMode(ThemeManager.ThemeMode.valueOf(config.themeMode()));

        StackPane root = new StackPane();
        root.getStyleClass().add("glass-pane");
        root.setPrefSize(640, 480);

        HBox mainLayout = new HBox();
        mainLayout.setAlignment(Pos.TOP_LEFT);

        ThemeManager.getInstance().applyTheme(root);

        // --- Left Nav ---
        VBox leftNav = new VBox(10);
        leftNav.getStyleClass().add("nav-container");
        leftNav.setPadding(new Insets(40, 0, 20, 0));
        leftNav.setPrefWidth(160);
        leftNav.setAlignment(Pos.TOP_CENTER);

        Label navTitle = new Label("设置中心");
        navTitle.getStyleClass().add("nav-title");

        Button appearanceBtn = createNavBtn("外观设置", true);
        Button serviceBtn = createNavBtn("服务配置", false);
        Button aboutBtn = createNavBtn("关于灵枢", false);

        leftNav.getChildren().addAll(navTitle, appearanceBtn, serviceBtn, aboutBtn);

        VBox rightContent = new VBox(25);
        rightContent.setPadding(new Insets(45, 35, 35, 35));
        HBox.setHgrow(rightContent, Priority.ALWAYS);
        rightContent.setAlignment(Pos.TOP_LEFT);

        VBox header = new VBox(8);
        Label title = new Label("个性化与服务");
        title.getStyleClass().add("title-text");
        Label subtitle = new Label("自定义您的 AI 助手外观，并配置核心服务地址。");
        subtitle.getStyleClass().add("subtitle-text");
        header.getChildren().addAll(title, subtitle);

        VBox settingsArea = new VBox(18);

        // Mode Toggle
        VBox modeSection = new VBox(8);
        Label modeLabel = new Label("界面模式");
        modeLabel.getStyleClass().add("accent-label");
        
        HBox modeToggle = new HBox(0);
        modeToggle.getStyleClass().add("theme-toggle-group");
        Button lightBtn = new Button("浅色模式");
        Button darkBtn = new Button("深色模式");
        lightBtn.getStyleClass().add("theme-toggle-item");
        darkBtn.getStyleClass().add("theme-toggle-item");
        
        lightBtn.setOnAction(e -> {
            lightBtn.getStyleClass().add("selected");
            darkBtn.getStyleClass().remove("selected");
            ThemeManager.getInstance().setThemeMode(ThemeManager.ThemeMode.LIGHT);
        });
        darkBtn.setOnAction(e -> {
            darkBtn.getStyleClass().add("selected");
            lightBtn.getStyleClass().remove("selected");
            ThemeManager.getInstance().setThemeMode(ThemeManager.ThemeMode.DARK);
        });
        
        if (ThemeManager.getInstance().getThemeMode() == ThemeManager.ThemeMode.LIGHT) {
            lightBtn.getStyleClass().add("selected");
        } else {
            darkBtn.getStyleClass().add("selected");
        }
        
        modeToggle.getChildren().addAll(lightBtn, darkBtn);
        modeSection.getChildren().addAll(modeLabel, modeToggle);

        // Color Picker
        VBox colorSection = new VBox(8);
        Label colorLabel = new Label("系统风格色");
        colorLabel.getStyleClass().add("accent-label");

        ComboBox<String> themeBox = new ComboBox<>();
        themeBox.getItems().addAll("深海海洋 (蓝色)", "黑客帝国 (绿色)", "霓虹城市 (紫色)", "烈日骄阳 (橙色)");
        themeBox.getStyleClass().add("modern-combo-box");
        themeBox.setMaxWidth(Double.MAX_VALUE);

        String currentColor = ThemeManager.getInstance().getThemeColor();
        if ("#0078D7".equals(currentColor)) themeBox.setValue("深海海洋 (蓝色)");
        else if ("#00C853".equals(currentColor)) themeBox.setValue("黑客帝国 (绿色)");
        else if ("#D500F9".equals(currentColor)) themeBox.setValue("霓虹城市 (紫色)");
        else if ("#FF9100".equals(currentColor)) themeBox.setValue("烈日骄阳 (橙色)");

        themeBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            String color = switch (newVal) {
                case "黑客帝国 (绿色)" -> "#00C853";
                case "霓虹城市 (紫色)" -> "#D500F9";
                case "烈日骄阳 (橙色)" -> "#FF9100";
                default -> "#0078D7";
            };
            ThemeManager.getInstance().setThemeColor(color);
        });
        colorSection.getChildren().addAll(colorLabel, themeBox);

        VBox asrSection = new VBox(8);
        Label asrToggleLabel = new Label("语音识别 (ASR)");
        asrToggleLabel.getStyleClass().add("accent-label");
        
        CheckBox asrEnabledCheckBox = new CheckBox("启用语音识别功能");
        asrEnabledCheckBox.setSelected(config.asrEnabled());
        asrEnabledCheckBox.getStyleClass().add("modern-checkbox");
        
        asrSection.getChildren().addAll(asrToggleLabel, asrEnabledCheckBox);

        VBox ttsSection = new VBox(8);
        Label ttsToggleLabel = new Label("语音合成 (TTS)");
        ttsToggleLabel.getStyleClass().add("accent-label");
        
        CheckBox ttsEnabledCheckBox = new CheckBox("启用语音合成功能");
        ttsEnabledCheckBox.setSelected(config.ttsEnabled());
        ttsEnabledCheckBox.getStyleClass().add("modern-checkbox");
        
        ttsSection.getChildren().addAll(ttsToggleLabel, ttsEnabledCheckBox);
        
        VBox vadSection = new VBox(8);
        Label vadLabel = new Label("ASR 灵敏度 (VAD 阈值)");
        vadLabel.getStyleClass().add("accent-label");
        Label vadHint = new Label("数值越小越灵敏。如果 ASR 没反应，请尝试调低该值（如 50-100）。");
        vadHint.getStyleClass().add("subtitle-text");
        vadHint.setStyle("-fx-font-size: 11px;");
        
        TextField vadThresholdField = new TextField(String.valueOf(config.vadThreshold()));
        vadThresholdField.getStyleClass().add("modern-text-field");
        
        vadSection.getChildren().addAll(vadLabel, vadHint, vadThresholdField);

        // Service URLs
        VBox urlSection = new VBox(12);
        Label ttsLabel = new Label("TTS 服务地址");
        ttsLabel.getStyleClass().add("accent-label");
        TextField ttsUrlField = createUrlField(config.ttsWsUrl());
        
        Label asrLabel = new Label("ASR 服务地址");
        asrLabel.getStyleClass().add("accent-label");
        TextField asrUrlField = createUrlField(config.asrWsUrl());
        
        urlSection.getChildren().addAll(ttsLabel, ttsUrlField, asrLabel, asrUrlField);

        Label saveStatus = createHintLabel("");
        saveStatus.setWrapText(true);

        settingsArea.getChildren().addAll(modeSection, colorSection, asrSection, ttsSection, vadSection, urlSection, saveStatus);

               HBox actionBar = new HBox(12);
        actionBar.setAlignment(Pos.CENTER_LEFT);

        Button applyBtn = new Button("保存并应用");
        applyBtn.getStyleClass().add("action-button");
        applyBtn.setPrefWidth(140);
        applyBtn.setOnAction(e -> {
            AppConfig updatedConfig = new AppConfig(
                ttsUrlField.getText(), 
                asrUrlField.getText(),
                ThemeManager.getInstance().getThemeColor(),
                ThemeManager.getInstance().getThemeMode().name(),
                asrEnabledCheckBox.isSelected(),
                ttsEnabledCheckBox.isSelected(),
                Integer.parseInt(vadThresholdField.getText().trim())
            );
            appConfigService.save(updatedConfig);
            saveStatus.setTextFill(Color.web(ThemeManager.getInstance().getThemeColor()));
            saveStatus.setText("设置已保存并实时生效。");
        });

        Button closeBtnInline = new Button("取消");
        closeBtnInline.getStyleClass().add("action-button");
        closeBtnInline.setStyle("-fx-background-color: transparent; -fx-border-color: -nav-border; -fx-border-radius: 12;");
        closeBtnInline.setOnAction(e -> this.close());

        actionBar.getChildren().addAll(applyBtn, closeBtnInline);
        rightContent.getChildren().addAll(header, settingsArea, actionBar);

        mainLayout.getChildren().addAll(leftNav, rightContent);

        Button closeBtn = new Button("×");
        closeBtn.getStyleClass().add("close-button");
        StackPane.setAlignment(closeBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(closeBtn, new Insets(15));
        closeBtn.setOnAction(e -> this.close());

        root.getChildren().addAll(mainLayout, closeBtn);

        root.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        root.setOnMouseDragged(event -> {
            this.setX(event.getScreenX() - xOffset);
            this.setY(event.getScreenY() - yOffset);
        });

        Scene scene = new Scene(root, 640, 480, Color.TRANSPARENT);
        ThemeManager.getInstance().loadStylesheet(scene);
        this.setScene(scene);
    }

    private Button createNavBtn(String text, boolean active) {
        Button btn = new Button(text);
        btn.getStyleClass().add("nav-item");
        if (active) btn.getStyleClass().add("active");
        btn.setDisable(!active);
        return btn;
    }

    private TextField createUrlField(String value) {
        TextField textField = new TextField(value);
        textField.setMaxWidth(Double.MAX_VALUE);
        textField.getStyleClass().add("modern-text-field");
        return textField;
    }

    private Label createHintLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("subtitle-text");
        label.setStyle("-fx-font-size: 12px;");
        return label;
    }
}
