package com.t0in4;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.redis.RedisEmbeddingStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;

import java.util.function.Supplier;

@ApplicationScoped
public class RidesRetrievalAugmentor implements Supplier<RetrievalAugmentor> {
    @Inject
    EmbeddingStore<TextSegment> store;  // ✅ CDI from extension
    @Inject @Named("local-embed")
    EmbeddingModel model;        // Your GigaChat producer

    @Override
    public RetrievalAugmentor get() {  // Lazy init
        /*EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(model)
                .maxResults(10)
                .minScore(0.3)
                .build();*/
        HeightAwareRetriever retriever = new HeightAwareRetriever(
                store,
                model
        );
        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(retriever)
                .build();
    }
}
