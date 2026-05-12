package com.t0in4;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import jakarta.persistence.criteria.CriteriaBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class HeightAwareRetriever implements ContentRetriever {
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;
    private final int maxResults;
    private final double minScore;

    private static final Pattern HEIGHT_PATTERN = Pattern.compile("([0-9]+)\\s*cm", Pattern.CASE_INSENSITIVE);
    public HeightAwareRetriever(EmbeddingStore<TextSegment> embeddingStore,
                                EmbeddingModel embeddingModel,
                                int maxResults,
                                double minScore) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
        this.maxResults = maxResults;
        this.minScore = minScore;
    }

    @Override
    public List<Content> retrieve(Query query) {
        String queryText = query.text();
        Integer userHeight = extractHeight(queryText);
        List<EmbeddingMatch<TextSegment>> matches;
        if (userHeight != null) {
            matches = embeddingStore.findRelevant(
                    embeddingModel.embed(queryText).content(),
                            maxResults * 2,
                            minScore
            );
            List<EmbeddingMatch<TextSegment>> filteredMatches = new ArrayList<>();
            for (EmbeddingMatch<TextSegment> match : matches) {
                Object minHeightObj = match.embedding().metadata().get("min_height_cm");
                int minHeight = (minHeightObj instanceof Number) ? ((Number) minHeightObj).intValue() : -1;
                if (minHeight == -1 || userHeight >= minHeight) {
                    filteredMatches.add(match);
                }
            }
            if (filteredMatches.size() > maxResults) {
                matches = filteredMatches.subList(0, maxResults);
            } else {
                matches = filteredMatches;
            }
        } else {
            matches = embeddingStore.findRelevant(
                    embeddingModel.embed(queryText).content(),
                    maxResults,
                    minScore
            );
        }
        List<Content> contents = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : matches) {
            contents.add(Content.from(match.embedded()));
        }
        return contents
    }

    private Integer extractHeight(String text) {
        Matcher matcher = HEIGHT_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null
    }


}
