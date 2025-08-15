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

/**
 * Main application class for Akka Cluster demonstration.
 * 
 * This class creates different types of cluster nodes based on command line arguments:
 * - seed: Creates a seed node (first node in the cluster)
 * - llm: Creates an LLM service node for AI query processing
 * - query-handler: Creates a query handler node for user interaction management
 * - router: Creates a router node for message forwarding demonstrations
 * - interactive: Creates an interactive client for real-time LLM queries
 * 
 * Usage:
 * java -jar target/akka-cluster-demo.jar seed
 * java -jar target/akka-cluster-demo.jar llm 2555
 * java -jar target/akka-cluster-demo.jar query-handler 2556
 * java -jar target/akka-cluster-demo.jar router 2557
 * java -jar target/akka-cluster-demo.jar interactive
 * java -jar target/akka-cluster-demo.jar interactive
 */
public class ClusterApp {
    
    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java ClusterApp <node-type> [port]");
            System.out.println("Node types: seed, llm, query-handler, router, interactive, whatsapp-qa");
            System.out.println("Example: java ClusterApp seed");
            System.out.println("Example: java ClusterApp llm 2555");
            System.out.println("Example: java ClusterApp query-handler 2556");
            System.out.println("Example: java ClusterApp router 2557");
            System.out.println("Example: java ClusterApp interactive 2559");
            System.out.println("Example: java ClusterApp whatsapp-qa 2560");
            System.exit(1);
        }
        
        String nodeType = args[0];
        int port = args.length > 1 ? Integer.parseInt(args[1]) : getDefaultPort(nodeType);
        
        startNode(nodeType, port);
    }
    
    private static int getDefaultPort(String nodeType) {
        switch (nodeType) {
            case "seed": return 2551;
            case "llm": return 2555;
            case "query-handler": return 2556;
            case "router": return 2557;
            case "interactive": return 2559;
            case "whatsapp-qa": return 2560;
            default: return 2554;
        }
    }
    
    private static void startNode(String nodeType, int port) {
        Config config = createConfig(nodeType, port);
        
        ActorSystem<Void> system = ActorSystem.create(
            getRootBehavior(nodeType), 
            "ClusterSystem", 
            config
        );
        
        // Print startup information
        System.out.println(String.format(
            "Starting %s node on port %d", nodeType, port
        ));
        
        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Shutting down cluster node...");
            system.terminate();
        }));
    }
    
    private static Config createConfig(String nodeType, int port) {
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
                
                roles = ["%s"]
                
                downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"
                
                # Log cluster events
                log-info = on
                log-info-verbose = on
              }
            }
            """, port, nodeType);
            
        return ConfigFactory.parseString(configString)
                .withFallback(ConfigFactory.load());
    }
    
    private static Behavior<Void> getRootBehavior(String nodeType) {
        return Behaviors.setup(context -> {
            Cluster cluster = Cluster.get(context.getSystem());
            
            // Create the appropriate actor based on node type
            switch (nodeType) {
                case "seed":
                    context.spawn(ClusterListener.create(), "cluster-listener");
                    System.out.println("Seed node started. Other nodes can join the cluster.");
                    break;
                    
                case "llm":
                    context.spawn(ClusterListener.create(), "cluster-listener");
                    var llmActorRef = context.spawn(LLMActor.create(), "llm-actor");
                    // Register LLM service for cluster discovery
                    context.getSystem().receptionist().tell(
                        akka.actor.typed.receptionist.Receptionist.register(
                            ClusterMessageRouter.LLM_SERVICE_KEY, llmActorRef));
                    System.out.println("LLM node started with Perplexity API integration.");
                    break;
                    
                case "query-handler":
                    context.spawn(ClusterListener.create(), "cluster-listener");
                    // Create logging actor first
                    var loggingActorRef = context.spawn(LoggingActor.create(), "logging-actor");
                    // Create local LLM actor reference for this query-handler node
                    // In production, query handlers could discover LLM services via cluster receptionist
                    var localLlmActor = context.spawn(LLMActor.create(), "local-llm-actor");
                    var queryHandlerRef = context.spawn(UserQueryHandler.create(localLlmActor, loggingActorRef), "query-handler");
                    
                    // Register services for cluster discovery
                    context.getSystem().receptionist().tell(
                        akka.actor.typed.receptionist.Receptionist.register(
                            ClusterMessageRouter.QUERY_HANDLER_SERVICE_KEY, queryHandlerRef));
                    context.getSystem().receptionist().tell(
                        akka.actor.typed.receptionist.Receptionist.register(
                            ClusterMessageRouter.LOGGING_SERVICE_KEY, loggingActorRef));
                    context.getSystem().receptionist().tell(
                        akka.actor.typed.receptionist.Receptionist.register(
                            ClusterMessageRouter.LLM_SERVICE_KEY, localLlmActor));
                    
                    System.out.println("Query handler node started with LLM integration.");
                    break;
                    
                case "router":
                    context.spawn(ClusterListener.create(), "cluster-listener");
                    ActorRef<ClusterMessageRouter.Command> routerRef = 
                        context.spawn(ClusterMessageRouter.create(), "cluster-router");
                    
                    // Register the router with the receptionist so other nodes can discover it
                    ServiceKey<ClusterMessageRouter.Command> ROUTER_SERVICE_KEY = 
                        ServiceKey.create(ClusterMessageRouter.Command.class, "cluster-router");
                    context.getSystem().receptionist().tell(
                        Receptionist.register(ROUTER_SERVICE_KEY, routerRef));
                    
                    System.out.println("Cluster message router node started.");
                    break;
                    
                case "interactive":
                    context.spawn(ClusterListener.create(), "cluster-listener");
                    context.spawn(InteractiveClient.create(), "interactive-client");
                    System.out.println("Interactive client node started.");
                    break;
                    
                case "whatsapp-qa":
                    context.spawn(ClusterListener.create(), "cluster-listener");
                    context.spawn(WhatsAppQAClient.create(), "whatsapp-qa-client");
                    System.out.println("WhatsApp Chat Q&A client node started.");
                    break;
                    
                default:
                    System.out.println("Unknown node type: " + nodeType);
                    context.getSystem().terminate();
                    break;
            }
            
            return Behaviors.empty();
        });
    }
}
