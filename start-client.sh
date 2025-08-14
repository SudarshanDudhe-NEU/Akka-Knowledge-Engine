#!/bin/bash

# WhatsApp QA Client Launcher with Full Dependencies
# This script ensures all Maven dependencies are included in the classpath

echo "🚀 Starting WhatsApp Q&A Client with Enhanced System"
echo "===================================================="

# Build the project first
echo "📦 Building project..."
mvn clean compile -q

if [ $? -ne 0 ]; then
    echo "❌ Build failed"
    exit 1
fi

echo "✅ Build successful!"

# Use Maven to run with full classpath
echo ""
echo "🎮 Starting WhatsApp Q&A Client..."
echo "   (Make sure the cluster node is running first)"
echo ""

mvn exec:java -Dexec.mainClass=com.akka.cluster.demo.ClusterApp -Dexec.classpathScope=runtime -Dexec.args="whatsapp-qa"
