#!/bin/bash

# Kill any existing Akka cluster processes
echo "🧹 Stopping all Akka cluster processes..."

# Method 1: Kill by process name
pkill -f "ClusterApp" 2>/dev/null || true

# Method 2: Kill by specific ports (if needed)
for port in 2551 2555 2556 2557 2558; do
    pid=$(lsof -ti:$port 2>/dev/null)
    if [ ! -z "$pid" ]; then
        echo "🔪 Killing process using port $port (PID: $pid)"
        kill -9 $pid 2>/dev/null || true
    fi
done

# Wait a moment for cleanup
sleep 2

echo "✅ Cleanup complete! You can now start the cluster."
echo ""
echo "💡 Usage:"
echo "  ./cleanup-cluster.sh"
echo "  ./start-cluster-clean.sh    # or"
echo "  ./start-cluster.sh"
