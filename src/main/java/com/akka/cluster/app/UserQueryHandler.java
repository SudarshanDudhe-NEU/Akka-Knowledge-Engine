package com.akka.cluster.app;

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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * UserQueryHandler demonstrates the complete message flow:
 * 1. Accept user queries
 * 2. Route to LLM actor via cluster
 * 3. Use ask to request response from LLM
 * 4. Forward results to logging
 * 5. Return final response to user
 */
public class UserQueryHandler extends AbstractBehavior<UserQueryHandler.Command> {

    // Command messages
    public interface Command extends CborSerializable {}

    public static final class UserQuery implements Command {
        public final String userId;
        public final String query;
        public final ActorRef<UserResponse> replyTo;

        @JsonCreator
        public UserQuery(
            @JsonProperty("userId") String userId,
            @JsonProperty("query") String query,
            @JsonProperty("replyTo") ActorRef<UserResponse> replyTo
        ) {
            this.userId = userId;
            this.query = query;
            this.replyTo = replyTo;
        }
    }

    public static final class UserResponse implements CborSerializable {
        public final String queryId;
        public final String userId;
        public final String originalQuery;
        public final String response;
        public final boolean success;
        public final String processedBy;
        public final String timestamp;

        @JsonCreator
        public UserResponse(
            @JsonProperty("queryId") String queryId,
            @JsonProperty("userId") String userId,
            @JsonProperty("originalQuery") String originalQuery,
            @JsonProperty("response") String response,
            @JsonProperty("success") boolean success,
            @JsonProperty("processedBy") String processedBy,
            @JsonProperty("timestamp") String timestamp
        ) {
            this.queryId = queryId;
            this.userId = userId;
            this.originalQuery = originalQuery;
            this.response = response;
            this.success = success;
            this.processedBy = processedBy;
            this.timestamp = timestamp;
        }
    }

    private static final class LLMResponseReceived implements Command {
        public final UserQuery originalQuery;
        public final LLMActor.QueryResponse llmResponse;

        public LLMResponseReceived(UserQuery originalQuery, LLMActor.QueryResponse llmResponse) {
            this.originalQuery = originalQuery;
            this.llmResponse = llmResponse;
        }
    }

    public static Behavior<Command> create(ActorRef<LLMActor.Command> llmActor, ActorRef<LoggingActor.Command> loggingActor) {
        return Behaviors.setup(context -> new UserQueryHandler(context, llmActor, loggingActor));
    }

    private final AtomicInteger queryCounter = new AtomicInteger(0);
    private final ActorRef<LLMActor.Command> llmActor;
    private final ActorRef<LoggingActor.Command> loggingActor;
    private final String nodeAddress;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private UserQueryHandler(ActorContext<Command> context, ActorRef<LLMActor.Command> llmActor, ActorRef<LoggingActor.Command> loggingActor) {
        super(context);
        this.llmActor = llmActor;
        this.loggingActor = loggingActor;
        this.nodeAddress = context.getSystem().address().toString();
        
        System.out.println("👤 UserQueryHandler started on " + nodeAddress);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(UserQuery.class, this::onUserQuery)
            .onMessage(LLMResponseReceived.class, this::onLLMResponseReceived)
            .build();
    }

    private Behavior<Command> onUserQuery(UserQuery userQuery) {
        String queryId = "query-" + queryCounter.incrementAndGet() + "-" + System.currentTimeMillis();
        
        System.out.println(String.format(
            "👤 [USER QUERY] %s from user '%s': %s", 
            queryId, 
            userQuery.userId, 
            userQuery.query
        ));

        // FORWARD pattern - Log the incoming query using new LoggingActor interface
        if (loggingActor != null) {
            String logMessage = String.format("User query received: [%s] %s -> %s", 
                queryId, userQuery.userId, userQuery.query);
            LoggingActor.LogMessage logMsg = new LoggingActor.LogMessage(
                logMessage, "INFO", "UserQueryHandler", null);
            loggingActor.tell(logMsg);
        }

        // ASK pattern - Send query to LLM actor and wait for response
        LLMActor.QueryRequest llmRequest = new LLMActor.QueryRequest(
            queryId,
            userQuery.query,
            getContext().messageAdapter(LLMActor.QueryResponse.class, 
                response -> new LLMResponseReceived(userQuery, response))
        );

        // TELL pattern - Send request to LLM actor
        llmActor.tell(llmRequest);
        
        System.out.println(String.format(
            "🔄 [ROUTING] Query '%s' routed to LLM actor", queryId
        ));

        return this;
    }

    private Behavior<Command> onLLMResponseReceived(LLMResponseReceived responseReceived) {
        UserQuery originalQuery = responseReceived.originalQuery;
        LLMActor.QueryResponse llmResponse = responseReceived.llmResponse;
        
        String timestamp = LocalDateTime.now().format(timeFormatter);
        
        // Create final user response
        UserResponse finalResponse = new UserResponse(
            llmResponse.queryId,
            originalQuery.userId,
            originalQuery.query,
            llmResponse.response,
            llmResponse.success,
            llmResponse.processedBy,
            timestamp
        );

        System.out.println(String.format(
            "✅ [QUERY COMPLETE] %s processed %s", 
            llmResponse.queryId,
            llmResponse.success ? "successfully" : "with errors"
        ));

        // FORWARD pattern - Log the completion using new LoggingActor interface
        if (loggingActor != null) {
            String logMessage = String.format("Query completed: [%s] %s -> Success: %s", 
                llmResponse.queryId, originalQuery.userId, llmResponse.success);
            String level = llmResponse.success ? "INFO" : "ERROR";
            LoggingActor.LogMessage logMsg = new LoggingActor.LogMessage(
                logMessage, level, "UserQueryHandler", null);
            loggingActor.tell(logMsg);
        }

        // TELL pattern - Send final response back to user
        originalQuery.replyTo.tell(finalResponse);

        return this;
    }
}
