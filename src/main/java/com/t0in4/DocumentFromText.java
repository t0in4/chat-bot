package com.t0in4;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class DocumentFromText {
    List<Document> createDocuments(Path directory) {
        return dev.langchain4j.data.document.loader.FileSystemDocumentLoader.loadDocuments(
                directory.toAbsolutePath().toString(),
                new dev.langchain4j.data.document.parser.TextDocumentParser()
        );
    }
}
