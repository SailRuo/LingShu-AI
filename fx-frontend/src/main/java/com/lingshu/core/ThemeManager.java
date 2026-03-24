package com.lingshu.core;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.Scene;
import javafx.scene.layout.Region;

/**
 * 全局主题管理器
 * 负责管理主题色并动态更新所有订阅的 UI 节点。
 */
public class ThemeManager {
    private static final ThemeManager instance = new ThemeManager();

    public enum ThemeMode {
        LIGHT, DARK
    }

    // 默认值
    private final StringProperty currentThemeColor = new SimpleStringProperty("#0078D7");
    private final SimpleStringProperty currentThemeMode = new SimpleStringProperty("DARK");

    private ThemeManager() {}

    public static ThemeManager getInstance() {
        return instance;
    }

    public StringProperty themeColorProperty() {
        return currentThemeColor;
    }

    public String getThemeColor() {
        return currentThemeColor.get();
    }

    public void setThemeColor(String color) {
        currentThemeColor.set(color);
    }

    public StringProperty themeModeProperty() {
        return currentThemeMode;
    }

    public ThemeMode getThemeMode() {
        return ThemeMode.valueOf(currentThemeMode.get());
    }

    public void setThemeMode(ThemeMode mode) {
        currentThemeMode.set(mode.name());
    }

    /**
     * 将主题变量和模式类注入到指定的根节点上
     * @param root 场景的根节点或其他容器
     */
    public void applyTheme(Region root) {
        root.styleProperty().bind(currentThemeColor.map(color -> 
            "-theme-color: " + color + "; -fx-background-color: transparent;"));
        
        root.getStyleClass().remove("light-mode");
        root.getStyleClass().remove("dark-mode");
        
        currentThemeMode.addListener((obs, old, newVal) -> {
            root.getStyleClass().remove("light-mode");
            root.getStyleClass().remove("dark-mode");
            root.getStyleClass().add(newVal.toLowerCase() + "-mode");
        });
        
        root.getStyleClass().add(currentThemeMode.get().toLowerCase() + "-mode");
    }

    /**
     * 为场景加载全局样式表
     */
    public void loadStylesheet(Scene scene) {
        String cssPath = getClass().getResource("/css/style.css").toExternalForm();
        if (!scene.getStylesheets().contains(cssPath)) {
            scene.getStylesheets().add(cssPath);
        }
    }
}
