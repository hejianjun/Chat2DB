# Chat2DB Project Initialization Guide

This document outlines the steps required to initialize and build the Chat2DB project from scratch.

## Prerequisites

Before starting, ensure you have the following software installed:

- **Java**: Version 17 or higher (Java 17.0.9 was used in this initialization)
- **Node.js**: Version 16 or higher (Node.js 20.19.1 was used in this initialization)
- **Yarn**: Version 4.9.1 or higher (Yarn 4.9.1 was used in this initialization)
- **Maven**: Version 3.6.1 or higher (Maven 3.6.1 was used in this initialization)

## Project Structure

Chat2DB is a full-stack application with the following main components:

- `chat2db-client`: The frontend React/Electron application
- `chat2db-server`: The backend Spring Boot application

## Step-by-step Initialization Process

### 1. Clone the Repository

```bash
git clone <repository-url>
cd Chat2DB
```

### 2. Initialize Client Application

#### Navigate to the client directory:
```bash
cd chat2db-client
```

#### Install client dependencies:
```bash
yarn install
```

This will install all necessary JavaScript/TypeScript dependencies for the frontend.

#### Build the client application:
```bash
yarn run build:web
```

This compiles the React application for production use.

### 3. Initialize Server Application

#### Navigate to the server directory:
```bash
cd ../chat2db-server
```

#### Install server dependencies and build the project:
```bash
mvn clean install -DskipTests
```

This command:
- Downloads all required Maven dependencies
- Compiles all Java components
- Builds executable JAR files for the server
- Skips running tests to speed up the build process

### 4. Verify Build Output

After successful initialization, you should find these executable JAR files:

- **Main Server**: `chat2db-server\chat2db-server-start\target\chat2db-server-start.jar`
- **Web Server**: `chat2db-server\chat2db-server-web-start\target\chat2db-server-web-start.jar`

## Running the Application

### For Development

#### Frontend development server:
```bash
cd chat2db-client
yarn run start:web
```

#### Backend development:
```bash
cd chat2db-server
mvn spring-boot:run -pl chat2db-server-start
```

### For Production

Run the server application:
```bash
java -jar chat2db-server\chat2db-server-start\target\chat2db-server-start.jar
```

The application will be accessible via the web interface or through the Electron desktop application.

## Troubleshooting

### Common Issues

1. **Maven Build Fails with Memory Issues**
   - Ensure you have sufficient memory allocated to Maven
   - You may need to set `MAVEN_OPTS` environment variable with increased heap size

2. **Yarn Install Fails**
   - Clear the yarn cache: `yarn cache clean`
   - Try installing with network timeout tolerance: `yarn install --network-timeout 100000`

3. **JDK Version Issues**
   - Ensure you're using Java 17 or higher
   - Verify with: `java -version`

## Additional Notes

- The project uses a multi-module Maven structure for server components
- Database plugins are built as separate modules (MySQL, PostgreSQL, Oracle, etc.)
- Client-side uses Umi framework with React and Ant Design
- The application supports various databases through plugin architecture
- AI integration features are available using ChatGPT-like APIs

## Development Workflow

After initialization, developers can:
- Modify frontend components in the `chat2db-client` directory
- Modify backend logic in the `chat2db-server` directory
- Test with the development servers before building for production
- Add new database plugins following the existing plugin architecture