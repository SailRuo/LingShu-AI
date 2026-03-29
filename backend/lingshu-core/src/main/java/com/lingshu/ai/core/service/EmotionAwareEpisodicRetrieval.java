package com.lingshu.ai.core.service;

import com.lingshu.ai.core.dto.EmotionAnalysis;
import com.lingshu.ai.core.dto.EmotionContext;
import com.lingshu.ai.infrastructure.entity.EmotionalEpisode;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class EmotionAwareEpisodicRetrieval {

    private static final Logger log = LoggerFactory.getLogger(EmotionAwareEpisodicRetrieval.class);
    private static final double EMOTION_MATCH_BOOST = 0.15;
    private static final double INTENSITY_SIMILARITY_THRESHOLD = 0.3;

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final EmotionalEpisodeService episodeService;
    private final EmotionContextCache emotionContextCache;

    public EmotionAwareEpisodicRetrieval(EmbeddingStore<TextSegment> embeddingStore,
                                         EmbeddingModel embeddingModel,
                                         EmotionalEpisodeService episodeService,
                                         EmotionContextCache emotionContextCache) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.episodeService = episodeService;
        this.emotionContextCache = emotionContextCache;
    }

    public List<EpisodicMemoryMatch> retrieveWithContext(String userId, String query, EmotionAnalysis currentEmotion) {
        List<EpisodicMemoryMatch> results = new ArrayList<>();

        try {
            List<EmbeddingMatch<TextSegment>> semanticMatches = retrieveSemanticMatches(query);
            
            for (EmbeddingMatch<TextSegment> match : semanticMatches) {
                EpisodicMemoryMatch episodicMatch = convertToEpisodicMatch(match, currentEmotion);
                if (episodicMatch != null) {
                    results.add(episodicMatch);
                }
            }

            if (currentEmotion != null && currentEmotion.isNegative()) {
                List<EmotionalEpisode> episodeMatches = episodeService.findSimilarEpisodes(
                        userId,
                        currentEmotion.getEmotion(),
                        currentEmotion.getIntensity() != null ? currentEmotion.getIntensity() * 0.7 : 0.3
                );
                
                for (EmotionalEpisode episode : episodeMatches) {
                    EpisodicMemoryMatch episodeMatch = convertEpisodeToMatch(episode, currentEmotion);
                    if (episodeMatch != null && !containsSimilarContent(results, episodeMatch)) {
                        results.add(episodeMatch);
                    }
                }
            }

            results.sort((a, b) -> Double.compare(b.getFinalScore(), a.getFinalScore()));
            
            return results.stream().limit(10).collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("情感感知场景检索失败: {}", e.getMessage(), e);
            return results;
        }
    }

    private List<EmbeddingMatch<TextSegment>> retrieveSemanticMatches(String query) {
        try {
            dev.langchain4j.data.embedding.Embedding queryEmbedding = embeddingModel.embed(query).content();
            
            EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(15)
                    .minScore(0.5)
                    .build();
            
            EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
            return new ArrayList<>(searchResult.matches());
        } catch (Exception e) {
            log.warn("语义检索失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private EpisodicMemoryMatch convertToEpisodicMatch(EmbeddingMatch<TextSegment> match, EmotionAnalysis currentEmotion) {
        TextSegment segment = match.embedded();
        if (segment == null) return null;

        EpisodicMemoryMatch result = new EpisodicMemoryMatch();
        result.setContent(segment.text());
        result.setSemanticScore(match.score());
        
        String emotionalTone = segment.metadata().getString("emotional_tone");
        String factType = segment.metadata().getString("fact_type");
        String triggerKeywords = segment.metadata().getString("trigger_keywords");
        
        result.setEmotionalTone(emotionalTone);
        result.setFactType(factType);
        
        if (triggerKeywords != null && !triggerKeywords.isEmpty()) {
            result.setTriggerKeywords(Arrays.asList(triggerKeywords.split(",")));
        }
        
        double emotionBoost = calculateEmotionBoost(emotionalTone, currentEmotion);
        result.setEmotionBoost(emotionBoost);
        result.setFinalScore(match.score() + emotionBoost);
        
        return result;
    }

    private EpisodicMemoryMatch convertEpisodeToMatch(EmotionalEpisode episode, EmotionAnalysis currentEmotion) {
        EpisodicMemoryMatch result = new EpisodicMemoryMatch();
        
        StringBuilder content = new StringBuilder();
        content.append("情感事件: ").append(episode.getTriggerEvent());
        if (episode.getCopingMechanism() != null && !episode.getCopingMechanism().isEmpty()) {
            content.append(" | 应对方式: ").append(episode.getCopingMechanism());
        }
        if (episode.getContextSummary() != null) {
            content.append(" | 情境: ").append(episode.getContextSummary());
        }
        
        result.setContent(content.toString());
        result.setSemanticScore(0.6);
        result.setEmotionalTone(episode.getEmotionType());
        result.setEpisodeId(episode.getId());
        
        if (episode.getTriggerKeywords() != null) {
            result.setTriggerKeywords(new ArrayList<>(episode.getTriggerKeywords()));
        }
        
        double emotionBoost = calculateEmotionBoost(episode.getEmotionType(), currentEmotion);
        if (currentEmotion != null && currentEmotion.getIntensity() != null && episode.getEmotionIntensity() != null) {
            double intensityDiff = Math.abs(currentEmotion.getIntensity() - episode.getEmotionIntensity());
            if (intensityDiff < INTENSITY_SIMILARITY_THRESHOLD) {
                emotionBoost += 0.1;
            }
        }
        
        result.setEmotionBoost(emotionBoost);
        result.setFinalScore(0.6 + emotionBoost);
        result.setFromEpisode(true);
        
        return result;
    }

    private double calculateEmotionBoost(String storedEmotion, EmotionAnalysis currentEmotion) {
        if (currentEmotion == null || storedEmotion == null) {
            return 0.0;
        }
        
        if (storedEmotion.equalsIgnoreCase(currentEmotion.getEmotion())) {
            return EMOTION_MATCH_BOOST;
        }
        
        if ("negative".equalsIgnoreCase(currentEmotion.getEmotion()) && 
            "negative".equalsIgnoreCase(storedEmotion)) {
            return EMOTION_MATCH_BOOST * 0.8;
        }
        
        return 0.0;
    }

    private boolean containsSimilarContent(List<EpisodicMemoryMatch> results, EpisodicMemoryMatch newMatch) {
        String newContent = newMatch.getContent();
        if (newContent == null) return true;
        
        for (EpisodicMemoryMatch existing : results) {
            if (existing.getContent() != null && 
                calculateSimilarity(existing.getContent(), newContent) > 0.8) {
                return true;
            }
        }
        return false;
    }

    private double calculateSimilarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        
        Set<String> wordsA = new HashSet<>(Arrays.asList(a.toLowerCase().split("\\s+")));
        Set<String> wordsB = new HashSet<>(Arrays.asList(b.toLowerCase().split("\\s+")));
        
        Set<String> intersection = new HashSet<>(wordsA);
        intersection.retainAll(wordsB);
        
        Set<String> union = new HashSet<>(wordsA);
        union.addAll(wordsB);
        
        return union.isEmpty() ? 0.0 : (double) intersection.size() / union.size();
    }

    public String buildEpisodicContextPrompt(String userId, String query, EmotionAnalysis currentEmotion) {
        List<EpisodicMemoryMatch> matches = retrieveWithContext(userId, query, currentEmotion);
        
        if (matches.isEmpty()) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n【场景记忆检索结果】\n");
        
        for (int i = 0; i < Math.min(5, matches.size()); i++) {
            EpisodicMemoryMatch match = matches.get(i);
            sb.append(String.format("%d. %s\n", i + 1, 
                match.getContent().length() > 150 ? 
                    match.getContent().substring(0, 150) + "..." : 
                    match.getContent()));
            
            if (match.getTriggerKeywords() != null && !match.getTriggerKeywords().isEmpty()) {
                sb.append(String.format("   关键词: %s\n", String.join(", ", match.getTriggerKeywords())));
            }
        }
        
        sb.append("\n");
        
        return sb.toString();
    }

    public static class EpisodicMemoryMatch {
        private String content;
        private double semanticScore;
        private double emotionBoost;
        private double finalScore;
        private String emotionalTone;
        private String factType;
        private List<String> triggerKeywords;
        private Long episodeId;
        private boolean fromEpisode;

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public double getSemanticScore() { return semanticScore; }
        public void setSemanticScore(double semanticScore) { this.semanticScore = semanticScore; }
        public double getEmotionBoost() { return emotionBoost; }
        public void setEmotionBoost(double emotionBoost) { this.emotionBoost = emotionBoost; }
        public double getFinalScore() { return finalScore; }
        public void setFinalScore(double finalScore) { this.finalScore = finalScore; }
        public String getEmotionalTone() { return emotionalTone; }
        public void setEmotionalTone(String emotionalTone) { this.emotionalTone = emotionalTone; }
        public String getFactType() { return factType; }
        public void setFactType(String factType) { this.factType = factType; }
        public List<String> getTriggerKeywords() { return triggerKeywords; }
        public void setTriggerKeywords(List<String> triggerKeywords) { this.triggerKeywords = triggerKeywords; }
        public Long getEpisodeId() { return episodeId; }
        public void setEpisodeId(Long episodeId) { this.episodeId = episodeId; }
        public boolean isFromEpisode() { return fromEpisode; }
        public void setFromEpisode(boolean fromEpisode) { this.fromEpisode = fromEpisode; }
    }
}
