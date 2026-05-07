package com.t0in4;

import chat.giga.client.auth.AuthClient;
import chat.giga.client.auth.AuthClientBuilder;
import chat.giga.langchain4j.GigaChatEmbeddingModel;
import chat.giga.model.Scope;
import dev.langchain4j.store.embedding.redis.RedisEmbeddingStore;
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;

@ApplicationScoped
class EmbeddingConfig {
    @Produces
    RedisEmbeddingStore redisEmbeddingStore() {
        return RedisEmbeddingStore.builder()
                .host("localhost")
                .port(6379)
                .dimension(768)
                .indexName("rides")
                .build();
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