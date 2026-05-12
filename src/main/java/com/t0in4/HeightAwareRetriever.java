package com.t0in4;

import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class HeightAwareRetriever implements ContentRetriever {
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    private static final Pattern HEIGHT_PATTERN = Pattern.compile("([0-9]+)\\s*cm", Pattern.CASE_INSENSITIVE);
    public HeightAwareRetriever(EmbeddingStore<TextSegment> embeddingStore,
                                EmbeddingModel embeddingModel,

                                ) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<Content> retrieve(Query query) {
        String queryText = query.text();
        Integer userHeight = null;
        Matcher matcher = HEIGHT_PATTERN.matcher(queryText);
        if (matcher.find()) {
            userHeight = Integer.parseInt(matcher.group(1));
        }
        Embedding queryEmbedding = embeddingModel.embed(queryText).content();
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(10)
                .minScore(0.0)
                .build();
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();
        if (userHeight != null) {
            return matches.stream()
                    .filter(match -> {
                        Object minHeightObj = match.embedded().metadata().getInteger("min_height_cm");
                        if (minHeightObj == null) {
                            return true;
                        }
                        int minHeight;
                        if (minHeightObj instanceof Number) {
                            minHeight = ((Number) minHeightObj).intValue();
                        } else {
                            try {
                                minHeight = Integer.parseInt(minHeightObj.toString());
                            } catch (NumberFormatException e) {
                                return true;
                            }
                        }
                        return minHeight == -1 || userHeight >= minHeight;
                    })
                    .map(match -> Content.from(match.embedded()))
                    .collect(Collectors.toList());
        }
        return matches.stream()
                .map(match -> Content.from(match.embedded()))
                .collect(Collectors.toList());


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
        return null;
    }


}
