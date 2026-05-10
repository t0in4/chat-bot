package com.t0in4;

import chat.giga.client.auth.AuthClient;
import chat.giga.client.auth.AuthClientBuilder;
import chat.giga.langchain4j.GigaChatChatRequestParameters;
import chat.giga.langchain4j.GigaChatStreamingChatModel;
import chat.giga.model.ModelName;
import chat.giga.model.Scope;
import dev.langchain4j.model.chat.StreamingChatModel;
import io.github.cdimascio.dotenv.Dotenv;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import jakarta.ws.rs.Produces;

import java.util.logging.Logger;

@ApplicationScoped
public class ChatModelConfig {
    private static final Logger LOG = Logger.getLogger(ChatModelConfig.class.getName());

    @Produces
    @Named("giga-chat")
    public StreamingChatModel chatLanguageModel() {
        Dotenv dotenv = Dotenv.load();
        String authKey = dotenv.get("GIGACHAT_AUTH_KEY");
        if (authKey == null || authKey.isBlank()) {
            throw new IllegalStateException("GIGACHAT_AUTH_KEY is required in .env");
        }
        AuthClient authClient = AuthClient.builder()
                .withOAuth(AuthClientBuilder.OAuthBuilder.builder()
                        .scope(Scope.GIGACHAT_API_PERS)
                        .authKey(authKey)
                        .build())
                .build();

        return GigaChatStreamingChatModel.builder()
                .authClient(authClient)
                .defaultChatRequestParameters(GigaChatChatRequestParameters.builder()
                        .modelName(ModelName.GIGA_CHAT_2)
                        .temperature(0.0)
                        .build())
                .build();
    }
}
