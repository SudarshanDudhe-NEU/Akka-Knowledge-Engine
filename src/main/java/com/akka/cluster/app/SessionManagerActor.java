package com.akka.cluster.app;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.ClusterSingleton;
import akka.cluster.typed.SingletonActor;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * SessionManagerActor manages user sessions and coordinates the WhatsApp Q&A workflow.
 * Demonstrates orchestration of TELL, ASK, and FORWARD patterns across the cluster.
 */
public class SessionManagerActor extends AbstractBehavior<SessionManagerActor.Command> {

    // Command messages
    public interface Command extends CborSerializable {}

    public static final class UploadChatFile implements Command {
        public final String sessionId;
        public final String filePath;
        public final ActorRef<UploadResponse> replyTo;

        @JsonCreator
        public UploadChatFile(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("filePath") String filePath,
            @JsonProperty("replyTo") ActorRef<UploadResponse> replyTo
        ) {
            this.sessionId = sessionId;
            this.filePath = filePath;
            this.replyTo = replyTo;
        }
    }

    public static final class AskQuestion implements Command {
        public final String sessionId;
        public final String question;
        public final ActorRef<QuestionResponse> replyTo;

        @JsonCreator
        public AskQuestion(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("question") String question,
            @JsonProperty("replyTo") ActorRef<QuestionResponse> replyTo
        ) {
            this.sessionId = sessionId;
            this.question = question;
            this.replyTo = replyTo;
        }
    }

    public static final class UploadResponse implements CborSerializable {
        public final String sessionId;
        public final boolean success;
        public final String message;
        public final int totalMessages;

        @JsonCreator
        public UploadResponse(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("success") boolean success,
            @JsonProperty("message") String message,
            @JsonProperty("totalMessages") int totalMessages
        ) {
            this.sessionId = sessionId;
            this.success = success;
            this.message = message;
            this.totalMessages = totalMessages;
        }
    }

    public static final class QuestionResponse implements CborSerializable {
        public final String sessionId;
        public final String question;
        public final String answer;
        public final boolean success;
        public final String error;
        public final List<String> relevantMessages;
        public final long processingTimeMs;

        @JsonCreator
        public QuestionResponse(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("question") String question,
            @JsonProperty("answer") String answer,
            @JsonProperty("success") boolean success,
            @JsonProperty("error") String error,
            @JsonProperty("relevantMessages") List<String> relevantMessages,
            @JsonProperty("processingTimeMs") long processingTimeMs
        ) {
            this.sessionId = sessionId;
            this.question = question;
            this.answer = answer;
            this.success = success;
            this.error = error;
            this.relevantMessages = relevantMessages;
            this.processingTimeMs = processingTimeMs;
        }
    }

    // Internal state tracking messages
    private static final class ChatParsingCompleted implements Command {
        public final ChatParserActor.ParsingCompleted result;
        public final ActorRef<UploadResponse> originalReplyTo;

        public ChatParsingCompleted(ChatParserActor.ParsingCompleted result, ActorRef<UploadResponse> originalReplyTo) {
            this.result = result;
            this.originalReplyTo = originalReplyTo;
        }
    }

    private static final class IndexingCompleted implements Command {
        public final EnhancedRAGRetrieverActor.IndexingCompleted result;
        public final ActorRef<UploadResponse> originalReplyTo;

        public IndexingCompleted(EnhancedRAGRetrieverActor.IndexingCompleted result, ActorRef<UploadResponse> originalReplyTo) {
            this.result = result;
            this.originalReplyTo = originalReplyTo;
        }
    }

    private static final class ContextRetrieved implements Command {
        public final EnhancedRAGRetrieverActor.SearchResults result;
        public final String question;
        public final ActorRef<QuestionResponse> originalReplyTo;
        public final long startTime;

        public ContextRetrieved(EnhancedRAGRetrieverActor.SearchResults result, String question, 
                              ActorRef<QuestionResponse> originalReplyTo, long startTime) {
            this.result = result;
            this.question = question;
            this.originalReplyTo = originalReplyTo;
            this.startTime = startTime;
        }
    }

    private static final class LLMResponseReceived implements Command {
        public final LLMActor.QueryResponse response;
        public final String question;
        public final ActorRef<QuestionResponse> originalReplyTo;
        public final List<String> relevantMessages;
        public final long startTime;

        public LLMResponseReceived(LLMActor.QueryResponse response, String question,
                                 ActorRef<QuestionResponse> originalReplyTo, List<String> relevantMessages, long startTime) {
            this.response = response;
            this.question = question;
            this.originalReplyTo = originalReplyTo;
            this.relevantMessages = relevantMessages;
            this.startTime = startTime;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(SessionManagerActor::new);
    }

    private final String nodeAddress;
    private final ActorRef<ChatParserActor.Command> chatParser;
    private final ActorRef<EnhancedRAGRetrieverActor.Command> ragRetriever;
    private final ActorRef<LLMActor.Command> llmActor;
    private final ActorRef<WhatsAppLoggerActor.Command> logger;
    private final Map<String, SessionData> sessions;

    private static class SessionData {
        public boolean chatLoaded = false;
        public int totalMessages = 0;
        public long lastActivity = System.currentTimeMillis();
    }

    private SessionManagerActor(ActorContext<Command> context) {
        super(context);
        this.nodeAddress = context.getSystem().address().toString();
        this.sessions = new HashMap<>();
        
        // Spawn or get references to other actors
        this.chatParser = context.spawn(ChatParserActor.create(), "chat-parser");
        this.ragRetriever = context.spawn(EnhancedRAGRetrieverActor.create(), "enhanced-rag-retriever");
        this.llmActor = context.spawn(LLMActor.create(), "llm-actor");
        this.logger = context.spawn(WhatsAppLoggerActor.create(), "session-logger");
        
        System.out.println("👤 SessionManagerActor started on " + nodeAddress);
        System.out.println("📋 WhatsApp Chat Q&A workflow coordinator ready with Vector Database");
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(UploadChatFile.class, this::onUploadChatFile)
            .onMessage(AskQuestion.class, this::onAskQuestion)
            .onMessage(ChatParsingCompleted.class, this::onChatParsingCompleted)
            .onMessage(IndexingCompleted.class, this::onIndexingCompleted)
            .onMessage(ContextRetrieved.class, this::onContextRetrieved)
            .onMessage(LLMResponseReceived.class, this::onLLMResponseReceived)
            .build();
    }

    private Behavior<Command> onUploadChatFile(UploadChatFile command) {
        System.out.println(String.format(
            "👤 [SESSION] Starting chat upload for session '%s': %s", 
            command.sessionId, 
            command.filePath
        ));

        // Initialize session
        sessions.put(command.sessionId, new SessionData());
        
        // Log the upload activity using TELL pattern
        logger.tell(new WhatsAppLoggerActor.LogActivity(
            command.sessionId,
            "UPLOAD_STARTED",
            "Chat file upload initiated: " + command.filePath,
            System.currentTimeMillis()
        ));

        // Use TELL pattern to send file to ChatParserActor
        ActorRef<ChatParserActor.ParsingCompleted> parsingAdapter = 
            getContext().messageAdapter(ChatParserActor.ParsingCompleted.class, 
                result -> new ChatParsingCompleted(result, command.replyTo));
        
        chatParser.tell(new ChatParserActor.ParseChatFile(
            command.sessionId, 
            command.filePath, 
            parsingAdapter
        ));

        return this;
    }

    private Behavior<Command> onChatParsingCompleted(ChatParsingCompleted message) {
        ChatParserActor.ParsingCompleted result = message.result;
        
        if (result.success) {
            System.out.println(String.format(
                "✅ [SESSION] Chat parsing completed for session '%s': %d messages", 
                result.sessionId, 
                result.totalMessages
            ));

            // Now index the parsed messages using TELL pattern to EnhancedRAGRetrieverActor
            ActorRef<EnhancedRAGRetrieverActor.IndexingCompleted> indexingAdapter = 
                getContext().messageAdapter(EnhancedRAGRetrieverActor.IndexingCompleted.class, 
                    indexResult -> new IndexingCompleted(indexResult, message.originalReplyTo));
            
            ragRetriever.tell(new EnhancedRAGRetrieverActor.IndexChatData(
                result.sessionId, 
                result.messages, 
                indexingAdapter
            ));
            
        } else {
            System.out.println("❌ [SESSION] Chat parsing failed: " + result.error);
            
            UploadResponse errorResponse = new UploadResponse(
                result.sessionId,
                false,
                "Chat parsing failed: " + result.error,
                0
            );
            
            message.originalReplyTo.tell(errorResponse);
        }

        return this;
    }

    private Behavior<Command> onIndexingCompleted(IndexingCompleted message) {
        EnhancedRAGRetrieverActor.IndexingCompleted result = message.result;
        
        if (result.success) {
            System.out.println(String.format(
                "✅ [SESSION] Chat indexing completed for session '%s': %d chunks indexed", 
                result.sessionId, 
                result.chunksIndexed
            ));

            // Update session state
            SessionData session = sessions.get(result.sessionId);
            if (session != null) {
                session.chatLoaded = true;
                session.totalMessages = result.chunksIndexed;
                session.lastActivity = System.currentTimeMillis();
            }

            // Log completion using TELL pattern
            logger.tell(new WhatsAppLoggerActor.LogActivity(
                result.sessionId,
                "UPLOAD_COMPLETED",
                String.format("Chat indexed successfully: %d chunks", result.chunksIndexed),
                System.currentTimeMillis()
            ));

            UploadResponse successResponse = new UploadResponse(
                result.sessionId,
                true,
                String.format("Chat loaded and indexed successfully! %d chunks ready for queries.", result.chunksIndexed),
                result.chunksIndexed
            );
            
            message.originalReplyTo.tell(successResponse);
            
        } else {
            System.out.println("❌ [SESSION] Chat indexing failed: " + result.message);
            
            UploadResponse errorResponse = new UploadResponse(
                result.sessionId,
                false,
                "Chat indexing failed: " + result.message,
                0
            );
            
            message.originalReplyTo.tell(errorResponse);
        }

        return this;
    }

    private Behavior<Command> onAskQuestion(AskQuestion command) {
        long startTime = System.currentTimeMillis();
        
        System.out.println(String.format(
            "👤 [SESSION] Processing question for session '%s': %s", 
            command.sessionId, 
            command.question
        ));

        // Check if session has chat data loaded
        SessionData session = sessions.get(command.sessionId);
        if (session == null || !session.chatLoaded) {
            QuestionResponse errorResponse = new QuestionResponse(
                command.sessionId,
                command.question,
                "",
                false,
                "No chat data loaded for this session. Please upload a chat file first.",
                Collections.emptyList(),
                System.currentTimeMillis() - startTime
            );
            
            command.replyTo.tell(errorResponse);
            return this;
        }

        // Update session activity
        session.lastActivity = System.currentTimeMillis();

        // Log the question using TELL pattern
        logger.tell(new WhatsAppLoggerActor.LogActivity(
            command.sessionId,
            "QUESTION_ASKED",
            "User question: " + command.question,
            System.currentTimeMillis()
        ));

        // Use ASK pattern to retrieve relevant context from EnhancedRAGRetrieverActor
        ActorRef<EnhancedRAGRetrieverActor.SearchResults> contextAdapter = 
            getContext().messageAdapter(EnhancedRAGRetrieverActor.SearchResults.class, 
                result -> new ContextRetrieved(result, command.question, command.replyTo, startTime));
        
        ragRetriever.tell(new EnhancedRAGRetrieverActor.SearchContext(
            command.sessionId, 
            command.question, 
            5, // max results
            contextAdapter
        ));

        return this;
    }

    private Behavior<Command> onContextRetrieved(ContextRetrieved message) {
        EnhancedRAGRetrieverActor.SearchResults result = message.result;
        
        if (result.success && !result.contexts.isEmpty()) {
            System.out.println(String.format(
                "✅ [SESSION] Retrieved %d relevant contexts for question (Vector Search)", 
                result.contexts.size()
            ));

            // Build context for LLM
            StringBuilder contextBuilder = new StringBuilder();
            contextBuilder.append("WhatsApp Chat Context (Vector Search Results):\n\n");
            
            List<String> relevantMessages = new ArrayList<>();
            
            for (EnhancedRAGRetrieverActor.ContextChunk context : result.contexts) {
                contextBuilder.append(String.format(
                    "[%s] %s: %s\n", 
                    context.timestamp, 
                    context.sender, 
                    context.content
                ));
                
                relevantMessages.add(String.format(
                    "[%s] %s: %s (similarity: %.3f)", 
                    context.timestamp, 
                    context.sender, 
                    context.content,
                    context.relevanceScore
                ));
            }
            
            contextBuilder.append("\nUser Question: ").append(message.question);
            contextBuilder.append("\nPlease answer the question based on the WhatsApp chat context above. ");
            contextBuilder.append("Be specific about who said what and when. If the information isn't in the chat, say so clearly.");

            // Use ASK pattern to get LLM response
            ActorRef<LLMActor.QueryResponse> llmAdapter = 
                getContext().messageAdapter(LLMActor.QueryResponse.class, 
                    response -> new LLMResponseReceived(response, message.question, message.originalReplyTo, relevantMessages, message.startTime));
            
            String queryId = "chat_q_" + System.currentTimeMillis();
            llmActor.tell(new LLMActor.QueryRequest(
                queryId, 
                contextBuilder.toString(), 
                llmAdapter
            ));
            
        } else {
            System.out.println("⚠️ [SESSION] No relevant context found for question");
            
            // Still ask LLM but without specific context
            List<String> emptyRelevant = Collections.emptyList();
            String generalQuery = "User asks about WhatsApp chat: \"" + message.question + 
                                 "\". No specific relevant messages found in the chat. " +
                                 "Please explain that no relevant information was found in the uploaded chat.";
            
            ActorRef<LLMActor.QueryResponse> llmAdapter = 
                getContext().messageAdapter(LLMActor.QueryResponse.class, 
                    response -> new LLMResponseReceived(response, message.question, message.originalReplyTo, emptyRelevant, message.startTime));
            
            String queryId = "chat_q_" + System.currentTimeMillis();
            llmActor.tell(new LLMActor.QueryRequest(
                queryId, 
                generalQuery, 
                llmAdapter
            ));
        }

        return this;
    }

    private Behavior<Command> onLLMResponseReceived(LLMResponseReceived message) {
        long processingTime = System.currentTimeMillis() - message.startTime;
        
        if (message.response.success) {
            System.out.println(String.format(
                "✅ [SESSION] LLM response received for question (%.2fs)", 
                processingTime / 1000.0
            ));

            // Extract session ID from the LLM response context (we'll need to track this better)
            String sessionId = "current"; // For now, we'll need to improve session tracking
            
            // Log the response using FORWARD pattern to LoggingActor
            logger.tell(new WhatsAppLoggerActor.LogActivity(
                sessionId,
                "ANSWER_PROVIDED",
                "Question answered: " + message.question,
                System.currentTimeMillis()
            ));

            QuestionResponse successResponse = new QuestionResponse(
                sessionId,
                message.question,
                message.response.response,
                true,
                null,
                message.relevantMessages,
                processingTime
            );
            
            message.originalReplyTo.tell(successResponse);
            
        } else {
            System.out.println("❌ [SESSION] LLM response failed: " + message.response.error);
            
            QuestionResponse errorResponse = new QuestionResponse(
                "current", // Need better session tracking
                message.question,
                "",
                false,
                "LLM processing failed: " + message.response.error,
                message.relevantMessages,
                processingTime
            );
            
            message.originalReplyTo.tell(errorResponse);
        }

        return this;
    }
}
