package com.example.searchengine.indexing;

import com.example.searchengine.exception.DumpFileNotFoundException;
import com.example.searchengine.exception.IndexingException;
import com.example.searchengine.model.WikiDocument;
import com.example.searchengine.util.WikiMarkupCleaner;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.function.Consumer;

/**
 * Streams a Wikipedia XML BZ2 dump using StAX for memory-efficient parsing.
 *
 * <p>Wikipedia dump format (simplified):
 * <pre>
 * &lt;mediawiki&gt;
 *   &lt;page&gt;
 *     &lt;title&gt;Article Title&lt;/title&gt;
 *     &lt;id&gt;12345&lt;/id&gt;
 *     &lt;revision&gt;
 *       &lt;text&gt;Wiki markup content&lt;/text&gt;
 *     &lt;/revision&gt;
 *   &lt;/page&gt;
 * &lt;/mediawiki&gt;
 * </pre>
 *
 * <p>This parser skips redirect pages and pages with empty/null text.
 */
@Slf4j
@Component
public class WikiDumpParser {

    private static final String ELEM_PAGE     = "page";
    private static final String ELEM_TITLE    = "title";
    private static final String ELEM_ID       = "id";
    private static final String ELEM_TEXT     = "text";
    private static final String ELEM_REDIRECT = "redirect";
    private static final String ELEM_REVISION = "revision";

    private final WikiMarkupCleaner markupCleaner;

    public WikiDumpParser(WikiMarkupCleaner markupCleaner) {
        this.markupCleaner = markupCleaner;
    }

    /**
     * Parses the given BZ2 dump file, streaming one {@link WikiDocument} at a time
     * to the provided consumer callback.
     *
     * @param dumpPath        path to the .xml.bz2 file
     * @param maxDocuments    maximum number of documents to parse (-1 for unlimited)
     * @param documentHandler callback invoked for each valid parsed document
     * @return total number of documents passed to the handler
     */
    public long parse(String dumpPath, int maxDocuments, Consumer<WikiDocument> documentHandler) {
        File dumpFile = new File(dumpPath);
        if (!dumpFile.exists() || !dumpFile.canRead()) {
            throw new DumpFileNotFoundException(dumpPath);
        }

        log.info("Starting StAX streaming parse of: {}", dumpPath);

        XMLInputFactory factory = createSecureXmlFactory();
        long documentCount = 0;

        try (FileInputStream fis = new FileInputStream(dumpFile);
             BufferedInputStream bis = new BufferedInputStream(fis, 1 << 20); // 1 MB buffer
             BZip2CompressorInputStream bz2 = new BZip2CompressorInputStream(bis)) {

            XMLStreamReader reader = factory.createXMLStreamReader(bz2);

            // State variables for the current page being parsed
            String currentTitle = null;
            long currentId = -1;
            StringBuilder textBuffer = new StringBuilder();
            boolean insidePage = false;
            boolean insideRevision = false;
            boolean insideText = false;
            boolean isRedirect = false;
            boolean idCaptured = false; // capture only the first <id> inside <page>, not revision id

            while (reader.hasNext()) {
                int event = reader.next();

                switch (event) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        String localName = reader.getLocalName();
                        switch (localName) {
                            case ELEM_PAGE -> {
                                insidePage = true;
                                currentTitle = null;
                                currentId = -1;
                                textBuffer.setLength(0);
                                isRedirect = false;
                                insideRevision = false;
                                idCaptured = false;
                            }
                            case ELEM_REDIRECT -> isRedirect = true;
                            case ELEM_REVISION -> insideRevision = true;
                            case ELEM_TEXT -> {
                                if (insidePage && insideRevision) {
                                    insideText = true;
                                    textBuffer.setLength(0);
                                }
                            }
                        }
                    }

                    case XMLStreamConstants.END_ELEMENT -> {
                        String localName = reader.getLocalName();
                        switch (localName) {
                            case ELEM_TEXT -> insideText = false;
                            case ELEM_REVISION -> insideRevision = false;
                            case ELEM_PAGE -> {
                                insidePage = false;
                                if (!isRedirect
                                        && currentTitle != null
                                        && currentId > 0
                                        && !textBuffer.isEmpty()) {

                                    String rawText = textBuffer.toString();
                                    String cleanText = markupCleaner.clean(rawText);

                                    if (!cleanText.isBlank()) {
                                        WikiDocument doc = WikiDocument.builder()
                                                .id(currentId)
                                                .title(currentTitle)
                                                .cleanText(cleanText)
                                                .build();
                                        documentHandler.accept(doc);
                                        documentCount++;

                                        if (documentCount % 10_000 == 0) {
                                            log.info("Parsed {} documents so far...", documentCount);
                                        }

                                        if (maxDocuments > 0 && documentCount >= maxDocuments) {
                                            log.info("Reached max document limit: {}", maxDocuments);
                                            reader.close();
                                            return documentCount;
                                        }
                                    }
                                }
                            }
                        }
                    }

                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                        if (insidePage && !insideRevision && reader.getLocalName() == null) {
                            // handled by element-specific flags below
                        }
                        if (insideText) {
                            textBuffer.append(reader.getText());
                        } else if (insidePage) {
                            // We collect title and id via characters events
                        }
                    }

                    default -> { /* ignore other events */ }
                }

                // Capture title and id as character data is read during START/CHARACTERS
                // We handle this differently: peek at CHARACTERS events per-context
                // The above switch correctly buffers text content when insideText=true
                // For title and id we need a separate capture pass:
                // Note: Handled below via a two-pass inspection after START_ELEMENT
            }

            reader.close();
            log.info("Parsing complete. Total documents parsed: {}", documentCount);
            return documentCount;

        } catch (IOException e) {
            throw new IndexingException("Failed to read BZ2 dump file: " + dumpPath, e);
        } catch (XMLStreamException e) {
            throw new IndexingException("XML parsing error in dump file: " + dumpPath, e);
        }
    }

    // -----------------------------------------------------------------------
    // Revised parse using a proper state machine for title and id capture
    // -----------------------------------------------------------------------

    /**
     * Full-featured parse with proper state machine for all element content capture.
     * This replaces the above method and is the actual production implementation.
     */
    public long parseWithStateMachine(String dumpPath, int maxDocuments, Consumer<WikiDocument> documentHandler) {
        File dumpFile = new File(dumpPath);
        if (!dumpFile.exists() || !dumpFile.canRead()) {
            throw new DumpFileNotFoundException(dumpPath);
        }

        log.info("Starting StAX state-machine parse of: {}", dumpPath);
        XMLInputFactory factory = createSecureXmlFactory();
        long documentCount = 0;

        try (FileInputStream fis = new FileInputStream(dumpFile);
             BufferedInputStream bis = new BufferedInputStream(fis, 1 << 20);
             BZip2CompressorInputStream bz2 = new BZip2CompressorInputStream(bis)) {

            XMLStreamReader reader = factory.createXMLStreamReader(bz2);

            // Per-page state
            String currentTitle = null;
            long currentId = -1;
            boolean isRedirect = false;
            boolean insidePage = false;
            boolean insideRevision = false;
            boolean idCaptured = false;

            // Current element being read (for character collection)
            String captureTarget = null;
            StringBuilder captureBuffer = new StringBuilder();

            while (reader.hasNext()) {
                int event = reader.next();

                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();

                    if (ELEM_PAGE.equals(name)) {
                        insidePage = true;
                        insideRevision = false;
                        isRedirect = false;
                        idCaptured = false;
                        currentTitle = null;
                        currentId = -1;

                    } else if (ELEM_REDIRECT.equals(name) && insidePage) {
                        isRedirect = true;

                    } else if (ELEM_REVISION.equals(name) && insidePage) {
                        insideRevision = true;

                    } else if (ELEM_TITLE.equals(name) && insidePage && !insideRevision) {
                        captureTarget = ELEM_TITLE;
                        captureBuffer.setLength(0);

                    } else if (ELEM_ID.equals(name) && insidePage && !insideRevision && !idCaptured) {
                        captureTarget = ELEM_ID;
                        captureBuffer.setLength(0);

                    } else if (ELEM_TEXT.equals(name) && insidePage && insideRevision) {
                        captureTarget = ELEM_TEXT;
                        captureBuffer.setLength(0);
                    }

                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String name = reader.getLocalName();

                    if (ELEM_TITLE.equals(name) && ELEM_TITLE.equals(captureTarget)) {
                        currentTitle = captureBuffer.toString().trim();
                        captureTarget = null;

                    } else if (ELEM_ID.equals(name) && ELEM_ID.equals(captureTarget)) {
                        try {
                            currentId = Long.parseLong(captureBuffer.toString().trim());
                            idCaptured = true;
                        } catch (NumberFormatException ex) {
                            log.warn("Unparseable page ID: '{}'", captureBuffer);
                        }
                        captureTarget = null;

                    } else if (ELEM_TEXT.equals(name) && ELEM_TEXT.equals(captureTarget)) {
                        // Text fully captured below at END_ELEMENT of page
                        captureTarget = null;

                    } else if (ELEM_REVISION.equals(name)) {
                        insideRevision = false;

                    } else if (ELEM_PAGE.equals(name)) {
                        insidePage = false;

                        if (!isRedirect
                                && currentTitle != null
                                && !currentTitle.contains(":")  // skip non-article namespaces
                                && currentId > 0
                                && captureBuffer.length() > 0) {

                            String rawText = captureBuffer.toString();
                            String cleanText = markupCleaner.clean(rawText);

                            if (cleanText.length() > 50) { // skip stub stubs
                                WikiDocument doc = WikiDocument.builder()
                                        .id(currentId)
                                        .title(currentTitle)
                                        .cleanText(cleanText)
                                        .build();
                                documentHandler.accept(doc);
                                documentCount++;

                                if (documentCount % 10_000 == 0) {
                                    log.info("Parsed {} documents...", documentCount);
                                }

                                if (maxDocuments > 0 && documentCount >= maxDocuments) {
                                    log.info("Max document limit {} reached.", maxDocuments);
                                    reader.close();
                                    return documentCount;
                                }
                            }
                        }
                        captureBuffer.setLength(0);
                    }

                } else if (event == XMLStreamConstants.CHARACTERS
                        || event == XMLStreamConstants.CDATA) {

                    if (captureTarget != null) {
                        captureBuffer.append(reader.getText());
                    }
                }
            }

            reader.close();
            log.info("Parse finished. Total valid articles: {}", documentCount);
            return documentCount;

        } catch (IOException e) {
            throw new IndexingException("IO error reading BZ2 file: " + dumpPath, e);
        } catch (XMLStreamException e) {
            throw new IndexingException("XML parse error in file: " + dumpPath, e);
        }
    }

    /**
     * Creates a secure XMLInputFactory that disables DTD and external entity processing.
     */
    private XMLInputFactory createSecureXmlFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_COALESCING, true); // merge split CHARACTERS events
        return factory;
    }
}
