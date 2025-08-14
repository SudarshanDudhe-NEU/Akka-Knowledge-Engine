#!/bin/bash

# Enhanced WhatsApp Q&A System with Vector Database Setup
# This script sets up and starts the enhanced system with Qdrant vector database

echo "🚀 Setting up Enhanced WhatsApp Q&A System with Vector Database"
echo "=============================================================================="

# Check if Docker is running
if ! docker info >/dev/null 2>&1; then
    echo "❌ Docker is not running. Please start Docker Desktop first."
    exit 1
fi

# Start Qdrant vector database
echo "📊 Starting Qdrant Vector Database..."
docker-compose up -d qdrant

# Wait for Qdrant to be ready
echo "⏳ Waiting for Qdrant to be ready..."
max_attempts=30
attempt=0

while ! curl -s http://localhost:6333/health >/dev/null 2>&1; do
    attempt=$((attempt + 1))
    if [ $attempt -ge $max_attempts ]; then
        echo "❌ Qdrant failed to start within 30 seconds"
        echo "💡 Try running: docker-compose logs qdrant"
        exit 1
    fi
    echo "   Waiting... ($attempt/$max_attempts)"
    sleep 1
done

echo "✅ Qdrant Vector Database is ready!"
echo "   - HTTP API: http://localhost:6333"
echo "   - gRPC API: localhost:6334"
echo "   - Dashboard: http://localhost:6333/dashboard"

# Build the project
echo ""
echo "🔨 Building Enhanced Akka Cluster Demo..."
mvn clean compile

if [ $? -ne 0 ]; then
    echo "❌ Build failed. Please check the compilation errors above."
    exit 1
fi

echo "✅ Build successful!"

# Start the cluster components
echo ""
echo "🌟 Starting Enhanced WhatsApp Q&A Cluster..."
echo "=============================================================================="

# Start the nodes in order
echo "1️⃣  Starting Seed Node (2551)..."
mvn exec:java -Dexec.mainClass=com.akka.cluster.demo.ClusterApp -Dexec.args="seed" &
SEED_PID=$!

# Wait a bit for seed node to start
sleep 3

echo "2️⃣  Starting Backend Node (2552)..."
mvn exec:java -Dexec.mainClass=com.akka.cluster.demo.ClusterApp -Dexec.args="backend 2552" &
BACKEND1_PID=$!

sleep 2

echo "3️⃣  Starting WhatsApp Q&A Node (2553)..."
mvn exec:java -Dexec.mainClass=com.akka.cluster.demo.ClusterApp -Dexec.args="whatsapp-qa 2553" &
QA_PID=$!

sleep 2

echo "4️⃣  Starting Additional Backend Node (2554)..."
mvn exec:java -Dexec.mainClass=com.akka.cluster.demo.ClusterApp -Dexec.args="backend 2554" &
BACKEND2_PID=$!

echo ""
echo "🎉 Enhanced WhatsApp Q&A System is starting up!"
echo "=============================================================================="
echo "🔗 Cluster Nodes:"
echo "   • Seed Node:        akka://ClusterSystem@127.0.0.1:2551"
echo "   • Backend Node 1:   akka://ClusterSystem@127.0.0.1:2552"
echo "   • WhatsApp Q&A:     akka://ClusterSystem@127.0.0.1:2553"
echo "   • Backend Node 2:   akka://ClusterSystem@127.0.0.1:2554"
echo ""
echo "📊 Vector Database:"
echo "   • Qdrant Dashboard: http://localhost:6333/dashboard"
echo "   • Health Check:     curl http://localhost:6333/health"
echo ""
echo "💡 Usage:"
echo "   • Start client: java -cp target/classes com.akka.cluster.demo.WhatsAppQAClient"
echo "   • Upload chat:  upload /path/to/chat.txt"
echo "   • Ask question: ask your question here"
echo ""
echo "🛑 To stop the system:"
echo "   • Press Ctrl+C to stop cluster nodes"
echo "   • Run: docker-compose down (to stop Qdrant)"
echo ""

# Wait for all processes
wait $SEED_PID $BACKEND1_PID $QA_PID $BACKEND2_PID

echo "🔄 All cluster nodes stopped. Stopping vector database..."
docker-compose down
