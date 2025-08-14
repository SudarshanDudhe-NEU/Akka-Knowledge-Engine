#!/bin/bash

# Script to start WhatsApp Chat Q&A system
# This demonstrates the complete WhatsApp Q&A Bot functionality

echo "🚀 Starting WhatsApp Chat Q&A Bot System"
echo "========================================"

# Function to kill all background processes on exit
cleanup() {
    echo ""
    echo "🛑 Shutting down WhatsApp Q&A system..."
    jobs -p | xargs -r kill
    exit
}

# Set trap to cleanup on script exit
trap cleanup SIGINT SIGTERM EXIT

# Build the project first
echo "📦 Building project..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "❌ Build failed!"
    exit 1
fi

echo "✅ Build successful!"
echo ""

# Start seed node (port 2551)
echo "🌱 Starting seed node on port 2551..."
mvn exec:java -Dexec.mainClass="com.akka.cluster.demo.ClusterApp" -Dexec.args="seed" -q &
SEED_PID=$!
sleep 3

# Start LLM node (port 2555) - for processing questions with AI
echo "🤖 Starting LLM node on port 2555..."
mvn exec:java -Dexec.mainClass="com.akka.cluster.demo.ClusterApp" -Dexec.args="llm 2555" -q &
LLM_PID=$!
sleep 2

# Start router node (port 2557) - for message routing demonstration
echo "🌐 Starting router node on port 2557..."
mvn exec:java -Dexec.mainClass="com.akka.cluster.demo.ClusterApp" -Dexec.args="router 2557" -q &
ROUTER_PID=$!
sleep 2

echo ""
echo "🎉 Core cluster nodes started!"
echo ""
echo "Cluster nodes running:"
echo "  📍 Seed node:         localhost:2551"
echo "  🤖 LLM node:          localhost:2555"
echo "  🌐 Router node:       localhost:2557"
echo ""
echo "💡 WhatsApp Q&A Bot Features:"
echo "  ✅ WhatsApp chat file parsing"
echo "  ✅ Semantic search and context retrieval (RAG)"
echo "  ✅ LLM integration for intelligent answers"
echo "  ✅ TELL, ASK, and FORWARD communication patterns"
echo "  ✅ Session management and logging"
echo ""
echo "🚀 Starting WhatsApp Q&A Client..."
echo ""

# Start the WhatsApp Q&A client
mvn exec:java -Dexec.mainClass="com.akka.cluster.demo.ClusterApp" -Dexec.args="whatsapp-qa 2560"

# This script will run until the client exits or Ctrl+C is pressed
echo ""
echo "👋 WhatsApp Q&A session ended."
