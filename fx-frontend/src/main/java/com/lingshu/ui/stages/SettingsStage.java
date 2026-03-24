package com.lingshu.ui.stages;

import com.lingshu.core.ThemeManager;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * 设置弹窗窗口类
 * 实现左右结构布局、毛玻璃 UI 和全局主题切换。
 */
public class SettingsStage extends Stage {

    private double xOffset = 0;
    private double yOffset = 0;

    public SettingsStage() {
        this.initStyle(StageStyle.TRANSPARENT);
        this.setAlwaysOnTop(true);
        this.setTitle("灵枢 AI - 系统设置");

        // 1. 基准容器 (StackPane 用于叠加关闭按钮)
        StackPane root = new StackPane();
        root.getStyleClass().add("glass-pane");
        root.setPrefSize(500, 380);

        // 主布局 (左导航 + 右内容)
        HBox mainLayout = new HBox(0);
        mainLayout.setAlignment(Pos.TOP_LEFT);

        // 注入全局主题色变量并同步
        ThemeManager.getInstance().applyTheme(root);

        // --- 左侧导航栏 ---
        VBox leftNav = new VBox(15);
        leftNav.getStyleClass().add("nav-container");
        leftNav.setPadding(new Insets(40, 0, 20, 0));
        leftNav.setPrefWidth(140);
        leftNav.setAlignment(Pos.TOP_CENTER);

        Label navTitle = new Label("设置中心");
        navTitle.getStyleClass().add("nav-title");
        
        Button generalBtn = new Button("外观设置");
        generalBtn.getStyleClass().addAll("nav-item", "active");
        
        Button modelBtn = new Button("模型配置");
        modelBtn.getStyleClass().add("nav-item");

        Button aboutBtn = new Button("关于灵枢");
        aboutBtn.getStyleClass().add("nav-item");

        leftNav.getChildren().addAll(navTitle, generalBtn, modelBtn, aboutBtn);

        // --- 右侧内容区 ---
        VBox rightContent = new VBox(25);
        rightContent.setPadding(new Insets(45, 30, 30, 30));
        HBox.setHgrow(rightContent, Priority.ALWAYS);
        rightContent.setAlignment(Pos.TOP_LEFT);

        // 标题区
        VBox header = new VBox(5);
        Label title = new Label("外观设置");
        title.getStyleClass().add("title-text");
        Label subtitle = new Label("个性化您的灵枢 AI 服务端");
        subtitle.getStyleClass().add("subtitle-text");
        header.getChildren().addAll(title, subtitle);

        // 设置项
        VBox settingsArea = new VBox(15);
        
        Label colorLabel = new Label("系统主题色 (神经通路颜色)");
        colorLabel.getStyleClass().add("accent-label");

        // 下拉框
        ComboBox<String> themeBox = new ComboBox<>();
        themeBox.getItems().addAll("深邃海洋 (蓝色)", "黑客帝国 (绿色)", "霓虹城市 (紫色)", "烈日骄阳 (橙色)");
        themeBox.getStyleClass().add("modern-combo-box");
        themeBox.setMaxWidth(Double.MAX_VALUE);
        
        // 读取管理器当前状态
        String currentColor = ThemeManager.getInstance().getThemeColor();
        ThemeManager.getInstance().themeColorProperty().addListener((obs, old, newVal) -> {
            colorLabel.setTextFill(Color.web(newVal));
        });
        
        if (currentColor.equals("#0078D7")) themeBox.setValue("深邃海洋 (蓝色)");
        else if (currentColor.equals("#00C853")) themeBox.setValue("黑客帝国 (绿色)");
        else if (currentColor.equals("#D500F9")) themeBox.setValue("霓虹城市 (紫色)");
        else if (currentColor.equals("#FF9100")) themeBox.setValue("烈日骄阳 (橙色)");

        themeBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            String color = switch (newVal) {
                case "黑客帝国 (绿色)" -> "#00C853";
                case "霓虹城市 (紫色)" -> "#D500F9";
                case "烈日骄阳 (橙色)" -> "#FF9100";
                default -> "#0078D7";
            };
            ThemeManager.getInstance().setThemeColor(color);
        });

        settingsArea.getChildren().addAll(colorLabel, themeBox);

        // 应用按钮
        Button applyBtn = new Button("保存设置");
        applyBtn.getStyleClass().add("action-button");
        applyBtn.setPrefWidth(120);
        applyBtn.setOnAction(e -> this.close());

        rightContent.getChildren().addAll(header, settingsArea, applyBtn);

        mainLayout.getChildren().addAll(leftNav, rightContent);

        // --- 右上角关闭按钮 ---
        Button closeBtn = new Button("×");
        closeBtn.getStyleClass().add("close-button");
        StackPane.setAlignment(closeBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(closeBtn, new Insets(15));
        closeBtn.setOnAction(e -> this.close());

        root.getChildren().addAll(mainLayout, closeBtn);

        // 3. 实现拖拽逻辑 (主要通过左侧导航栏拖拽)
        root.setOnMousePressed(event -> {
            xOffset = event.getSceneX();
            yOffset = event.getSceneY();
        });
        root.setOnMouseDragged(event -> {
            this.setX(event.getScreenX() - xOffset);
            this.setY(event.getScreenY() - yOffset);
        });

        // 4. 加载场景
        Scene scene = new Scene(root, 550, 400, Color.TRANSPARENT);
        ThemeManager.getInstance().loadStylesheet(scene);
        this.setScene(scene);
    }
}
