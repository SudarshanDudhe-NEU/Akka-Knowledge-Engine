package com.akka.cluster.demo;

/**
 * Marker interface for JSON serializable messages.
 * All messages sent between cluster nodes should implement this interface
 * to ensure proper serialization with Jackson CBOR.
 */
public interface CborSerializable {
}
