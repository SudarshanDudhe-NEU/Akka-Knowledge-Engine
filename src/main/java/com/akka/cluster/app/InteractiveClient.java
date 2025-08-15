package com.akka.cluster.app;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.receptionist.Receptionist;
import akka.actor.typed.receptionist.ServiceKey;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Join;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public class InteractiveClient {
    
    public interface Command extends CborSerializable {}
    
    public static final class StartInteractiveSession implements Command {
        public static final StartInteractiveSession INSTANCE = new StartInteractiveSession();
        private StartInteractiveSession() {}
    }
    
    public static final class UserQuery implements Command {
        public final String query;
        public final String userId;
        
        public UserQuery(String query, String userId) {
            this.query = query;
            this.userId = userId;
        }
    }
    
    public static final class QueryResponse implements Command {
        public final String queryId;
        public final String response;
        public final boolean success;
        public final long processingTimeMs;
        
        public QueryResponse(String queryId, String response, boolean success, long processingTimeMs) {
            this.queryId = queryId;
            this.response = response;
            this.success = success;
            this.processingTimeMs = processingTimeMs;
        }
    }
    
    public static final class RouterServicesUpdated implements Command {
        public final Set<ActorRef<ClusterMessageRouter.Command>> routers;
        
        public RouterServicesUpdated(Set<ActorRef<ClusterMessageRouter.Command>> routers) {
            this.routers = Collections.unmodifiableSet(new HashSet<>(routers));
        }
    }
    
    private static final ServiceKey<ClusterMessageRouter.Command> ROUTER_SERVICE_KEY =
        ServiceKey.create(ClusterMessageRouter.Command.class, "cluster-router");
    
    public static Behavior<Command> create() {
        return Behaviors.setup(context -> {
            // Register for router service updates
            ActorRef<Receptionist.Listing> listingAdapter = 
                context.messageAdapter(Receptionist.Listing.class, listing -> {
                    Set<ActorRef<ClusterMessageRouter.Command>> routers = listing.getServiceInstances(ROUTER_SERVICE_KEY);
                    return new RouterServicesUpdated(routers);
                });
            
            context.getSystem().receptionist().tell(Receptionist.subscribe(ROUTER_SERVICE_KEY, listingAdapter));
            
            // Create response adapter
            ActorRef<ClusterMessageRouter.DistributedQueryResponse> responseAdapter = 
                context.messageAdapter(ClusterMessageRouter.DistributedQueryResponse.class, response -> 
                    new QueryResponse(response.queryId, response.response, response.success, response.processingTimeMs));
            
            System.out.println("\n🎮 INTERACTIVE CLIENT STARTED");
            System.out.println("══════════════════════════════════════════════════════════════════════════════");
            System.out.println("🔍 Discovering cluster services...");
            System.out.println("💡 Waiting for cluster routers to become available...");
            
            return waitingForServices(Collections.emptySet(), responseAdapter);
        });
    }
    
    private static Behavior<Command> waitingForServices(
            Set<ActorRef<ClusterMessageRouter.Command>> routers,
            ActorRef<ClusterMessageRouter.DistributedQueryResponse> responseAdapter) {
        return Behaviors.receive(Command.class)
            .onMessage(RouterServicesUpdated.class, update -> {
                if (!update.routers.isEmpty() && routers.isEmpty()) {
                    System.out.println("✅ Cluster routers discovered: " + update.routers.size());
                    System.out.println("");
                    System.out.println("🎯 READY TO ACCEPT QUERIES!");
                    System.out.println("══════════════════════════════════════════════════════════════════════════════");
                    System.out.println("Type your questions below. Examples:");
                    System.out.println("• Find the top 3 trending AI startups with recent funding");
                    System.out.println("• How does Akka cluster handle network partitions?");
                    System.out.println("• What are the latest trends in machine learning?");
                    System.out.println("• Explain microservices architecture best practices");
                    System.out.println("");
                    System.out.println("Type 'quit' or 'exit' to stop.");
                    System.out.println("══════════════════════════════════════════════════════════════════════════════");
                    
                    // Start the interactive session
                    startInteractiveLoop(update.routers, responseAdapter);
                    return interactive(update.routers, responseAdapter);
                } else if (!update.routers.isEmpty()) {
                    return waitingForServices(update.routers, responseAdapter);
                } else {
                    System.out.println("⚠️ No cluster routers available. Waiting...");
                    return waitingForServices(Collections.emptySet(), responseAdapter);
                }
            })
            .build();
    }
    
    private static Behavior<Command> interactive(
            Set<ActorRef<ClusterMessageRouter.Command>> routers,
            ActorRef<ClusterMessageRouter.DistributedQueryResponse> responseAdapter) {
        return Behaviors.receive(Command.class)
            .onMessage(UserQuery.class, query -> {
                handleUserQuery(query, routers, responseAdapter);
                return Behaviors.same();
            })
            .onMessage(QueryResponse.class, response -> {
                handleQueryResponse(response);
                return Behaviors.same();
            })
            .onMessage(RouterServicesUpdated.class, update -> {
                if (update.routers.isEmpty()) {
                    System.out.println("⚠️ Lost connection to cluster routers. Waiting for reconnection...");
                    return waitingForServices(Collections.emptySet(), responseAdapter);
                }
                return interactive(update.routers, responseAdapter);
            })
            .build();
    }
    
    private static void startInteractiveLoop(
            Set<ActorRef<ClusterMessageRouter.Command>> routers,
            ActorRef<ClusterMessageRouter.DistributedQueryResponse> responseAdapter) {
        // Start a separate thread for reading user input
        Thread inputThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            String currentUserId = "interactive-user";
            
            while (true) {
                System.out.print("\n🤖 Enter your query: ");
                
                if (!scanner.hasNextLine()) {
                    break;
                }
                
                String input = scanner.nextLine().trim();
                
                if (input.isEmpty()) {
                    continue;
                }
                
                if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit")) {
                    System.out.println("👋 Goodbye! Shutting down interactive client...");
                    System.exit(0);
                    break;
                }
                
                // Send the query
                System.out.println("\n🚀 Processing your query: \"" + input + "\"");
                System.out.println("🔄 Sending to distributed cluster...");
                
                if (!routers.isEmpty()) {
                    ActorRef<ClusterMessageRouter.Command> router = routers.iterator().next();
                    String queryId = "interactive-" + System.currentTimeMillis();
                    
                    router.tell(new ClusterMessageRouter.DistributedQuery(
                        queryId,
                        currentUserId,
                        input,
                        "interactive-client",
                        System.currentTimeMillis(),
                        responseAdapter
                    ));
                } else {
                    System.out.println("❌ No cluster routers available. Please wait for cluster to be ready.");
                }
            }
            
            scanner.close();
        });
        
        inputThread.setDaemon(true);
        inputThread.start();
    }
    
    private static void handleUserQuery(
            UserQuery query, 
            Set<ActorRef<ClusterMessageRouter.Command>> routers,
            ActorRef<ClusterMessageRouter.DistributedQueryResponse> responseAdapter) {
        if (!routers.isEmpty()) {
            ActorRef<ClusterMessageRouter.Command> router = routers.iterator().next();
            String queryId = "user-" + System.currentTimeMillis();
            
            router.tell(new ClusterMessageRouter.DistributedQuery(
                queryId,
                query.userId,
                query.query,
                "interactive-client",
                System.currentTimeMillis(),
                responseAdapter
            ));
        } else {
            System.out.println("❌ No cluster routers available");
        }
    }
    
    private static void handleQueryResponse(QueryResponse response) {
        System.out.println("\n✅ RESPONSE RECEIVED");
        System.out.println("────────────────────────────────────────────────────────────────────────────────");
        System.out.println("🤖 " + response.response);
        System.out.println("📊 Processing time: " + response.processingTimeMs + "ms");
        System.out.println("🎯 Status: " + (response.success ? "SUCCESS" : "FAILED"));
        System.out.println("────────────────────────────────────────────────────────────────────────────────");
    }
    
    public static void main(String[] args) {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 2559;
        
        // Create config for interactive client
        String configString = String.format("""
            akka {
              actor {
                provider = "cluster"
                
                serialization-bindings {
                  "com.akka.cluster.app.CborSerializable" = jackson-cbor
                }
              }
              
              remote.artery {
                canonical {
                  hostname = "127.0.0.1"
                  port = %d
                }
              }
              
              cluster {
                seed-nodes = [
                  "akka://ClusterSystem@127.0.0.1:2551"
                ]
                
                roles = ["interactive-client"]
                
                downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"
                
                # Log cluster events
                log-info = on
                log-info-verbose = on
              }
            }
            """, port);
            
        Config config = ConfigFactory.parseString(configString)
                .withFallback(ConfigFactory.load());
        
        ActorSystem<Command> system = ActorSystem.create(
            create(),
            "ClusterSystem",
            config
        );
        
        // Join the cluster
        Cluster cluster = Cluster.get(system);
        cluster.manager().tell(Join.create(cluster.selfMember().address()));
        
        System.out.println("🎮 Interactive Client started on port " + port);
        System.out.println("🌐 Cluster Address: " + cluster.selfMember().address());
    }
}
