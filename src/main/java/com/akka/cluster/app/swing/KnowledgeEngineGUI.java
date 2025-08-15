package com.akka.cluster.app.swing;

import com.akka.cluster.app.*;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import akka.util.Timeout;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

/**
 * Swing GUI Frontend for Akka Knowledge Engine
 * Provides rich desktop interface for WhatsApp chat analysis
 */
public class KnowledgeEngineGUI extends JFrame {
    
    private static final Color PRIMARY_COLOR = new Color(37, 99, 235);
    private static final Color SUCCESS_COLOR = new Color(34, 197, 94);
    private static final Color ERROR_COLOR = new Color(239, 68, 68);
    private static final Color BACKGROUND_COLOR = new Color(248, 250, 252);
    
    // Components
    private JTextArea chatDisplayArea;
    private JTextArea queryTextArea;  // Changed from JTextField to JTextArea
    private JButton uploadButton;
    private JButton queryButton;
    private JLabel statusLabel;
    private JProgressBar progressBar;
    private JTextArea resultsArea;
    private JLabel fileInfoLabel;
    private JList<String> historyList;
    private DefaultListModel<String> historyModel;
    
    // Akka Integration
    private AkkaSwingBridge akkaBridge;
    private String currentSessionId;
    private boolean chatLoaded = false;
    
    public KnowledgeEngineGUI() {
        initializeGUI();
        setupEventHandlers();
        initializeAkkaSystem();
    }
    
    private void initializeAkkaSystem() {
        try {
            akkaBridge = new AkkaSwingBridge();
            statusLabel.setText("🟢 Connected to Akka cluster - Ready to upload WhatsApp chat file");
        } catch (Exception e) {
            statusLabel.setText("🔴 Failed to connect to Akka cluster: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void initializeGUI() {
        setTitle("🤖 Akka Knowledge Engine - WhatsApp Chat Analyzer");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 800);
        setLocationRelativeTo(null);
        
        // Add window listener for cleanup
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                if (akkaBridge != null) {
                    akkaBridge.shutdown();
                }
                System.exit(0);
            }
        });
        
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
        
        // Top Panel - Header and Controls
        JPanel topPanel = createTopPanel();
        add(topPanel, BorderLayout.NORTH);
        
        // Center Panel - Split view
        JSplitPane centerSplit = createCenterPanel();
        add(centerSplit, BorderLayout.CENTER);
        
        // Bottom Panel - Status and Progress
        JPanel bottomPanel = createBottomPanel();
        add(bottomPanel, BorderLayout.SOUTH);
    }
    
    private JPanel createTopPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(PRIMARY_COLOR);
        panel.setBorder(new EmptyBorder(15, 20, 15, 20));
        
        // Title
        JLabel titleLabel = new JLabel("🤖 Akka Knowledge Engine");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 24));
        titleLabel.setForeground(Color.WHITE);
        
        // Upload section
        JPanel uploadPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        uploadPanel.setBackground(PRIMARY_COLOR);
        
        uploadButton = new JButton("📁 Upload WhatsApp Chat");
        uploadButton.setFont(new Font("Arial", Font.BOLD, 14));
        uploadButton.setBackground(Color.WHITE);
        uploadButton.setForeground(PRIMARY_COLOR);
        uploadButton.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        uploadButton.setFocusPainted(false);
        
        fileInfoLabel = new JLabel("No file selected");
        fileInfoLabel.setForeground(Color.WHITE);
        fileInfoLabel.setFont(new Font("Arial", Font.ITALIC, 12));
        
        uploadPanel.add(fileInfoLabel);
        uploadPanel.add(Box.createHorizontalStrut(10));
        uploadPanel.add(uploadButton);
        
        panel.add(titleLabel, BorderLayout.WEST);
        panel.add(uploadPanel, BorderLayout.EAST);
        
        return panel;
    }
    
    private JSplitPane createCenterPanel() {
        // Left Panel - Chat Display
        JPanel leftPanel = createChatDisplayPanel();
        
        // Right Panel - Query Interface
        JPanel rightPanel = createQueryPanel();
        
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
        splitPane.setDividerLocation(600);
        splitPane.setResizeWeight(0.5);
        
        return splitPane;
    }
    
    private JPanel createChatDisplayPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("📱 Chat Content"));
        
        chatDisplayArea = new JTextArea();
        chatDisplayArea.setEditable(false);
        chatDisplayArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        chatDisplayArea.setBackground(Color.WHITE);
        chatDisplayArea.setText("Upload a WhatsApp chat file to view messages here...");
        
        JScrollPane scrollPane = new JScrollPane(chatDisplayArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        
        panel.add(scrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createQueryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("💬 Ask Questions"));
        
        // Query input
        JPanel queryInputPanel = new JPanel(new BorderLayout(5, 5));
        queryInputPanel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        // Create scrollable text area for questions
        queryTextArea = new JTextArea(3, 30); // 3 rows, auto-width
        queryTextArea.setFont(new Font("Arial", Font.PLAIN, 14));
        queryTextArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        queryTextArea.setLineWrap(true);
        queryTextArea.setWrapStyleWord(true);
        queryTextArea.setEnabled(false);
        
        // Add placeholder text
        queryTextArea.setText("Enter your question here");
        queryTextArea.setForeground(Color.GRAY);
        
        // Add focus listeners for placeholder text
        queryTextArea.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override
            public void focusGained(java.awt.event.FocusEvent e) {
                if (queryTextArea.getText().equals("Enter your question here")) {
                    queryTextArea.setText("");
                    queryTextArea.setForeground(Color.BLACK);
                }
            }
            
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                if (queryTextArea.getText().trim().isEmpty()) {
                    queryTextArea.setText("Enter your question here");
                    queryTextArea.setForeground(Color.GRAY);
                }
            }
        });
        
        // Create scroll pane for the text area
        JScrollPane queryScrollPane = new JScrollPane(queryTextArea);
        queryScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        queryScrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        queryScrollPane.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(Color.LIGHT_GRAY),
            BorderFactory.createEmptyBorder(2, 2, 2, 2)
        ));
        
        queryButton = new JButton("🔍 Ask");
        queryButton.setFont(new Font("Arial", Font.BOLD, 14));
        queryButton.setBackground(PRIMARY_COLOR);
        queryButton.setForeground(Color.WHITE);
        queryButton.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        queryButton.setFocusPainted(false);
        queryButton.setEnabled(false);
        
        // Create a panel for the label and button
        JPanel labelButtonPanel = new JPanel(new BorderLayout());
        labelButtonPanel.add(new JLabel("Question:"), BorderLayout.NORTH);
        labelButtonPanel.add(queryButton, BorderLayout.SOUTH);
        
        queryInputPanel.add(labelButtonPanel, BorderLayout.WEST);
        queryInputPanel.add(queryScrollPane, BorderLayout.CENTER);
        
        // Create split pane for results and history
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        splitPane.setResizeWeight(0.7); // Give more space to results
        
        // Results area
        resultsArea = new JTextArea();
        resultsArea.setEditable(false);
        resultsArea.setFont(new Font("Arial", Font.PLAIN, 13));
        resultsArea.setBackground(Color.WHITE);
        resultsArea.setLineWrap(true);
        resultsArea.setWrapStyleWord(true);
        resultsArea.setText("Upload a chat file and ask questions to see AI-powered responses here...");
        
        JScrollPane resultsScroll = new JScrollPane(resultsArea);
        resultsScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        resultsScroll.setBorder(BorderFactory.createTitledBorder("🤖 AI Response"));
        
        // Question history panel
        JPanel historyPanel = createHistoryPanel();
        
        splitPane.setTopComponent(resultsScroll);
        splitPane.setBottomComponent(historyPanel);
        
        panel.add(queryInputPanel, BorderLayout.NORTH);
        panel.add(splitPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createHistoryPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder("📝 Recent Questions (Double-click to reuse)"));
        
        // Initialize history model and list
        historyModel = new DefaultListModel<>();
        historyList = new JList<>(historyModel);
        historyList.setFont(new Font("Arial", Font.PLAIN, 12));
        historyList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        
        // Add click listener to reuse questions
        historyList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) { // Double-click
                    String selectedQuestion = historyList.getSelectedValue();
                    if (selectedQuestion != null && queryTextArea.isEnabled()) {
                        queryTextArea.setText(selectedQuestion);
                        queryTextArea.requestFocus();
                    }
                }
            }
        });
        
        JScrollPane historyScroll = new JScrollPane(historyList);
        historyScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        historyScroll.setPreferredSize(new Dimension(0, 120));
        
        // Add clear history button
        JButton clearHistoryButton = new JButton("🗑️ Clear All History");
        clearHistoryButton.setFont(new Font("Arial", Font.PLAIN, 11));
        clearHistoryButton.setToolTipText("Clear question history");
        clearHistoryButton.addActionListener(e -> {
            historyModel.clear();
            if (akkaBridge != null) {
                akkaBridge.clearAllHistory();
            }
        });
        
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(clearHistoryButton);
        
        panel.add(historyScroll, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        
        return panel;
    }
    
    private JPanel createBottomPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(10, 20, 10, 20));
        panel.setBackground(BACKGROUND_COLOR);
        
        statusLabel = new JLabel("🔄 Initializing Akka cluster connection...");
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 12));
        
        progressBar = new JProgressBar();
        progressBar.setStringPainted(true);
        progressBar.setString("Waiting...");
        progressBar.setVisible(false);
        
        panel.add(statusLabel, BorderLayout.WEST);
        panel.add(progressBar, BorderLayout.CENTER);
        
        return panel;
    }
    
    private void styleComponents() {
        // Add hover effects and styling
        addHoverEffect(uploadButton, Color.WHITE, new Color(240, 240, 240));
        addHoverEffect(queryButton, PRIMARY_COLOR, PRIMARY_COLOR.darker());
    }
    
    private void addHoverEffect(JButton button, Color normalColor, Color hoverColor) {
        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setBackground(hoverColor);
            }
            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setBackground(normalColor);
            }
        });
    }
    
    private void setupEventHandlers() {
        // File upload handler
        uploadButton.addActionListener(e -> handleFileUpload());
        
        // Query handler
        queryButton.addActionListener(e -> handleQuery());
        
        // Enter key for query (Ctrl+Enter to submit in multi-line)
        queryTextArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER && e.isControlDown()) {
                    e.consume(); // Prevent new line
                    handleQuery();
                }
            }
        });
    }
    
    private void handleFileUpload() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileNameExtensionFilter("Text files", "txt"));
        fileChooser.setDialogTitle("Select WhatsApp Chat Export File");
        
        if (fileChooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            uploadChatFile(selectedFile);
        }
    }
    
    private void uploadChatFile(File file) {
        // Show progress
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        progressBar.setString("Processing chat file with Akka cluster...");
        statusLabel.setText("🔄 Uploading to Akka cluster: " + file.getName());
        
        // Disable UI during processing
        uploadButton.setEnabled(false);
        queryButton.setEnabled(false);
        queryTextArea.setEnabled(false);
        
        // Use Akka cluster for real processing
        akkaBridge.uploadChatFile(file.getAbsolutePath(), result -> {
            SwingUtilities.invokeLater(() -> {
                if (result.success) {
                    // Success - update UI
                    progressBar.setVisible(false);
                    statusLabel.setText("✅ Chat processed by Akka cluster! Messages: " + result.messagesProcessed);
                    fileInfoLabel.setText("📁 " + file.getName());
                    
                    // Store session ID for queries
                    currentSessionId = result.sessionId;
                    
                    // Enable query interface
                    queryTextArea.setEnabled(true);
                    queryButton.setEnabled(true);
                    uploadButton.setEnabled(true);
                    chatLoaded = true;
                    
                    // Update chat display with real results
                    chatDisplayArea.setText(String.format(
                        "📱 WhatsApp Chat: %s\n\n" +
                        "✅ Processed by Akka Knowledge Engine Cluster\n" +
                        "� Messages parsed: %d\n" +
                        "🆔 Session ID: %s\n" +
                        "🤖 AI-powered analysis ready!\n\n" +
                        "💬 Ask questions about the conversation:\n" +
                        "• Who mentioned [topic]?\n" +
                        "• When did we discuss [subject]?\n" +
                        "• What did [person] say about [topic]?\n" +
                        "• Summarize the conversation\n" +
                        "• What are the main themes?",
                        file.getName(), result.messagesProcessed, result.sessionId
                    ));
                    
                    queryTextArea.requestFocus();
                } else {
                    // Error - show failure
                    progressBar.setVisible(false);
                    statusLabel.setText("❌ Akka cluster processing failed: " + result.error);
                    
                    uploadButton.setEnabled(true);
                    
                    chatDisplayArea.setText("❌ Error processing file with Akka cluster:\n" + result.error);
                }
            });
        });
    }
    
    private void handleQuery() {
        String question = queryTextArea.getText().trim();
        
        if (question.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please enter a question!", "Empty Query", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        if (!chatLoaded) {
            JOptionPane.showMessageDialog(this, "Please upload a chat file first!", "No Chat Loaded", JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // Show processing
        progressBar.setVisible(true);
        progressBar.setIndeterminate(true);
        progressBar.setString("Akka cluster generating AI response...");
        statusLabel.setText("🤖 Processing with Akka LLM: " + question);
        
        queryButton.setEnabled(false);
        queryTextArea.setEnabled(false);
        
        // Use Akka cluster for real AI processing
        akkaBridge.processQuery(currentSessionId, question, result -> {
            SwingUtilities.invokeLater(() -> {
                try {
                    if (result.success) {
                        // Display real AI response from Akka cluster
                        resultsArea.setText(result.response);
                        
                        // Update question history display
                        updateHistoryDisplay();
                        
                        // Clear and re-enable input
                        queryTextArea.setText("");
                        queryTextArea.setEnabled(true);
                        queryButton.setEnabled(true);
                        
                        progressBar.setVisible(false);
                        statusLabel.setText(String.format(
                            "✅ Akka cluster response complete! ⏱️ %dms 📊 %d contexts",
                            result.responseTimeMs, result.contextCount
                        ));
                        
                        queryTextArea.requestFocus();
                    } else {
                        // Error from Akka cluster
                        resultsArea.setText("❌ Akka cluster error: " + result.error);
                        statusLabel.setText("❌ Query processing failed");
                        
                        queryTextArea.setEnabled(true);
                        queryButton.setEnabled(true);
                        progressBar.setVisible(false);
                    }
                } catch (Exception e) {
                    resultsArea.setText("❌ Error processing Akka response: " + e.getMessage());
                    statusLabel.setText("❌ Error occurred");
                    
                    queryTextArea.setEnabled(true);
                    queryButton.setEnabled(true);
                    progressBar.setVisible(false);
                }
            });
        });
    }
    
    /**
     * Update the history display with recent questions from the bridge
     */
    private void updateHistoryDisplay() {
        if (akkaBridge != null && historyModel != null) {
            SwingUtilities.invokeLater(() -> {
                historyModel.clear();
                for (String question : akkaBridge.getQuestionHistory()) {
                    historyModel.addElement(question);
                }
            });
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new KnowledgeEngineGUI().setVisible(true);
        });
    }
}
