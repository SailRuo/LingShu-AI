package com.lingshu.ai.infrastructure.memory;

import com.lingshu.ai.infrastructure.entity.ChatSession;
import com.lingshu.ai.infrastructure.repository.ChatMessageRepository;
import com.lingshu.ai.infrastructure.repository.ChatSessionRepository;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * 实现 LangChain4j 的 ChatMemoryStore，将对话历史持久化到数据库
 */
@Slf4j
@Component("databaseChatMemoryStore")
public class DatabaseChatMemoryStore implements ChatMemoryStore {

    private final ChatMessageRepository messageRepository;
    private final ChatSessionRepository sessionRepository;
    private final ConcurrentMap<Long, SystemMessage> sessionSystemMessages = new ConcurrentHashMap<>();

    public DatabaseChatMemoryStore(ChatMessageRepository messageRepository, 
                                   ChatSessionRepository sessionRepository) {
        this.messageRepository = messageRepository;
        this.sessionRepository = sessionRepository;
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        log.debug("Memory retrieval: Loading history for session ID: {}", memoryId);
        Long sessionId = parseId(memoryId);
        
        // 分页获取最近的 10 条消息，避免过量加载
        org.springframework.data.domain.Pageable pageable = 
                org.springframework.data.domain.PageRequest.of(0, 10, org.springframework.data.domain.Sort.by("createdAt").descending());
        List<com.lingshu.ai.infrastructure.entity.ChatMessage> dbMessages = 
                new java.util.ArrayList<>(messageRepository.findBySessionIdOrderByCreatedAtDesc(sessionId, pageable).getContent());
        
        // 翻转回正序供 LangChain4j 使用
        java.util.Collections.reverse(dbMessages);
        
        List<ChatMessage> messages = dbMessages.stream()
                .map(this::toLangChain4jMessage)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        SystemMessage systemMessage = sessionSystemMessages.get(sessionId);
        if (systemMessage != null) {
            messages.add(0, systemMessage);
        }

        return messages;
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) return;

        Long sessionId = parseId(memoryId);

        Optional<SystemMessage> systemMessage = messages.stream()
                .filter(SystemMessage.class::isInstance)
                .map(SystemMessage.class::cast)
                .reduce((first, second) -> second);
        if (systemMessage.isPresent()) {
            sessionSystemMessages.put(sessionId, systemMessage.get());
        } else {
            sessionSystemMessages.remove(sessionId);
        }
        
        // 过滤掉 SystemMessage，因为 system prompt 由 ChatServiceImpl 每次动态注入，不应持久化
        java.util.List<ChatMessage> persistableMessages = messages.stream()
                .filter(m -> !(m instanceof SystemMessage))
                .collect(java.util.stream.Collectors.toList());
        if (persistableMessages.isEmpty()) return;
        
        log.debug("Memory update: Syncing {} messages for session ID: {}", messages.size(), memoryId);
        ChatSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        // 获取数据库中已有的最后一条消息
        org.springframework.data.domain.Pageable pageable = 
                org.springframework.data.domain.PageRequest.of(0, 1, org.springframework.data.domain.Sort.by("id").descending());
        List<com.lingshu.ai.infrastructure.entity.ChatMessage> lastInDbList = 
                messageRepository.findBySessionIdOrderByCreatedAtDesc(sessionId, pageable).getContent();
        
        if (lastInDbList.isEmpty()) {
            // 数据库为空，保存所有（已过滤 SystemMessage）
            for (ChatMessage msg : persistableMessages) {
                saveMessage(session, msg);
            }
            return;
        }

        com.lingshu.ai.infrastructure.entity.ChatMessage lastDbMsg = lastInDbList.get(0);
        
        // 找到内存列表中哪些是新消息（使用已过滤 SystemMessage 的列表）
        // 我们通过内容和角色进行简单的比对。
        // 注意：由于 LangChain4j 列表是窗口化的，我们从后往前找
        int newMessagesStartIndex = -1;
        for (int i = persistableMessages.size() - 1; i >= 0; i--) {
            ChatMessage currentMemMsg = persistableMessages.get(i);
            String currentRole = getRole(currentMemMsg);
            String currentText = getText(currentMemMsg);
            
            if (currentRole != null && currentRole.equals(lastDbMsg.getRole()) && currentText.equals(lastDbMsg.getContent())) {
                // 找到了在 DB 中存在的最后一条，那么它之后的都是新的
                newMessagesStartIndex = i + 1;
                break;
            }
        }
        
        if (newMessagesStartIndex == -1) {
            // 如果没找到匹配（可能是窗口滑动导致旧消息不在内存了），则检查最后一条是否匹配
            ChatMessage lastMemMsg = persistableMessages.get(persistableMessages.size() - 1);
            if (!java.util.Objects.equals(getText(lastMemMsg), lastDbMsg.getContent())) {
                saveMessage(session, lastMemMsg);
            }
        } else {
            for (int i = newMessagesStartIndex; i < persistableMessages.size(); i++) {
                saveMessage(session, persistableMessages.get(i));
            }
        }
    }

    private String getText(ChatMessage msg) {
        String text = "";
        if (msg instanceof UserMessage) {
            text = ((UserMessage) msg).singleText();
        } else if (msg instanceof AiMessage) {
            text = ((AiMessage) msg).text();
        } else if (msg instanceof dev.langchain4j.data.message.ToolExecutionResultMessage) {
            text = ((dev.langchain4j.data.message.ToolExecutionResultMessage) msg).text();
        }
        return text != null ? text : "";
    }

    @Override
    public void deleteMessages(Object memoryId) {
        log.debug("Memory deletion: Clearing history for session ID: {}", memoryId);
        Long sessionId = parseId(memoryId);
        sessionSystemMessages.remove(sessionId);
        // 通常我们不真正删除历史，只是逻辑分离。根据需求实现。
    }

    private void saveMessage(ChatSession session, ChatMessage msg) {
        String role = getRole(msg);
        if (role == null) return; 
        
        String text = getText(msg);
        if (text == null || text.isBlank()) return;
        
        com.lingshu.ai.infrastructure.entity.ChatMessage dbMsg = 
                com.lingshu.ai.infrastructure.entity.ChatMessage.builder()
                        .session(session)
                        .role(role)
                        .content(text)
                        .createdAt(LocalDateTime.now())
                        .build();
        messageRepository.save(dbMsg);
    }

    private String getRole(ChatMessage msg) {
        if (msg instanceof SystemMessage) return "system";
        if (msg instanceof UserMessage) return "user";
        if (msg instanceof AiMessage) return "assistant";
        if (msg instanceof dev.langchain4j.data.message.ToolExecutionResultMessage) return "tool";
        return null;
    }

    private ChatMessage toLangChain4jMessage(com.lingshu.ai.infrastructure.entity.ChatMessage dbMsg) {
        String role = dbMsg.getRole();
        String content = dbMsg.getContent() != null ? dbMsg.getContent() : "";
        
        if ("user".equalsIgnoreCase(role)) {
            return UserMessage.from(content);
        } else if ("assistant".equalsIgnoreCase(role)) {
            return AiMessage.from(content);
        } else if ("tool".equalsIgnoreCase(role)) {
            // 工具返回结果不再作为 SystemMessage 加载，避免与动态注入的系统提示冲突。
            // 未来如果需要完整的工具链，应支持 ToolExecutionResultMessage。
            return null;
        }
        
        // 不再加载数据库中的 System 消息，确保每一轮对话都由 ChatService 动态注入最新的混合事实提示词
        return null;
    }

    private Long parseId(Object memoryId) {
        if (memoryId instanceof Long) return (Long) memoryId;
        if (memoryId instanceof String) return Long.parseLong((String) memoryId);
        return 1L; // Fallback
    }
}
