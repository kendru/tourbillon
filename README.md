# Tourbillon: scheduling as a service

Tourbillon is a web service for managing scheduled tasks and event-based workflows. Uses include triggered emails, multi-party transactions, and recurring reminders. I suppose you could even use it as a parser engine!

The goals of this project are twofold:

1. to create an event-driven process engine that can drive the stateful portion of web application.
2. to build a distributed task scheduling system that is able to run with little to no downtime and can scale to support responsive task execution under any load. Currently, the app only supports single-server operation and has no persistence.

## State-based workflows

Many of the complexities of modern web programming have to do with maintaining state for a transaction that involves multiple parties and a span of time. These can usually be modeled as a state machine, which is the model that Tourbillon workflows use. Combining the concept of state machines with multiple side effects that may be triggered on state transitions is a powerful way of solving a great deal of real-world application business logic.

## Scheduled events

The simplest usage of Tourbillon is to schedule future and repeating events. While this cron-like functionality is nothing new, there are few solutions that have all of the following properties:

- Web-based
- Distributed for fault tolerance
- Have email and HTTP capabilities baked in

## Usage

Please see the API description page for details of the message specification.

## Set-up

## Todo

**Priority:** Since persistence will be critical for using Tourbillon for any production app, the first task
will be implementing pluggable persistence layers via configuration.

1. Add additional "subscribers" that can perform different sorts of tasks
2. Support a distributed set-up. Ideally, we should be able to run multiple instances in the same JVM, across JVM intances on a single machine, or across JVM instances on multiple machines.
3. Add authentication and associate jobs with individual accounts
4. Support HTTP Basic and shared private key/HMAC authentication for Tourbillon as well as for Tourbillon to authenticate with the webhook subscriber
5. Support pluggable persistence layers, and include a adapters for Datomic, DynamoDb, Redis, and Mongo out of the box

## License

Copyright © 2014 Andrew S. Meredith

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
