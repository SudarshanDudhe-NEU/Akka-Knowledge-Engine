# 🤖 How to Ask Queries

## 🚀 Quick Start

```bash
./start-cluster.sh
./start-client.sh
```

## 📋 Example Queries

**AI/ML Topics**:

- "What is machine learning?"
- "Explain neural networks"

**Technical**:

- "How does Akka cluster work?"
- "What is actor model?"

**Startup/Business**:

- "Find trending AI startups"
- "Explain venture capital"

## 🔄 What You'll See

- **TELL** patterns for logging
- **ASK** patterns for LLM queries
- **FORWARD** patterns for routing
- JSON responses with confidence scores

🌐 DISTRIBUTED SYSTEMS:
"What are the best practices for microservices architecture?"

````

---

## 📋 **Method 2: Programmatic Queries via RealMessageFlowDemo**

### Start the Demo:

```bash
mvn exec:java -Dexec.mainClass=com.akka.cluster.app.RealMessageFlowDemo
````

This will:

- ✅ Start all required cluster nodes automatically
- ✅ Send predefined queries showing all communication patterns
- ✅ Display complete message flow tracing
- ✅ Show structured JSON responses with confidence scores

### Sample Queries Included:

```java
// The demo includes these example queries:
"What are the latest trends in machine learning?"
"How does Akka Cluster handle network partitions?"
"Explain reactive programming principles"
"What are the monitoring strategies for distributed systems?"
```

---

## 📋 **Method 3: Direct API Testing**

### Test Enhanced API Features:

```bash
./test-enhanced-api.sh
```

This script demonstrates:

- 🎯 Structured JSON response format
- 📊 Confidence scoring (0.0 - 1.0)
- 🔖 Source type classification
- 💡 Additional information arrays

---

## 🎯 **Enhanced Response Format**

Your queries now return structured responses like:

```json
{
  "answer": "Based on recent market analysis and funding rounds, here are 3 trending AI startups with significant recent funding: 1) **Anthropic** - Received $4 billion from Amazon in September 2024...",
  "confidence": 0.85,
  "source_type": "factual",
  "additional_info": [
    "Total AI funding reached $25.2 billion in Q3 2024",
    "Enterprise AI startups saw 40% increase in Series A rounds",
    "AI safety and alignment companies attracted $3.2B in new funding"
  ]
}
```

**Displayed as:**

```
🤖 Based on recent market analysis and funding rounds, here are 3 trending AI startups...

📊 Confidence: 85.0%
🔖 Source Type: factual

💡 Additional Info:
• Total AI funding reached $25.2 billion in Q3 2024
• Enterprise AI startups saw 40% increase in Series A rounds
• AI safety and alignment companies attracted $3.2B in new funding
```

---

## 🛠️ **Production Setup**

### With Real Perplexity API:

```bash
# 1. Set your API key
export PERPLEXITY_API_KEY="your-perplexity-api-key-here"

# 2. Start the cluster
./start-cluster.sh

# 3. Ask queries - you'll get live AI responses!
```

### With Mock Responses (No API Key):

```bash
# Simply start without setting PERPLEXITY_API_KEY
./start-cluster.sh

# The system will use enhanced mock responses that demonstrate
# the structured JSON format
```

---

## 🎨 **Query Examples by Category**

### 💰 **Startup & Business:**

```
"Find the top 3 trending AI startups with recent funding"
"What are the current valuations of major tech companies?"
"Explain the venture capital landscape for AI companies"
```

### 🔧 **Technical & Engineering:**

```
"How does Akka cluster handle network partitions?"
"Explain microservices architecture best practices"
"What is the difference between REST and GraphQL?"
```

### 🤖 **AI & Machine Learning:**

```
"What are the latest trends in machine learning?"
"Explain transformer architecture in simple terms"
"How does GPT work under the hood?"
```

### 📊 **Data & Analytics:**

```
"What is the difference between batch and stream processing?"
"Explain the CAP theorem in simple terms"
"How do you implement real-time data pipelines?"
```

### 🌐 **Distributed Systems:**

```
"What are the monitoring strategies for distributed systems?"
"How do you handle service discovery in microservices?"
"Explain container orchestration with Kubernetes"
```

---

## 🔍 **Message Flow Tracing**

When you ask queries, you'll see complete distributed flow:

```
🚀 [2025-08-13 10:30:15] Sending distributed query: query-123
👤 User: alice (Data Scientist)
📝 Query: What are the latest trends in machine learning?

🌐 [2025-08-13 10:30:15] Routing distributed query: query-123
🎯 Selected query handler: akka://ClusterSystem@127.0.0.1:2556/user/query-handler

✅ [2025-08-13 10:30:16] Distributed query response: query-123
🤖 Response: Machine learning continues to evolve rapidly in 2025...
📊 Confidence: 87.0%
🔖 Source Type: analytical
📊 Status: SUCCESS | Time: 1,250ms | Node: akka://ClusterSystem@127.0.0.1:2556
🛤️  Routing Path: Client → Router → QueryHandler → LLM → Response
```

---

## 🎓 **Advanced Features**

### **Load Balancing:**

- Multiple LLM nodes automatically balance queries
- Fault tolerance if nodes go down
- Dynamic scaling by adding more nodes

### **Service Discovery:**

- Automatic detection of available services
- Real-time cluster membership monitoring
- Graceful handling of node failures

### **Communication Patterns:**

- **TELL**: Fire-and-forget messages
- **ASK**: Request-response with timeouts
- **FORWARD**: Message passing through intermediaries

---

## ✅ **Your System is Ready!**

You now have a **production-grade distributed AI system** that:

🎯 **Processes queries across multiple cluster nodes**
📊 **Returns structured JSON responses with confidence scores**
🔄 **Provides complete message flow tracing**
⚡ **Handles failures gracefully with fault tolerance**
🌐 **Scales horizontally by adding more nodes**

**Start asking queries and see your distributed AI system in action!** 🚀
