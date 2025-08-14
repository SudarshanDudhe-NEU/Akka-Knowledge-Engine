package com.akka.cluster.demo;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * WhatsAppLoggerActor records WhatsApp Q&A sessions and activities.
 * Demonstrates FORWARD pattern for distributed logging and TELL for fire-and-forget logging.
 */
public class WhatsAppLoggerActor extends AbstractBehavior<WhatsAppLoggerActor.Command> {

    // Command messages
    public interface Command extends CborSerializable {}

    public static final class LogActivity implements Command {
        public final String sessionId;
        public final String activityType;
        public final String message;
        public final long timestamp;

        @JsonCreator
        public LogActivity(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("activityType") String activityType,
            @JsonProperty("message") String message,
            @JsonProperty("timestamp") long timestamp
        ) {
            this.sessionId = sessionId;
            this.activityType = activityType;
            this.message = message;
            this.timestamp = timestamp;
        }
    }

    public static final class LogQuestionAnswer implements Command {
        public final String sessionId;
        public final String question;
        public final String answer;
        public final boolean success;
        public final long processingTimeMs;
        public final List<String> relevantContext;

        @JsonCreator
        public LogQuestionAnswer(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("question") String question,
            @JsonProperty("answer") String answer,
            @JsonProperty("success") boolean success,
            @JsonProperty("processingTimeMs") long processingTimeMs,
            @JsonProperty("relevantContext") List<String> relevantContext
        ) {
            this.sessionId = sessionId;
            this.question = question;
            this.answer = answer;
            this.success = success;
            this.processingTimeMs = processingTimeMs;
            this.relevantContext = relevantContext;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(WhatsAppLoggerActor::new);
    }

    private final String nodeAddress;
    private final Map<String, List<LogEntry>> sessionLogs;
    private final DateTimeFormatter timestampFormatter;
    private final String logFileName;

    public static final class LogEntry {
        public final String timestamp;
        public final String activityType;
        public final String message;
        public final Map<String, Object> metadata;

        public LogEntry(String timestamp, String activityType, String message, Map<String, Object> metadata) {
            this.timestamp = timestamp;
            this.activityType = activityType;
            this.message = message;
            this.metadata = metadata;
        }
    }

    private WhatsAppLoggerActor(ActorContext<Command> context) {
        super(context);
        this.nodeAddress = context.getSystem().address().toString();
        this.sessionLogs = new HashMap<>();
        this.timestampFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
        this.logFileName = "whatsapp_qa_logs.txt";
        
        System.out.println("📋 WhatsAppLoggerActor started on " + nodeAddress);
        System.out.println("📁 Logging to file: " + logFileName);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(LogActivity.class, this::onLogActivity)
            .onMessage(LogQuestionAnswer.class, this::onLogQuestionAnswer)
            .build();
    }

    private Behavior<Command> onLogActivity(LogActivity command) {
        String timestamp = LocalDateTime.now().format(timestampFormatter);
        
        System.out.println(String.format(
            "📋 [WHATSAPP-LOG] [%s] %s: %s", 
            command.activityType,
            command.sessionId, 
            command.message
        ));

        // Create log entry
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("timestamp_ms", command.timestamp);
        metadata.put("node", nodeAddress);
        
        LogEntry entry = new LogEntry(
            timestamp,
            command.activityType,
            command.message,
            metadata
        );

        // Store in memory
        sessionLogs.computeIfAbsent(command.sessionId, k -> new ArrayList<>()).add(entry);

        // Write to file (TELL pattern - fire and forget)
        writeToFile(command.sessionId, entry);

        return this;
    }

    private Behavior<Command> onLogQuestionAnswer(LogQuestionAnswer command) {
        String timestamp = LocalDateTime.now().format(timestampFormatter);
        
        System.out.println(String.format(
            "📋 [WHATSAPP-LOG] Q&A [%s]: Question processed in %dms", 
            command.sessionId, 
            command.processingTimeMs
        ));

        // Write detailed Q&A to file
        writeQuestionAnswerToFile(command);

        return this;
    }

    private void writeToFile(String sessionId, LogEntry entry) {
        try (FileWriter writer = new FileWriter(logFileName, true)) {
            writer.write(String.format(
                "[%s] [%s] [%s] %s%n",
                entry.timestamp,
                sessionId,
                entry.activityType,
                entry.message
            ));
        } catch (IOException e) {
            System.err.println("❌ [WHATSAPP-LOG] Error writing to log file: " + e.getMessage());
        }
    }

    private void writeQuestionAnswerToFile(LogQuestionAnswer qa) {
        try (FileWriter writer = new FileWriter(logFileName, true)) {
            writer.write(String.format(
                "=== Q&A SESSION: %s ===\n" +
                "Timestamp: %s\n" +
                "Question: %s\n" +
                "Success: %s\n" +
                "Processing Time: %dms\n" +
                "Answer: %s\n" +
                "Relevant Context (%d items):\n",
                qa.sessionId,
                LocalDateTime.now().format(timestampFormatter),
                qa.question,
                qa.success,
                qa.processingTimeMs,
                qa.answer,
                qa.relevantContext.size()
            ));

            for (int i = 0; i < qa.relevantContext.size(); i++) {
                writer.write(String.format("  %d. %s\n", i + 1, qa.relevantContext.get(i)));
            }

            writer.write("=== END Q&A ===\n\n");

        } catch (IOException e) {
            System.err.println("❌ [WHATSAPP-LOG] Error writing Q&A to log file: " + e.getMessage());
        }
    }
}
