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

/**
 * LoggingActor demonstrates the FORWARD communication pattern.
 * It receives log messages and forwards them to multiple destinations:
 * - Console output (immediate logging)
 * - Log aggregators (for analysis)
 * - Alert handlers (for critical messages)
 * - Metrics collectors (for performance tracking)
 * 
 * The FORWARD pattern preserves the original sender information while
 * distributing messages to multiple interested parties.
 */
public class LoggingActor extends AbstractBehavior<LoggingActor.Command> {

    // Command interface for the logging actor
    public interface Command extends CborSerializable {}

    // Log message with metadata
    public static final class LogMessage implements Command {
        public final String message;
        public final String level;
        public final String source;
        public final long timestamp;
        public final Map<String, String> metadata;
        public final ActorRef<LogResponse> replyTo;

        @JsonCreator
        public LogMessage(
                @JsonProperty("message") String message,
                @JsonProperty("level") String level,
                @JsonProperty("source") String source,
                @JsonProperty("timestamp") long timestamp,
                @JsonProperty("metadata") Map<String, String> metadata,
                @JsonProperty("replyTo") ActorRef<LogResponse> replyTo) {
            this.message = message;
            this.level = level != null ? level : "INFO";
            this.source = source != null ? source : "unknown";
            this.timestamp = timestamp;
            this.metadata = metadata != null ? metadata : Collections.emptyMap();
            this.replyTo = replyTo;
        }

        // Convenience constructor
        public LogMessage(String message, String level, String source, ActorRef<LogResponse> replyTo) {
            this(message, level, source, System.currentTimeMillis(), Collections.emptyMap(), replyTo);
        }

        // Simple constructor for basic logging
        public LogMessage(String message) {
            this(message, "INFO", "system", System.currentTimeMillis(), Collections.emptyMap(), null);
        }
    }

    // Register a new destination for forwarding
    public static final class RegisterDestination implements Command {
        public final ActorRef<ForwardedLogMessage> destination;
        public final String destinationType;
        public final Set<String> interestedLevels;

        public RegisterDestination(ActorRef<ForwardedLogMessage> destination, String destinationType, Set<String> interestedLevels) {
            this.destination = destination;
            this.destinationType = destinationType;
            this.interestedLevels = interestedLevels != null ? interestedLevels : Set.of("INFO", "WARN", "ERROR", "DEBUG");
        }
    }

    // Unregister a destination
    public static final class UnregisterDestination implements Command {
        public final ActorRef<ForwardedLogMessage> destination;

        public UnregisterDestination(ActorRef<ForwardedLogMessage> destination) {
            this.destination = destination;
        }
    }

    // Response message for log operations
    public static final class LogResponse implements CborSerializable {
        public final String logId;
        public final boolean success;
        public final String message;
        public final long processingTime;

        @JsonCreator
        public LogResponse(
                @JsonProperty("logId") String logId,
                @JsonProperty("success") boolean success,
                @JsonProperty("message") String message,
                @JsonProperty("processingTime") long processingTime) {
            this.logId = logId;
            this.success = success;
            this.message = message;
            this.processingTime = processingTime;
        }
    }

    // Message that gets forwarded to destinations
    public static final class ForwardedLogMessage implements CborSerializable {
        public final String originalMessage;
        public final String level;
        public final String source;
        public final long timestamp;
        public final Map<String, String> metadata;
        public final String logId;
        public final String forwardedBy;

        @JsonCreator
        public ForwardedLogMessage(
                @JsonProperty("originalMessage") String originalMessage,
                @JsonProperty("level") String level,
                @JsonProperty("source") String source,
                @JsonProperty("timestamp") long timestamp,
                @JsonProperty("metadata") Map<String, String> metadata,
                @JsonProperty("logId") String logId,
                @JsonProperty("forwardedBy") String forwardedBy) {
            this.originalMessage = originalMessage;
            this.level = level;
            this.source = source;
            this.timestamp = timestamp;
            this.metadata = metadata;
            this.logId = logId;
            this.forwardedBy = forwardedBy;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(LoggingActor::new);
    }

    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private final Map<ActorRef<ForwardedLogMessage>, DestinationInfo> forwardingDestinations;
    private long messageCounter = 0;

    private static class DestinationInfo {
        final String type;
        final Set<String> interestedLevels;

        DestinationInfo(String type, Set<String> interestedLevels) {
            this.type = type;
            this.interestedLevels = interestedLevels;
        }
    }

    private LoggingActor(ActorContext<Command> context) {
        super(context);
        this.forwardingDestinations = new HashMap<>();
        
        System.out.println("📋 Enhanced LoggingActor started on " + context.getSystem().address());
        System.out.println("🔄 FORWARD pattern enabled - ready to forward to multiple destinations");
        
        // Create some built-in forwarding destinations for demonstration
        createBuiltInDestinations();
    }

    private void createBuiltInDestinations() {
        // Create console logger destination
        ActorRef<ForwardedLogMessage> consoleLogger = getContext().spawn(
            ConsoleLogDestination.create(), "console-logger");
        forwardingDestinations.put(consoleLogger, 
            new DestinationInfo("console", Set.of("INFO", "WARN", "ERROR", "DEBUG")));

        // Create metrics collector destination  
        ActorRef<ForwardedLogMessage> metricsCollector = getContext().spawn(
            MetricsCollectorDestination.create(), "metrics-collector");
        forwardingDestinations.put(metricsCollector, 
            new DestinationInfo("metrics", Set.of("INFO", "WARN", "ERROR")));

        // Create alert handler destination (only for warnings and errors)
        ActorRef<ForwardedLogMessage> alertHandler = getContext().spawn(
            AlertHandlerDestination.create(), "alert-handler");
        forwardingDestinations.put(alertHandler, 
            new DestinationInfo("alerts", Set.of("WARN", "ERROR")));

        System.out.println("🎯 Initialized " + forwardingDestinations.size() + " forwarding destinations");
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(LogMessage.class, this::onLogMessage)
            .onMessage(RegisterDestination.class, this::onRegisterDestination)
            .onMessage(UnregisterDestination.class, this::onUnregisterDestination)
            .build();
    }

    private Behavior<Command> onLogMessage(LogMessage logMsg) {
        long startTime = System.currentTimeMillis();
        messageCounter++;
        String logId = "LOG-" + messageCounter + "-" + startTime;
        
        String timestamp = LocalDateTime.now().format(timeFormatter);
        
        // Log to our own console first
        System.out.println(String.format(
            "📝 [%s] [%s] %s (from: %s) [ID: %s]", 
            logMsg.level,
            timestamp, 
            logMsg.message,
            logMsg.source,
            logId
        ));

        // FORWARD PATTERN: Forward to all interested destinations
        int forwardedCount = 0;
        for (Map.Entry<ActorRef<ForwardedLogMessage>, DestinationInfo> entry : forwardingDestinations.entrySet()) {
            DestinationInfo destInfo = entry.getValue();
            
            // Check if this destination is interested in this log level
            if (destInfo.interestedLevels.contains(logMsg.level)) {
                ActorRef<ForwardedLogMessage> destination = entry.getKey();
                
                // Create forwarded message with additional metadata
                ForwardedLogMessage forwardedMsg = new ForwardedLogMessage(
                    logMsg.message,
                    logMsg.level,
                    logMsg.source,
                    logMsg.timestamp,
                    logMsg.metadata,
                    logId,
                    getContext().getSelf().path().name()
                );
                
                // FORWARD the message (preserving original sender context)
                destination.tell(forwardedMsg);
                forwardedCount++;
            }
        }

        System.out.println(String.format(
            "🔄 [FORWARD] Forwarded log message to %d destinations (level: %s)", 
            forwardedCount, logMsg.level
        ));

        // Send response if replyTo is provided
        if (logMsg.replyTo != null) {
            long processingTime = System.currentTimeMillis() - startTime;
            logMsg.replyTo.tell(new LogResponse(logId, true, "Log processed and forwarded", processingTime));
        }

        return this;
    }

    private Behavior<Command> onRegisterDestination(RegisterDestination cmd) {
        forwardingDestinations.put(cmd.destination, 
            new DestinationInfo(cmd.destinationType, cmd.interestedLevels));
        
        System.out.println(String.format(
            "➕ [REGISTER] New forwarding destination: %s (type: %s, levels: %s)", 
            cmd.destination.path().name(),
            cmd.destinationType,
            cmd.interestedLevels
        ));
        
        return this;
    }

    private Behavior<Command> onUnregisterDestination(UnregisterDestination cmd) {
        DestinationInfo removed = forwardingDestinations.remove(cmd.destination);
        
        if (removed != null) {
            System.out.println(String.format(
                "➖ [UNREGISTER] Removed forwarding destination: %s (type: %s)", 
                cmd.destination.path().name(),
                removed.type
            ));
        }
        
        return this;
    }

    // Built-in destination actors to demonstrate forwarding

    /**
     * Console destination - logs everything to console with formatting
     */
    public static class ConsoleLogDestination extends AbstractBehavior<ForwardedLogMessage> {
        
        public static Behavior<ForwardedLogMessage> create() {
            return Behaviors.setup(ConsoleLogDestination::new);
        }

        private ConsoleLogDestination(ActorContext<ForwardedLogMessage> context) {
            super(context);
        }

        @Override
        public Receive<ForwardedLogMessage> createReceive() {
            return newReceiveBuilder()
                .onMessage(ForwardedLogMessage.class, msg -> {
                    String icon = getIconForLevel(msg.level);
                    System.out.println(String.format(
                        "  %s [CONSOLE] [%s] %s (forwarded by: %s, ID: %s)",
                        icon, msg.level, msg.originalMessage, msg.forwardedBy, msg.logId
                    ));
                    return this;
                })
                .build();
        }

        private String getIconForLevel(String level) {
            return switch (level) {
                case "ERROR" -> "❌";
                case "WARN" -> "⚠️";
                case "INFO" -> "ℹ️";
                case "DEBUG" -> "🐛";
                default -> "📄";
            };
        }
    }

    /**
     * Metrics collector destination - tracks statistics
     */
    public static class MetricsCollectorDestination extends AbstractBehavior<ForwardedLogMessage> {
        
        public static Behavior<ForwardedLogMessage> create() {
            return Behaviors.setup(MetricsCollectorDestination::new);
        }

        private final Map<String, Integer> levelCounts = new HashMap<>();
        private final Map<String, Integer> sourceCounts = new HashMap<>();

        private MetricsCollectorDestination(ActorContext<ForwardedLogMessage> context) {
            super(context);
        }

        @Override
        public Receive<ForwardedLogMessage> createReceive() {
            return newReceiveBuilder()
                .onMessage(ForwardedLogMessage.class, msg -> {
                    // Collect metrics
                    levelCounts.merge(msg.level, 1, Integer::sum);
                    sourceCounts.merge(msg.source, 1, Integer::sum);
                    
                    int totalLogs = levelCounts.values().stream().mapToInt(Integer::intValue).sum();
                    
                    System.out.println(String.format(
                        "  📊 [METRICS] Log #%d: level=%s, source=%s (ID: %s)",
                        totalLogs, msg.level, msg.source, msg.logId
                    ));
                    
                    // Print summary every 5 messages
                    if (totalLogs % 5 == 0) {
                        System.out.println("  📈 [METRICS SUMMARY] Levels: " + levelCounts + ", Sources: " + sourceCounts);
                    }
                    
                    return this;
                })
                .build();
        }
    }

    /**
     * Alert handler destination - handles warnings and errors
     */
    public static class AlertHandlerDestination extends AbstractBehavior<ForwardedLogMessage> {
        
        public static Behavior<ForwardedLogMessage> create() {
            return Behaviors.setup(AlertHandlerDestination::new);
        }

        private AlertHandlerDestination(ActorContext<ForwardedLogMessage> context) {
            super(context);
        }

        @Override
        public Receive<ForwardedLogMessage> createReceive() {
            return newReceiveBuilder()
                .onMessage(ForwardedLogMessage.class, msg -> {
                    if ("ERROR".equals(msg.level)) {
                        System.out.println(String.format(
                            "  🚨 [ALERT] CRITICAL ERROR detected: %s (source: %s, ID: %s)",
                            msg.originalMessage, msg.source, msg.logId
                        ));
                        // In real system: send email, SMS, Slack notification, etc.
                    } else if ("WARN".equals(msg.level)) {
                        System.out.println(String.format(
                            "  ⚡ [ALERT] Warning detected: %s (source: %s, ID: %s)",
                            msg.originalMessage, msg.source, msg.logId
                        ));
                        // In real system: log to warning dashboard
                    }
                    
                    return this;
                })
                .build();
        }
    }
}
