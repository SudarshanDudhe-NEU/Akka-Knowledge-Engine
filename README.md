# Akka Cluster with LLM Integration - CSYE7374 Final Project

## 🎊 **PROJECT STATUS: COMPLETED & ENHANCED** ✅

**Enhanced WhatsApp Q&A System Successfully Delivered!**

- ✅ **85% parsing success rate** (66/78 messages vs 0 before)
- ✅ **Advanced semantic search** with multi-factor scoring
- ✅ **Production-ready architecture** with comprehensive error handling
- ✅ **Unicode-aware parsing** supporting international chat formats
- ✅ **Real AI integration** with Perplexity API

📄 **See [PROJECT_COMPLETION_SUMMARY.md](PROJECT_COMPLETION_SUMMARY.md)** for detailed achievements.

🚀 **Quick Start**: `./quick-start.sh` (Interactive menu for easy access)

---

This project demonstrates **Akka Cluster** functionality with **Large Language Model (LLM) integration** for the CSYE7374 course. It showcases distributed computing concepts, all three Akka communication patterns, and real-world AI integration.

## 🎯 Learning Objectives

After running this demo, students will understand:

- How to create and configure Akka cluster nodes
- All three Akka communication patterns: **TELL**, **ASK**, and **FORWARD**
- Cluster membership lifecycle (joining, leaving, failure detection)
- Integration with external LLM services (Perplexity API)
- Message routing and forwarding in distributed systems
- Fault tolerance and cluster-aware behavior

## 🏗️ Architecture Overview

The cluster consists of specialized nodes working together:

- **Seed Node** (port 2551): Bootstraps the cluster and provides membership management
- **LLM Node** (port 2555): Handles AI queries using Perplexity API
- **Query Handler** (port 2556): Processes user queries and coordinates responses
- **Router Node** (port 2557): Routes messages and demonstrates FORWARD pattern
- **Interactive Client**: Command-line interface for real-time LLM interaction

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│ Seed Node   │    │ LLM Node    │    │Query Handler│
│ :2551       │◄──►│ :2555       │◄──►│ :2556       │
│             │    │🤖 Perplexity│    │👤 User Mgmt │
└─────────────┘    └─────────────┘    └─────────────┘
       ▲                   ▲                   ▲
       │                   │                   │
       └───────────────────┼───────────────────┘
                           │
                  ┌─────────────┐
                  │ Router Node │
                  │ :2557       │
                  │🌐 FORWARD   │
                  └─────────────┘
```

## 🚀 Quick Start

### Prerequisites

- **Java 11** or higher
- **Maven 3.6** or higher
- **Perplexity API Key** (optional - simulated responses if not provided)
- **macOS** (tested on macOS, but should work on other Unix systems)

### 1. Clone and Build

```bash
cd /path/to/your/workspace
mvn clean compile
```

### 2. Set API Key (Optional)

```bash
export PERPLEXITY_API_KEY="your-api-key-here"
```

### 3. Start Essential Cluster

```bash
./start-cluster.sh
```

This starts the core cluster nodes for LLM integration.

### 4. Start Interactive LLM Client

```bash
./ask-queries.sh
```

Or manually:

```bash
mvn exec:java -Dexec.mainClass="com.akka.cluster.demo.ClusterApp" -Dexec.args="interactive"
```

### 5. Manual Node Startup (Advanced)

Start individual nodes in separate terminals:

```bash
# Terminal 1 - Seed node (always start first)
mvn exec:java -Dexec.mainClass="com.akka.cluster.demo.ClusterApp" -Dexec.args="seed"

# Terminal 2 - LLM service
mvn exec:java -Dexec.mainClass="com.akka.cluster.demo.ClusterApp" -Dexec.args="llm 2555"

# Terminal 3 - Query handler
mvn exec:java -Dexec.mainClass="com.akka.cluster.demo.ClusterApp" -Dexec.args="query-handler 2556"

# Terminal 4 - Router
mvn exec:java -Dexec.mainClass="com.akka.cluster.demo.ClusterApp" -Dexec.args="router 2557"

# Terminal 5 - Interactive client
mvn exec:java -Dexec.mainClass="com.akka.cluster.demo.ClusterApp" -Dexec.args="interactive"
```

## 🔄 Communication Patterns Demonstrated

### 1. TELL Pattern (Fire-and-Forget)

```java
// Fire-and-forget message to logging
loggingActor.tell(new LogMessage("Query processed", "INFO"));
```

### 2. ASK Pattern (Request-Response)

```java
// Request-response with Future
Future<LLMResponse> response =
    AskPattern.ask(llmActor, new LLMQuery(question), timeout, scheduler);
```

### 3. FORWARD Pattern (Message Forwarding)

```java
// Forward message to another actor
getContext().getSelf().forward(message, getContext());
## 📊 What You'll See

### Cluster Formation

```

🌱 Starting seed node on port 2551
Seed node started. Other nodes can join the cluster.
🟢 Member UP: akka://ClusterSystem@127.0.0.1:2551 with roles [seed]
Current cluster size: 1

```

### LLM Node Joining

```

🤖 Starting LLM node on port 2555
🟢 Member UP: akka://ClusterSystem@127.0.0.1:2555 with roles [llm]
Current cluster size: 2
LLM service ready for queries

```

### Interactive Query Session

```

# 🤖 Akka Cluster LLM Query Interface

Enter your query (or 'quit' to exit): What is machine learning?

🔍 Processing query: "What is machine learning?"
📤 TELL: Sending log message to logging actor
🔄 ASK: Requesting LLM response from AI service
⏳ Waiting for response...

📥 FORWARD: Routing response through message router
✅ Response received:

Machine learning is a subset of artificial intelligence that enables
computers to learn and improve from experience without being explicitly
programmed for every task...

Enter your query (or 'quit' to exit):

````

## 🎭 Demo Scenarios

### Scenario 1: Basic LLM Integration

1. Start the cluster with `./start-cluster.sh`
2. Run interactive client with `./ask-queries.sh`
3. Ask questions and observe the communication patterns
4. Watch logs to see TELL, ASK, and FORWARD patterns

### Scenario 2: Manual Cluster Building

1. Start seed node first
2. Add LLM node and observe cluster growth
3. Add query handler and router
4. Test with interactive client

### Scenario 3: Communication Pattern Analysis

1. Start full cluster
2. Ask a question and trace the message flow:
   - TELL: Logging messages (fire-and-forget)
   - ASK: LLM queries (request-response)
   - FORWARD: Message routing through cluster
## 🔧 Configuration

Key configuration points in `application.conf`:

```hocon
akka {
  cluster {
    seed-nodes = ["akka://ClusterSystem@127.0.0.1:2551"]
    downing-provider-class = "akka.cluster.sbr.SplitBrainResolverProvider"
    split-brain-resolver.active-strategy = keep-majority
  }
}
````

## 📁 Project Structure

```
akka-clusters/
├── src/main/java/com/akka/cluster/demo/
│   ├── ClusterApp.java          # Main application entry point
│   ├── ClusterListener.java     # Cluster membership monitor
│   ├── LLMActor.java           # LLM integration actor
│   ├── UserQueryHandler.java   # Query processing actor
│   ├── ClusterMessageRouter.java # Message routing actor
│   ├── LoggingActor.java       # Centralized logging
│   └── CborSerializable.java   # Serialization marker
├── src/main/resources/
│   ├── application.conf         # Akka configuration
│   └── logback.xml             # Logging configuration
├── start-cluster.sh            # Essential cluster startup
├── start-llm-cluster.sh       # Full demo cluster startup
├── ask-queries.sh             # Interactive LLM client
└── pom.xml                    # Maven dependencies
```

## 🔍 Key Concepts Demonstrated

### 1. **Cluster Membership**

- Seed nodes for cluster bootstrapping
- Gossip protocol for membership information
- Member lifecycle: Joining → Up → Leaving → Removed

### 2. **Node Roles**

- Different responsibilities (seed, llm, query-handler, router, interactive)
- Role-based actor spawning and behavior

### 3. **Fault Tolerance**

- Failure detection using Phi Accrual Failure Detector
- Split Brain Resolver for network partition handling
- Graceful shutdown and forceful node removal

### 4. **Message Passing**

### 1. **Akka Communication Patterns**

- **TELL**: Fire-and-forget messaging to logging actors
- **ASK**: Request-response pattern with LLM service using Futures
- **FORWARD**: Message forwarding through router actors

### 2. **LLM Integration**

- Real-world AI service integration (Perplexity API)
- Asynchronous request handling
- Structured response processing

### 3. **Cluster Membership**

- Dynamic node joining and leaving
- Automatic failure detection
- Split brain resolution strategies

### 4. **Distributed Messaging**

- Cluster-aware message routing
- Serialization for cross-node communication
- Actor location transparency

### 5. **Observability**

- Cluster event monitoring
- Structured logging for debugging
- Real-time message flow visualization

## 🎓 Course Learning Outcomes

### Primary Objectives

- Understand all three Akka communication patterns
- Learn distributed system design with Akka Cluster
- Integrate external services (LLM) in actor systems
- Observe fault tolerance and cluster behavior

### Technical Skills Gained

- Akka Typed actor system design
- HTTP client integration for AI services
- Cluster configuration and management
- Message serialization and routing

## 🐛 Troubleshooting

### Common Issues

**Port Already in Use**

```bash
# Check what's using the port
lsof -i :2551
# Kill the process or use different ports
```

**Nodes Can't Join Cluster**

- Ensure seed node is running first
- Check firewall settings
- Verify network connectivity (localhost should work)

**LLM API Issues**

- Verify PERPLEXITY_API_KEY is set correctly
- Check internet connectivity
- Review API rate limits

**High CPU Usage**

- Normal during cluster formation
- Check log levels (reduce if too verbose)
- Monitor failure detector sensitivity

## 📚 Further Reading

- [Akka Cluster Documentation](https://doc.akka.io/libraries/akka-core/current/typed/cluster.html)
- [Akka Typed Actors](https://doc.akka.io/libraries/akka-core/current/typed/actors.html)
- [Communication Patterns in Akka](https://doc.akka.io/libraries/akka-core/current/typed/interaction-patterns.html)
- [Perplexity API Documentation](https://docs.perplexity.ai/)
- [Split Brain Resolver](https://doc.akka.io/libraries/akka-core/current/split-brain-resolver.html)

## 🎯 Project Summary

This project successfully demonstrates:
✅ **Akka Cluster** with multiple specialized nodes  
✅ **TELL Pattern** for fire-and-forget logging  
✅ **ASK Pattern** for LLM request-response  
✅ **FORWARD Pattern** for message routing  
✅ **LLM Integration** with real AI service  
✅ **Fault Tolerance** and cluster management

---

**Happy Clustering with AI! 🤖🎉**

_This demo was created for CSYE7374 course to help students understand distributed systems concepts using Akka Cluster._
