package com.akka.cluster.app.swing;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.AskPattern;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Join;
import com.akka.cluster.app.SessionManagerActor;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import akka.util.Timeout;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.UUID;
import java.util.LinkedList;
import java.util.List;
import java.util.Collections;

/**
 * Bridge class connecting Swing GUI to the distributed Akka cluster
 * This class replaces mock responses with real cluster integration
 */
public class AkkaSwingBridge {
    
    private ActorSystem<Void> system;
    private ActorRef<SessionManagerActor.Command> sessionManager;
    private Timeout timeout;
    
    // Question history management
    private final LinkedList<String> questionHistory = new LinkedList<>();
    private final int MAX_HISTORY_SIZE = 5;
    
    // Result classes for GUI callbacks
    public static class UploadResult {
        public final boolean success;
        public final String sessionId;
        public final String message;
        public final int messagesProcessed;
        public final String error;
        
        public UploadResult(boolean success, String sessionId, String message, int messagesProcessed) {
            this.success = success;
            this.sessionId = sessionId;
            this.message = message;
            this.messagesProcessed = messagesProcessed;
            this.error = success ? null : message;
        }
    }
    
    public static class QueryResult {
        public final boolean success;
        public final String response;
        public final String error;
        public final long responseTimeMs;
        public final int contextCount;
        
        public QueryResult(boolean success, String response, String error, long responseTimeMs, int contextCount) {
            this.success = success;
            this.response = response;
            this.error = error;
            this.responseTimeMs = responseTimeMs;
            this.contextCount = contextCount;
        }
        
        public QueryResult(boolean success, String response, String error) {
            this(success, response, error, 0, 0);
        }
    }
    
    public AkkaSwingBridge() {
        initialize();
    }
    
    private void initialize() {
        try {
            // Create configuration for GUI client node
            Config config = ConfigFactory.parseString(
                "akka.remote.artery.canonical.port = 0\n" +
                "akka.cluster.roles = [\"gui-client\"]\n"
            ).withFallback(ConfigFactory.load());
            
            // Create typed actor system that connects to cluster
            system = ActorSystem.create(
                Behaviors.setup(context -> {
                    // Join the cluster
                    Cluster cluster = Cluster.get(system);
                    cluster.manager().tell(Join.create(cluster.selfMember().address()));
                    
                    // Create session manager
                    sessionManager = context.spawn(
                        SessionManagerActor.create(),
                        "gui-session-manager"
                    );
                    
                    return Behaviors.empty();
                }),
                "ClusterSystem",
                config
            );
            
            // Set timeout for ask pattern
            timeout = Timeout.create(Duration.ofSeconds(30));
            
            System.out.println("AkkaSwingBridge initialized successfully");
            
        } catch (Exception e) {
            System.err.println("Failed to initialize AkkaSwingBridge: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Upload chat file to the cluster for processing with callback
     */
    public void uploadChatFile(String filePath, Consumer<UploadResult> callback) {
        if (sessionManager == null) {
            callback.accept(new UploadResult(false, null, "Akka system not initialized", 0));
            return;
        }
        
        try {
            String sessionId = UUID.randomUUID().toString();
            
            // Use ask pattern with typed actors
            CompletionStage<SessionManagerActor.UploadResponse> result = AskPattern.ask(
                sessionManager,
                (ActorRef<SessionManagerActor.UploadResponse> replyTo) -> 
                    new SessionManagerActor.UploadChatFile(sessionId, filePath, replyTo),
                Duration.ofSeconds(30),
                system.scheduler()
            );
            
            // Handle response asynchronously
            result.whenComplete((response, throwable) -> {
                if (throwable != null) {
                    callback.accept(new UploadResult(false, sessionId, 
                        "Error uploading file: " + throwable.getMessage(), 0));
                } else {
                    callback.accept(new UploadResult(response.success, response.sessionId, 
                        response.message, response.totalMessages));
                }
            });
                
        } catch (Exception e) {
            callback.accept(new UploadResult(false, null, 
                "Error processing upload: " + e.getMessage(), 0));
        }
    }
    
    /**
     * Send query to the cluster and get AI-powered response with callback
     */
    public void processQuery(String sessionId, String query, Consumer<QueryResult> callback) {
        if (sessionManager == null) {
            callback.accept(new QueryResult(false, null, "Akka system not initialized"));
            return;
        }
        
        if (query == null || query.trim().isEmpty()) {
            callback.accept(new QueryResult(false, null, "Please enter a valid query"));
            return;
        }
        
        try {
            // Add question to history
            addToQuestionHistory(query.trim());
            
            // Use ask pattern with typed actors
            CompletionStage<SessionManagerActor.QuestionResponse> result = AskPattern.ask(
                sessionManager,
                (ActorRef<SessionManagerActor.QuestionResponse> replyTo) -> 
                    new SessionManagerActor.AskQuestion(sessionId, query.trim(), replyTo),
                Duration.ofSeconds(30),
                system.scheduler()
            );
            
            // Handle response asynchronously
            result.whenComplete((response, throwable) -> {
                if (throwable != null) {
                    callback.accept(new QueryResult(false, null, 
                        "Error processing query: " + throwable.getMessage(), 0, 0));
                } else {
                    callback.accept(new QueryResult(true, response.answer, null, 0, 0));
                }
            });
                
        } catch (Exception e) {
            callback.accept(new QueryResult(false, null, 
                "Error sending query: " + e.getMessage(), 0, 0));
        }
    }
    
    /**
     * Add question to history, maintaining only the last 5 questions
     */
    private void addToQuestionHistory(String question) {
        synchronized (questionHistory) {
            // Add to the front of the list (most recent first)
            questionHistory.addFirst(question);
            
            // Remove oldest question if we exceed the limit
            if (questionHistory.size() > MAX_HISTORY_SIZE) {
                questionHistory.removeLast();
            }
        }
    }
    
    /**
     * Get the question history (most recent first)
     */
    public List<String> getQuestionHistory() {
        synchronized (questionHistory) {
            return new LinkedList<>(questionHistory);
        }
    }
    
    /**
     * Get a specific question from history by index (0 = most recent)
     */
    public String getQuestionFromHistory(int index) {
        synchronized (questionHistory) {
            if (index >= 0 && index < questionHistory.size()) {
                return questionHistory.get(index);
            }
            return null;
        }
    }
    
    /**
     * Clear question history
     */
    public void clearQuestionHistory() {
        synchronized (questionHistory) {
            questionHistory.clear();
        }
    }
    
    /**
     * Clear all history
     */
    public void clearAllHistory() {
        clearQuestionHistory();
    }
    
    /**
     * Get formatted question history for display
     */
    public String getFormattedQuestionHistory() {
        synchronized (questionHistory) {
            if (questionHistory.isEmpty()) {
                return "No recent questions";
            }
            
            StringBuilder sb = new StringBuilder("Recent Questions:\n");
            for (int i = 0; i < questionHistory.size(); i++) {
                sb.append(String.format("%d. %s\n", i + 1, questionHistory.get(i)));
            }
            return sb.toString();
        }
    }
    
    /**
     * Get system health status
     */
    public String getSystemStatus() {
        if (system == null) {
            return "System not initialized";
        }
        
        try {
            // Check if system is running
            boolean isRunning = !system.whenTerminated().isCompleted();
            String status = isRunning ? "Running" : "Terminated";
            
            // Try to get cluster information
            Cluster cluster = Cluster.get(system);
            String clusterInfo = cluster != null ? 
                " | Cluster: " + cluster.selfMember().address() : 
                " | No cluster info";
                
            // Add question history info
            String historyInfo = "";
            synchronized (questionHistory) {
                if (!questionHistory.isEmpty()) {
                    historyInfo = String.format(" | Question history: %d questions", questionHistory.size());
                }
            }
                
            return "Akka System: " + status + clusterInfo + historyInfo;
            
        } catch (Exception e) {
            return "Status check failed: " + e.getMessage();
        }
    }
    
    /**
     * Shutdown the actor system gracefully
     */
    public void shutdown() {
        if (system != null) {
            try {
                system.terminate();
                System.out.println("AkkaSwingBridge shut down gracefully");
            } catch (Exception e) {
                System.err.println("Error during shutdown: " + e.getMessage());
            }
        }
    }
}