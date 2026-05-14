package com.t0in4;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.ws.rs.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static dev.langchain4j.data.document.splitter.DocumentSplitters.recursive;

@Path("/ride")
public class RideResource {
    //@Inject EmbeddingModel embeddingModel;
    @Inject @Named("local-embed")
    EmbeddingModel embeddingModel;
    @Inject
    EmbeddingStore<TextSegment> embeddingStore;
    @Inject
    DocumentFromText documentFromText;
    @Inject
    RideRepository rideRepository;
    @Inject
    WaitingTime waitingTime;
    @Startup
    public void ingest() {
       /* List<Document> documents = documentFromText
                .createDocuments(Paths.get("./ride"));*/
      /*  java.nio.file.Path rideDir = Paths.get("./ride");
        List<Document> documents = new ArrayList<>();
        try (Stream<java.nio.file.Path> paths = Files.walk(rideDir)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".txt"))
                    .forEach(path -> {
                        try {
                            String content = Files.readString(path);
                            String fileName = path.getFileName().toString();

                            Integer minHeight = null;
                            Pattern pattern = Pattern.compile("minimum height.*?is\\s+(\\d+)\\s*cm", Pattern.CASE_INSENSITIVE);
                            Matcher matcher = pattern.matcher(content);
                            if (matcher.find()) {
                                minHeight = Integer.parseInt(matcher.group(1));
                            }
                            String heightInfo = (minHeight != null) ? String.format("\n[HEIGHT RESTRICTION: Minimum %d cm]", minHeight) : "\n[HEIGHT RESTRICTION: None specified]";
                            String enrichedContent = heightInfo + "\n\n" + content;

                            Metadata metadata = Metadata.from(
                                    Map.of(
                                            "file_name", fileName,
                                            "min_height_cm", minHeight != null ? minHeight : -1
                                    )
                            );
                            TextSegment segment = TextSegment.from(content, metadata);
                            documents.add(Document.from(segment.text()));
                            System.out.println("Ingested: " + fileName + " | Height" + (minHeight != null ? minHeight : "None"));
                        } catch (IOException e) {
                            throw new RuntimeException("Error reading file: " + path, e);
                        }
                    });
        } catch (IOException e) {
            throw new RuntimeException("Error scanning directory", e);
        }*/
        System.out.println("ingest is running");
        List<TextSegment> segments = documentFromText.createTextSegments(Paths.get("./ride"));
        if (segments.isEmpty()) {
            System.err.println("No segments created!");
            return;
        }
        List<Document> documents = segments.stream()
                .map(segment -> Document.from(segment.text(), segment.metadata()))
                .toList();
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .documentSplitter(recursive(300, 30))
                .build();
        segments.forEach(seg -> {
            System.out.println("Segment: " + seg.text().substring(0, 50) + "...");
            System.out.println("Metadata: " + seg.metadata().toMap());
        });

        ingestor.ingest(documents);
        // ✅ DEBUG: Verify the store actually has data now
        // We search for a generic term to see if anything is there
        Embedding dummyQuery = embeddingModel.embed("ride").content();
        var checkResult = embeddingStore.search(EmbeddingSearchRequest.builder()
                .queryEmbedding(dummyQuery)
                .maxResults(5)
                .minScore(0.0)
                .build());
        System.out.println("✅ Ingestion complete. Store size check: Found " + checkResult.matches().size() + " items immediately after ingest.");
    }
    @io.quarkus.runtime.Startup
    @jakarta.transaction.Transactional
    public void populateData() {
        insertRides();
    }

    private void insertRides() {
        Ride r1 = new Ride();
        r1.name = "Oncharted. My Penitence";
        r1.rating = 5.0;
        rideRepository.persist(r1);
        waitingTime.setRandomWaitingTime(r1.name);
        Ride r2 = new Ride();
        r2.name = "Dragon Fun";
        r2.rating = 4.9;
        rideRepository.persist(r2);
        waitingTime.setRandomWaitingTime(r2.name);
    }
    @Inject
    ThemeParkChatBot themeParkChatBot;
    @GET @Path("/chat/best")
    public String askForTheBest(@QueryParam("sessionId") String sessionId) {
        String id = (sessionId != null) ? sessionId : "anonymous";
        List<String> tokens = themeParkChatBot
                .chat("Best ride name + rating", id)
                .collect().asList()
                .await().indefinitely();

        return tokens.stream()
                .filter(s -> s.length() > 1)  // Skip single chars
                .map(String::trim)
                .reduce((a, b) -> a + " " + b)
                .orElse("No response");
    }
    @GET
    @Path("/chat/waiting")
    public String askForWaitingTime(@QueryParam("sessionId") String sessionId) {
        String id = (sessionId != null) ? sessionId : "default-session";
        return this.themeParkChatBot
                .chat("What is the waiting time for Dragon Fun ride?", id)
                .collect().asList()
                .await().indefinitely()
                .stream()
                .map(Object::toString)
                .filter(s -> !s.trim().isEmpty())  // Better filter
                .collect(Collectors.joining(" "));  // ✅
    }
    @GET
    @Path("/chat/ask-both")
    public String askForBoth(@QueryParam("sessionId") String sessionId) {
        String id = (sessionId != null) ? sessionId : "default-session";
        this.themeParkChatBot.chat("What is the waiting time for Dragon Fun ride?", id);
        return this.themeParkChatBot.chat("What is he waiting time for that?", id)
                .collect().asList()
                .await().indefinitely()
                .stream()
                .map(Object::toString)
                .filter(s -> !s.trim().isEmpty())  // Better filter
                .collect(Collectors.joining(" "));
    }

}
