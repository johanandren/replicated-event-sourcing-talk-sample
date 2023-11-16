package com.example;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.crdt.ORSet;
import akka.persistence.typed.javadsl.*;
import akka.projection.grpc.replication.javadsl.ReplicatedBehaviors;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class ReplicatedMovieWatchList
    extends ReplicatedEventSourcedBehavior<
        ReplicatedMovieWatchList.Command,
        ORSet.DeltaOp,
        ORSet<String>> {

  sealed interface Command {}
  public record AddMovie(String movieId) implements Command { }
  public record RemoveMovie(String movieId) implements Command { }
  public record GetMovieList(ActorRef<Set<String>> replyTo) implements Command {}

  public static Behavior<Command> create(
          ReplicatedBehaviors<Command, ORSet.DeltaOp, ORSet<String>> replicatedBehaviors) {
    return Behaviors.setup(context ->
      replicatedBehaviors.setup(replicationContext -> new ReplicatedMovieWatchList(replicationContext))
    );
  }

  public ReplicatedMovieWatchList(ReplicationContext replicationContext) {
    super(replicationContext);
  }

  public ORSet<String> emptyState() {
    return ORSet.empty(getReplicationContext().replicaId());
  }

  public CommandHandler<Command, ORSet.DeltaOp, ORSet<String>> commandHandler() {
    return newCommandHandlerBuilder()
        .forAnyState()
        .onCommand(AddMovie.class, (state, cmd) -> Effect().persist(state.add(cmd.movieId)))
        .onCommand(RemoveMovie.class, (state, cmd) -> Effect().persist(state.remove(cmd.movieId)))
        .onCommand(GetMovieList.class, (state, cmd) -> Effect().reply(cmd.replyTo, state.getElements()))
        .build();
  }

  @Override
  public EventHandler<ORSet<String>, ORSet.DeltaOp> eventHandler() {
    return newEventHandlerBuilder().forAnyState().onAnyEvent(ORSet::applyOperation);
  }
}