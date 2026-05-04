package com.t0in4;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import io.quarkus.redis.datasource.RedisDataSource;
import io.quarkus.redis.datasource.keys.KeyCommands;
import io.quarkus.redis.datasource.value.ValueCommands;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.util.List;

@ApplicationScoped
public class RedisChatMemoryStore implements ChatMemoryStore {
    private final ValueCommands<String, byte[]> valueCommands;
    private final KeyCommands<String> keyCommands;

    public RedisChatMemoryStore(RedisDataSource ds) {
        this.valueCommands = ds.value(byte[].class);
        this.keyCommands = ds.key(String.class);
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        byte[] bytes = valueCommands.get(memoryId.toString());
        if (bytes == null) return List.of();
        // Use the built-in serializer
        return ChatMessageDeserializer.messagesFromJson(new String(bytes));
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        // Use the built-in serializer
        String json = ChatMessageSerializer.messagesToJson(messages);
        valueCommands.set(memoryId.toString(), json.getBytes());
    }

    @Override
    public void deleteMessages(Object memoryId) {
        keyCommands.del(memoryId.toString());
    }
}