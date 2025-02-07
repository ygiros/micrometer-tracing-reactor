# micrometer-tracing-reactor

Small standalone project to test [Micrometer Tracing](https://docs.micrometer.io/tracing/reference/index.html) with [OpenTelemetry](https://opentelemetry.io/) bridge and [reactive programming](https://projectreactor.io/).

## Requirements

---
- JDK17
- Docker Engine / Docker CLI / Docker Compose plugin

## Technical stack

---
- Spring Boot 3
- Spring Webflux / Reactor
- Micrometer Tracing + OTEL Bridge

(cf `build.gradle` for details)

## External services

---
External services are available with docker containers. Check `docker/compose.yaml` for details.

### Start services
```shell
cd ${PATH_TO_REPO}/docker

docker compose up -d
```

### Services
#### [Jaeger](https://www.jaegertracing.io/)
- Open source distributed tracing platform
- Native support for OpenTelemetry
- Jaeger UI available on http://localhost:16686/

## Project description

---
Gradle multi-module project
- reactive-front (Spring Boot web server)
- reactive-delegate (Spring Boot web server)

![docs/project_description.svg](docs/project_description.svg)

| Step | Description                                                          |
|------|----------------------------------------------------------------------|
| 1    | Client requests 'front' web server to compute square value of 2      |
| 2    | Front is a lazy server and delegate compute to 'delegate' web server |
| 3    | Delegate computes square value and returns result to front           |
| 4    | Front returns result to client                                       |

Endpoints have {version}

| Version | Description                                                                                        |
|---------|----------------------------------------------------------------------------------------------------|
| v1      | Trace request with Micrometer Tracing but no context propagation                                   |
| v2      | Trace request with Micrometer Tracing with context propagation between front and delegate services |

Objectives
- Use Micrometer Tracing with OpenTelemetry Bridge to trace HTTP request from client
  - Use automatic instrumentation available with spring-webflux `WebClient`
  - Use Micrometer Observation API
    - Create manual observation on reactor sequences
    - Alter current observation to add KeyValue inside reactor sequence
    - Use context propagation with Baggage injection inside reactor sequence
  - Use OTEL Tracer API
    - Log current Baggage (to validate context propagation)