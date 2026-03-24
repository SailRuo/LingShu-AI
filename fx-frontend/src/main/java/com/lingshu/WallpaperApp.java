package com.lingshu;

import com.lingshu.core.TrayService;
import com.lingshu.ui.stages.MainMascotStage;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * 灵枢 AI 桌面挂件 - 启动器
 * 负责整体生命周期管理及架构各模块解耦。
 */
public class WallpaperApp extends Application {

    private MainMascotStage mainStage;
    private TrayService trayService;

    @Override
    public void start(Stage primaryStage) {
        // 1. 初始化主挂件窗口 (不再使用传入的 primaryStage)
        mainStage = new MainMascotStage();
        
        // 2. 初始化托盘服务与右键菜单
        trayService = new TrayService(mainStage);

        // 3. 显示 UI
        mainStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
