package com.akka.cluster.app;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.util.Timeout;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * WhatsAppQAClient provides a terminal-based interface for the WhatsApp Chat Q&A system.
 * Demonstrates user interaction with the distributed system using all three communication patterns.
 */
public class WhatsAppQAClient extends AbstractBehavior<WhatsAppQAClient.Command> {

    // Command messages
    public interface Command extends CborSerializable {}

    private static final class UserInput implements Command {
        public final String input;
        
        public UserInput(String input) {
            this.input = input;
        }
    }

    private static final class UploadCompleted implements Command {
        public final SessionManagerActor.UploadResponse response;
        
        public UploadCompleted(SessionManagerActor.UploadResponse response) {
            this.response = response;
        }
    }

    private static final class QuestionAnswered implements Command {
        public final SessionManagerActor.QuestionResponse response;
        
        public QuestionAnswered(SessionManagerActor.QuestionResponse response) {
            this.response = response;
        }
    }

    public static Behavior<Command> create() {
        return Behaviors.setup(WhatsAppQAClient::new);
    }

    private final ActorRef<SessionManagerActor.Command> sessionManager;
    private final String sessionId;
    private boolean chatLoaded = false;

    private WhatsAppQAClient(ActorContext<Command> context) {
        super(context);
        this.sessionManager = context.spawn(SessionManagerActor.create(), "session-manager");
        this.sessionId = "session_" + UUID.randomUUID().toString().substring(0, 8);
        
        System.out.println("🎮 WhatsApp Chat Q&A Client Started");
        System.out.println("=====================================");
        System.out.println("Session ID: " + sessionId);
        System.out.println("");
        
        startInteractiveSession();
    }

    @Override
    public Receive<Command> createReceive() {
        return newReceiveBuilder()
            .onMessage(UserInput.class, this::onUserInput)
            .onMessage(UploadCompleted.class, this::onUploadCompleted)
            .onMessage(QuestionAnswered.class, this::onQuestionAnswered)
            .build();
    }

    private void startInteractiveSession() {
        System.out.println("💬 Welcome to WhatsApp Chat Q&A Bot!");
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        System.out.println("");
        System.out.println("📋 Instructions:");
        System.out.println("  1. First upload a WhatsApp chat export (.txt file)");
        System.out.println("     Command: upload <file_path>");
        System.out.println("     Example: upload /path/to/your/whatsapp_chat.txt");
        System.out.println("");
        System.out.println("  2. Then ask questions about the conversation");
        System.out.println("     Command: ask <your_question>");
        System.out.println("     Example: ask When did we talk about the trip to Boston?");
        System.out.println("     Example: ask What did John say about the meeting?");
        System.out.println("");
        System.out.println("  3. Type 'help' for this guide, 'quit' to exit");
        System.out.println("");
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        
        // Start reading user input in a separate thread
        Thread inputThread = new Thread(() -> {
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("\n💬 WhatsApp Q&A> ");
                String input = scanner.nextLine().trim();
                
                if (input.equalsIgnoreCase("quit") || input.equalsIgnoreCase("exit")) {
                    System.out.println("👋 Goodbye! WhatsApp Q&A session ended.");
                    System.exit(0);
                }
                
                getContext().getSelf().tell(new UserInput(input));
            }
        });
        
        inputThread.setDaemon(true);
        inputThread.start();
    }

    private Behavior<Command> onUserInput(UserInput command) {
        String input = command.input.trim();
        
        if (input.isEmpty()) {
            return this;
        }
        
        if (input.equalsIgnoreCase("help")) {
            printHelp();
            return this;
        }
        
        if (input.startsWith("upload ")) {
            String filePath = input.substring(7).trim();
            if (filePath.isEmpty()) {
                System.out.println("❌ Please specify a file path. Example: upload /path/to/chat.txt");
                return this;
            }
            
            handleUpload(filePath);
            return this;
        }
        
        if (input.startsWith("ask ")) {
            String question = input.substring(4).trim();
            if (question.isEmpty()) {
                System.out.println("❌ Please ask a question. Example: ask What did John say?");
                return this;
            }
            
            if (!chatLoaded) {
                System.out.println("❌ Please upload a chat file first using 'upload <file_path>'");
                return this;
            }
            
            handleQuestion(question);
            return this;
        }
        
        // If no command matches, try to interpret as a question if chat is loaded
        if (chatLoaded) {
            handleQuestion(input);
        } else {
            System.out.println("❌ Unknown command. Type 'help' for instructions or 'upload <file_path>' to start.");
        }
        
        return this;
    }

    private void handleUpload(String filePath) {
        System.out.println("📤 Uploading chat file: " + filePath);
        System.out.println("🔄 Processing...");
        
        ActorRef<SessionManagerActor.UploadResponse> uploadAdapter = 
            getContext().messageAdapter(SessionManagerActor.UploadResponse.class, UploadCompleted::new);
        
        sessionManager.tell(new SessionManagerActor.UploadChatFile(
            sessionId, 
            filePath, 
            uploadAdapter
        ));
    }

    private Behavior<Command> onUploadCompleted(UploadCompleted message) {
        SessionManagerActor.UploadResponse response = message.response;
        
        if (response.success) {
            chatLoaded = true;
            System.out.println("✅ " + response.message);
            System.out.println("📊 Total messages indexed: " + response.totalMessages);
            System.out.println("");
            System.out.println("🎯 Ready to answer questions! Try asking:");
            System.out.println("   • When did we talk about [topic]?");
            System.out.println("   • What did [person] say about [topic]?");
            System.out.println("   • Show me messages about [keyword]");
        } else {
            System.out.println("❌ Upload failed: " + response.message);
            System.out.println("💡 Make sure the file path is correct and the file is a WhatsApp chat export");
        }
        
        return this;
    }

    private void handleQuestion(String question) {
        System.out.println("🔍 Processing your question: \"" + question + "\"");
        System.out.println("🔄 Searching chat and generating response...");
        
        long startTime = System.currentTimeMillis();
        
        ActorRef<SessionManagerActor.QuestionResponse> questionAdapter = 
            getContext().messageAdapter(SessionManagerActor.QuestionResponse.class, QuestionAnswered::new);
        
        sessionManager.tell(new SessionManagerActor.AskQuestion(
            sessionId, 
            question, 
            questionAdapter
        ));
    }

    private Behavior<Command> onQuestionAnswered(QuestionAnswered message) {
        SessionManagerActor.QuestionResponse response = message.response;
        
        if (response.success) {
            System.out.println("");
            System.out.println("✅ ANSWER FOUND");
            System.out.println("────────────────────────────────────────────────────────────────────────────────");
            System.out.println("🤖 " + response.answer);
            System.out.println("");
            
            if (!response.relevantMessages.isEmpty()) {
                System.out.println("📋 Relevant Chat Context (" + response.relevantMessages.size() + " messages):");
                System.out.println("────────────────────────────────────────────────────────────────────────────────");
                for (int i = 0; i < response.relevantMessages.size(); i++) {
                    System.out.println((i + 1) + ". " + response.relevantMessages.get(i));
                }
            }
            
            System.out.println("");
            System.out.println("📊 Processing time: " + response.processingTimeMs + "ms");
            System.out.println("────────────────────────────────────────────────────────────────────────────────");
        } else {
            System.out.println("❌ Error processing question: " + response.error);
        }
        
        return this;
    }

    private void printHelp() {
        System.out.println("");
        System.out.println("📋 WhatsApp Chat Q&A Bot - Help Guide");
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
        System.out.println("");
        System.out.println("📤 Upload Commands:");
        System.out.println("   upload <file_path>     - Upload WhatsApp chat export file");
        System.out.println("   Example: upload /Users/john/Downloads/whatsapp_chat.txt");
        System.out.println("");
        System.out.println("❓ Question Commands:");
        System.out.println("   ask <question>         - Ask about the uploaded chat");
        System.out.println("   <question>             - Direct question (when chat is loaded)");
        System.out.println("");
        System.out.println("📝 Sample Questions:");
        System.out.println("   • When did we talk about the trip to Boston?");
        System.out.println("   • What did John say about the meeting?");
        System.out.println("   • Show me messages about dinner plans");
        System.out.println("   • Who mentioned the project deadline?");
        System.out.println("   • What happened yesterday?");
        System.out.println("");
        System.out.println("🔧 System Commands:");
        System.out.println("   help                   - Show this help guide");
        System.out.println("   quit / exit            - Exit the application");
        System.out.println("");
        System.out.println("💡 Tips:");
        System.out.println("   • WhatsApp exports should be in .txt format");
        System.out.println("   • Export from WhatsApp: Chat > More > Export Chat > Without Media");
        System.out.println("   • Be specific in your questions for better results");
        System.out.println("   • The system uses AI to understand context and provide relevant answers");
        System.out.println("");
        System.out.println("══════════════════════════════════════════════════════════════════════════════");
    }

    // Main method for standalone execution
    public static void main(String[] args) {
        ActorSystem<Command> system = ActorSystem.create(
            WhatsAppQAClient.create(), 
            "WhatsAppQASystem"
        );
        
        // Keep the system running
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            system.terminate();
        }
    }
}
