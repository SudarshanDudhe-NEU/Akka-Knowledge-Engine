package com.akka.cluster.app;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ChatParserActor handles parsing WhatsApp chat export files.
 * Demonstrates TELL pattern for file processing and data indexing.
 */
public class ChatParserActor extends AbstractBehavior<ChatParserActor.Command> {

    // Command messages
    public interface Command extends CborSerializable {}

    public static final class ParseChatFile implements Command {
        public final String sessionId;
        public final String filePath;
        public final ActorRef<ParsingCompleted> replyTo;

        @JsonCreator
        public ParseChatFile(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("filePath") String filePath,
            @JsonProperty("replyTo") ActorRef<ParsingCompleted> replyTo
        ) {
            this.sessionId = sessionId;
            this.filePath = filePath;
            this.replyTo = replyTo;
        }
    }

    public static final class ParsingCompleted implements CborSerializable {
        public final String sessionId;
        public final List<ChatMessage> messages;
        public final boolean success;
        public final String error;
        public final int totalMessages;

        @JsonCreator
        public ParsingCompleted(
            @JsonProperty("sessionId") String sessionId,
            @JsonProperty("messages") List<ChatMessage> messages,
            @JsonProperty("success") boolean success,
            @JsonProperty("error") String error,
            @JsonProperty("totalMessages") int totalMessages
        ) {
            this.sessionId = sessionId;
            this.messages = messages;
            this.success = success;
            this.error = error;
            this.totalMessages = totalMessages;
        }
    }

    public static final class ChatMessage implements CborSerializable {
        public final String timestamp;
        public final String sender;
        public final String content;
        public final String messageId;
        public final LocalDateTime dateTime;

        @JsonCreator
        public ChatMessage(
            @JsonProperty("timestamp") String timestamp,
            @JsonProperty("sender") String sender,
            @JsonProperty("content") String content,
            @JsonProperty("messageId") String messageId,
            @JsonProperty("dateTime") LocalDateTime dateTime
        ) {
            this.timestamp = timestamp;
            this.sender = sender;
            this.content = content;
            this.messageId = messageId;
            this.dateTime = dateTime;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(ChatParserActor::new);
    }

    private final String nodeAddress;
    
    // WhatsApp chat patterns for different export formats
    private static final Pattern WHATSAPP_PATTERN_1 = Pattern.compile(
        "^(\\d{1,2}/\\d{1,2}/\\d{2,4}),\\s(\\d{1,2}:\\d{2})\\s-\\s([^:]+):\\s(.*)$"
    );
    
    private static final Pattern WHATSAPP_PATTERN_2 = Pattern.compile(
        "^\\[(\\d{1,2}/\\d{1,2}/\\d{2,4}),\\s(\\d{1,2}:\\d{2}:\\d{2})\\]\\s([^:]+):\\s(.*)$"
    );
    
    private static final Pattern WHATSAPP_PATTERN_3 = Pattern.compile(
        "^(\\d{1,2}-\\d{1,2}-\\d{4})\\s(\\d{1,2}:\\d{2})\\s-\\s([^:]+):\\s(.*)$"
    );
    
    // New pattern for 12-hour format with AM/PM: [7/1/25, 11:31:28 PM] Name: message
    // Updated to handle special characters, Unicode, and various name formats
    private static final Pattern WHATSAPP_PATTERN_4 = Pattern.compile(
        "^\\[(\\d{1,2}/\\d{1,2}/\\d{2,4}),\\s+(\\d{1,2}:\\d{2}:\\d{2})\\s+(AM|PM)\\]\\s+([^:]+?):\\s*(.*)$",
        Pattern.UNICODE_CHARACTER_CLASS
    );

    private ChatParserActor(ActorContext<Command> context) {
        super(context);
        this.nodeAddress = context.getSystem().address().toString();
        System.out.println("💬 ChatParserActor started on " + nodeAddress);
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(ParseChatFile.class, this::onParseChatFile)
            .build();
    }

    private Behavior<Command> onParseChatFile(ParseChatFile command) {
        System.out.println(String.format(
            "💬 [PARSER] Processing chat file for session '%s': %s", 
            command.sessionId, 
            command.filePath
        ));

        try {
            List<String> lines = Files.readAllLines(Path.of(command.filePath));
            List<ChatMessage> messages = parseWhatsAppChat(lines);
            
            System.out.println(String.format(
                "✅ [PARSER] Successfully parsed %d messages from %d lines", 
                messages.size(), 
                lines.size()
            ));

            ParsingCompleted response = new ParsingCompleted(
                command.sessionId,
                messages,
                true,
                null,
                messages.size()
            );
            
            command.replyTo.tell(response);
            
        } catch (IOException e) {
            System.out.println("❌ [PARSER] Error reading file: " + e.getMessage());
            
            ParsingCompleted errorResponse = new ParsingCompleted(
                command.sessionId,
                Collections.emptyList(),
                false,
                "Error reading file: " + e.getMessage(),
                0
            );
            
            command.replyTo.tell(errorResponse);
        } catch (Exception e) {
            System.out.println("❌ [PARSER] Error parsing chat: " + e.getMessage());
            
            ParsingCompleted errorResponse = new ParsingCompleted(
                command.sessionId,
                Collections.emptyList(),
                false,
                "Error parsing chat: " + e.getMessage(),
                0
            );
            
            command.replyTo.tell(errorResponse);
        }

        return this;
    }

    private List<ChatMessage> parseWhatsAppChat(List<String> lines) {
        List<ChatMessage> messages = new ArrayList<>();
        int messageCounter = 0;
        
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            
            ChatMessage message = parseMessage(line, messageCounter++);
            if (message != null) {
                messages.add(message);
            }
        }
        
        return messages;
    }

    private ChatMessage parseMessage(String line, int messageCounter) {
        // Debug: Print first few characters in hex to see Unicode issues
        if (messageCounter < 3) {
            System.out.printf("🔍 [DEBUG] Line %d: %s%n", messageCounter, 
                line.substring(0, Math.min(50, line.length())));
        }
        
        // Try different WhatsApp export patterns
        
        // Pattern 4: "[7/1/25, 11:31:28 PM] Name: message" (try this first as it's most likely)
        Matcher matcher4 = WHATSAPP_PATTERN_4.matcher(line);
        if (matcher4.matches()) {
            return createChatMessage(
                matcher4.group(1), // date
                matcher4.group(2) + " " + matcher4.group(3), // time + AM/PM
                matcher4.group(4), // sender
                matcher4.group(5), // content
                messageCounter
            );
        }
        
        // Pattern 1: "12/31/2023, 10:30 - John: Hello there"
        Matcher matcher1 = WHATSAPP_PATTERN_1.matcher(line);
        if (matcher1.matches()) {
            return createChatMessage(
                matcher1.group(1), // date
                matcher1.group(2), // time
                matcher1.group(3), // sender
                matcher1.group(4), // content
                messageCounter
            );
        }
        
        // Pattern 2: "[12/31/2023, 10:30:45] John: Hello there"
        Matcher matcher2 = WHATSAPP_PATTERN_2.matcher(line);
        if (matcher2.matches()) {
            return createChatMessage(
                matcher2.group(1), // date
                matcher2.group(2), // time
                matcher2.group(3), // sender
                matcher2.group(4), // content
                messageCounter
            );
        }
        
        // Pattern 3: "31-12-2024 10:30 - John: Hello there"
        Matcher matcher3 = WHATSAPP_PATTERN_3.matcher(line);
        if (matcher3.matches()) {
            return createChatMessage(
                matcher3.group(1), // date
                matcher3.group(2), // time
                matcher3.group(3), // sender
                matcher3.group(4), // content
                messageCounter
            );
        }
        
        // Debug: Show why line didn't match
        if (messageCounter < 5) {
            System.out.printf("❌ [DEBUG] No pattern matched for line %d%n", messageCounter);
        }
        
        // If no pattern matches, it might be a continuation of previous message
        // For simplicity, we'll skip these lines in this demo
        return null;
    }

    private ChatMessage createChatMessage(String dateStr, String timeStr, String sender, String content, int messageCounter) {
        try {
            String timestamp = dateStr + " " + timeStr;
            String messageId = "msg_" + messageCounter + "_" + System.currentTimeMillis();
            
            // Clean up sender name and content by removing invisible Unicode characters
            String cleanSender = sender.trim().replaceAll("\\p{C}", ""); // Remove control characters
            String cleanContent = content.trim().replaceAll("^\\p{C}+", ""); // Remove leading control characters
            
            // Skip system messages that are mostly invisible characters
            if (cleanContent.isEmpty() || cleanContent.length() < 3) {
                return null;
            }
            
            // Parse the date-time for search functionality
            LocalDateTime dateTime = parseDateTime(dateStr, timeStr);
            
            return new ChatMessage(timestamp, cleanSender, cleanContent, messageId, dateTime);
            
        } catch (Exception e) {
            System.out.println("⚠️ [PARSER] Could not parse message: " + e.getMessage());
            return null;
        }
    }

    private LocalDateTime parseDateTime(String dateStr, String timeStr) {
        try {
            // Handle different date formats
            DateTimeFormatter dateFormatter;
            String fullTimestamp = dateStr + " " + timeStr;
            
            if (dateStr.contains("-")) {
                // Format: 31-12-2024 10:30
                dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
            } else if (timeStr.contains("AM") || timeStr.contains("PM")) {
                // Format: 7/1/25 11:31:28 PM (2-digit year with AM/PM)
                // Handle both single and double digit months/days
                if (dateStr.matches("\\d{1,2}/\\d{1,2}/\\d{2}")) {
                    dateFormatter = DateTimeFormatter.ofPattern("M/d/yy h:mm:ss a");
                } else {
                    dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yyyy h:mm:ss a");
                }
            } else if (dateStr.matches("\\d{1,2}/\\d{1,2}/\\d{2}")) {
                // Format: 7/1/25 10:30 (2-digit year, 24-hour)
                dateFormatter = DateTimeFormatter.ofPattern("M/d/yy HH:mm");
            } else if (timeStr.length() > 5) {
                // Format: 12/31/2023 10:30:45 (with seconds)
                dateFormatter = DateTimeFormatter.ofPattern("M/d/yyyy HH:mm:ss");
            } else {
                // Format: 12/31/2023 10:30
                dateFormatter = DateTimeFormatter.ofPattern("M/d/yyyy HH:mm");
            }
            
            return LocalDateTime.parse(fullTimestamp, dateFormatter);
            
        } catch (Exception e) {
            // If parsing fails, return current time as fallback
            // Use context logger instead of System.out for cleaner output
            getContext().getLog().debug("Could not parse datetime: {} {}", dateStr, timeStr);
            return LocalDateTime.now();
        }
    }
}
