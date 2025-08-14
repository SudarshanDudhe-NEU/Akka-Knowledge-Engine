# 💬 WhatsApp Chat Contextual Q&A Bot - Implementation Guide

## 📋 Project Overview

This project extends the existing Akka Cluster LLM integration to create a **WhatsApp Chat Contextual Q&A Bot**. The system allows users to upload WhatsApp chat exports and ask natural language questions about the conversations, demonstrating advanced distributed computing concepts with real-world AI integration.

## 🏗️ System Architecture

### Core Components

The system is built on **Akka Cluster** with distributed processing across multiple specialized nodes:

- **ChatParserActor**: Parses WhatsApp chat export files into structured data
- **RAGRetrieverActor**: Performs semantic search and context retrieval
- **LLMActor**: Generates intelligent responses using Perplexity API
- **SessionManagerActor**: Orchestrates the complete workflow
- **WhatsAppLoggerActor**: Records all activities and Q&A sessions
- **WhatsAppQAClient**: Interactive terminal-based user interface

### Communication Patterns Demonstrated

1. **TELL Pattern (Fire-and-Forget)**

   - File upload initiation
   - Activity logging
   - Asynchronous notifications

2. **ASK Pattern (Request-Response)**

   - Context retrieval from RAG system
   - LLM query processing
   - Session state management

3. **FORWARD Pattern (Message Forwarding)**
   - Logging activities across multiple destinations
   - Message routing through the cluster
   - Preserving original sender context

## 🚀 Quick Start

### Prerequisites

- **Java 11** or higher
- **Maven 3.6** or higher
- **Perplexity API Key** (optional - system works with mock responses)
- **WhatsApp chat export** (.txt format)

### 1. Export WhatsApp Chat

From WhatsApp:

1. Open the chat you want to analyze
2. Tap **More** (⋯) → **Export Chat**
3. Select **Without Media**
4. Save the .txt file

### 2. Start the System

```bash
# Start the complete WhatsApp Q&A system
./start-whatsapp-qa.sh
```

This will start:

- Seed node (cluster coordination)
- LLM node (AI processing)
- Router node (message forwarding)
- WhatsApp Q&A client (user interface)

### 3. Upload and Query

Once the client starts:

```bash
# Upload your WhatsApp chat
💬 WhatsApp Q&A> upload /path/to/your/whatsapp_chat.txt

# Ask questions about the chat
💬 WhatsApp Q&A> ask When did we talk about the trip to Boston?
💬 WhatsApp Q&A> ask What did John say about the meeting?
💬 WhatsApp Q&A> What happened yesterday?
```

## 🔄 Workflow Demonstration

### 1. Chat Upload (TELL Pattern)

```
User → SessionManager → ChatParser → RAGRetriever → User
       ↓ (TELL)
   WhatsAppLogger
```

### 2. Question Processing (ASK Pattern)

```
User → SessionManager ←ASK→ RAGRetriever ←ASK→ LLMActor → User
       ↓ (TELL)
   WhatsAppLogger
```

### 3. Logging (FORWARD Pattern)

```
Any Actor → WhatsAppLogger → [File, Console, Other Loggers]
```

## 📝 Sample Queries

The system can answer various types of questions:

### Temporal Queries

- "When did we talk about the trip to Boston?"
- "What happened yesterday?"
- "Show me messages from last week"

### Person-Specific Queries

- "What did John say about the meeting?"
- "Who mentioned the project deadline?"
- "What did Sarah bring to dinner?"

### Topic-Based Queries

- "Tell me about the dinner plans"
- "What was discussed about the work project?"
- "Show me messages about the New Year party"

### General Content Search

- "Find messages about restaurants"
- "What was the magic show about?"
- "When is the deadline?"

## 🎯 Features Demonstrated

### 1. Distributed Processing

- Multiple specialized nodes working together
- Fault tolerance and cluster membership management
- Service discovery and actor communication

### 2. Real-World Integration

- WhatsApp chat format parsing
- LLM integration with structured responses
- Semantic search and context retrieval

### 3. Advanced Akka Patterns

- All three communication patterns (TELL, ASK, FORWARD)
- Actor supervision and lifecycle management
- Message serialization with CBOR

### 4. Production-Ready Concepts

- Session management
- Comprehensive logging and auditing
- Error handling and graceful degradation
- Scalable architecture design

## 📁 File Structure

```
akka-clusters/
├── src/main/java/com/akka/cluster/demo/
│   ├── ChatParserActor.java           # Parses WhatsApp chat exports
│   ├── RAGRetrieverActor.java         # Semantic search and context retrieval
│   ├── SessionManagerActor.java       # Workflow orchestration
│   ├── WhatsAppLoggerActor.java       # Specialized logging for Q&A
│   ├── WhatsAppQAClient.java          # Interactive user interface
│   ├── LLMActor.java                  # Existing LLM integration
│   ├── ClusterApp.java                # Updated with new node types
│   └── [other existing files]
├── start-whatsapp-qa.sh              # System startup script
├── sample_whatsapp_chat.txt          # Sample chat for testing
└── WHATSAPP-QA-GUIDE.md             # This documentation
```

## 🧪 Testing the System

### 1. Basic Functionality Test

```bash
# Start the system
./start-whatsapp-qa.sh

# Upload the sample chat
upload sample_whatsapp_chat.txt

# Test different query types
ask When did we talk about the trip to Boston?
ask What did John say about the meeting?
ask Who mentioned the magic show?
```

### 2. Communication Pattern Verification

Watch the console output to see:

- **TELL**: Immediate logging messages
- **ASK**: Request-response cycles with timing
- **FORWARD**: Message routing through the cluster

### 3. Error Handling

Test error scenarios:

- Upload non-existent file
- Ask questions before uploading
- Network failures (API unavailable)

## 📊 Performance Metrics

The system tracks and displays:

- **Processing Time**: End-to-end query response time
- **Context Relevance**: Number of relevant messages found
- **Success Rate**: Query processing success/failure ratio
- **Session Activity**: Complete audit trail of all actions

## 🔧 Configuration

### Environment Variables

```bash
# Optional: Enable real LLM responses
export PERPLEXITY_API_KEY="your-api-key-here"

# Default: System works with intelligent mock responses
```

### Port Configuration

- **Seed Node**: 2551
- **LLM Node**: 2555
- **Router Node**: 2557
- **WhatsApp Q&A Client**: 2560

## 🎓 Educational Value

This project demonstrates:

### 1. Distributed Systems Concepts

- **Microservices Architecture**: Specialized actors for specific responsibilities
- **Fault Tolerance**: Cluster membership and failure detection
- **Scalability**: Horizontal scaling across nodes
- **Load Distribution**: Work sharing across cluster members

### 2. Modern AI Integration

- **RAG (Retrieval-Augmented Generation)**: Context-aware AI responses
- **Semantic Search**: Intelligent content matching
- **LLM Integration**: Real API communication with structured responses
- **Contextual Processing**: Understanding conversation flows

### 3. Software Engineering Best Practices

- **Actor Model**: Message-driven architecture
- **Pattern Implementation**: All three Akka communication patterns
- **Error Handling**: Graceful degradation and recovery
- **Logging and Monitoring**: Comprehensive activity tracking

## 🚀 Next Steps

### Potential Enhancements

1. **Advanced NLP**: Better semantic understanding
2. **Multi-Chat Support**: Handle multiple chat files simultaneously
3. **Web Interface**: Replace terminal with web UI
4. **Real-time Processing**: Live chat monitoring
5. **Analytics Dashboard**: Query patterns and insights
6. **Export Features**: Save Q&A sessions and summaries

### Production Considerations

1. **Security**: Encrypt chat data and API keys
2. **Performance**: Optimize large chat file processing
3. **Storage**: Persistent chat indexing with databases
4. **Monitoring**: Health checks and metrics collection
5. **Deployment**: Container orchestration and cloud deployment

## 🎯 Success Criteria

✅ **Architecture**: Multi-node Akka cluster with specialized actors  
✅ **Communication**: All three patterns (TELL, ASK, FORWARD) demonstrated  
✅ **Functionality**: Complete WhatsApp Q&A workflow operational  
✅ **Integration**: Real LLM API integration with fallback  
✅ **Documentation**: Comprehensive setup and usage guide  
✅ **Testing**: Sample data and test scenarios provided

---

**This project successfully demonstrates advanced distributed computing concepts while solving a practical real-world problem: making chat conversations searchable and queryable through natural language.**
