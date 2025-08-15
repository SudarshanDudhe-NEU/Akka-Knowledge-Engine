package com.akka.cluster.app;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import akka.cluster.typed.Cluster;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * ClusterMessageRouter demonstrates real distributed message flow:
 * - Service discovery across cluster nodes
 * - Load balancing and routing
 * - Fault tolerance and failover
 * - Real request-response cycles across the cluster
 */
public class ClusterMessageRouter extends AbstractBehavior<ClusterMessageRouter.Command> {

    // Service keys for cluster-wide service discovery
    public static final ServiceKey<LLMActor.Command> LLM_SERVICE_KEY = 
        ServiceKey.create(LLMActor.Command.class, "llm-service");
    
    public static final ServiceKey<UserQueryHandler.Command> QUERY_HANDLER_SERVICE_KEY = 
        ServiceKey.create(UserQueryHandler.Command.class, "query-handler-service");
    
    public static final ServiceKey<LoggingActor.Command> LOGGING_SERVICE_KEY = 
        ServiceKey.create(LoggingActor.Command.class, "logging-service");

    // Command messages
    public interface Command extends CborSerializable {}

    // Distributed query request
    public static final class DistributedQuery implements Command {
        public final String queryId;
        public final String userId;
        public final String query;
        public final String originNode;
        public final long timestamp;
        public final ActorRef<DistributedQueryResponse> replyTo;

        @JsonCreator
        public DistributedQuery(
            @JsonProperty("queryId") String queryId,
            @JsonProperty("userId") String userId,
            @JsonProperty("query") String query,
            @JsonProperty("originNode") String originNode,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("replyTo") ActorRef<DistributedQueryResponse> replyTo
        ) {
            this.queryId = queryId;
            this.userId = userId;
            this.query = query;
            this.originNode = originNode;
            this.timestamp = timestamp;
            this.replyTo = replyTo;
        }
    }

    // Distributed query response
    public static final class DistributedQueryResponse implements CborSerializable {
        public final String queryId;
        public final String response;
        public final boolean success;
        public final String processingNode;
        public final String routingPath;
        public final long processingTimeMs;
        public final Map<String, String> metadata;

        @JsonCreator
        public DistributedQueryResponse(
            @JsonProperty("queryId") String queryId,
            @JsonProperty("response") String response,
            @JsonProperty("success") boolean success,
            @JsonProperty("processingNode") String processingNode,
            @JsonProperty("routingPath") String routingPath,
            @JsonProperty("processingTimeMs") long processingTimeMs,
            @JsonProperty("metadata") Map<String, String> metadata
        ) {
            this.queryId = queryId;
            this.response = response;
            this.success = success;
            this.processingNode = processingNode;
            this.routingPath = routingPath;
            this.processingTimeMs = processingTimeMs;
            this.metadata = metadata;
        }
    }

    // Internal messages for service discovery
    private static final class ServicesUpdated implements Command {
        public final Receptionist.Listing listing;

        public ServicesUpdated(Receptionist.Listing listing) {
            this.listing = listing;
        }
    }

    // Internal message for handling query routing
    private static final class QueryRouted implements Command {
        public final DistributedQuery originalQuery;
        public final UserQueryHandler.UserResponse response;
        public final long startTime;

        public QueryRouted(DistributedQuery originalQuery, UserQueryHandler.UserResponse response, long startTime) {
            this.originalQuery = originalQuery;
            this.response = response;
            this.startTime = startTime;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(ClusterMessageRouter::new);
    }

    private final String nodeAddress;
    private final Cluster cluster;
    private final Set<ActorRef<LLMActor.Command>> availableLLMServices = new HashSet<>();
    private final Set<ActorRef<UserQueryHandler.Command>> availableQueryHandlers = new HashSet<>();
    private final Set<ActorRef<LoggingActor.Command>> availableLoggers = new HashSet<>();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private final Map<String, Long> activeQueries = new HashMap<>();

    private ClusterMessageRouter(ActorContext<Command> context) {
        super(context);
        this.cluster = Cluster.get(context.getSystem());
        this.nodeAddress = cluster.selfMember().address().toString();
        
        // Subscribe to service discoveries
        ActorRef<Receptionist.Listing> listingAdapter = 
            context.messageAdapter(Receptionist.Listing.class, ServicesUpdated::new);
        
        context.getSystem().receptionist().tell(
            Receptionist.subscribe(LLM_SERVICE_KEY, listingAdapter));
        context.getSystem().receptionist().tell(
            Receptionist.subscribe(QUERY_HANDLER_SERVICE_KEY, listingAdapter));
        context.getSystem().receptionist().tell(
            Receptionist.subscribe(LOGGING_SERVICE_KEY, listingAdapter));
        
        System.out.println("🌐 ClusterMessageRouter started on " + nodeAddress);
        System.out.println("🔍 Discovering services across the cluster...");
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(DistributedQuery.class, this::onDistributedQuery)
            .onMessage(ServicesUpdated.class, this::onServicesUpdated)
            .onMessage(QueryRouted.class, this::onQueryRouted)
            .build();
    }

    private Behavior<Command> onDistributedQuery(DistributedQuery query) {
        long startTime = System.currentTimeMillis();
        activeQueries.put(query.queryId, startTime);
        
        String timestamp = LocalDateTime.now().format(timeFormatter);
        System.out.println(String.format(
            "\n🌐 [%s] Routing distributed query: %s", 
            timestamp, 
            query.queryId
        ));
        System.out.println(String.format(
            "👤 From: %s@%s", 
            query.userId, 
            query.originNode
        ));
        System.out.println("📝 Query: " + query.query);
        
        // Log to available loggers (FORWARD pattern)
        broadcastToLoggers(String.format(
            "Distributed query received: [%s] %s from %s", 
            query.queryId, 
            query.userId,
            query.originNode
        ));
        
        // Route to an available query handler (ASK pattern)
        if (availableQueryHandlers.isEmpty()) {
            // No query handlers available - send error response
            DistributedQueryResponse errorResponse = new DistributedQueryResponse(
                query.queryId,
                "No query handlers available in the cluster",
                false,
                nodeAddress,
                nodeAddress + " → ERROR (no handlers)",
                System.currentTimeMillis() - startTime,
                Map.of("error", "no_handlers", "cluster_size", String.valueOf(getClusterSize()))
            );
            
            query.replyTo.tell(errorResponse);
            System.out.println("❌ No query handlers available - sent error response");
            return this;
        }
        
        // Select a query handler using round-robin
        ActorRef<UserQueryHandler.Command> selectedHandler = selectQueryHandler();
        System.out.println("🎯 Selected query handler: " + selectedHandler.path());
        
        // Create user query
        UserQueryHandler.UserQuery userQuery = new UserQueryHandler.UserQuery(
            query.userId,
            query.query,
            getContext().messageAdapter(UserQueryHandler.UserResponse.class, 
                response -> new QueryRouted(query, response, startTime))
        );
        
        // TELL to query handler
        selectedHandler.tell(userQuery);
        
        System.out.println("🔄 Query routed for processing...");
        
        return this;
    }

    private Behavior<Command> onServicesUpdated(ServicesUpdated updated) {
        Receptionist.Listing listing = updated.listing;
        
        if (listing.isForKey(LLM_SERVICE_KEY)) {
            availableLLMServices.clear();
            availableLLMServices.addAll(listing.getServiceInstances(LLM_SERVICE_KEY));
            System.out.println("🤖 LLM services updated: " + availableLLMServices.size() + " available");
        } else if (listing.isForKey(QUERY_HANDLER_SERVICE_KEY)) {
            availableQueryHandlers.clear();
            availableQueryHandlers.addAll(listing.getServiceInstances(QUERY_HANDLER_SERVICE_KEY));
            System.out.println("👤 Query handler services updated: " + availableQueryHandlers.size() + " available");
        } else if (listing.isForKey(LOGGING_SERVICE_KEY)) {
            availableLoggers.clear();
            availableLoggers.addAll(listing.getServiceInstances(LOGGING_SERVICE_KEY));
            System.out.println("📋 Logging services updated: " + availableLoggers.size() + " available");
        }
        
        // Print service discovery summary
        if (!availableLLMServices.isEmpty() || !availableQueryHandlers.isEmpty() || !availableLoggers.isEmpty()) {
            System.out.println(String.format(
                "📊 Cluster services: %d LLM, %d QueryHandlers, %d Loggers", 
                availableLLMServices.size(),
                availableQueryHandlers.size(),
                availableLoggers.size()
            ));
        }
        
        return this;
    }

    private Behavior<Command> onQueryRouted(QueryRouted queryRouted) {
        DistributedQuery originalQuery = queryRouted.originalQuery;
        UserQueryHandler.UserResponse response = queryRouted.response;
        long totalProcessingTime = System.currentTimeMillis() - queryRouted.startTime;
        
        // Remove from active queries
        activeQueries.remove(originalQuery.queryId);
        
        // Create routing path information
        String routingPath = String.format(
            "%s → Router@%s → QueryHandler → LLM@%s → Response", 
            originalQuery.originNode,
            nodeAddress,
            response.processedBy
        );
        
        // Create metadata
        Map<String, String> metadata = new HashMap<>();
        metadata.put("cluster_size", String.valueOf(getClusterSize()));
        metadata.put("available_services", String.format("%d+%d+%d", 
            availableLLMServices.size(), availableQueryHandlers.size(), availableLoggers.size()));
        metadata.put("router_node", nodeAddress);
        metadata.put("query_timestamp", String.valueOf(originalQuery.timestamp));
        
        DistributedQueryResponse distributedResponse = new DistributedQueryResponse(
            originalQuery.queryId,
            response.response,
            response.success,
            response.processedBy,
            routingPath,
            totalProcessingTime,
            metadata
        );
        
        // Log completion (FORWARD pattern)
        broadcastToLoggers(String.format(
            "Distributed query completed: [%s] Success: %s, Time: %dms, Path: %s", 
            originalQuery.queryId,
            response.success,
            totalProcessingTime,
            routingPath
        ));
        
        // TELL response back to original requester
        originalQuery.replyTo.tell(distributedResponse);
        
        String timestamp = LocalDateTime.now().format(timeFormatter);
        System.out.println(String.format(
            "\n✅ [%s] Distributed query completed: %s", 
            timestamp, 
            originalQuery.queryId
        ));
        System.out.println(String.format(
            "📊 Total time: %dms, Success: %s", 
            totalProcessingTime,
            response.success
        ));
        System.out.println("🛤️  Path: " + routingPath);
        System.out.println("─".repeat(80));
        
        return this;
    }

    private ActorRef<UserQueryHandler.Command> selectQueryHandler() {
        // Simple round-robin selection
        List<ActorRef<UserQueryHandler.Command>> handlers = new ArrayList<>(availableQueryHandlers);
        int index = ThreadLocalRandom.current().nextInt(handlers.size());
        return handlers.get(index);
    }

    private void broadcastToLoggers(String message) {
        // FORWARD pattern - send to all available loggers
        for (ActorRef<LoggingActor.Command> logger : availableLoggers) {
            LoggingActor.LogMessage logMsg = new LoggingActor.LogMessage(
                message, "INFO", "ClusterMessageRouter", null);
            logger.tell(logMsg);
        }
    }

    // Helper method to get cluster statistics
    public static String getClusterStats(Cluster cluster) {
        String leaderAddress = "none";
        var leader = cluster.state().getLeader();
        if (leader != null) {
            leaderAddress = leader.toString();
        }
        return String.format(
            "Cluster: %d members, Leader: %s", 
            getClusterSizeStatic(cluster),
            leaderAddress
        );
    }
    
    private int getClusterSize() {
        return (int) cluster.state().getMembers().spliterator().estimateSize();
    }
    
    private static int getClusterSizeStatic(Cluster cluster) {
        return (int) cluster.state().getMembers().spliterator().estimateSize();
    }
}
