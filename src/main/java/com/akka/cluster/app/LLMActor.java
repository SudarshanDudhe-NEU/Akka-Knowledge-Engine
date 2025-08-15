package com.akka.cluster.app;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * LLMActor handles communication with the Perplexity API.
 * Demonstrates asynchronous LLM integration in an Akka cluster.
 */
public class LLMActor extends AbstractBehavior<LLMActor.Command> {

    // Command messages
    public interface Command extends CborSerializable {}

    public static final class QueryRequest implements Command {
        public final String queryId;
        public final String userQuery;
        public final ActorRef<QueryResponse> replyTo;

        @JsonCreator
        public QueryRequest(
            @JsonProperty("queryId") String queryId,
            @JsonProperty("userQuery") String userQuery,
            @JsonProperty("replyTo") ActorRef<QueryResponse> replyTo
        ) {
            this.queryId = queryId;
            this.userQuery = userQuery;
            this.replyTo = replyTo;
        }
    }

    public static final class QueryResponse implements CborSerializable {
        public final String queryId;
        public final String response;
        public final boolean success;
        public final String error;
        public final String processedBy;

        @JsonCreator
        public QueryResponse(
            @JsonProperty("queryId") String queryId,
            @JsonProperty("response") String response,
            @JsonProperty("success") boolean success,
            @JsonProperty("error") String error,
            @JsonProperty("processedBy") String processedBy
        ) {
            this.queryId = queryId;
            this.response = response;
            this.success = success;
            this.error = error;
            this.processedBy = processedBy;
        }
    }

    private static final class QueryCompleted implements Command {
        public final QueryRequest originalRequest;
        public final QueryResponse response;

        public QueryCompleted(QueryRequest originalRequest, QueryResponse response) {
            this.originalRequest = originalRequest;
            this.response = response;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(LLMActor::new);
    }

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String nodeAddress;
    
    // You'll need to set your Perplexity API key here
    private static final String PERPLEXITY_API_KEY = "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";
    //  null; // Temporarily disabled for mock demo
    // "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";
    // System.getenv("PERPLEXITY_API_KEY");
    private static final String PERPLEXITY_API_URL = "https://api.perplexity.ai/chat/completions";

    private LLMActor(ActorContext<Command> context) {
        super(context);
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
        this.objectMapper = new ObjectMapper();
        this.nodeAddress = context.getSystem().address().toString();
        
        System.out.println("🤖 LLMActor started on " + nodeAddress);
        
        if (PERPLEXITY_API_KEY == null || PERPLEXITY_API_KEY.isEmpty()) {
            System.out.println("⚠️ PERPLEXITY_API_KEY not found in environment variables");
            System.out.println("💡 Set it with: export PERPLEXITY_API_KEY=\"your-api-key\"");
        }
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(QueryRequest.class, this::onQueryRequest)
            .onMessage(QueryCompleted.class, this::onQueryCompleted)
            .build();
    }

    private Behavior<Command> onQueryRequest(QueryRequest request) {
        System.out.println(String.format(
            "🤖 [LLM] Processing query '%s': %s", 
            request.queryId, 
            request.userQuery
        ));

        if (PERPLEXITY_API_KEY == null || PERPLEXITY_API_KEY.isEmpty()) {
            // Fallback to mock response when API key is not available
            handleMockResponse(request);
        } else {
            // Make actual API call
            queryPerplexityAPI(request);
        }

        return this;
    }

    private void queryPerplexityAPI(QueryRequest request) {
        try {
            // Prepare the request body for Perplexity API with proper model and structured response
            String requestBody = String.format("""
                {
                    "model": "sonar",
                    "messages": [
                        {
                            "role": "system",
                            "content": "You are a WhatsApp Chat Analysis AI. You will receive WhatsApp chat context and a user question. Analyze the chat messages carefully and answer the question based ONLY on the information found in the WhatsApp chat. Be specific about who said what and when. If the information isn't clearly in the chat, say so explicitly. Focus on: 1) Who said what, 2) When they said it, 3) What the specific details were. Provide accurate, factual answers based on the chat content."
                        },
                        {
                            "role": "user",
                            "content": "%s"
                        }
                    ],
                    "max_tokens": 500,
                    "temperature": 0.1,
                    "top_p": 0.9,
                    "stream": false
                }
                """, escapeJson(request.userQuery));

            System.out.println("🔍 [LLM] Sending request to Perplexity API...");
            // System.out.println("Request: " + requestBody); // Uncomment for debugging

            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(PERPLEXITY_API_URL))
                .header("Authorization", "Bearer " + PERPLEXITY_API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .timeout(Duration.ofSeconds(30))
                .build();

            // Send async HTTP request
            CompletableFuture<HttpResponse<String>> responseFuture = 
                httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString());

            responseFuture.whenComplete((response, throwable) -> {
                if (throwable != null) {
                    System.out.println("❌ [LLM] API call failed: " + throwable.getMessage());
                    QueryResponse errorResponse = new QueryResponse(
                        request.queryId,
                        "API call failed: " + throwable.getMessage(),
                        false,
                        throwable.getMessage(),
                        nodeAddress
                    );
                    getContext().getSelf().tell(new QueryCompleted(request, errorResponse));
                } else {
                    handleAPIResponse(request, response);
                }
            });

        } catch (Exception e) {
            System.out.println("❌ [LLM] Error preparing API request: " + e.getMessage());
            QueryResponse errorResponse = new QueryResponse(
                request.queryId,
                "Error preparing request: " + e.getMessage(),
                false,
                e.getMessage(),
                nodeAddress
            );
            getContext().getSelf().tell(new QueryCompleted(request, errorResponse));
        }
    }

    private void handleAPIResponse(QueryRequest request, HttpResponse<String> response) {
        try {
            if (response.statusCode() == 200) {
                JsonNode jsonResponse = objectMapper.readTree(response.body());
                
                // Extract the content from the API response
                JsonNode contentNode = jsonResponse.path("choices").get(0)
                    .path("message").path("content");
                
                String llmResponse;
                
                if (contentNode.isTextual()) {
                    // Parse the structured JSON response
                    try {
                        JsonNode structuredResponse = objectMapper.readTree(contentNode.asText());
                        String answer = structuredResponse.path("answer").asText();
                        double confidence = structuredResponse.path("confidence").asDouble(0.8);
                        String sourceType = structuredResponse.path("source_type").asText("general");
                        
                        // Format the response nicely
                        llmResponse = String.format(
                            "🤖 %s\n\n📊 Confidence: %.1f%%\n🔖 Source Type: %s", 
                            answer, 
                            confidence * 100,
                            sourceType
                        );
                        
                        // Add key messages if available
                        JsonNode keyMessages = structuredResponse.path("key_messages");
                        if (keyMessages.isArray() && keyMessages.size() > 0) {
                            StringBuilder keyText = new StringBuilder("\n\n� Key Chat Messages:");
                            for (JsonNode message : keyMessages) {
                                keyText.append("\n• ").append(message.asText());
                            }
                            llmResponse += keyText.toString();
                        }
                        
                    } catch (Exception e) {
                        // Fallback to plain text if JSON parsing fails
                        llmResponse = contentNode.asText();
                    }
                } else {
                    llmResponse = contentNode.asText();
                }

                System.out.println("✅ [LLM] API response received for query: " + request.queryId);
                QueryResponse successResponse = new QueryResponse(
                    request.queryId,
                    llmResponse,
                    true,
                    null,
                    nodeAddress
                );
                getContext().getSelf().tell(new QueryCompleted(request, successResponse));
            } else {
                System.out.println("❌ [LLM] API returned status: " + response.statusCode());
                System.out.println("❌ [LLM] Response body: " + response.body());
                QueryResponse errorResponse = new QueryResponse(
                    request.queryId,
                    "API error: " + response.statusCode() + " - " + response.body(),
                    false,
                    "HTTP " + response.statusCode(),
                    nodeAddress
                );
                getContext().getSelf().tell(new QueryCompleted(request, errorResponse));
            }
        } catch (IOException e) {
            System.out.println("❌ [LLM] Error parsing API response: " + e.getMessage());
            QueryResponse errorResponse = new QueryResponse(
                request.queryId,
                "Error parsing response: " + e.getMessage(),
                false,
                e.getMessage(),
                nodeAddress
            );
            getContext().getSelf().tell(new QueryCompleted(request, errorResponse));
        }
    }

    private void handleMockResponse(QueryRequest request) {
        // Simulate processing time
        getContext().scheduleOnce(
            Duration.ofMillis(500 + (int)(Math.random() * 1000)),
            getContext().getSelf(),
            new QueryCompleted(request, generateMockResponse(request))
        );
    }

    private QueryResponse generateMockResponse(QueryRequest request) {
        String query = request.userQuery.toLowerCase();
        String mockResponse;

        if (query.contains("startup") || query.contains("funding") || query.contains("trending")) {
            mockResponse = """
                🤖 Here are 3 trending AI startups with recent funding (mock data):
                
                💰 Company: DeepMind 2.0
                   Funding: $2.1B Series D
                   Focus: Artificial General Intelligence (AGI)
                
                💰 Company: QuantumAI Labs  
                   Funding: $750M Series C
                   Focus: Quantum Machine Learning
                
                💰 Company: EdgeML Systems
                   Funding: $450M Series B
                   Focus: Edge AI for IoT devices
                
                📊 Confidence: 85%
                🔖 Source Type: mock_data
                
                💡 Additional Info:
                • This is mock data for demonstration
                • Real API responses would include current funding information
                • Set PERPLEXITY_API_KEY for live data""";
        } else if (query.contains("weather")) {
            mockResponse = """
                🤖 Weather information is available through real-time APIs.
                
                📊 Confidence: 90%
                🔖 Source Type: factual
                
                💡 Additional Info:
                • For live weather data, enable Perplexity API
                • Mock response simulates sunny, 72°F""";
        } else if (query.contains("hello") || query.contains("hi")) {
            mockResponse = """
                🤖 Hello! I'm your distributed AI assistant running in an Akka cluster.
                
                📊 Confidence: 100%
                🔖 Source Type: creative
                
                💡 Additional Info:
                • I'm currently in mock mode
                • Configure PERPLEXITY_API_KEY for real AI responses
                • Ask me anything about technology, startups, or AI!""";
        } else if (query.contains("akka") || query.contains("cluster")) {
            mockResponse = """
                🤖 Akka Cluster is a powerful distributed systems toolkit for the JVM.
                
                It provides:
                • Cluster membership and failure detection
                • Location-transparent message passing  
                • Load balancing and routing
                • Fault tolerance and self-healing
                
                📊 Confidence: 95%
                🔖 Source Type: technical
                
                💡 Additional Info:
                • Built on the Actor Model
                • Supports both Scala and Java
                • Used by companies like Netflix, PayPal, and LinkedIn""";
        } else if (query.contains("java") || query.contains("programming")) {
            mockResponse = """
                🤖 Java is a robust, object-oriented programming language.
                
                Key features:
                • Platform independence ("Write Once, Run Anywhere")
                • Strong memory management with garbage collection
                • Rich ecosystem and extensive libraries
                • Enterprise-grade scalability
                
                📊 Confidence: 98%
                🔖 Source Type: technical
                
                💡 Additional Info:
                • First released in 1995 by Sun Microsystems
                • Now maintained by Oracle
                • Popular for enterprise applications and Android development""";
        } else {
            mockResponse = String.format("""
                🤖 Mock AI response for your query: "%s"
                
                This is a simulated response to demonstrate the distributed message flow.
                The query has been processed through:
                1. Router service discovery
                2. Query handler orchestration  
                3. LLM processing (mock mode)
                4. Response aggregation and logging
                
                📊 Confidence: 75%%
                🔖 Source Type: analytical
                
                💡 Additional Info:
                • Configure PERPLEXITY_API_KEY for real AI responses
                • All communication patterns (TELL, ASK, FORWARD) working
                • Message traced through complete distributed system""", request.userQuery);
        }

        return new QueryResponse(
            request.queryId,
            mockResponse,
            true,
            null,
            nodeAddress + " (mock mode)"
        );
    }

    private Behavior<Command> onQueryCompleted(QueryCompleted completed) {
        QueryResponse response = completed.response;
        
        System.out.println(String.format(
            "✅ [LLM] Query '%s' completed: %s", 
            response.queryId,
            response.success ? "SUCCESS" : "FAILED"
        ));
        
        if (response.success) {
            System.out.println("🤖 [LLM RESPONSE] " + response.response);
        } else {
            System.out.println("❌ [LLM ERROR] " + response.error);
        }

        // Send response back to the original requester
        completed.originalRequest.replyTo.tell(response);
        
        return this;
    }
    
    private String escapeJson(String input) {
        if (input == null) return "";
        return input
            .replace("\\", "\\\\")    // Escape backslashes first
            .replace("\"", "\\\"")    // Escape quotes
            .replace("\n", "\\n")     // Escape newlines
            .replace("\r", "\\r")     // Escape carriage returns
            .replace("\t", "\\t")     // Escape tabs
            .replace("\b", "\\b")     // Escape backspace
            .replace("\f", "\\f");    // Escape form feed
    }
}
