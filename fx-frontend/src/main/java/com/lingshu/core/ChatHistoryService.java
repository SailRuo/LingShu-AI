package com.lingshu.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 聊天历史记录服务。
 */
public class ChatHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(ChatHistoryService.class);
    private static final ChatHistoryService INSTANCE = new ChatHistoryService();
    private final AppConfigService configService;

    private ChatHistoryService() {
        this.configService = AppConfigService.getInstance();
    }

    public static ChatHistoryService getInstance() {
        return INSTANCE;
    }

    /**
     * 保存一条聊天记录。
     */
    public void saveMessage(String role, String content) {
        String sql = "INSERT INTO chat_history (role, content) VALUES (?, ?)";
        try (Connection conn = configService.openConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, role);
            pstmt.setString(2, content);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("保存消息记录失败", e);
        }
    }

    /**
     * 获取最近的聊天记录。
     */
    public List<ChatMessage> getRecentMessages(int limit) {
        List<ChatMessage> messages = new ArrayList<>();
        String sql = "SELECT id, role, content, created_at FROM chat_history ORDER BY id DESC LIMIT ?";
        try (Connection conn = configService.openConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(new ChatMessage(
                            rs.getLong("id"),
                            rs.getString("role"),
                            rs.getString("content"),
                            parseDateTime(rs.getString("created_at"))
                    ));
                }
            }
        } catch (SQLException e) {
            logger.error("获取消息记录失败", e);
        }
        // 反转列表使时间正序
        return messages.reversed();
    }

    private LocalDateTime parseDateTime(String timestamp) {
        if (timestamp == null) return LocalDateTime.now();
        try {
            // SQLite 默认格式 YYYY-MM-DD HH:MM:SS
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return LocalDateTime.parse(timestamp, formatter);
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
