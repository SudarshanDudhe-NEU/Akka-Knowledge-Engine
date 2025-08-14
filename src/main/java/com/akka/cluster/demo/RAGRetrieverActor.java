package com.akka.cluster.demo;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RAGRetrieverActor performs semantic search on chat data.
 * Demonstrates ASK pattern for context retrieval and semantic matching.
 */
public class RAGRetrieverActor extends AbstractBehavior<RAGRetrieverActor.Command> {

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

    public static final class IndexingCompleted implements CborSerializable {
        public final String sessionId;
        public final boolean success;
        public final String error;
        public final int indexedMessages;

        @JsonCreator
        public IndexingCompleted(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("success") boolean success,
            @JsonProperty("error") String error,
            @JsonProperty("indexedMessages") int indexedMessages
        ) {
            this.sessionId = sessionId;
            this.success = success;
            this.error = error;
            this.indexedMessages = indexedMessages;
        }
    }

    public static final class SearchResults implements CborSerializable {
        public final String sessionId;
        public final String query;
        public final List<RelevantContext> contexts;
        public final boolean success;
        public final String error;

        @JsonCreator
        public SearchResults(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("query") String query,
            @JsonProperty("contexts") List<RelevantContext> contexts,
            @JsonProperty("success") boolean success,
            @JsonProperty("error") String error
        ) {
            this.sessionId = sessionId;
            this.query = query;
            this.contexts = contexts;
            this.success = success;
            this.error = error;
        }
    }

    public static final class RelevantContext implements CborSerializable {
        public final ChatParserActor.ChatMessage message;
        public final double relevanceScore;
        public final String matchReason;

        @JsonCreator
        public RelevantContext(
            @JsonProperty("message") ChatParserActor.ChatMessage message,
            @JsonProperty("relevanceScore") double relevanceScore,
            @JsonProperty("matchReason") String matchReason
        ) {
            this.message = message;
            this.relevanceScore = relevanceScore;
            this.matchReason = matchReason;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(RAGRetrieverActor::new);
    }

    private final String nodeAddress;
    private final Map<String, List<ChatParserActor.ChatMessage>> sessionChatData;
    private final Map<String, Map<String, Set<String>>> sessionWordIndex; // sessionId -> word -> messageIds

    private RAGRetrieverActor(ActorContext<Command> context) {
        super(context);
        this.nodeAddress = context.getSystem().address().toString();
        this.sessionChatData = new HashMap<>();
        this.sessionWordIndex = new HashMap<>();
        System.out.println("🔍 RAGRetrieverActor started on " + nodeAddress);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(IndexChatData.class, this::onIndexChatData)
            .onMessage(SearchContext.class, this::onSearchContext)
            .build();
    }

    private Behavior<Command> onIndexChatData(IndexChatData command) {
        System.out.println(String.format(
            "🔍 [RAG] Indexing %d messages for session '%s'", 
            command.messages.size(), 
            command.sessionId
        ));

        try {
            // Store chat data
            sessionChatData.put(command.sessionId, new ArrayList<>(command.messages));
            
            // Build word index for semantic search
            Map<String, Set<String>> wordIndex = buildWordIndex(command.messages);
            sessionWordIndex.put(command.sessionId, wordIndex);
            
            System.out.println(String.format(
                "✅ [RAG] Indexed %d messages with %d unique terms", 
                command.messages.size(),
                wordIndex.size()
            ));

            IndexingCompleted response = new IndexingCompleted(
                command.sessionId,
                true,
                null,
                command.messages.size()
            );
            
            command.replyTo.tell(response);
            
        } catch (Exception e) {
            System.out.println("❌ [RAG] Error during indexing: " + e.getMessage());
            
            IndexingCompleted errorResponse = new IndexingCompleted(
                command.sessionId,
                false,
                "Error during indexing: " + e.getMessage(),
                0
            );
            
            command.replyTo.tell(errorResponse);
        }

        return this;
    }

    private Behavior<Command> onSearchContext(SearchContext command) {
        System.out.println(String.format(
            "🔍 [RAG] Searching for context: '%s' in session '%s'", 
            command.query, 
            command.sessionId
        ));

        try {
            List<ChatParserActor.ChatMessage> messages = sessionChatData.get(command.sessionId);
            if (messages == null || messages.isEmpty()) {
                SearchResults errorResponse = new SearchResults(
                    command.sessionId,
                    command.query,
                    Collections.emptyList(),
                    false,
                    "No chat data found for session: " + command.sessionId
                );
                command.replyTo.tell(errorResponse);
                return this;
            }

            List<RelevantContext> contexts = findRelevantContext(
                command.sessionId, 
                command.query, 
                messages, 
                command.maxResults
            );
            
            System.out.println(String.format(
                "✅ [RAG] Found %d relevant contexts for query", 
                contexts.size()
            ));

            SearchResults response = new SearchResults(
                command.sessionId,
                command.query,
                contexts,
                true,
                null
            );
            
            command.replyTo.tell(response);
            
        } catch (Exception e) {
            System.out.println("❌ [RAG] Error during search: " + e.getMessage());
            
            SearchResults errorResponse = new SearchResults(
                command.sessionId,
                command.query,
                Collections.emptyList(),
                false,
                "Error during search: " + e.getMessage()
            );
            
            command.replyTo.tell(errorResponse);
        }

        return this;
    }

    private Map<String, Set<String>> buildWordIndex(List<ChatParserActor.ChatMessage> messages) {
        Map<String, Set<String>> wordIndex = new HashMap<>();
        
        for (ChatParserActor.ChatMessage message : messages) {
            String[] words = preprocessText(message.content).split("\\s+");
            
            for (String word : words) {
                if (word.length() > 2) { // Skip very short words
                    wordIndex.computeIfAbsent(word.toLowerCase(), k -> new HashSet<>())
                             .add(message.messageId);
                }
            }
            
            // Also index sender name
            if (message.sender != null && !message.sender.trim().isEmpty()) {
                wordIndex.computeIfAbsent(message.sender.toLowerCase(), k -> new HashSet<>())
                         .add(message.messageId);
            }
        }
        
        return wordIndex;
    }

    private List<RelevantContext> findRelevantContext(String sessionId, String query, 
                                                    List<ChatParserActor.ChatMessage> messages, int maxResults) {
        
        String[] queryWords = preprocessText(query).toLowerCase().split("\\s+");
        Map<String, Set<String>> wordIndex = sessionWordIndex.get(sessionId);
        
        // Score each message
        List<RelevantContext> scoredMessages = new ArrayList<>();
        
        for (ChatParserActor.ChatMessage message : messages) {
            double score = calculateRelevanceScore(message, queryWords, wordIndex, query);
            if (score > 0.1) { // Minimum relevance threshold
                String matchReason = determineMatchReason(message, queryWords, query);
                scoredMessages.add(new RelevantContext(message, score, matchReason));
            }
        }
        
        // Sort by relevance and return top results
        return scoredMessages.stream()
            .sorted((a, b) -> Double.compare(b.relevanceScore, a.relevanceScore))
            .limit(maxResults)
            .collect(Collectors.toList());
    }

    private double calculateRelevanceScore(ChatParserActor.ChatMessage message, String[] queryWords, 
                                         Map<String, Set<String>> wordIndex, String originalQuery) {
        double score = 0.0;
        String messageText = message.content.toLowerCase();
        String sender = message.sender.toLowerCase();
        
        // Exact phrase match (highest weight)
        if (messageText.contains(originalQuery.toLowerCase())) {
            score += 1.0;
        }
        
        // Word overlap score
        int matchingWords = 0;
        for (String word : queryWords) {
            if (word.length() > 2) {
                if (messageText.contains(word)) {
                    matchingWords++;
                    score += 0.3;
                }
                // Sender name match
                if (sender.contains(word)) {
                    score += 0.5;
                }
            }
        }
        
        // Date/time related queries
        if (isDateTimeQuery(originalQuery)) {
            score += scoreDateTimeRelevance(message, originalQuery);
        }
        
        // Topic-based scoring
        score += scoreTopicRelevance(message, originalQuery);
        
        return score;
    }

    private String determineMatchReason(ChatParserActor.ChatMessage message, String[] queryWords, String originalQuery) {
        List<String> reasons = new ArrayList<>();
        
        String messageText = message.content.toLowerCase();
        
        if (messageText.contains(originalQuery.toLowerCase())) {
            reasons.add("exact phrase match");
        }
        
        for (String word : queryWords) {
            if (word.length() > 2 && messageText.contains(word)) {
                reasons.add("keyword: " + word);
            }
        }
        
        if (isDateTimeQuery(originalQuery)) {
            reasons.add("temporal relevance");
        }
        
        return reasons.isEmpty() ? "content similarity" : String.join(", ", reasons);
    }

    private boolean isDateTimeQuery(String query) {
        String lowerQuery = query.toLowerCase();
        return lowerQuery.contains("when") || lowerQuery.contains("what time") ||
               lowerQuery.contains("date") || lowerQuery.contains("yesterday") ||
               lowerQuery.contains("today") || lowerQuery.contains("last week") ||
               lowerQuery.contains("ago") || lowerQuery.contains("before") ||
               lowerQuery.contains("after");
    }

    private double scoreDateTimeRelevance(ChatParserActor.ChatMessage message, String query) {
        // Simple temporal scoring - could be enhanced with NLP
        if (message.dateTime != null) {
            String queryLower = query.toLowerCase();
            LocalDateTime messageTime = message.dateTime;
            LocalDateTime now = LocalDateTime.now();
            
            if (queryLower.contains("yesterday")) {
                LocalDateTime yesterday = now.minusDays(1);
                if (messageTime.toLocalDate().equals(yesterday.toLocalDate())) {
                    return 0.8;
                }
            }
            
            if (queryLower.contains("last week")) {
                if (messageTime.isAfter(now.minusWeeks(1))) {
                    return 0.6;
                }
            }
            
            if (queryLower.contains("today")) {
                if (messageTime.toLocalDate().equals(now.toLocalDate())) {
                    return 0.8;
                }
            }
        }
        
        return 0.0;
    }

    private double scoreTopicRelevance(ChatParserActor.ChatMessage message, String query) {
        // Simple topic-based scoring
        String messageText = message.content.toLowerCase();
        String queryLower = query.toLowerCase();
        
        // Common topics
        Map<String, Set<String>> topicKeywords = Map.of(
            "travel", Set.of("trip", "travel", "flight", "hotel", "vacation", "journey"),
            "meeting", Set.of("meeting", "conference", "call", "zoom", "discuss"),
            "food", Set.of("food", "restaurant", "eat", "dinner", "lunch", "breakfast"),
            "work", Set.of("work", "job", "office", "project", "deadline", "task"),
            "family", Set.of("family", "mom", "dad", "sister", "brother", "parents")
        );
        
        for (Map.Entry<String, Set<String>> topic : topicKeywords.entrySet()) {
            boolean queryHasTopic = topic.getValue().stream().anyMatch(queryLower::contains);
            boolean messageHasTopic = topic.getValue().stream().anyMatch(messageText::contains);
            
            if (queryHasTopic && messageHasTopic) {
                return 0.4;
            }
        }
        
        return 0.0;
    }

    private String preprocessText(String text) {
        // Simple text preprocessing
        return text.replaceAll("[^a-zA-Z0-9\\s]", " ")
                  .replaceAll("\\s+", " ")
                  .trim();
    }
}
