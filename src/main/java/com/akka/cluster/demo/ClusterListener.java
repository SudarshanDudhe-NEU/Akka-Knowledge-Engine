package com.akka.cluster.demo;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.Subscribe;

import java.util.stream.StreamSupport;

/**
 * Actor that listens to cluster membership events and logs them.
 * This demonstrates how to monitor cluster state changes.
 */
public class ClusterListener extends AbstractBehavior<ClusterEvent.ClusterDomainEvent> {

    public static Behavior<ClusterEvent.ClusterDomainEvent> create() {
        return Behaviors.setup(ClusterListener::new);
    }

    private final Cluster cluster;

    private ClusterListener(ActorContext<ClusterEvent.ClusterDomainEvent> context) {
        super(context);
        this.cluster = Cluster.get(context.getSystem());
        
        // Subscribe to cluster events
        cluster.subscriptions().tell(Subscribe.create(
            context.getSelf(), 
            ClusterEvent.ClusterDomainEvent.class
        ));
        
        System.out.println("ClusterListener started on " + cluster.selfMember().address());
    }

    @Override
    public Receive<ClusterEvent.ClusterDomainEvent> createReceive() {
        return newReceiveBuilder()
            .onMessage(ClusterEvent.MemberUp.class, this::onMemberUp)
            .onMessage(ClusterEvent.MemberRemoved.class, this::onMemberRemoved)
            .onMessage(ClusterEvent.MemberLeft.class, this::onMemberLeft)
            .onMessage(ClusterEvent.UnreachableMember.class, this::onUnreachableMember)
            .onMessage(ClusterEvent.ReachableMember.class, this::onReachableMember)
            .onAnyMessage(this::onAnyOtherEvent)
            .build();
    }

    private Behavior<ClusterEvent.ClusterDomainEvent> onMemberUp(ClusterEvent.MemberUp event) {
        Member member = event.member();
        System.out.println(String.format(
            "🟢 Member UP: %s with roles %s", 
            member.address(), 
            member.getRoles()
        ));
        
        // Print current cluster size
        long clusterSize = StreamSupport.stream(cluster.state().getMembers().spliterator(), false).count();
        System.out.println("Current cluster size: " + clusterSize);
        return this;
    }

    private Behavior<ClusterEvent.ClusterDomainEvent> onMemberRemoved(ClusterEvent.MemberRemoved event) {
        Member member = event.member();
        System.out.println(String.format(
            "🔴 Member REMOVED: %s with roles %s", 
            member.address(), 
            member.getRoles()
        ));
        
        long clusterSize = StreamSupport.stream(cluster.state().getMembers().spliterator(), false).count();
        System.out.println("Current cluster size: " + clusterSize);
        return this;
    }

    private Behavior<ClusterEvent.ClusterDomainEvent> onMemberLeft(ClusterEvent.MemberLeft event) {
        Member member = event.member();
        System.out.println(String.format(
            "🟡 Member LEFT: %s with roles %s", 
            member.address(), 
            member.getRoles()
        ));
        return this;
    }

    private Behavior<ClusterEvent.ClusterDomainEvent> onUnreachableMember(ClusterEvent.UnreachableMember event) {
        Member member = event.member();
        System.out.println(String.format(
            "⚠️  Member UNREACHABLE: %s with roles %s", 
            member.address(), 
            member.getRoles()
        ));
        return this;
    }

    private Behavior<ClusterEvent.ClusterDomainEvent> onReachableMember(ClusterEvent.ReachableMember event) {
        Member member = event.member();
        System.out.println(String.format(
            "✅ Member REACHABLE: %s with roles %s", 
            member.address(), 
            member.getRoles()
        ));
        return this;
    }

    private Behavior<ClusterEvent.ClusterDomainEvent> onAnyOtherEvent(ClusterEvent.ClusterDomainEvent event) {
        System.out.println("📋 Cluster event: " + event.getClass().getSimpleName());
        return this;
    }
}
