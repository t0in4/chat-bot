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
                                EmbeddingModel embeddingModel

                                ) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    @Override
    public List<Content> retrieve(Query query) {
        System.out.println("HeightAwareRetriever.retrieve ENTER: " + query.text());
        String queryText = query.text();
        Integer userHeight = null;
        Matcher matcher = HEIGHT_PATTERN.matcher(queryText);
        if (matcher.find()) {
            userHeight = Integer.parseInt(matcher.group(1));
        }
        Embedding queryEmbedding = embeddingModel.embed(queryText).content();
        System.out.println("HeightAwareRetriever.retrieve AFTER EMBED");
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(10)
                .minScore(0.0)
                .build();
        System.out.println("🔍 Searching store... QueryEmbedding dimensions: " + queryEmbedding.vector().length);
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);
        System.out.println("🔍 Search Result Count: " + searchResult.matches().size()); // Should match the debug log you saw
        List<EmbeddingMatch<TextSegment>> matches = searchResult.matches();
        // DEBUG: Print what we found before filtering
        System.out.println("--- HeightAwareRetriever Debug ---");
        System.out.println("User Height Detected: " + userHeight);
        System.out.println("Total Matches Found: " + matches.size());
        for (EmbeddingMatch<TextSegment> m : matches) {
            Object h = m.embedded().metadata().getInteger("min_height_cm");
            System.out.println("Ride: " + m.embedded().metadata().getString("file_name") + " | Min Height Raw: " + h + " (Type: " + (h != null ? h.getClass().getSimpleName() : "null") + ")");
        }
        if (userHeight != null) {
            final int finalUserHeight = userHeight;
            return matches.stream()
                    .filter(match -> {
                        Integer minHeight = match.embedded().metadata().getInteger("min_height_cm");
                        String rideName = match.embedded().metadata().getString("file_name");
                        if (minHeight == null || minHeight == -1) {
                            return true;
                        }
                        boolean allowed = finalUserHeight >= minHeight;
                        if (allowed) {
                            System.out.println("✅ Keeping ride (height OK): " + rideName + " (min: " + minHeight + ")");
                        } else {
                            System.out.println("❌ Filtering out ride (too short): " + rideName + " (min: " + minHeight + ", user: " + finalUserHeight + ")");
                        }
                        return allowed;
                    })
                    .map(match -> Content.from(match.embedded()))
                    .collect(Collectors.toList());
        }
        return matches.stream()
                .map(match -> Content.from(match.embedded()))
                .collect(Collectors.toList());


    }
}
