package com.lingshu.ai.core.service;

import com.lingshu.ai.core.dto.EmotionAnalysis;
import com.lingshu.ai.core.dto.EmotionalEpisodeResult;
import com.lingshu.ai.core.model.DynamicMemoryModel;
import com.lingshu.ai.infrastructure.entity.EmotionalEpisode;
import com.lingshu.ai.infrastructure.entity.UserNode;
import com.lingshu.ai.infrastructure.repository.EmotionalEpisodeRepository;
import com.lingshu.ai.infrastructure.repository.UserRepository;
import dev.langchain4j.service.AiServices;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class EmotionalEpisodeService {

    private static final Logger log = LoggerFactory.getLogger(EmotionalEpisodeService.class);

    private final EmotionalEpisodeRepository episodeRepository;
    private final UserRepository userRepository;
    private final SystemLogService systemLogService;
    private final DynamicMemoryModel dynamicMemoryModel;
    private EmotionalEpisodeExtractor episodeExtractor;

    public EmotionalEpisodeService(EmotionalEpisodeRepository episodeRepository,
                                   UserRepository userRepository,
                                   SystemLogService systemLogService,
                                   DynamicMemoryModel dynamicMemoryModel) {
        this.episodeRepository = episodeRepository;
        this.userRepository = userRepository;
        this.systemLogService = systemLogService;
        this.dynamicMemoryModel = dynamicMemoryModel;
    }

    private EmotionalEpisodeExtractor getExtractor() {
        if (episodeExtractor == null) {
            episodeExtractor = AiServices.builder(EmotionalEpisodeExtractor.class)
                    .chatModel(dynamicMemoryModel)
                    .build();
        }
        return episodeExtractor;
    }

    public void extractAndSaveEpisode(String userId, String message, EmotionAnalysis emotion) {
        if (emotion == null || !shouldAttemptExtraction(emotion)) {
            return;
        }

        try {
            systemLogService.info("尝试提取情感片段...", "EMOTION_EPISODE");
            
            String triggerKeywords = emotion.getKeywords() != null ? 
                String.join(", ", emotion.getKeywords()) : "";
            
            EmotionalEpisodeResult result = getExtractor().extract(
                    message,
                    emotion.getEmotion(),
                    emotion.getIntensity(),
                    triggerKeywords,
                    emotion.getNeedsComfort()
            );

            if (result != null && result.isShouldExtract() && result.getEpisode() != null) {
                saveEpisode(userId, result.getEpisode(), message);
                systemLogService.info("情感片段已保存: " + result.getEpisode().getTriggerEvent(), "EMOTION_EPISODE");
            } else {
                systemLogService.debug("未提取到情感片段: " + (result != null ? result.getReason() : "无结果"), "EMOTION_EPISODE");
            }
        } catch (Exception e) {
            log.warn("情感片段提取失败: {}", e.getMessage(), e);
            systemLogService.error("情感片段提取失败: " + e.getMessage(), "EMOTION_EPISODE");
        }
    }

    private boolean shouldAttemptExtraction(EmotionAnalysis emotion) {
        if (emotion.getIntensity() != null && emotion.getIntensity() < 0.5) {
            return false;
        }
        return emotion.isNegative() || Boolean.TRUE.equals(emotion.getNeedsComfort());
    }

    private void saveEpisode(String userId, EmotionalEpisodeResult.EpisodeData data, String originalMessage) {
        UserNode user = userRepository.findByName(userId).orElse(null);
        if (user == null) {
            log.debug("用户不存在，跳过情感片段保存: {}", userId);
            return;
        }

        EmotionalEpisode episode = EmotionalEpisode.builder()
                .triggerEvent(data.getTriggerEvent())
                .emotionType(data.getEmotionType())
                .emotionIntensity(data.getEmotionIntensity())
                .triggerKeywords(data.getTriggerKeywords() != null ? 
                    new HashSet<>(data.getTriggerKeywords()) : null)
                .userResponse(data.getUserResponse())
                .copingMechanism(data.getCopingMechanism())
                .outcomeEmotion(data.getOutcomeEmotion())
                .outcomeIntensity(data.getOutcomeIntensity())
                .contextSummary(data.getContextSummary())
                .occurredAt(LocalDateTime.now())
                .importance(calculateImportance(data))
                .recallCount(0)
                .status("active")
                .user(user)
                .build();

        episodeRepository.save(episode);
        log.info("Saved emotional episode: {} for user {}", data.getTriggerEvent(), userId);
    }

    private double calculateImportance(EmotionalEpisodeResult.EpisodeData data) {
        double base = 0.7;
        if (data.getEmotionIntensity() != null) {
            base += data.getEmotionIntensity() * 0.2;
        }
        if (data.getCopingMechanism() != null && !data.getCopingMechanism().isEmpty()) {
            base += 0.1;
        }
        return Math.min(1.0, base);
    }

    public List<EmotionalEpisode> findSimilarEpisodes(String userId, String emotionType, double minIntensity) {
        return episodeRepository.findSimilarEpisodes(userId, emotionType, minIntensity, 5);
    }

    public List<EmotionalEpisode> findRecentEpisodes(String userId, int limit) {
        return episodeRepository.findRecentByUserId(userId, limit);
    }

    public List<EmotionalEpisode> findEpisodesWithCopingMechanism(String userId, int limit) {
        return episodeRepository.findWithCopingMechanism(userId, limit);
    }

    public String buildEpisodeContextPrompt(String userId, EmotionAnalysis currentEmotion) {
        if (currentEmotion == null || !currentEmotion.isNegative()) {
            return "";
        }

        List<EmotionalEpisode> similarEpisodes = findSimilarEpisodes(
                userId, 
                currentEmotion.getEmotion(), 
                currentEmotion.getIntensity() != null ? currentEmotion.getIntensity() * 0.7 : 0.3
        );

        if (similarEpisodes.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n【历史情感场景参考】\n");
        
        for (int i = 0; i < Math.min(3, similarEpisodes.size()); i++) {
            EmotionalEpisode ep = similarEpisodes.get(i);
            sb.append(String.format("- %s (情绪: %s, 应对: %s)\n",
                ep.getTriggerEvent(),
                translateEmotion(ep.getEmotionType()),
                ep.getCopingMechanism() != null ? ep.getCopingMechanism() : "无"
            ));
            
            episodeRepository.incrementRecall(ep.getId());
        }
        
        sb.append("\n提示: 可以参考用户之前的情感经历和应对方式来调整回应。\n");
        
        return sb.toString();
    }

    private String translateEmotion(String emotion) {
        if (emotion == null) return "中性";
        return switch (emotion.toLowerCase()) {
            case "positive" -> "积极";
            case "negative" -> "消极";
            default -> "中性";
        };
    }

    public void cleanupOldEpisodes(int daysToKeep) {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(daysToKeep);
        log.info("Cleaning up emotional episodes older than {} days", daysToKeep);
        
        List<EmotionalEpisode> oldEpisodes = episodeRepository.findAll().stream()
                .filter(ep -> ep.getOccurredAt() != null && ep.getOccurredAt().isBefore(cutoff))
                .filter(ep -> "inactive".equals(ep.getStatus()) || ep.getRecallCount() < 2)
                .collect(Collectors.toList());
        
        for (EmotionalEpisode ep : oldEpisodes) {
            episodeRepository.delete(ep);
        }
        
        log.info("Cleaned up {} old emotional episodes", oldEpisodes.size());
    }
}
