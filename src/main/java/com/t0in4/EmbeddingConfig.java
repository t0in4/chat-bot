package com.t0in4;

import chat.giga.client.auth.AuthClient;
import chat.giga.client.auth.AuthClientBuilder;
import chat.giga.langchain4j.GigaChatEmbeddingModel;
import chat.giga.model.Scope;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.redis.RedisEmbeddingStore;
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

@ApplicationScoped
class EmbeddingConfig {
    @Produces
    EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }
    @Produces
    @Named("giga-embed")
    GigaChatEmbeddingModel embeddingModel() {
        Dotenv dotenv = Dotenv.load();
        String authKey = dotenv.get("GIGACHAT_AUTH_KEY");
        return GigaChatEmbeddingModel.builder()
                .authClient(
                        AuthClient.builder()
                                .withOAuth(
                                        AuthClientBuilder.OAuthBuilder.builder()
                                                .scope(Scope.GIGACHAT_API_PERS)
                                                .authKey(authKey)
                                                .build()
                                )
                                .build()
                )
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}