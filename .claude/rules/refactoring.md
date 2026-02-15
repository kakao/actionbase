# Refactoring Guidelines

## Core Principle: Readability Over Deduplication

리팩터링의 목표는 "중복 제거"가 아니라 **"흐름을 읽기 쉽게"** 만드는 것이다.

AI가 중복을 제거하면 인프라를 추출하고 비즈니스 로직을 caller lambda로 남기는 경향이 있음. 결과: `caller → pipeline → caller's lambda → caller` — 3단 점프. 흐름 파악 불가.

올바른 방향: 비즈니스 로직을 파이프라인 안으로 흡수하고, 차이점만 파라미터로 밀어내어 **하나의 체인으로 읽히게** 만든다.

## Reactive Chain Rule

```kotlin
// BAD: 3 pieces, 3 jumps to read
eventFlux = request.toEvents(schema).writeWal(ctx)
executeMutationPipeline(
    events = eventFlux,
    executeGroup = { key, group ->
        sort -> mutate -> writeCDC -> err
    },
).map { toResponse() }

// GOOD: one chain, one read
request
    .toEvents(schema)
    .writeWal(ctx)
    .groupBy { edge }
    .flatMap { mutateGroup() }
    .collectList()
    .map(toResponse)
```

## Rules

1. **One chain, one read** — 핵심 흐름이 하나의 reactive chain으로 읽혀야 함
2. **No 3-jump splits** — `caller → extracted function → caller's lambda → caller` 구조 금지
3. **Absorb, don't extract** — 비즈니스 로직을 파이프라인 안으로 흡수. 차이점만 파라미터로
4. **Tests are necessary but insufficient** — 테스트 통과만으로 리팩터링 완료가 아님. 흐름의 가독성을 코드 리뷰로 검증
5. **Minimal diff** — 가능한 가장 작은 변경. 5줄 수정이 500줄 리라이트보다 낫다
6. **Don't touch adjacent code** — PRD에 명시되지 않은 주변 코드를 리팩터링하지 않을 것

## Checklist (Before PR)

- [ ] 핵심 흐름이 하나의 체인으로 읽히는가?
- [ ] 불필요한 indirection이 추가되지 않았는가?
- [ ] 변경 범위가 PRD 스코프 내인가?
- [ ] 테스트가 동작 변경 없음을 보장하는가?

## Reference

- kakao/actionbase#195 — AI 리팩터링 회고
- kakao/actionbase#199 — 최종 코드 및 크로스 리뷰
