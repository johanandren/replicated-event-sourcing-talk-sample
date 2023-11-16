package com.example;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.persistence.typed.javadsl.CommandHandler;
import akka.persistence.typed.javadsl.EventHandler;
import akka.persistence.typed.javadsl.ReplicatedEventSourcedBehavior;
import akka.persistence.typed.javadsl.ReplicationContext;
import akka.projection.grpc.replication.javadsl.ReplicatedBehaviors;

public class BankAccount
        extends ReplicatedEventSourcedBehavior<
        BankAccount.Command,
        BankAccount.Event,
        BankAccount.State> {

    sealed interface Command {}
    record Deposit(long amount) implements Command {}
    record Withdraw(long amount) implements Command {}
    record GetBalance(ActorRef<Long> replyTo) implements Command {}
    private record AlertOverdrawn(long amount) implements Command {}

    sealed interface Event {}
    record Deposited(long amount) implements Event {}
    record Withdrawn(long amount) implements Event {}
    record Overdrawn(long amount) implements Event {}

    record State(long balance) {
        State applyOperation(Event event) {
            return switch(event) {
                case Deposited deposited -> new State(balance + deposited.amount);
                case Withdrawn withdrawn -> new State(balance - withdrawn.amount);
                case Overdrawn overdrawn -> this;
            };
        }
    }

    public static Behavior<BankAccount.Command> create(
            ReplicatedBehaviors<BankAccount.Command, BankAccount.Event, BankAccount.State> replicatedBehaviors) {
        return Behaviors.setup(context ->
                replicatedBehaviors.setup(replicationContext -> new BankAccount(context, replicationContext))
        );
    }

    private final ActorContext<Command> context;

    public BankAccount(ActorContext<Command> context, ReplicationContext replicationContext) {
        super(replicationContext);
        this.context = context;
    }

    public State emptyState() {
        return new State(0);
    }

    public CommandHandler<Command, Event, State> commandHandler() {
        return newCommandHandlerBuilder()
                .forAnyState()
                .onCommand(Deposit.class, deposit -> Effect().persist(new Deposited(deposit.amount)))
                .onCommand(Withdraw.class, (state, withdraw) -> {
                        if (state.balance - withdraw.amount >= 0)
                            return Effect().persist(new Withdrawn(withdraw.amount));
                        else
                            return Effect().none();
                })
                .onCommand(GetBalance.class, (state, getBalance) -> Effect().reply(getBalance.replyTo, state.balance))
                .onCommand(AlertOverdrawn.class, alert -> Effect().persist(new Overdrawn(alert.amount)))
                .build();
    }

    @Override
    public EventHandler<State, Event> eventHandler() {
        return newEventHandlerBuilder().forAnyState().onAnyEvent((state, event) -> {
            var newState = state.applyOperation(event);
            detectOverdrawn(newState);
            return newState;
        });
    }

    private void detectOverdrawn(State state) {
        if (getReplicationContext().concurrent() &&
                getReplicationContext().replicaId().equals("eu-central-1") &&
                !getReplicationContext().recoveryRunning()) {

            if (state.balance < 0) {
                context.getSelf().tell(new AlertOverdrawn(state.balance));
            }

        }
    }
}
