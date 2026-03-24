package com.lingshu.core;

import com.lingshu.ui.stages.MainMascotStage;
import com.lingshu.ui.stages.SettingsStage;
import javafx.application.Platform;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * 系统托盘及右键菜单服务
 * 处理全局系统级交互入口。
 */
public class TrayService {

    private static final Logger logger = LoggerFactory.getLogger(TrayService.class);
    private final MainMascotStage mainStage;
    private TrayIcon trayIcon;

    public TrayService(MainMascotStage mainStage) {
        this.mainStage = mainStage;
        
        // 初始化右键菜单 (JavaFX 实现)
        initContextMenu();
        
        // 初始化 AWT 系统托盘
        Platform.runLater(this::installTrayIcon);
    }

    private void initContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        
        // 绑定主题 CSS 类
        contextMenu.getStyleClass().add("context-menu");
        
        // 1. 说话菜单
        MenuItem speakItem = new MenuItem("Speak / 开启 Neural 对话");
        speakItem.setOnAction(e -> mainStage.getSpeechBubble().streamText("你好！我是灵枢核心 AI。主题与架构已全面升级。"));

        // 2. 设置中心
        MenuItem settingsItem = new MenuItem("Tuning / 核心设置");
        settingsItem.setOnAction(e -> new SettingsStage().show());

        // 3. 关于
        MenuItem aboutItem = new MenuItem("About / 关于 LingShu");

        // 4. 退出
        MenuItem exitItem = new MenuItem("Shutdown / 关闭程序");
        exitItem.setOnAction(e -> shutdown());

        contextMenu.getItems().addAll(speakItem, settingsItem, new SeparatorMenuItem(), aboutItem, exitItem);

        // 为 AI 挂件绑定交互 (改用 addEventHandler 避免覆盖)
        mainStage.getAiView().addEventHandler(javafx.scene.input.ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            logger.debug("请求右键菜单");
            contextMenu.show(mainStage.getAiView(), event.getScreenX(), event.getScreenY());
        });
        
        // 也可以绑定点击展开 (改用 addEventHandler)
        mainStage.getAiView().addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                logger.debug("次要按钮点击，显示菜单");
                contextMenu.show(mainStage.getAiView(), event.getScreenX(), event.getScreenY());
            }
        });
    }

    private void installTrayIcon() {
        if (!SystemTray.isSupported()) return;

        java.awt.PopupMenu menu = new java.awt.PopupMenu();
        java.awt.MenuItem exitItem = new java.awt.MenuItem("Terminate Program");
        exitItem.addActionListener(e -> shutdown());
        menu.add(exitItem);

        trayIcon = new TrayIcon(createTrayImage(), "LingShu AI Core", menu);
        trayIcon.setImageAutoSize(true);

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            System.err.println("TrayIcon 注册失败: " + e.getMessage());
        }
    }

    private Image createTrayImage() {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        String currentHex = ThemeManager.getInstance().getThemeColor();
        java.awt.Color themeAWT = java.awt.Color.decode(currentHex);
        
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setColor(themeAWT);
        graphics.fillOval(1, 1, 14, 14);
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillOval(5, 5, 6, 6);
        graphics.dispose();
        return image;
    }

    public void shutdown() {
        if (trayIcon != null) {
            SystemTray.getSystemTray().remove(trayIcon);
        }
        Platform.exit();
        System.exit(0);
    }
}
