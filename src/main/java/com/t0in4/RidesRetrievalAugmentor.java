package com.t0in4;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.onnx.OnnxScoringModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.store.embedding.EmbeddingStore;
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
    EmbeddingStore<TextSegment> store;

    @Inject
    @Named("local-embed")
    EmbeddingModel model;

    @ConfigProperty(name = "embedding.model.path")
    String modelDir;

    private RetrievalAugmentor augmentor;

    @PostConstruct
    void init() {
        Path modelPath = Paths.get(modelDir, "model.onnx").toAbsolutePath().normalize();
        Path tokenizerPath = Paths.get(modelDir, "tokenizer.json").toAbsolutePath().normalize();

        System.out.println("modelPath = " + modelPath);
        System.out.println("tokenizerPath = " + tokenizerPath);

        if (!Files.exists(modelPath)) {
            throw new IllegalStateException("Missing model file: " + modelPath);
        }
        if (!Files.exists(tokenizerPath)) {
            throw new IllegalStateException("Missing tokenizer file: " + tokenizerPath);
        }
        long tO = System.currentTimeMillis();
        System.out.println("before OnnxScoringModel");
        OnnxScoringModel scoringModel = new OnnxScoringModel(modelPath.toString(), tokenizerPath.toString());
        System.out.println("after OnnxScoringModel, ms=" + (System.currentTimeMillis() - tO));

        ContentAggregator contentAggregator = ReRankingContentAggregator.builder()
                .scoringModel(scoringModel)
                .minScore(0.8)
                .build();

        augmentor = DefaultRetrievalAugmentor.builder()
                .contentRetriever(new HeightAwareRetriever(store, model))
                //.contentAggregator(contentAggregator)
                .build();
    }

    @Override
    public RetrievalAugmentor get() {
        if (augmentor == null) {
            throw new IllegalStateException("Augmentor not initialized");
        }
        return augmentor;
    }
}
