# Hooks Documentation

## What Are Hooks?

Hooks are automated actions that run at specific points during development:
- **PreToolUse**: Before a tool executes
- **PostToolUse**: After a tool executes
- **Stop**: When session ends

## Configured Hooks

### PreToolUse Hooks

1. **Build Reminder**
   - Triggers on Kotlin/Go file edits
   - Reminds to run build after changes

2. **Long-Running Command Reminder**
   - Suggests using background mode for builds/tests

### PostToolUse Hooks

1. **Format Reminder**
   - After editing Kotlin files
   - Reminds about `./gradlew spotlessApply`

2. **Println Warning (Advisory)**
   - Warns about println/System.out statements
   - Suggests using proper logging

### Stop Hooks

1. **Debug Code Check**
   - Before session ends
   - Warns about debug statements in modified files

## Hook Behavior

All hooks are configured as **advisory** (warn only, don't block).

## Configuration Location

Hooks are configured in `.claude/settings.json`

## Adding Custom Hooks

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Edit",
        "command": "echo 'Remember to run tests after editing!'"
      }
    ]
  }
}
```

## Troubleshooting

If hooks cause issues:
1. Check `.claude/settings.json`
2. Temporarily disable problematic hooks
3. Report issues to maintainers
