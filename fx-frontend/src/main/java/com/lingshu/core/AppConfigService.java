package com.lingshu.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class AppConfigService {

    private static final Logger logger = LoggerFactory.getLogger(AppConfigService.class);
    private static final Path DB_PATH = Paths.get("lingshu.db").toAbsolutePath();
    private static final String JDBC_URL = "jdbc:sqlite:" + DB_PATH;
    private static final AppConfigService INSTANCE = new AppConfigService();

    private static final String KEY_TTS_WS_URL = "tts_ws_url";
    private static final String KEY_ASR_WS_URL = "asr_ws_url";
    private static final String KEY_THEME_COLOR = "theme_color";
    private static final String KEY_THEME_MODE = "theme_mode";
    private static final String KEY_ASR_ENABLED = "asr_enabled";
    private static final String KEY_TTS_ENABLED = "tts_enabled";
    private static final String KEY_VAD_MULTIPLIER = "vad_multiplier";

    private AppConfigService() {
        initSchema();
        ensureDefaults();
    }

    public static AppConfigService getInstance() {
        return INSTANCE;
    }

    public synchronized AppConfig load() {
        Map<String, String> configMap = loadAll();
        boolean asrEnabled = Boolean.parseBoolean(
                configMap.getOrDefault(KEY_ASR_ENABLED, String.valueOf(AppConfig.DEFAULT_ASR_ENABLED))
        );
        boolean ttsEnabled = Boolean.parseBoolean(
                configMap.getOrDefault(KEY_TTS_ENABLED, String.valueOf(AppConfig.DEFAULT_TTS_ENABLED))
        );
        double vadMultiplier = Double.parseDouble(
                configMap.getOrDefault(KEY_VAD_MULTIPLIER, String.valueOf(AppConfig.DEFAULT_VAD_MULTIPLIER))
        );
        return new AppConfig(
                configMap.getOrDefault(KEY_TTS_WS_URL, AppConfig.DEFAULT_TTS_WS_URL),
                configMap.getOrDefault(KEY_ASR_WS_URL, AppConfig.DEFAULT_ASR_WS_URL),
                configMap.getOrDefault(KEY_THEME_COLOR, AppConfig.DEFAULT_THEME_COLOR),
                configMap.getOrDefault(KEY_THEME_MODE, AppConfig.DEFAULT_THEME_MODE),
                asrEnabled,
                ttsEnabled,
                vadMultiplier
        );
    }

    public synchronized void save(AppConfig config) {
        upsert(KEY_TTS_WS_URL, config.ttsWsUrl());
        upsert(KEY_ASR_WS_URL, config.asrWsUrl());
        upsert(KEY_THEME_COLOR, config.themeColor());
        upsert(KEY_THEME_MODE, config.themeMode());
        upsert(KEY_ASR_ENABLED, String.valueOf(config.asrEnabled()));
        upsert(KEY_TTS_ENABLED, String.valueOf(config.ttsEnabled()));
        upsert(KEY_VAD_MULTIPLIER, String.valueOf(config.vadMultiplier()));
        logger.info("配置已保存到 SQLite: {}", DB_PATH);
    }

    public synchronized String get(String key) {
        return get(key, null);
    }

    public synchronized String get(String key, String defaultValue) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT value FROM app_config WHERE key = ?"
             )) {
            statement.setString(1, key);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getString("value");
                }
            }
        } catch (SQLException e) {
            logger.error("读取配置失败: key={}", key, e);
        }
        return defaultValue;
    }

    public synchronized void set(String key, String value) {
        upsert(key, value);
        logger.info("配置已更新: {} = {}", key, value);
    }

    public synchronized void delete(String key) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM app_config WHERE key = ?"
             )) {
            statement.setString(1, key);
            statement.executeUpdate();
            logger.info("配置已删除: {}", key);
        } catch (SQLException e) {
            logger.error("删除配置失败: key={}", key, e);
        }
    }

    private Map<String, String> loadAll() {
        Map<String, String> configMap = new HashMap<>();
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT key, value FROM app_config"
             );
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                configMap.put(resultSet.getString("key"), resultSet.getString("value"));
            }
        } catch (SQLException e) {
            logger.error("读取所有配置失败", e);
        }
        return configMap;
    }

    private void upsert(String key, String value) {
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO app_config (key, value) VALUES (?, ?) " +
                     "ON CONFLICT(key) DO UPDATE SET value = excluded.value"
             )) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.error("保存配置失败: key={}, value={}", key, value, e);
        }
    }

    private void initSchema() {
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            migrateOldSchema(connection);

            statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS app_config (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                    """
            );
            statement.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS chat_history (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        role TEXT NOT NULL,
                        content TEXT NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    )
                    """
            );
        } catch (SQLException e) {
            logger.error("数据库初始化失败", e);
        }
    }

    private void migrateOldSchema(Connection connection) {
        try {
            ResultSet columns = connection.getMetaData().getColumns(null, null, "app_config", "id");
            if (columns.next()) {
                logger.info("检测到旧表结构，开始迁移...");
                ResultSet oldData = connection.createStatement().executeQuery(
                        "SELECT tts_ws_url, asr_ws_url, theme_color, theme_mode FROM app_config WHERE id = 1"
                );
                if (oldData.next()) {
                    String ttsUrl = oldData.getString("tts_ws_url");
                    String asrUrl = oldData.getString("asr_ws_url");
                    String themeColor = oldData.getString("theme_color");
                    String themeMode = oldData.getString("theme_mode");

                    connection.createStatement().executeUpdate("DROP TABLE app_config");

                    connection.createStatement().executeUpdate(
                            """
                            CREATE TABLE app_config (
                                key TEXT PRIMARY KEY,
                                value TEXT NOT NULL
                            )
                            """
                    );

                    if (ttsUrl != null) upsertDirect(connection, KEY_TTS_WS_URL, ttsUrl);
                    if (asrUrl != null) upsertDirect(connection, KEY_ASR_WS_URL, asrUrl);
                    if (themeColor != null) upsertDirect(connection, KEY_THEME_COLOR, themeColor);
                    if (themeMode != null) upsertDirect(connection, KEY_THEME_MODE, themeMode);

                    logger.info("旧表数据迁移完成");
                }
                columns.close();
            }
        } catch (SQLException e) {
            logger.info("无需迁移或迁移失败，将使用新表结构");
        }
    }

    private void upsertDirect(Connection connection, String key, String value) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(
                "INSERT INTO app_config (key, value) VALUES (?, ?)"
        )) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.executeUpdate();
        }
    }

    private void ensureDefaults() {
        save(load());
    }

    protected Connection openConnection() throws SQLException {
        return DriverManager.getConnection(JDBC_URL);
    }
}
