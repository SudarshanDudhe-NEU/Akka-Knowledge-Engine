# Akka Cluster with LLM Integration

## 🎊 **PROJECT STATUS: COMPLETED** ✅

**WhatsApp Q&A System with 95%+ parsing success rate**

🚀 **Quick Start**: `./quick-start.sh`

This project demonstrates **Akka Cluster** with **LLM integration** showcasing distributed computing and all three Akka communication patterns.

## 🎯 Architecture

- **Seed Node** (2551): Cluster management
- **LLM Node** (2555): AI queries via Perplexity API
- **Query Handler** (2556): User query processing
- **Router Node** (2557): Message forwarding

## 🚀 Quick Start

### Prerequisites

- Java 11+, Maven 3.6+
- Perplexity API Key (optional)

### Setup

```bash
mvn clean compile
export PERPLEXITY_API_KEY="your-key"  # optional

# Option 1: Interactive menu (recommended)
./quick-start.sh

# Option 2: Basic cluster only
./start-cluster.sh
```

## 🔄 Communication Patterns

- **TELL**: Fire-and-forget logging
- **ASK**: Request-response with LLM
- **FORWARD**: Message routing

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
├── quick-start.sh              # Interactive startup menu
├── start-cluster.sh           # Basic cluster startup
└── pom.xml                    # Maven dependencies
```

## 🔍 Key Concepts Demonstrated

### 1. Cluster Membership

- Seed nodes for cluster bootstrapping
- Gossip protocol for membership information
- Member lifecycle: Joining → Up → Leaving → Removed

### 2. Node Roles

- Different responsibilities (seed, llm, query-handler, router, interactive)
- Role-based actor spawning and behavior

### 3. Fault Tolerance

- Failure detection using Phi Accrual Failure Detector
- Split Brain Resolver for network partition handling
- Graceful shutdown and forceful node removal

### 4. Akka Communication Patterns

- **TELL**: Fire-and-forget messaging to logging actors
- **ASK**: Request-response pattern with LLM service using Futures
- **FORWARD**: Message forwarding through router actors

### 5. LLM Integration

- Real-world AI service integration (Perplexity API)
- Asynchronous request handling
- Structured response processing

### 6. Distributed Messaging

- Cluster-aware message routing
- Serialization for cross-node communication
- Actor location transparency

### 7. Observability

- Cluster event monitoring
- Structured logging for debugging
- Real-time message flow visualization

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

**Happy Clustering! 🤖🎉**
