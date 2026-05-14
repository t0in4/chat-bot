package com.t0in4;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.redis.RedisEmbeddingStore;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Supplier;

@ApplicationScoped
public class RidesRetrievalAugmentor implements Supplier<RetrievalAugmentor> {
    @Inject
    EmbeddingStore<TextSegment> store;  // ✅ CDI from extension
    @Inject
    @Named("local-embed")
    EmbeddingModel model;

    //reramking model and tokenizer https://huggingface.co/BAAI/bge-reranker-large/tree/main
    @ConfigProperty(name = "embedding.model.path", defaultValue = "protected")
    String modelDir;
    OnnxScoringModel scoringModel;
    ContentAggregator contentAggregator;

    @PostConstruct
    void init() {
        Path modelPath = Paths.get(modelDir, "model.onnx").toAbsolutePath().normalize();
        Path tokenizerPath = Paths.get(modelDir, "tokenizer.json").toAbsolutePath().normalize();
        scoringModel = new OnnxScoringModel(modelPath.toString(), tokenizerPath.toString());
        // Content aggregator adds/removes/sorts content
        contentAggregator = ReRankingContentAggregator.builder()
                .scoringModel(scoringModel)
                .minScore(0.8)
                .build();
    }

    @Override
    public RetrievalAugmentor get() {  // Lazy init
        /*EmbeddingStoreContentRetriever retriever = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(model)
                .maxResults(10)
                .minScore(0.3)
                .build();*/
        System.out.println("Creating Augmentor with Store instance: " + store);
      /*  HeightAwareRetriever retriever = new HeightAwareRetriever(
                store,
                model
        );*/
        return DefaultRetrievalAugmentor.builder()
                .contentRetriever(new HeightAwareRetriever(store, model))
                .contentAggregator(contentAggregator) // Injects the content aggregator to sort texts based on the scoring model
                .build();
    }
}
