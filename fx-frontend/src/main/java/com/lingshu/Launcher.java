package com.lingshu;

import javafx.application.Application;

/**
 * 灵枢 AI - 核心应用启动入口 (Standard Launcher)
 * 用于解决 JavaFX 的运行时加载问题，避免直接从继承了 Application 的类中启动 Main。
 */
public class Launcher {
    public static void main(String[] args) {
        // 直接调用 WallpaperApp 的 main 或者 launch 方法
        Application.launch(WallpaperApp.class, args);
    }
}
