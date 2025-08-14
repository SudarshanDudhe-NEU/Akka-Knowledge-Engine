#!/bin/bash

echo "🚀 Interactive Node Startup for Akka Cluster LLM Demo"
echo "===================================================="
echo ""
echo "Choose a node to start:"
echo "1) Seed node (port 2551) - Start this first!"
echo "2) LLM node (port 2555)"
echo "3) Query handler (port 2556)"
echo "4) Router node (port 2557)"
echo "5) Interactive client"
echo "6) Custom node type"
echo ""

read -p "Enter your choice (1-6): " choice

case $choice in
    1)
        echo "🌱 Starting seed node..."
        mvn exec:java -Dexec.mainClass="com.akka.cluster.demo.ClusterApp" -Dexec.args="seed"
        ;;
    2)
        echo "🤖 Starting LLM node..."
        mvn exec:java -Dexec.mainClass="com.akka.cluster.demo.ClusterApp" -Dexec.args="llm 2555"
        ;;
    3)
        echo "👤 Starting query handler..."
        mvn exec:java -Dexec.mainClass="com.akka.cluster.demo.ClusterApp" -Dexec.args="query-handler 2556"
        ;;
    4)
        echo "🌐 Starting router node..."
        mvn exec:java -Dexec.mainClass="com.akka.cluster.demo.ClusterApp" -Dexec.args="router 2557"
        ;;
    5)
        echo "🎮 Starting interactive client..."
        mvn exec:java -Dexec.mainClass="com.akka.cluster.demo.ClusterApp" -Dexec.args="interactive"
        ;;
    6)
        read -p "Enter node type (seed/llm/query-handler/router/interactive): " type
        if [[ "$type" == "llm" || "$type" == "query-handler" || "$type" == "router" ]]; then
            read -p "Enter port number: " port
            mvn exec:java -Dexec.mainClass="com.akka.cluster.demo.ClusterApp" -Dexec.args="$type $port"
        else
            mvn exec:java -Dexec.mainClass="com.akka.cluster.demo.ClusterApp" -Dexec.args="$type"
        fi
        ;;
    *)
        echo "Invalid choice. Please run the script again."
        exit 1
        ;;
esac
