package com.t0in4;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.data.document.Metadata;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static dev.langchain4j.data.document.Metadata.metadata;

@ApplicationScoped
public class DocumentFromText {
    public List<TextSegment> createTextSegments(Path directory) {
        List<TextSegment> segments = new ArrayList<>();
        try {
            Files.walk(directory)
                    .filter(Files::isRegularFile)
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
                            var meta = new Metadata()

                                    .put("file_name", fileName)
                                    .put("min_height_cm", minHeight != null ? minHeight : -1);
                            TextSegment segment = TextSegment.from(content, meta);
                            segments.add(segment);
                            System.out.println("Loaded: " + fileName + ", min_height: " + (minHeight != null ? minHeight + " com" : "not specified"));
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (Exception e) {
            throw new RuntimeException("Error scanning directory", e);
        }
        return segments;
    }
    List<Document> createDocuments(Path directory) {
        return dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocuments(
                directory.toAbsolutePath().toString(),
                new dev.langchain4j.data.document.parser.TextDocumentParser()
        );
    }
}
