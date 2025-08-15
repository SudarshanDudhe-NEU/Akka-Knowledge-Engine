#!/bin/bash

# 🚀 Enhanced WhatsApp Q&A System - Quick Start Script
# This script provides easy access to the Enhanced WhatsApp Q&A System

echo "🎊 Enhanced WhatsApp Q&A System - Quick Start"
echo "=============================================="
echo ""

# Function to display menu
show_menu() {
    echo "Choose an option:"
    echo "1. 🚀 Start Enhanced WhatsApp Q&A System (Recommended)"
    echo "2. 🏗️  Build Project Only"
    echo "3. 🧹 Clean Project"
    echo "4. 📊 View Project Summary"
    echo "5. 📝 View Usage Instructions"
    echo "6. ❌ Exit"
    echo ""
}

# Function to start the enhanced system
start_system() {
    echo "� Building and starting Enhanced WhatsApp Q&A System..."
    mvn clean compile
    if [ $? -eq 0 ]; then
        echo ""
        echo "✅ Build successful! Starting system on port 2558..."
        echo ""
        echo "💡 Usage:"
        echo "  1. upload /path/to/your/whatsapp_chat.txt"
        echo "  2. ask Who is [person] and what did they say about [topic]?"
        echo "  3. Type 'exit' to quit"
        echo ""
        echo "🚀 Starting Enhanced WhatsApp Q&A System..."
        echo "================================================="
        mvn exec:java -Dexec.mainClass="com.akka.cluster.app.ClusterApp" -Dexec.args="whatsapp-qa 2558"
    else
        echo "❌ Build failed. Please check the error messages above."
    fi
}

# Function to build only
build_only() {
    echo "🏗️ Building project..."
    mvn clean compile
    if [ $? -eq 0 ]; then
        echo "✅ Build successful!"
    else
        echo "❌ Build failed."
    fi
}

# Function to clean project
clean_project() {
    echo "🧹 Cleaning project..."
    mvn clean
    rm -f whatsapp_qa_logs.txt
    echo "✅ Project cleaned!"
}

# Function to show project summary
show_summary() {
    if [ -f "PROJECT_COMPLETION_SUMMARY.md" ]; then
        echo "� Project Summary:"
        echo "==================="
        head -30 PROJECT_COMPLETION_SUMMARY.md
        echo ""
        echo "📄 Full summary available in: PROJECT_COMPLETION_SUMMARY.md"
    else
        echo "❌ Project summary not found."
    fi
}

# Function to show usage
show_usage() {
    echo "� Enhanced WhatsApp Q&A System Usage:"
    echo "======================================"
    echo ""
    echo "🚀 Quick Start:"
    echo "  ./quick-start.sh"
    echo ""
    echo "🔧 Manual Start:"
    echo "  mvn clean compile"
    echo "  mvn exec:java -Dexec.mainClass="com.akka.cluster.app.ClusterApp" -Dexec.args="whatsapp-qa 2558""
    echo ""
    echo "💬 In the system:"
    echo "  upload /path/to/your/whatsapp_chat.txt"
    echo "  ask Who is Pritam and what was the concern about his visa?"
    echo "  ask What payment arrangements were discussed?"
    echo "  exit"
    echo ""
    echo "⚙️ Configuration:"
    echo "  Set PERPLEXITY_API_KEY environment variable for AI responses"
    echo "  export PERPLEXITY_API_KEY="your-api-key""
    echo ""
}

# Main menu loop
while true; do
    show_menu
    read -p "Enter your choice (1-6): " choice
    echo ""
    
    case $choice in
        1)
            start_system
            break
            ;;
        2)
            build_only
            echo ""
            ;;
        3)
            clean_project
            echo ""
            ;;
        4)
            show_summary
            echo ""
            ;;
        5)
            show_usage
            echo ""
            ;;
        6)
            echo "👋 Goodbye!"
            exit 0
            ;;
        *)
            echo "❌ Invalid option. Please choose 1-6."
            echo ""
            ;;
    esac
done
