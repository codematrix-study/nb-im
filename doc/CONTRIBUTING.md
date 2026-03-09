# Contributing to NB-IM

Thank you for your interest in contributing to NB-IM! This document provides guidelines and instructions for contributing.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Workflow](#development-workflow)
- [Coding Standards](#coding-standards)
- [Testing Guidelines](#testing-guidelines)
- [Commit Message Guidelines](#commit-message-guidelines)
- [Pull Request Process](#pull-request-process)

## Code of Conduct

Please be respectful and constructive in all interactions. We aim to maintain a welcoming and inclusive community.

## Getting Started

### Prerequisites

- JDK 17+
- Maven 3.6+
- Docker (for integration tests)
- IDE (IntelliJ IDEA recommended)

### Setup Development Environment

1. Fork the repository
2. Clone your fork:
   ```bash
   git clone https://github.com/your-username/nb-im.git
   cd nb-im
   ```

3. Add remote upstream:
   ```bash
   git remote add upstream https://github.com/original-org/nb-im.git
   ```

4. Install dependencies:
   ```bash
   mvn clean install
   ```

5. Start development services:
   ```bash
   docker-compose up -d redis kafka zookeeper
   ```

## Development Workflow

1. **Create a branch** from `main`:
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes** following our coding standards

3. **Write tests** for your changes

4. **Run tests**:
   ```bash
   mvn test
   ```

5. **Commit your changes** with a clear message

6. **Push to your fork**:
   ```bash
   git push origin feature/your-feature-name
   ```

7. **Create a Pull Request** to the original repository

## Coding Standards

### Java Code Style

- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Use 4 spaces for indentation (no tabs)
- Maximum line length: 120 characters
- Use Lombok annotations to reduce boilerplate

### Naming Conventions

- **Classes**: `PascalCase` (e.g., `MessageHandler`)
- **Methods**: `camelCase` (e.g., `sendMessage`)
- **Constants**: `UPPER_SNAKE_CASE` (e.g., `MAX_CONNECTIONS`)
- **Packages**: `lowercase` (e.g., `com.cw.im.server`)

### Documentation

- Add Javadoc for all public APIs
- Use `@param`, `@return`, `@throws` tags
- Include usage examples for complex APIs

Example:
```java
/**
 * Sends a message to the specified user.
 *
 * @param fromUserId the sender user ID
 * @param toUserId the recipient user ID
 * @param message the message content
 * @return {@code true} if the message was sent successfully
 * @throws MessageSendException if message sending fails
 * @throws IllegalArgumentException if any parameter is null
 *
 * @see Message
 */
public boolean sendMessage(Long fromUserId, Long toUserId, Message message) {
    // implementation
}
```

### Code Organization

- Keep classes focused on a single responsibility
- Prefer composition over inheritance
- Use interfaces for public APIs
- Group related methods together

## Testing Guidelines

### Unit Tests

- Write tests for all new functionality
- Aim for >80% code coverage
- Use JUnit 5 and Mockito
- Follow Given-When-Then pattern

Example:
```java
@Test
@DisplayName("Should send message successfully")
void testSendMessageSuccess() {
    // Given
    Long fromUserId = 1001L;
    Long toUserId = 1002L;
    Message message = createTestMessage();

    // When
    boolean result = messageService.sendMessage(fromUserId, toUserId, message);

    // Then
    assertTrue(result);
    verify(kafkaProducer).send(any(Message.class));
}
```

### Integration Tests

- Test end-to-end workflows
- Use Testcontainers for real dependencies
- Clean up resources after tests

### Performance Tests

- Document performance characteristics
- Include benchmarks for critical paths
- Note any performance regressions

## Commit Message Guidelines

Follow [Conventional Commits](https://www.conventionalcommits.org/) specification:

### Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

- `feat`: New feature
- `fix`: Bug fix
- `docs`: Documentation changes
- `style`: Code style changes (formatting, etc.)
- `refactor`: Code refactoring
- `test`: Adding or updating tests
- `chore`: Build process or auxiliary tool changes
- `perf`: Performance improvement

### Examples

```
feat(handler): add message encryption support

- Implement AES-256 encryption for private messages
- Add encryption key management
- Update protocol version to 2.0

Closes #123
```

```
fix(redis): prevent connection leak on error

- Ensure connections are closed in finally block
- Add connection pool monitoring
- Add tests for error scenarios

Fixes #456
```

## Pull Request Process

### Before Submitting

1. **Update documentation** if needed
2. **Add tests** for new functionality
3. **Run all tests**: `mvn test`
4. **Run code quality checks**: `mvn checkstyle:check`
5. **Update CHANGELOG.md**

### PR Description

Your PR description should include:

- **Summary**: Brief description of changes
- **Motivation**: Why this change is needed
- **Changes**: List of main changes
- **Testing**: How you tested the changes
- **Screenshots**: If applicable (UI changes)
- **Breaking Changes**: If any, describe them
- **Related Issues**: Link to related issues

### PR Checklist

- [ ] Code follows project style guidelines
- [ ] Tests pass locally
- [ ] Documentation updated
- [ ] CHANGELOG.md updated
- [ ] No new warnings generated
- [ ] Added tests for new functionality
- [ ] All tests pass

### Review Process

1. Automated checks must pass
2. At least one maintainer approval required
3. Address all review comments
4. Squash commits if requested
5. Merge after approval

## Getting Help

- Create an issue for bugs or feature requests
- Start a discussion for questions
- Check existing documentation first

## Recognition

Contributors will be acknowledged in the project documentation.

---

Thank you for contributing to NB-IM! 🎉
