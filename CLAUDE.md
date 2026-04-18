# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Web Monitor is a Spring Boot 3 application designed for web monitoring functionality using JSoup for web scraping. The project uses an in-memory H2 database for development.

## Technology Stack

- **Spring Boot**: 3.2.4
- **Java**: 17
- **Build Tool**: Maven
- **Database**: H2 (in-memory)
- **Web Scraping**: JSoup 1.17.2
- **Persistence**: Spring Data JPA

## Maven Commands

Run the application:
```bash
mvn spring-boot:run
```

Run tests:
```bash
mvn test
```

Run a single test class:
```bash
mvn test -Dtest=ClassName
```

Run a single test method:
```bash
mvn test -Dtest=ClassName#methodName
```

Build the project:
```bash
mvn clean package
```

## Database Access

H2 Console is available at: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:webmonitor`
- Username: `sa`
- Password: (empty)

## Application Configuration

The application uses `application.properties` for configuration:
- Server runs on port 8080
- JPA DDL strategy is `create-drop` (schema recreated on each run)
- SQL logging is enabled with formatting
- DevTools is enabled for hot reload during development

## Package Structure

Base package: `com.webmonitor`
- Main application class: `WebMonitorApplication.java`
