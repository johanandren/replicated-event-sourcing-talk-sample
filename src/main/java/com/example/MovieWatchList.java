package com.example;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.persistence.typed.PersistenceId;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.EventSourcedBehavior;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class MovieWatchList
    extends EventSourcedBehavior<
        MovieWatchList.Command,
        MovieWatchList.Event,
        MovieWatchList.MovieList> {

  sealed interface Command {}
  public record AddMovie(String movieId) implements Command { }
  public record RemoveMovie(String movieId) implements Command { }
  public record GetMovieList(ActorRef<MovieList> replyTo) implements Command {}

  sealed interface Event {}
  public record MovieAdded(String movieId) implements Event {}
  public record MovieRemoved(String movieId) implements Event {}

  public record MovieList(Set<String> movieIds) {
    public MovieList add(String movieId) {
      Set<String> newSet = new HashSet<>(movieIds);
      newSet.add(movieId);
      return new MovieList(Collections.unmodifiableSet(newSet));
    }
    public MovieList remove(String movieId) {
      Set<String> newSet = new HashSet<>(movieIds);
      newSet.remove(movieId);
      return new MovieList(Collections.unmodifiableSet(newSet));
    }
  }

  public static Behavior<Command> create(String userId) {
    return new MovieWatchList(PersistenceId.ofUniqueId("movies-" + userId));
  }

  public MovieWatchList(PersistenceId persistenceId) {
    super(persistenceId);
  }

  public MovieList emptyState() {
    return new MovieList(Collections.emptySet());
  }

  public CommandHandler<Command, Event, MovieList> commandHandler() {
    return newCommandHandlerBuilder()
        .forAnyState()
        .onCommand(AddMovie.class, (state, cmd) -> Effect().persist(new MovieAdded(cmd.movieId)))
        .onCommand(RemoveMovie.class, (state, cmd) -> Effect().persist(new MovieRemoved(cmd.movieId)))
        .onCommand(GetMovieList.class, (state, cmd) -> Effect().reply(cmd.replyTo, state))
        .build();
  }

  @Override
  public EventHandler<MovieList, Event> eventHandler() {
    return newEventHandlerBuilder()
        .forAnyState()
        .onEvent(MovieAdded.class, (state, event) -> state.add(event.movieId))
        .onEvent(MovieRemoved.class, (state, event) -> state.remove(event.movieId))
        .build();
  }
}