# Data Model

## Core Entities

### Interaction

The fundamental unit of data in Actionbase.

| Field | Type | Description |
|-------|------|-------------|
| schema | String | Interaction type (likes, follows, views) |
| userId | String | User performing action |
| targetId | String | Target of action |
| action | Enum | CREATE or DELETE |
| timestamp | Long | When action occurred |
| properties | Map | Optional metadata |

### Schema

Defines the structure for a type of interaction.

| Field | Type | Description |
|-------|------|-------------|
| name | String | Unique identifier |
| description | String | Human-readable description |
| indexes | List | Index configurations |
| ttl | Duration | Time-to-live (optional) |

## Storage Model

Row key encoding is finalized. See [Encoding Documentation](/internals/encoding/).

### Row Types

| Type | Code | Purpose |
|------|------|---------|
| Edge State | -3 | Current state (Get queries) |
| Edge Index | -4 | Index entries (Scan queries) |
| Edge Count | -2 | Counters (Count queries) |

### Key Structure

```
[4-byte hash] + [1-byte + source] + [1-byte + table code] + [1-byte + type code] + [additional fields...]
```

- Hash: xxhash32 for region distribution
- Type codes: Negative values (-2, -3, -4)
- Strings: 1-byte length prefix

## Query Patterns

### Forward Query
Get interactions by user.
```
Schema: likes, User: alice → All posts alice liked
```

### Reverse Query
Get users who interacted with target.
```
Schema: likes, Target: post1 → All users who liked post1
```

### Count Query
Get interaction count.
```
Schema: follows, User: alice → Number of users alice follows
```

## CQRS Pattern

- **Mutation Path**: Server → Engine → Storage + Messaging
- **Query Path**: Server → Engine → Storage

Separate read and write optimizations.
