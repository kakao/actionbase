# Security Guidelines

## Mandatory Security Checks

Before ANY commit:
- [ ] No hardcoded secrets (API keys, passwords, tokens)
- [ ] All user inputs validated
- [ ] Input sanitization for all API endpoints
- [ ] Error messages don't leak sensitive data

## Secret Management

```kotlin
// NEVER: Hardcoded secrets
val apiKey = "sk-proj-xxxxx"

// ALWAYS: Environment variables
val apiKey = System.getenv("API_KEY")
    ?: throw IllegalStateException("API_KEY not configured")
```

```go
// NEVER: Hardcoded secrets
apiKey := "sk-proj-xxxxx"

// ALWAYS: Environment variables
apiKey := os.Getenv("API_KEY")
if apiKey == "" {
    log.Fatal("API_KEY not configured")
}
```

## Storage Security

```kotlin
// NEVER: Unvalidated user input in storage keys
val key = "$userId#$targetId"  // BAD if not validated

// ALWAYS: Validate before use
require(userId.matches(Regex("^[a-zA-Z0-9_-]+$"))) { "Invalid userId" }
val key = "$userId#$targetId"
```

## Security Response Protocol

If security issue found:
1. STOP immediately
2. Use **security-reviewer** agent
3. Fix CRITICAL issues before continuing
4. Rotate any exposed secrets
5. Review entire codebase for similar issues
