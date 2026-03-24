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

    // 默认主题色
    private final StringProperty currentThemeColor = new SimpleStringProperty("#0078D7");

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

    /**
     * 将主题色 CSS 变量注入到指定的根节点上
     * @param root 场景的根节点或其他容器
     */
    public void applyTheme(Region root) {
        // 绑定节点样式到主题色变量
        root.styleProperty().bind(currentThemeColor.map(color -> "-theme-color: " + color + ";"));
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
