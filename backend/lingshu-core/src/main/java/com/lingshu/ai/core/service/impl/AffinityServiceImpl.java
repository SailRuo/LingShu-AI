package com.lingshu.ai.core.service.impl;

import com.lingshu.ai.core.service.AffinityService;
import com.lingshu.ai.infrastructure.entity.UserState;
import com.lingshu.ai.infrastructure.repository.UserStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AffinityServiceImpl implements AffinityService {

    private final UserStateRepository userStateRepository;

    @Override
    public UserState getUserState(String userId) {
        return userStateRepository.findByUserId(userId).orElse(null);
    }

    @Override
    @Transactional("transactionManager")
    public UserState getOrCreateUserState(String userId) {
        return userStateRepository.findByUserId(userId)
                .orElseGet(() -> {
                    UserState newState = UserState.builder()
                            .userId(userId)
                            .affinity(50)
                            .relationshipStage("初识")
                            .positiveInteractions(0)
                            .totalInteractions(0)
                            .lastActiveTime(LocalDateTime.now())
                            .inactiveHours(0)
                            .needsGreeting(false)
                            .lastEmotion("neutral")
                            .lastEmotionIntensity(0.0)
                            .build();
                    return userStateRepository.save(newState);
                });
    }

    @Override
    @Transactional("transactionManager")
    public void increaseAffinity(String userId, int delta) {
        UserState state = getOrCreateUserState(userId);
        state.increaseAffinity(delta);
        state.setLastActiveTime(LocalDateTime.now());
        state.setInactiveHours(0);
        userStateRepository.save(state);
        log.info("用户 {} 好感度增加 {}，当前好感度: {}，关系阶段: {}", 
                userId, delta, state.getAffinity(), state.getRelationshipStage());
    }

    @Override
    @Transactional("transactionManager")
    public void decreaseAffinity(String userId, int delta) {
        UserState state = getOrCreateUserState(userId);
        state.decreaseAffinity(delta);
        state.setLastActiveTime(LocalDateTime.now());
        state.setInactiveHours(0);
        userStateRepository.save(state);
        log.info("用户 {} 好感度减少 {}，当前好感度: {}，关系阶段: {}", 
                userId, delta, state.getAffinity(), state.getRelationshipStage());
    }

    @Override
    @Transactional("transactionManager")
    public void recordInteraction(String userId) {
        UserState state = getOrCreateUserState(userId);
        state.recordInteraction();
        state.setLastActiveTime(LocalDateTime.now());
        state.setInactiveHours(0);
        userStateRepository.save(state);
        log.debug("记录用户 {} 互动，总互动次数: {}", userId, state.getTotalInteractions());
    }

    @Override
    @Transactional("transactionManager")
    public void updateEmotion(String userId, String emotion, Double intensity) {
        UserState state = getOrCreateUserState(userId);
        state.updateEmotion(emotion, intensity);
        state.setLastActiveTime(LocalDateTime.now());
        userStateRepository.save(state);
        log.debug("更新用户 {} 情绪状态: {} (强度: {})", userId, emotion, intensity);
    }

    @Override
    public String getRelationshipPrompt(String userId) {
        UserState state = getUserState(userId);
        if (state == null) {
            return """
                    【当前关系状态】
                    好感度: 50/100
                    关系阶段: 初识
                    请保持礼貌、谨慎的交流风格。
                    """;
        }

        String styleGuide = getStyleGuide(state.getRelationshipStage());
        
        return String.format("""
                【当前关系状态】
                好感度: %d/100
                关系阶段: %s
                最近情绪: %s
                互动次数: %d
                
                %s
                """, 
                state.getAffinity(),
                state.getRelationshipStage(),
                state.getLastEmotion(),
                state.getTotalInteractions(),
                styleGuide);
    }

    @Override
    public int getAffinity(String userId) {
        UserState state = getUserState(userId);
        return state != null ? state.getAffinity() : 50;
    }

    @Override
    public String getRelationshipStage(String userId) {
        UserState state = getUserState(userId);
        return state != null ? state.getRelationshipStage() : "初识";
    }

    private String getStyleGuide(String stage) {
        return switch (stage) {
            case "挚友" -> """
                    交流风格指南:
                    - 可以使用轻松、亲密的语气
                    - 可以适度调侃和开玩笑
                    - 主动分享想法和建议
                    - 表达深度的理解和关心
                    """;
            case "亲密" -> """
                    交流风格指南:
                    - 保持温暖、关心的语气
                    - 可以适度表达情感
                    - 主动询问用户状态
                    - 分享个人见解
                    """;
            case "熟悉" -> """
                    交流风格指南:
                    - 保持自然、友好的语气
                    - 适度关心用户
                    - 可以主动提出建议
                    - 保持适度的亲密感
                    """;
            default -> """
                    交流风格指南:
                    - 保持礼貌、专业的语气
                    - 谨慎表达，不过度亲密
                    - 以帮助用户为主要目标
                    - 逐步建立信任关系
                    """;
        };
    }
}
