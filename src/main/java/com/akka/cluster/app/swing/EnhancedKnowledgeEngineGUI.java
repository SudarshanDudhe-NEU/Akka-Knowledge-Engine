package com.akka.cluster.app.swing;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;

/**
 * Enhanced Swing GUI with full Akka Integration
 * Professional desktop interface for the Akka Knowledge Engine
 */
public class EnhancedKnowledgeEngineGUI extends JFrame {
    
    private static final Color PRIMARY_COLOR = new Color(37, 99, 235);
    private static final Color SUCCESS_COLOR = new Color(34, 197, 94);
    private static final Color ERROR_COLOR = new Color(239, 68, 68);
    private static final Color BACKGROUND_COLOR = new Color(248, 250, 252);
    
    // Components
    private JTextArea chatDisplayArea;
    private JTextField queryField;
    private JButton uploadButton;
    private JButton queryButton;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private JTextArea resultsArea;
    private JLabel fileInfoLabel;
    private JLabel metricsLabel;
    
    // Akka Integration
    private AkkaSwingBridge akkaBridge;
    private String currentSessionId;
    private boolean chatLoaded = false;
    private int totalMessages = 0;
    
    public EnhancedKnowledgeEngineGUI() {
        initializeAkka();
        initializeGUI();
        setupEventHandlers();
    }
    
    private void initializeAkka() {
        akkaBridge = new AkkaSwingBridge();
        
        // Handle window closing to shutdown Akka properly
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                akkaBridge.shutdown();
                System.exit(0);
            }
        });
    }
    
    private void initializeGUI() {
        setTitle("🤖 Akka Knowledge Engine - WhatsApp Chat Analyzer");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1400, 900);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(1000, 600));
        
        // Set modern look and feel
        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception e) {
            // If Nimbus is not available, fall back to default
            e.printStackTrace();
        }
        
        createMainLayout();
        styleComponents();
    }
    
    private void createMainLayout() {
        setLayout(new BorderLayout());
        getContentPane().setBackground(BACKGROUND_COLOR);
        
        // Header
        add(createHeaderPanel(), BorderLayout.NORTH);
        
        // Main content
        add(createMainContentPanel(), BorderLayout.CENTER);
        
        // Footer
        add(createFooterPanel(), BorderLayout.SOUTH);
    }
    
    private JPanel createHeaderPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(PRIMARY_COLOR);
        panel.setBorder(new EmptyBorder(20, 25, 20, 25));
        
        // Left side - Title and description
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setBackground(PRIMARY_COLOR);
        
        JLabel titleLabel = new JLabel("🤖 Akka Knowledge Engine");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 28));
        titleLabel.setForeground(Color.WHITE);
        
        JLabel subtitleLabel = new JLabel("WhatsApp Chat Analysis with Distributed AI Processing");
        subtitleLabel.setFont(new Font("Arial", Font.PLAIN, 14));
        subtitleLabel.setForeground(new Color(200, 220, 255));
        
        leftPanel.add(titleLabel, BorderLayout.NORTH);
        leftPanel.add(subtitleLabel, BorderLayout.SOUTH);
        
        // Right side - Upload controls
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightPanel.setBackground(PRIMARY_COLOR);
        
        fileInfoLabel = new JLabel("No file selected");
        fileInfoLabel.setForeground(new Color(200, 220, 255));
        fileInfoLabel.setFont(new Font("Arial", Font.ITALIC, 13));
        
        uploadButton = new JButton("📁 Upload WhatsApp Chat");
        uploadButton.setFont(new Font("Arial", Font.BOLD, 14));
        uploadButton.setBackground(Color.WHITE);
        uploadButton.setForeground(PRIMARY_COLOR);
        uploadButton.setBorder(BorderFactory.createEmptyBorder(12, 25, 12, 25));
        uploadButton.setFocusPainted(false);
        uploadButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        rightPanel.add(fileInfoLabel);
        rightPanel.add(Box.createHorizontalStrut(15));
        rightPanel.add(uploadButton);
        
        panel.add(leftPanel, BorderLayout.WEST);
        panel.add(rightPanel, BorderLayout.EAST);
        
        return panel;
    }
    
    private JPanel createMainContentPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(20, 25, 10, 25));
        panel.setBackground(BACKGROUND_COLOR);
        
        // Create three-panel layout
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        mainSplit.setDividerLocation(450);
        mainSplit.setResizeWeight(0.35);
        
        // Left: Chat display
        mainSplit.setLeftComponent(createChatDisplayPanel());
        
        // Right: Query interface
        mainSplit.setRightComponent(createQueryInterfacePanel());
        
        panel.add(mainSplit, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createChatDisplayPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.LIGHT_GRAY), 
            "📱 Chat Content Preview"
        ));
        panel.setBackground(Color.WHITE);
        
        chatDisplayArea = new JTextArea();
        chatDisplayArea.setEditable(false);
        chatDisplayArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        chatDisplayArea.setBackground(new Color(250, 250, 250));
        chatDisplayArea.setMargin(new Insets(10, 10, 10, 10));
        chatDisplayArea.setText(
            "🔄 Waiting for WhatsApp chat file...\n\n" +
            "Upload a WhatsApp chat export (.txt) to:\n" +
            "• View message content\n" +
            "• Analyze conversation patterns\n" +
            "• Ask AI-powered questions\n\n" +
            "Supported formats:\n" +
            "✅ [7/1/25, 11:31:28 PM] Name: message\n" +
            "✅ 12/31/2023, 10:30 - Name: message\n" +
            "✅ Various WhatsApp export formats"
        );
        
        JScrollPane scrollPane = new JScrollPane(chatDisplayArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createQueryInterfacePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createLineBorder(Color.LIGHT_GRAY), 
            "💬 AI-Powered Chat Analysis"
        ));
        panel.setBackground(Color.WHITE);
        
        // Top: Query input
        JPanel queryPanel = createQueryInputPanel();
        
        // Center: Results display
        JPanel resultsPanel = createResultsPanel();
        
        // Bottom: Metrics
        JPanel metricsPanel = createMetricsPanel();
        
        panel.add(queryPanel, BorderLayout.NORTH);
        panel.add(resultsPanel, BorderLayout.CENTER);
        panel.add(metricsPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createQueryInputPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));
        panel.setBackground(Color.WHITE);
        
        JLabel queryLabel = new JLabel("Ask a question about the chat:");
        queryLabel.setFont(new Font("Arial", Font.BOLD, 13));
        
        queryField = new JTextField();
        queryField.setFont(new Font("Arial", Font.PLAIN, 14));
        queryField.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(200, 200, 200)),
            BorderFactory.createEmptyBorder(10, 15, 10, 15)
        ));
        queryField.setEnabled(false);
        queryField.setToolTipText("Upload a chat file first to enable queries");
        
        queryButton = new JButton("🤖 Analyze with AI");
        queryButton.setFont(new Font("Arial", Font.BOLD, 13));
        queryButton.setBackground(SUCCESS_COLOR);
        queryButton.setForeground(Color.WHITE);
        queryButton.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        queryButton.setFocusPainted(false);
        queryButton.setEnabled(false);
        queryButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        
        JPanel inputPanel = new JPanel(new BorderLayout(10, 0));
        inputPanel.setBackground(Color.WHITE);
        inputPanel.add(queryField, BorderLayout.CENTER);
        inputPanel.add(queryButton, BorderLayout.EAST);
        
        panel.add(queryLabel, BorderLayout.NORTH);
        panel.add(inputPanel, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createResultsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(0, 15, 10, 15));
        panel.setBackground(Color.WHITE);
        
        resultsArea = new JTextArea();
        resultsArea.setEditable(false);
        resultsArea.setFont(new Font("Arial", Font.PLAIN, 13));
        resultsArea.setBackground(new Color(252, 252, 252));
        resultsArea.setLineWrap(true);
        resultsArea.setWrapStyleWord(true);
        resultsArea.setMargin(new Insets(15, 15, 15, 15));
        resultsArea.setText(
            "🎯 AI Response Area\n\n" +
            "Upload a WhatsApp chat file and ask questions to see:\n\n" +
            "🤖 AI-powered analysis and responses\n" +
            "📊 Context-aware search results\n" +
            "⚡ Real-time processing via Akka cluster\n" +
            "🔍 Semantic similarity matching\n" +
            "📈 Performance metrics and insights\n\n" +
            "Example questions:\n" +
            "• \"Who mentioned security deposit?\"\n" +
            "• \"What arrangements were discussed?\"\n" +
            "• \"When did we talk about moving?\""
        );
        
        JScrollPane scrollPane = new JScrollPane(resultsArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        scrollPane.setBorder(BorderFactory.createLineBorder(new Color(230, 230, 230)));
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createMetricsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(10, 15, 15, 15));
        panel.setBackground(Color.WHITE);
        
        metricsLabel = new JLabel("📊 Ready for analysis");
        metricsLabel.setFont(new Font("Arial", Font.ITALIC, 12));
        metricsLabel.setForeground(Color.GRAY);
        
        panel.add(metricsLabel, BorderLayout.WEST);
        
        return panel;
    }
    
    private JPanel createFooterPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(240, 240, 240));
        panel.setBorder(new EmptyBorder(15, 25, 15, 25));
        
        statusLabel = new JLabel("🔄 Ready to upload WhatsApp chat file");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 13));
        
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Waiting...");
        progressBar.setVisible(false);
        progressBar.setPreferredSize(new Dimension(300, 20));
        
        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightPanel.setBackground(new Color(240, 240, 240));
        rightPanel.add(progressBar);
        
        panel.add(statusLabel, BorderLayout.WEST);
        panel.add(rightPanel, BorderLayout.EAST);
        
        return panel;
    }
    
    private void styleComponents() {
        // Add hover effects
        addHoverEffect(uploadButton, Color.WHITE, new Color(245, 245, 245));
        addHoverEffect(queryButton, SUCCESS_COLOR, SUCCESS_COLOR.darker());
    }
    
    private void addHoverEffect(JButton button, Color normalColor, Color hoverColor) {
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                if (button.isEnabled()) {
                    button.setBackground(hoverColor);
                }
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(normalColor);
            }
        });
    }
    
    private void setupEventHandlers() {
        uploadButton.addActionListener(e -> handleFileUpload());
        queryButton.addActionListener(e -> handleQuery());
        queryField.addActionListener(e -> handleQuery());
    }
    
    private void handleFileUpload() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("WhatsApp Chat Files (*.txt)", "txt"));
        fileChooser.setDialogTitle("Select WhatsApp Chat Export File");
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            uploadChatFile(selectedFile);
        }
    }
    
    private void uploadChatFile(File file) {
        // Update UI for processing
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        progressBar.setString("🔄 Processing with Akka cluster...");
        statusLabel.setText("📤 Uploading: " + file.getName());
        
        uploadButton.setEnabled(false);
        queryButton.setEnabled(false);
        queryField.setEnabled(false);
        
        // Use Akka bridge to process file
        akkaBridge.uploadChatFile(file.getAbsolutePath(), result -> {
            SwingUtilities.invokeLater(() -> {
                progressBar.setVisible(false);
                
                if (result.success) {
                    // Success
                    currentSessionId = result.sessionId;
                    totalMessages = result.messagesProcessed;
                    chatLoaded = true;
                    
                    statusLabel.setText("✅ Chat processed successfully: " + file.getName());
                    fileInfoLabel.setText("📁 " + file.getName() + " (" + totalMessages + " messages)");
                    
                    chatDisplayArea.setText(String.format(
                        "✅ Chat Analysis Complete\n\n" +
                        "📱 File: %s\n" +
                        "💬 Messages processed: %d\n" +
                        "🔗 Session ID: %s\n" +
                        "⚡ Processed by Akka cluster\n\n" +
                        "🤖 AI Analysis Ready!\n" +
                        "The chat has been indexed and is ready for intelligent querying.\n\n" +
                        "🎯 Try asking questions like:\n" +
                        "• \"Who mentioned security deposit?\"\n" +
                        "• \"What payment arrangements were discussed?\"\n" +
                        "• \"When did we talk about moving?\"",
                        file.getName(), totalMessages, currentSessionId
                    ));
                    
                    // Enable query interface
                    queryField.setEnabled(true);
                    queryButton.setEnabled(true);
                    queryField.setToolTipText("Ask questions about the uploaded chat");
                    queryField.requestFocus();
                    
                    metricsLabel.setText(String.format("📊 %d messages indexed | Ready for AI analysis", totalMessages));
                    
                } else {
                    // Error
                    statusLabel.setText("❌ Error processing file");
                    JOptionPane.showMessageDialog(this, 
                        "Error processing chat file:\n" + result.error, 
                        "Processing Error", 
                        JOptionPane.ERROR_MESSAGE);
                }
                
                uploadButton.setEnabled(true);
            });
        });
    }
    
    private void handleQuery() {
        String question = queryField.getText().trim();
        
        if (question.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a question!", "Empty Query", JOptionPane.WARNING_MESSAGE);
            queryField.requestFocus();
            return;
        }
        
        if (!chatLoaded) {
            JOptionPane.showMessageDialog(this, "Please upload a chat file first!", "No Chat Loaded", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Update UI for processing
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        progressBar.setString("🤖 AI processing via Akka cluster...");
        statusLabel.setText("🧠 Analyzing: " + question);
        
        queryButton.setEnabled(false);
        queryField.setEnabled(false);
        
        // Process query through Akka
        akkaBridge.processQuery(currentSessionId, question, result -> {
            SwingUtilities.invokeLater(() -> {
                progressBar.setVisible(false);
                
                if (result.success) {
                    // Display results
                    resultsArea.setText(result.response);
                    
                    metricsLabel.setText(String.format(
                        "📊 Query processed | %d context messages | %d ms response time", 
                        result.contextCount, result.responseTimeMs
                    ));
                    
                    statusLabel.setText("✅ Question answered successfully!");
                    
                } else {
                    // Error
                    resultsArea.setText("❌ Error processing query:\n\n" + result.error);
                    statusLabel.setText("❌ Query failed");
                }
                
                // Re-enable interface
                queryField.setText("");
                queryField.setEnabled(true);
                queryButton.setEnabled(true);
                queryField.requestFocus();
            });
        });
    }
    
    public static void main(String[] args) {
        // Set system properties for better rendering
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        
        SwingUtilities.invokeLater(() -> {
            try {
                new EnhancedKnowledgeEngineGUI().setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, 
                    "Error starting application: " + e.getMessage(), 
                    "Startup Error", 
                    JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
