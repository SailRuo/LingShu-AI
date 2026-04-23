package com.lingshu.ai.core.model;

import com.lingshu.ai.core.service.SettingService;
import com.lingshu.ai.infrastructure.entity.SystemSetting;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 动态 PgVector EmbeddingStore。
 * 根据系统设置中的 embedding.table + embedding dimension 自动切换底层表。
 */
public class DynamicPgVectorEmbeddingStore implements EmbeddingStore<TextSegment> {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DynamicPgVectorEmbeddingStore.class);
    private static final String DEFAULT_TABLE = "memory_segments";
    private static final Pattern DIMENSION_TABLE_PATTERN = Pattern.compile("^memory_segments_d(\\d+)(?:_.*)?$");

    private final SettingService settingService;
    private final DynamicEmbeddingModel dynamicEmbeddingModel;
    private final String host;
    private final int port;
    private final String database;
    private final String user;
    private final String password;

    private final Map<String, EmbeddingStore<TextSegment>> delegateCache = new ConcurrentHashMap<>();

    public DynamicPgVectorEmbeddingStore(SettingService settingService,
                                         DynamicEmbeddingModel dynamicEmbeddingModel,
                                         String host,
                                         int port,
                                         String database,
                                         String user,
                                         String password) {
        this.settingService = settingService;
        this.dynamicEmbeddingModel = dynamicEmbeddingModel;
        this.host = host;
        this.port = port;
        this.database = database;
        this.user = user;
        this.password = password;
    }

    private EmbeddingStore<TextSegment> resolveDelegate(int dimensionHint) {
        SystemSetting setting = settingService.getSetting();
        int dimension = dimensionHint > 0 ? dimensionHint : dynamicEmbeddingModel.dimension();
        String table = resolveTable(setting, dimension);
        persistResolvedEmbeddingConfig(setting, table, dimension);
        String cacheKey = table + "|" + dimension;

        return delegateCache.computeIfAbsent(cacheKey, k -> {
            log.info("初始化动态向量存储：table={}, dimension={}, host={}, port={}, database={}",
                    table, dimension, host, port, database);
            return PgVectorEmbeddingStore.builder()
                    .host(host)
                    .port(port)
                    .database(database)
                    .user(user)
                    .password(password)
                    .table(table)
                    .dimension(dimension)
                    .build();
        });
    }

    private String resolveTable(SystemSetting setting, int dimension) {
        if (setting == null || setting.getEmbeddingConfig() == null) {
            return defaultTableByDimension(dimension);
        }
        Object configuredTable = setting.getEmbeddingConfig().get("table");
        if (configuredTable instanceof String table && !table.isBlank()) {
            String trimmed = table.trim();
            // 兼容历史配置：旧版本通常把表固定为 memory_segments(768)。
            // 当切到 1024/1536/3072 等模型时自动切换到按维度命名的新表，避免维度冲突。
            if (DEFAULT_TABLE.equals(trimmed) && dimension != 768) {
                String migrated = defaultTableByDimension(dimension);
                log.warn("检测到向量维度已变更为 {}，自动从旧表 {} 切换到 {}", dimension, DEFAULT_TABLE, migrated);
                return migrated;
            }
            Integer tableDimension = parseDimensionFromTableName(trimmed);
            if (tableDimension != null && tableDimension != dimension) {
                String migrated = defaultTableByDimension(dimension);
                log.warn("检测到向量表维度与当前模型不一致：table={} ({}), current={}，自动切换到 {}",
                        trimmed, tableDimension, dimension, migrated);
                return migrated;
            }
            return trimmed;
        }
        return defaultTableByDimension(dimension);
    }

    private String defaultTableByDimension(int dimension) {
        return dimension == 768 ? DEFAULT_TABLE : "memory_segments_d" + dimension;
    }

    private Integer parseDimensionFromTableName(String tableName) {
        if (tableName == null || tableName.isBlank()) {
            return null;
        }
        Matcher matcher = DIMENSION_TABLE_PATTERN.matcher(tableName.trim());
        if (!matcher.matches()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void persistResolvedEmbeddingConfig(SystemSetting setting, String resolvedTable, int dimension) {
        if (setting == null) {
            return;
        }
        Map<String, Object> currentConfig = setting.getEmbeddingConfig();
        String currentTable = null;
        Object rawTable = currentConfig.get("table");
        if (rawTable instanceof String t && !t.isBlank()) {
            currentTable = t.trim();
        }
        Integer currentDimension = null;
        Object rawDimension = currentConfig.get("dimension");
        if (rawDimension instanceof Number n) {
            currentDimension = n.intValue();
        } else if (rawDimension instanceof String s) {
            try {
                currentDimension = Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                currentDimension = null;
            }
        }

        boolean tableChanged = currentTable == null || !resolvedTable.equals(currentTable);
        boolean dimensionChanged = currentDimension == null || currentDimension != dimension;
        if (!tableChanged && !dimensionChanged) {
            return;
        }

        try {
            Map<String, Object> newConfig = new HashMap<>(currentConfig);
            newConfig.put("table", resolvedTable);
            newConfig.put("dimension", dimension);
            setting.setEmbeddingConfig(newConfig);
            settingService.saveSetting(setting);
            log.info("已同步 embedding 配置: table={}, dimension={}", resolvedTable, dimension);
        } catch (Exception e) {
            log.warn("同步 embedding 配置失败: {}", e.getMessage());
        }
    }

    @Override
    public String add(Embedding embedding) {
        EmbeddingStore<TextSegment> delegate = resolveDelegate(embedding != null ? embedding.dimension() : 0);
        return delegate.add(embedding);
    }

    @Override
    public void add(String id, Embedding embedding) {
        EmbeddingStore<TextSegment> delegate = resolveDelegate(embedding != null ? embedding.dimension() : 0);
        delegate.add(id, embedding);
    }

    @Override
    public String add(Embedding embedding, TextSegment embedded) {
        EmbeddingStore<TextSegment> delegate = resolveDelegate(embedding != null ? embedding.dimension() : 0);
        return delegate.add(embedding, embedded);
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings) {
        if (embeddings == null || embeddings.isEmpty()) {
            return List.of();
        }
        int dimension = embeddings.get(0) != null ? embeddings.get(0).dimension() : 0;
        boolean sameDimension = embeddings.stream()
                .allMatch(e -> e == null || e.dimension() == dimension);
        if (sameDimension) {
            EmbeddingStore<TextSegment> delegate = resolveDelegate(dimension);
            return delegate.addAll(embeddings);
        }

        return embeddings.stream()
                .map(this::add)
                .toList();
    }

    @Override
    public List<String> addAll(List<Embedding> embeddings, List<TextSegment> embedded) {
        if (embeddings == null || embeddings.isEmpty()) {
            return List.of();
        }
        int dimension = embeddings.get(0) != null ? embeddings.get(0).dimension() : 0;
        boolean sameDimension = embeddings.stream()
                .allMatch(e -> e == null || e.dimension() == dimension);
        if (sameDimension) {
            EmbeddingStore<TextSegment> delegate = resolveDelegate(dimension);
            return delegate.addAll(embeddings, embedded);
        }

        int size = Math.min(embeddings.size(), embedded != null ? embedded.size() : 0);
        java.util.ArrayList<String> ids = new java.util.ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ids.add(add(embeddings.get(i), embedded.get(i)));
        }
        return ids;
    }

    @Override
    public void remove(String id) {
        EmbeddingStore<TextSegment> delegate = resolveDelegate(0);
        delegate.remove(id);
    }

    @Override
    public void removeAll(Collection<String> ids) {
        EmbeddingStore<TextSegment> delegate = resolveDelegate(0);
        delegate.removeAll(ids);
    }

    @Override
    public void removeAll(Filter filter) {
        EmbeddingStore<TextSegment> delegate = resolveDelegate(0);
        delegate.removeAll(filter);
    }

    @Override
    public EmbeddingSearchResult<TextSegment> search(EmbeddingSearchRequest request) {
        int queryDimension = request != null
                && request.queryEmbedding() != null
                ? request.queryEmbedding().dimension()
                : 0;
        EmbeddingStore<TextSegment> delegate = resolveDelegate(queryDimension);
        return delegate.search(request);
    }
}
