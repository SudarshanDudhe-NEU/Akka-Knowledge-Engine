# 🎉 Akka Cluster with LLM Integration - Final Project Demo!

## ✅ What We've Built

A complete **Akka Cluster with LLM integration** demonstration for the CSYE7374 final project. This showcases distributed computing concepts with real-world AI integration.

### 🏗️ Architecture Components
- **Seed Node** (port 2551) - Bootstraps the cluster
- **LLM Node** (port 2555) - Integrates with Perplexity API for AI responses
- **Query Handler** (port 2556) - Processes user queries and coordinates responses
- **Router Node** (port 2557) - Demonstrates message forwarding patterns
- **Interactive Client** - Real-time LLM query interface

### 🎯 Demo Capabilities
✅ **All Communication Patterns** - TELL, ASK, and FORWARD demonstrated  
✅ **LLM Integration** - Real AI service integration with Perplexity API  
✅ **Cluster Formation** - Watch nodes join and form a distributed system  
✅ **Fault Tolerance** - Node failure detection and recovery  
✅ **Message Routing** - Distributed message handling across cluster  
✅ **Interactive Queries** - Real-time AI question-answering interface  

## 🚀 Quick Demo Instructions

### Option 1: Essential Cluster (Recommended for Development)
```bash
./start-cluster.sh
```
This starts the core nodes needed for LLM functionality.

### Option 2: Full Demo Cluster (Best for Presentations)
```bash
./start-llm-cluster.sh
```
This starts all nodes with detailed logging and status information.

### Option 3: Interactive LLM Client
```bash
./ask-queries.sh
```
This starts the cluster (if not running) and launches the interactive query interface.

### Option 4: Step-by-Step Demo (For Educational Purposes)
```bash
# Terminal 1 - Start seed node
mvn exec:java -Dexec.mainClass="com.akka.cluster.demo.ClusterApp" -Dexec.args="seed"

# Terminal 2 - Add LLM service
mvn exec:java -Dexec.mainClass="com.akka.cluster.demo.ClusterApp" -Dexec.args="llm 2555"

# Terminal 3 - Add query handler
mvn exec:java -Dexec.mainClass="com.akka.cluster.demo.ClusterApp" -Dexec.args="query-handler 2556"

# Terminal 4 - Add router
mvn exec:java -Dexec.mainClass="com.akka.cluster.demo.ClusterApp" -Dexec.args="router 2557"

# Terminal 5 - Start interactive client
mvn exec:java -Dexec.mainClass="com.akka.cluster.demo.ClusterApp" -Dexec.args="interactive"
```

## 📊 What You'll See During Demo

### 1. Cluster Formation
```
🌱 Starting seed node on port 2551
Seed node started. Other nodes can join the cluster.
🟢 Member UP: akka://ClusterSystem@127.0.0.1:2551 with roles [seed]
Current cluster size: 1
```

### 2. LLM Service Integration
```
🤖 LLM service started on akka://ClusterSystem@127.0.0.1:2555
🟢 Member UP: akka://ClusterSystem@127.0.0.1:2555 with roles [llm]
🔗 Connected to Perplexity API
Ready to process AI queries
```

### 3. Interactive Query Processing
```
🤖 Akka Cluster LLM Query Interface
==================================

Enter your query (or 'quit' to exit): What is distributed computing?

🔍 Processing query: "What is distributed computing?"
📤 TELL: Logging query to system
🔄 ASK: Requesting LLM response
📥 FORWARD: Routing through cluster
✅ Response received:

Distributed computing is a field of computer science that studies 
distributed systems - systems whose components are located on 
networked computers that communicate and coordinate their actions...
```

### 4. Communication Pattern Demonstration
```
✅ TELL Pattern: Fire-and-forget logging messages
✅ ASK Pattern: Request-response with LLM service using Futures
✅ FORWARD Pattern: Message forwarding through router nodes
```

## 🎭 Demo Scenarios for Presentation

### Scenario 1: Basic LLM Integration
1. Start essential cluster: `./start-cluster.sh`
2. Launch interactive client: `./ask-queries.sh`
3. Ask AI questions and show real responses
4. Highlight the three communication patterns in action

### Scenario 2: Cluster Growth Visualization
1. Start seed node only
2. Add LLM node and show cluster growth
3. Add query handler and router
4. Show how distributed system scales

### Scenario 3: Real-World AI Integration
1. Set PERPLEXITY_API_KEY environment variable
2. Ask technical questions about distributed systems
3. Show structured JSON responses from real AI service
4. Demonstrate fault tolerance when API is unavailable

## 🔧 Prerequisites for Demo

### Required
- Java 11+
- Maven 3.6+
- Internet connection (for LLM API)

### Optional (for enhanced demo)
```bash
export PERPLEXITY_API_KEY="your-api-key"
```

### Build Before Demo
```bash
mvn clean compile
```

## 🎯 Key Learning Points to Highlight

### 1. Akka Communication Patterns
- **TELL**: Asynchronous fire-and-forget messaging
- **ASK**: Request-response with Futures for guaranteed replies
- **FORWARD**: Message forwarding maintaining original sender context

### 2. Distributed System Design
- Cluster membership and failure detection
- Actor location transparency across nodes
- Fault tolerance and recovery strategies

### 3. Real-World Integration
- External service integration (LLM APIs)
- Handling network failures and API limits
- Structured data processing and response handling

## 🎬 Presentation Tips

1. **Start Simple**: Begin with `./start-cluster.sh` to show basic cluster
2. **Show Patterns**: Use interactive client to demonstrate all three communication patterns
3. **Highlight AI**: Ask interesting questions to showcase real LLM integration
4. **Demonstrate Scale**: Add/remove nodes to show distributed system behavior
5. **Discuss Fault Tolerance**: Kill nodes and show recovery mechanisms

---

**Ready to showcase distributed AI systems! 🤖🎉**
