package com.akka.cluster.demo;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Enhanced RAGRetrieverActor with improved semantic search capabilities.
 * Uses advanced text processing, chunk-based indexing, and semantic similarity scoring.
 * Demonstrates sophisticated ASK pattern for context retrieval.
 */
public class EnhancedRAGRetrieverActor extends AbstractBehavior<EnhancedRAGRetrieverActor.Command> {

    private static final Logger logger = LoggerFactory.getLogger(EnhancedRAGRetrieverActor.class);
    
    // Enhanced text processing configuration
    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 50;
    private static final int MIN_CHUNK_SIZE = 100;
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("[.!?]+\\s+");
    private static final Pattern WORD_BOUNDARY = Pattern.compile("\\W+");

    // Session storage with enhanced indexing
    private final Map<String, SessionData> sessions = new HashMap<>();

    // Command messages
    public interface Command extends CborSerializable {}

    public static final class IndexChatData implements Command {
        public final String sessionId;
        public final List<ChatParserActor.ChatMessage> messages;
        public final ActorRef<IndexingCompleted> replyTo;

        @JsonCreator
        public IndexChatData(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("messages") List<ChatParserActor.ChatMessage> messages,
            @JsonProperty("replyTo") ActorRef<IndexingCompleted> replyTo
        ) {
            this.sessionId = sessionId;
            this.messages = messages;
            this.replyTo = replyTo;
        }
    }

    public static final class SearchContext implements Command {
        public final String sessionId;
        public final String query;
        public final int maxResults;
        public final ActorRef<SearchResults> replyTo;

        @JsonCreator
        public SearchContext(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("query") String query,
            @JsonProperty("maxResults") int maxResults,
            @JsonProperty("replyTo") ActorRef<SearchResults> replyTo
        ) {
            this.sessionId = sessionId;
            this.query = query;
            this.maxResults = maxResults;
            this.replyTo = replyTo;
        }
    }

    // Response messages
    public static final class IndexingCompleted implements CborSerializable {
        public final String sessionId;
        public final boolean success;
        public final String message;
        public final int chunksIndexed;

        @JsonCreator
        public IndexingCompleted(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("success") boolean success,
            @JsonProperty("message") String message,
            @JsonProperty("chunksIndexed") int chunksIndexed
        ) {
            this.sessionId = sessionId;
            this.success = success;
            this.message = message;
            this.chunksIndexed = chunksIndexed;
        }
    }

    public static final class SearchResults implements CborSerializable {
        public final String sessionId;
        public final String query;
        public final List<ContextChunk> contexts;
        public final boolean success;
        public final String message;

        @JsonCreator
        public SearchResults(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("query") String query,
            @JsonProperty("contexts") List<ContextChunk> contexts,
            @JsonProperty("success") boolean success,
            @JsonProperty("message") String message
        ) {
            this.sessionId = sessionId;
            this.query = query;
            this.contexts = contexts;
            this.success = success;
            this.message = message;
        }
    }

    public static final class ContextChunk implements CborSerializable {
        public final String content;
        public final String sender;
        public final LocalDateTime timestamp;
        public final double relevanceScore;
        public final String chunkId;
        public final String matchReason;

        @JsonCreator
        public ContextChunk(
            @JsonProperty("content") String content,
            @JsonProperty("sender") String sender,
            @JsonProperty("timestamp") LocalDateTime timestamp,
            @JsonProperty("relevanceScore") double relevanceScore,
            @JsonProperty("chunkId") String chunkId,
            @JsonProperty("matchReason") String matchReason
        ) {
            this.content = content;
            this.sender = sender;
            this.timestamp = timestamp;
            this.relevanceScore = relevanceScore;
            this.chunkId = chunkId;
            this.matchReason = matchReason;
        }
    }

    // Enhanced data structures
    private static class SessionData {
        final String sessionId;
        final List<ChatParserActor.ChatMessage> messages;
        final List<TextChunk> chunks;
        final Map<String, Set<Integer>> wordIndex;
        final Map<String, Set<Integer>> senderIndex;
        final LocalDateTime indexedAt;

        SessionData(String sessionId, List<ChatParserActor.ChatMessage> messages, List<TextChunk> chunks) {
            this.sessionId = sessionId;
            this.messages = new ArrayList<>(messages);
            this.chunks = new ArrayList<>(chunks);
            this.wordIndex = buildWordIndex(chunks);
            this.senderIndex = buildSenderIndex(chunks);
            this.indexedAt = LocalDateTime.now();
        }

        private Map<String, Set<Integer>> buildWordIndex(List<TextChunk> chunks) {
            Map<String, Set<Integer>> index = new HashMap<>();
            for (int i = 0; i < chunks.size(); i++) {
                TextChunk chunk = chunks.get(i);
                String[] words = WORD_BOUNDARY.split(chunk.content.toLowerCase());
                for (String word : words) {
                    word = word.trim();
                    if (word.length() > 2) { // Skip very short words
                        index.computeIfAbsent(word, k -> new HashSet<>()).add(i);
                    }
                }
            }
            return index;
        }

        private Map<String, Set<Integer>> buildSenderIndex(List<TextChunk> chunks) {
            Map<String, Set<Integer>> index = new HashMap<>();
            for (int i = 0; i < chunks.size(); i++) {
                TextChunk chunk = chunks.get(i);
                String sender = chunk.sender.toLowerCase();
                index.computeIfAbsent(sender, k -> new HashSet<>()).add(i);
            }
            return index;
        }
    }

    private static class TextChunk {
        final String id;
        final String content;
        final String sender;
        final LocalDateTime timestamp;
        final int chunkIndex;
        final String sessionId;
        final Set<String> keywords;

        TextChunk(String id, String content, String sender, LocalDateTime timestamp, int chunkIndex, String sessionId) {
            this.id = id;
            this.content = content;
            this.sender = sender;
            this.timestamp = timestamp;
            this.chunkIndex = chunkIndex;
            this.sessionId = sessionId;
            this.keywords = extractKeywords(content);
        }

        private Set<String> extractKeywords(String text) {
            Set<String> keywords = new HashSet<>();
            String[] words = WORD_BOUNDARY.split(text.toLowerCase());
            for (String word : words) {
                word = word.trim();
                if (word.length() > 3 && !isStopWord(word)) {
                    keywords.add(word);
                }
            }
            return keywords;
        }

        private boolean isStopWord(String word) {
            Set<String> stopWords = Set.of("the", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by", "this", "that", "these", "those", "is", "are", "was", "were", "been", "have", "has", "had", "will", "would", "could", "should", "can", "may", "might", "must", "shall", "about", "from", "into", "through", "during", "before", "after", "above", "below", "up", "down", "out", "off", "over", "under", "again", "further", "then", "once");
            return stopWords.contains(word);
        }
    }

    // Actor behavior
    public static Behavior<Command> create() {
        return Behaviors.setup(EnhancedRAGRetrieverActor::new);
    }

    private EnhancedRAGRetrieverActor(ActorContext<Command> context) {
        super(context);
        logger.info("Enhanced RAGRetrieverActor started with advanced text processing");
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(IndexChatData.class, this::onIndexChatData)
            .onMessage(SearchContext.class, this::onSearchContext)
            .build();
    }

    private Behavior<Command> onIndexChatData(IndexChatData command) {
        try {
            logger.info("🔍 [RAG-Enhanced] Indexing chat data for session: {}", command.sessionId);

            // Process messages into enhanced chunks
            List<TextChunk> chunks = processMessagesIntoChunks(command.messages, command.sessionId);
            
            // Store session data with enhanced indexing
            sessions.put(command.sessionId, new SessionData(command.sessionId, command.messages, chunks));

            logger.info("✅ [RAG-Enhanced] Successfully indexed {} chunks for session: {}", chunks.size(), command.sessionId);

            // Send completion response
            command.replyTo.tell(new IndexingCompleted(
                command.sessionId,
                true,
                "Successfully indexed " + chunks.size() + " chunks with enhanced processing",
                chunks.size()
            ));

        } catch (Exception e) {
            logger.error("❌ [RAG-Enhanced] Failed to index chat data for session: {}", command.sessionId, e);
            command.replyTo.tell(new IndexingCompleted(
                command.sessionId,
                false,
                "Failed to index chat data: " + e.getMessage(),
                0
            ));
        }

        return this;
    }

    private Behavior<Command> onSearchContext(SearchContext command) {
        try {
            logger.info("🔍 [RAG-Enhanced] Searching for context - Session: {}, Query: '{}'", command.sessionId, command.query);

            // Perform enhanced search
            List<ContextChunk> contexts = performEnhancedSearch(command.sessionId, command.query, command.maxResults);

            logger.info("✅ [RAG-Enhanced] Found {} relevant contexts with enhanced scoring", contexts.size());

            // Send search results
            command.replyTo.tell(new SearchResults(
                command.sessionId,
                command.query,
                contexts,
                true,
                "Enhanced search completed successfully"
            ));

        } catch (Exception e) {
            logger.error("❌ [RAG-Enhanced] Failed to search context for session: {}", command.sessionId, e);
            command.replyTo.tell(new SearchResults(
                command.sessionId,
                command.query,
                Collections.emptyList(),
                false,
                "Search failed: " + e.getMessage()
            ));
        }

        return this;
    }

    private List<TextChunk> processMessagesIntoChunks(List<ChatParserActor.ChatMessage> messages, String sessionId) {
        List<TextChunk> chunks = new ArrayList<>();
        int chunkId = 0;

        for (ChatParserActor.ChatMessage message : messages) {
            String content = message.content;
            
            // Skip empty messages
            if (content == null || content.trim().isEmpty()) {
                continue;
            }

            // Create chunks with intelligent overlap
            List<String> messageChunks = createIntelligentChunks(content);
            
            for (int i = 0; i < messageChunks.size(); i++) {
                String chunkContent = messageChunks.get(i);
                String id = sessionId + "_chunk_" + chunkId++;
                
                chunks.add(new TextChunk(
                    id,
                    chunkContent,
                    message.sender,
                    message.dateTime,
                    i,
                    sessionId
                ));
            }
        }

        logger.info("📝 [Chunking] Created {} enhanced chunks from {} messages", chunks.size(), messages.size());
        return chunks;
    }

    private List<String> createIntelligentChunks(String text) {
        List<String> chunks = new ArrayList<>();
        
        if (text.length() <= CHUNK_SIZE) {
            chunks.add(text);
            return chunks;
        }
        
        // Try to split at sentence boundaries first
        String[] sentences = SENTENCE_BOUNDARY.split(text);
        StringBuilder currentChunk = new StringBuilder();
        
        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) continue;
            
            // If adding this sentence would exceed chunk size
            if (currentChunk.length() + sentence.length() > CHUNK_SIZE) {
                if (currentChunk.length() > MIN_CHUNK_SIZE) {
                    chunks.add(currentChunk.toString().trim());
                    
                    // Start new chunk with overlap
                    String[] words = currentChunk.toString().split("\\s+");
                    int overlapWords = Math.min(10, words.length / 2);
                    currentChunk = new StringBuilder();
                    for (int i = words.length - overlapWords; i < words.length; i++) {
                        currentChunk.append(words[i]).append(" ");
                    }
                }
            }
            
            currentChunk.append(sentence).append(". ");
        }
        
        // Add the final chunk
        if (currentChunk.length() > MIN_CHUNK_SIZE) {
            chunks.add(currentChunk.toString().trim());
        }
        
        return chunks.isEmpty() ? List.of(text) : chunks;
    }

    private List<ContextChunk> performEnhancedSearch(String sessionId, String query, int maxResults) {
        SessionData session = sessions.get(sessionId);
        if (session == null) {
            return Collections.emptyList();
        }

        List<ScoredChunk> scoredChunks = new ArrayList<>();
        String[] queryWords = WORD_BOUNDARY.split(query.toLowerCase());
        
        // Enhanced scoring algorithm
        for (int i = 0; i < session.chunks.size(); i++) {
            TextChunk chunk = session.chunks.get(i);
            double score = calculateEnhancedScore(chunk, queryWords, query);
            
            if (score > 0.1) { // Only include chunks with meaningful relevance
                String matchReason = generateMatchReason(chunk, queryWords);
                scoredChunks.add(new ScoredChunk(chunk, score, matchReason));
            }
        }

        // Sort by score and return top results
        return scoredChunks.stream()
            .sorted((a, b) -> Double.compare(b.score, a.score))
            .limit(maxResults)
            .map(sc -> new ContextChunk(
                sc.chunk.content,
                sc.chunk.sender,
                sc.chunk.timestamp,
                sc.score,
                sc.chunk.id,
                sc.matchReason
            ))
            .collect(Collectors.toList());
    }

    private double calculateEnhancedScore(TextChunk chunk, String[] queryWords, String fullQuery) {
        double score = 0.0;
        String chunkText = chunk.content.toLowerCase();
        
        // 1. Exact phrase matching (highest weight)
        if (chunkText.contains(fullQuery.toLowerCase())) {
            score += 1.0;
        }
        
        // 2. Keyword matching with TF-IDF-like scoring
        Set<String> chunkWords = new HashSet<>(Arrays.asList(WORD_BOUNDARY.split(chunkText)));
        int matchedWords = 0;
        for (String queryWord : queryWords) {
            queryWord = queryWord.trim().toLowerCase();
            if (queryWord.length() > 2 && chunkWords.contains(queryWord)) {
                matchedWords++;
                // Boost score for rare words (simple inverse frequency)
                score += 0.3 * (1.0 / Math.max(1, Collections.frequency(Arrays.asList(queryWords), queryWord)));
            }
        }
        
        // 3. Keyword density bonus
        if (queryWords.length > 0) {
            score += 0.5 * (matchedWords / (double) queryWords.length);
        }
        
        // 4. Semantic keyword matching
        for (String keyword : chunk.keywords) {
            for (String queryWord : queryWords) {
                if (areSemanticallySimilar(keyword, queryWord.toLowerCase().trim())) {
                    score += 0.2;
                }
            }
        }
        
        // 5. Recency boost (prefer recent messages)
        long hoursSinceEpoch = chunk.timestamp.toEpochSecond(java.time.ZoneOffset.UTC) / 3600;
        score += 0.1 * Math.exp(-(System.currentTimeMillis() / 3600000.0 - hoursSinceEpoch) / 24.0);
        
        return score;
    }

    private boolean areSemanticallySimilar(String word1, String word2) {
        // Simple semantic similarity based on common patterns
        if (word1.equals(word2)) return true;
        
        // Handle common variations
        Map<String, Set<String>> synonyms = Map.of(
            "good", Set.of("great", "excellent", "awesome", "nice", "cool"),
            "bad", Set.of("terrible", "awful", "horrible", "poor"),
            "big", Set.of("large", "huge", "massive", "enormous"),
            "small", Set.of("tiny", "little", "mini", "compact"),
            "happy", Set.of("glad", "joyful", "excited", "pleased"),
            "sad", Set.of("upset", "disappointed", "unhappy", "depressed")
        );
        
        for (Map.Entry<String, Set<String>> entry : synonyms.entrySet()) {
            if ((entry.getKey().equals(word1) && entry.getValue().contains(word2)) ||
                (entry.getKey().equals(word2) && entry.getValue().contains(word1))) {
                return true;
            }
        }
        
        // Check for common prefixes/suffixes
        return word1.length() > 4 && word2.length() > 4 && 
               (word1.startsWith(word2.substring(0, Math.min(4, word2.length()))) ||
                word2.startsWith(word1.substring(0, Math.min(4, word1.length()))));
    }

    private String generateMatchReason(TextChunk chunk, String[] queryWords) {
        StringBuilder reason = new StringBuilder();
        String chunkText = chunk.content.toLowerCase();
        
        // Check for exact matches
        List<String> exactMatches = new ArrayList<>();
        for (String word : queryWords) {
            word = word.trim().toLowerCase();
            if (word.length() > 2 && chunkText.contains(word)) {
                exactMatches.add(word);
            }
        }
        
        if (!exactMatches.isEmpty()) {
            reason.append("Keywords: ").append(String.join(", ", exactMatches));
        }
        
        // Check for sender relevance
        for (String word : queryWords) {
            if (chunk.sender.toLowerCase().contains(word.toLowerCase().trim())) {
                if (reason.length() > 0) reason.append("; ");
                reason.append("Sender match");
                break;
            }
        }
        
        return reason.length() > 0 ? reason.toString() : "Semantic similarity";
    }

    private static class ScoredChunk {
        final TextChunk chunk;
        final double score;
        final String matchReason;

        ScoredChunk(TextChunk chunk, double score, String matchReason) {
            this.chunk = chunk;
            this.score = score;
            this.matchReason = matchReason;
        }
    }
}
