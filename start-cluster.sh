#!/bin/bash

# Script to start essential Akka cluster nodes for LLM integration demo
# This script will start core nodes required for the final project

echo "🚀 Starting Akka Cluster LLM Demo"
echo "=================================="

# Function to kill all background processes on exit
cleanup() {
    echo ""
    echo "🛑 Shutting down all cluster nodes..."
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
mvn exec:java -Dexec.mainClass="com.akka.cluster.app.ClusterApp" -Dexec.args="seed" -q &
SEED_PID=$!
sleep 3

# Start LLM node (port 2555)
echo "🤖 Starting LLM node on port 2555..."
mvn exec:java -Dexec.mainClass="com.akka.cluster.app.ClusterApp" -Dexec.args="llm 2555" -q &
LLM_PID=$!
sleep 2

# Start query handler node (port 2556)
echo "� Starting query handler node on port 2556..."
mvn exec:java -Dexec.mainClass="com.akka.cluster.app.ClusterApp" -Dexec.args="query-handler 2556" -q &
HANDLER_PID=$!
sleep 2

# Start router node (port 2557)
echo "🌐 Starting router node on port 2557..."
mvn exec:java -Dexec.mainClass="com.akka.cluster.app.ClusterApp" -Dexec.args="router 2557" -q &
ROUTER_PID=$!

echo ""
echo "🎉 All essential nodes started!"
echo ""
echo "Cluster nodes running:"
echo "  📍 Seed node:        localhost:2551"
echo "  🤖 LLM node:         localhost:2555"
echo "  � Query handler:    localhost:2556"
echo "  🌐 Router node:      localhost:2557"
echo ""
echo "💡 Core functionality available:"
echo "  ✅ LLM integration with Perplexity API"
echo "  ✅ TELL, ASK, and FORWARD communication patterns"
echo "  ✅ Distributed query processing"
echo "  ✅ Message routing and logging"
echo ""
echo "🚀 Ready to process user queries!"
echo "💡 Press Ctrl+C to shutdown all nodes"
echo ""

# Wait for all background processes
wait
