# ADR 0001: Independent service skeletons

Status: Accepted for TICKET-0001.

## Context

The backlog requires three independently buildable backend projects inside
`enterprise-platform/`, plus two frontend directories, requests, and documentation.

## Decision

Use one parent-less Maven JAR project per backend service. Use `com.parvez` as the
Maven group ID, with artifact IDs matching the service directory names. Target
Java 25 without introducing dependencies or application implementations.

## Alternatives considered

A shared parent POM could centralize build configuration, and a root aggregator
could build all projects at once. Neither is needed for the requested empty,
independently buildable skeletons.

## Consequences

Each service can be verified with its own `mvn clean verify`. Common configuration
is repeated in three small POMs. There is no root reactor build. Later tickets
must verify compatible stack versions before introducing dependencies and record
any change to this build structure. There are no API migrations in this ticket.
