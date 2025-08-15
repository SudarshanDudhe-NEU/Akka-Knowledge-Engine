#!/bin/bash

# Start Swing GUI for Akka Knowledge Engine
# This launches the desktop interface for WhatsApp chat analysis

echo "🎨 Starting Akka Knowledge Engine - Swing Desktop Interface"
echo "============================================================="
echo ""
echo "✨ Features:"
echo "  • Professional desktop interface"
echo "  • Drag & drop file upload" 
echo "  • Real-time AI chat analysis"
echo "  • Akka cluster integration"
echo "  • Rich visual feedback"
echo ""

# Ensure project is compiled
echo "🔧 Ensuring project is compiled..."
mvn compile -q

if [ $? -eq 0 ]; then
    echo "✅ Compilation successful!"
    echo ""
    echo "🚀 Launching Swing GUI..."
    echo ""
    
    # Run the Swing GUI using direct classpath
    echo "📱 Loading GUI application..."
    java -cp "target/classes:$(mvn dependency:build-classpath -q -Dmdep.outputFile=/dev/stdout)" com.akka.cluster.app.swing.KnowledgeEngineGUI
else
    echo "❌ Compilation failed. Please check for errors."
    exit 1
fi
