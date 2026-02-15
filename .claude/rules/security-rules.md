# Security Rules (CRITICAL)

## Pre-Commit Checklist

Before ANY commit:
- [ ] No hardcoded secrets (API keys, passwords, tokens)
- [ ] All user inputs validated
- [ ] Input sanitization for all API endpoints
- [ ] Error messages don't leak sensitive data

## Secret Management

```kotlin
// NEVER
val apiKey = "sk-proj-xxxxx"

// ALWAYS
val apiKey = System.getenv("API_KEY")
    ?: throw IllegalStateException("API_KEY not configured")
```

## Input Validation

```kotlin
// NEVER: Unvalidated user input in storage keys
val key = "$userId#$targetId"

// ALWAYS: Validate first
require(userId.matches(Regex("^[a-zA-Z0-9_-]+$"))) { "Invalid userId" }
val key = "$userId#$targetId"
```

## Code Review Security Checklist

### CRITICAL
- Hardcoded credentials
- Storage injection risks
- Input validation
- Authentication/authorization

### HIGH
- Error handling (no sensitive data leak)
- Logging (no secrets in logs)
- Dependency vulnerabilities

### MEDIUM
- CORS configuration
- Rate limiting
- Session management
