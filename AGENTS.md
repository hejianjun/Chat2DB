# AGENTS.md - Chat2DB Development Guide

This document provides essential information for AI coding agents working on the Chat2DB codebase.

## Project Overview

Chat2DB is a multi-database client tool with AI capabilities. It consists of:
- **chat2db-client**: Frontend React/Electron application (TypeScript)
- **chat2db-server**: Backend Spring Boot application (Java 17)

## Build/Lint/Test Commands

### Frontend (chat2db-client)

```powershell
# Install dependencies (must use yarn)
cd chat2db-client
yarn install

# Development server
yarn run start:web          # Web version
yarn run start              # Desktop version (Electron + Web)

# Build
yarn run build:web          # Build for web
yarn run build:desktop      # Build for desktop
yarn run build:prod         # Production build

# Linting
yarn run lint               # Run ESLint

# Package manager: yarn 4.9.1 (REQUIRED - do not use npm)
```

### Backend (chat2db-server)

```powershell
# Build (from chat2db-server directory)
cd chat2db-server
mvn clean install -DskipTests    # Build without tests
mvn clean install                # Build with tests

# Run a single test class
mvn test -Dtest=TableOperationsTest -pl chat2db-server-test

# Run a single test method
mvn test -Dtest=TableOperationsTest#table -pl chat2db-server-test

# Run application
mvn spring-boot:run -pl chat2db-server-start

# Run packaged JAR
java -jar chat2db-server-start/target/chat2db-server-start.jar
```

## Requirements

- **Java**: 17 or higher
  - **Environment Setup**: `$env:JAVA_HOME = "D:\tool\Java\jdk-17"` before compiling Java code
- **Node.js**: 16 or higher  
  - **Environment Setup**: `nvm use 20` before using yarn to compile frontend code
- **Maven**: 3.6.1 or higher
- **Yarn**: 4.x (required for frontend)

## Code Style Guidelines

### TypeScript/React (Frontend)

#### Imports
```typescript
// React imports first
import React, { memo, useCallback, useEffect, useRef } from 'react';

// Third-party libraries
import classnames from 'classnames';
import { message } from 'antd';

// Internal imports (use @ alias for src)
import { useWorkspaceStore } from '@/pages/main/workspace/store';
import DraggableContainer from '@/components/DraggableContainer';

// Styles last
import styles from './index.less';
```

#### Formatting (Prettier)
- Print width: 120 characters
- Single quotes
- Trailing commas: 'all'
- Use `prettier-plugin-organize-imports` for import ordering

#### Naming Conventions
- Components: PascalCase (`WorkspaceLeft`, `TableController`)
- Files: camelCase for utilities, PascalCase for components
- Hooks: camelCase with `use` prefix (`useWorkspaceStore`)
- Stores: camelCase with `Store` suffix (`IWorkspaceStore`)
- Interfaces: PascalCase with `I` prefix (`IConfigStore`, `IStore`)
- Types: PascalCase with `Type` suffix or descriptive names

#### React Patterns
- Use `memo()` for component exports: `const Component = memo(() => { ... })`
- Use functional components with hooks
- Use Zustand for state management
- Use `@/` alias for src imports

#### ESLint Rules
- Max line length: 120 characters
- Prefer arrow functions
- Prefer const
- React hooks rules enforced
- TypeScript strict mode (noImplicitAny: false in tsconfig)

### Java (Backend)

#### Package Structure
```
ai.chat2db.server.{module}
├── controller/    # REST controllers
├── service/       # Business logic interfaces and implementations
├── param/         # Request parameters
├── converter/     # Object converters/mappers
├── vo/            # View Objects (response DTOs)
└── model/         # Domain models
```

#### Naming Conventions
- Classes: PascalCase (`TableServiceImpl`, `TableController`)
- Methods: camelCase (`queryColumns`, `buildSql`)
- Constants: UPPER_SNAKE_CASE (`TABLE_NAME`)
- Parameters: camelCase with `Param` suffix (`TableQueryParam`)
- Services: Interface without suffix, impl with `Impl` suffix
- Converters: `*Converter` suffix
- VOs: `*VO` suffix

#### Code Style
- Use Lombok annotations (`@Data`, `@Builder`, `@Slf4j`, `@AllArgsConstructor`)
- Use Spring annotations (`@Service`, `@RestController`, `@Autowired`)
- Use Jakarta validation (`@Valid`)
- 4-space indentation
- Opening braces on same line

#### Service Layer Pattern
```java
public interface TableService {
    DataResult<Table> query(TableQueryParam param, TableSelector selector);
    ActionResult drop(DropParam param);
}

@Service
@Slf4j
public class TableServiceImpl implements TableService {
    @Autowired
    private PinService pinService;
    
    @Override
    public DataResult<Table> query(TableQueryParam param, TableSelector selector) {
        // Implementation
    }
}
```

#### Controller Pattern
```java
@Slf4j
@ConnectionInfoAspect
@RequestMapping("/api/rdb/table")
@RestController
public class TableController {
    @Autowired
    private TableService tableService;
    
    @GetMapping("/list")
    public WebPageResult<TableVO> list(@Valid TableBriefQueryRequest request) {
        // Implementation
    }
}
```

#### Error Handling
- Use `ActionResult` for operations without return data
- Use `DataResult<T>` for single object returns
- Use `ListResult<T>` for list returns
- Use `PageResult<T>` for paginated results
- Log errors with `@Slf4j` and `log.error()`/`log.warn()`

## Project Structure

```
Chat2DB/
├── chat2db-client/           # Frontend
│   ├── src/
│   │   ├── blocks/           # Reusable UI blocks
│   │   ├── components/       # React components
│   │   ├── pages/            # Page components
│   │   ├── service/          # API service layer
│   │   ├── hooks/            # Custom React hooks
│   │   ├── store/            # Zustand stores
│   │   ├── typings/          # TypeScript types
│   │   ├── utils/            # Utility functions
│   │   └── i18n/             # Internationalization
│   ├── .eslintrc.js
│   ├── .prettierrc
│   └── package.json
│
├── chat2db-server/           # Backend
│   ├── chat2db-server-domain/    # Domain layer
│   │   ├── chat2db-server-domain-api/      # Service interfaces
│   │   └── chat2db-server-domain-core/     # Service implementations
│   ├── chat2db-server-web/       # Web/API layer
│   │   ├── chat2db-server-web-api/         # API controllers
│   │   └── chat2db-server-common-api/      # Common APIs
│   ├── chat2db-server-tools/     # Utilities
│   ├── chat2db-spi/              # Service Provider Interface
│   ├── chat2db-plugins/          # Database plugins (MySQL, PostgreSQL, etc.)
│   ├── chat2db-server-start/     # Main application
│   └── chat2db-server-test/      # Test module
│
└── docker/                   # Docker configuration
```

## Testing

### Backend Tests
- Located in `chat2db-server-test/src/test/java/`
- Use JUnit 5 (`@Test`, `@Order`)
- Extend `BaseTest` for integration tests
- Use `@SpringBootTest` for integration tests

### Running Tests
```powershell
# All tests
mvn test

# Single test class
mvn test -Dtest=ClassName

# Single test method
mvn test -Dtest=ClassName#methodName
```

## Key Technologies

### Frontend
- React 18 with TypeScript
- UmiJS 4 framework
- Ant Design 5
- Zustand for state management
- Monaco Editor for SQL editing
- Electron for desktop app

### Backend
- Spring Boot 3.1
- Java 17
- MyBatis Plus for database access
- Sa-Token for authentication
- Lombok for boilerplate reduction
- MapStruct for object mapping
- H2 as embedded database

## Important Notes

1. **Always use yarn** for frontend package management, not npm
2. **Java 17** is required for the backend
3. **Node 16+** is required for the frontend
4. Tests in the server use `@TestMethodOrder(OrderAnnotation.class)` for execution order
5. Database plugins follow the SPI pattern in `chat2db-spi`
6. Use `@Valid` annotation for request validation in controllers
7. Follow existing patterns when adding new controllers or services