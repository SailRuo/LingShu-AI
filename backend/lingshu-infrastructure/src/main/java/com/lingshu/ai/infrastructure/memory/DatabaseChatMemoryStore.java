package com.lingshu.ai.infrastructure.memory;

import com.lingshu.ai.infrastructure.entity.ChatSession;
import com.lingshu.ai.infrastructure.repository.ChatMessageRepository;
import com.lingshu.ai.infrastructure.repository.ChatSessionRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 实现 LangChain4j 的 ChatMemoryStore，将对话历史持久化到数据库
 */
@Slf4j
@Component("databaseChatMemoryStore")
public class DatabaseChatMemoryStore implements ChatMemoryStore {

    private final ChatMessageRepository messageRepository;
    private final ChatSessionRepository sessionRepository;

    public DatabaseChatMemoryStore(ChatMessageRepository messageRepository, 
                                   ChatSessionRepository sessionRepository) {
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        log.debug("Memory retrieval: Loading history for session ID: {}", memoryId);
        Long sessionId = parseId(memoryId);
        
        List<com.lingshu.ai.infrastructure.entity.ChatMessage> dbMessages = 
                messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        
        return dbMessages.stream()
                .map(this::toLangChain4jMessage)
                .collect(Collectors.toList());
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        log.debug("Memory update: Syncing {} messages for session ID: {}", messages.size(), memoryId);
        Long sessionId = parseId(memoryId);
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        // 获取数据库中已有的最后一条时间，避免重复保存
        // 注意：这种简单的比较在并发或极短时间内可能有风险，但对于单人对话已足够
        List<com.lingshu.ai.infrastructure.entity.ChatMessage> existing = 
                messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        
        int startIndex = existing.size();
        if (messages.size() > startIndex) {
            for (int i = startIndex; i < messages.size(); i++) {
                ChatMessage msg = messages.get(i);
                saveMessage(session, msg);
            }
        }
    }

    @Override
    public void deleteMessages(Object memoryId) {
        log.debug("Memory deletion: Clearing history for session ID: {}", memoryId);
        Long sessionId = parseId(memoryId);
        // 通常我们不真正删除历史，只是逻辑分离。根据需求实现。
    }

    private void saveMessage(ChatSession session, ChatMessage msg) {
        String role = getRole(msg);
        if (role == null) return; // Ignore SystemMessage or unknown types for history persistence
        
        com.lingshu.ai.infrastructure.entity.ChatMessage dbMsg = 
                com.lingshu.ai.infrastructure.entity.ChatMessage.builder()
                        .session(session)
                        .role(role)
                        .content(msg.text())
                        .createdAt(LocalDateTime.now())
                        .build();
        messageRepository.save(dbMsg);
    }

    private String getRole(ChatMessage msg) {
        if (msg instanceof UserMessage) return "user";
        if (msg instanceof AiMessage) return "assistant";
        return null;
    }

    private ChatMessage toLangChain4jMessage(com.lingshu.ai.infrastructure.entity.ChatMessage dbMsg) {
        if ("user".equalsIgnoreCase(dbMsg.getRole())) {
            return UserMessage.from(dbMsg.getContent());
        } else if ("assistant".equalsIgnoreCase(dbMsg.getRole())) {
            return AiMessage.from(dbMsg.getContent());
        }
        return SystemMessage.from(dbMsg.getContent()); // Should not happen for history
    }

    private Long parseId(Object memoryId) {
        if (memoryId instanceof Long) return (Long) memoryId;
        if (memoryId instanceof String) return Long.parseLong((String) memoryId);
        return 1L; // Fallback
    }
}
