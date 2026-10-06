---
name: autowired-style-di
description: dstone 저장소에서 Spring 의존성 주입을 `@Autowired`/생성자 필수 주입 스타일로 쓰는 규칙. 빈 주입 코드를 쓰거나, `@ConditionalOnProperty` 등으로 있을 수도 없을 수도 있는 빈을 다룰 때, `ObjectProvider`/`getIfAvailable()`/`Optional` 주입을 쓰려는 순간에 사용한다.
---

# @Autowired 스타일 의존성 주입

의존성은 `@Autowired`(또는 생성자) **필수 주입**이 항상 성립하도록 설계한다. `ObjectProvider<T>` + `getIfAvailable()` + null 체크는 기본 선택지가 아니다.

## 왜

사용자가 직접 말했다: "난 @Autowired 로 가능한 스타일로만 코딩해". dstone-ai-engine `ChatController`에서 RAG 빈(`dstone.ai.rag.enabled=true`일 때만 존재)을 `ObjectProvider`로 받던 것을 두고 나온 이야기다. 쓰는 쪽은 항상 있어야 하는데 의존 빈은 조건부라서, 그냥 `@Autowired`를 걸면 RAG를 껐을 때 기동이 깨진다.

## 조건부 빈을 만났을 때

먼저 **기본/no-op 대체 빈 패턴**을 제안한다.

1. 공통 인터페이스를 둔다.
2. 진짜 구현은 조건부로 등록한다 (`@ConditionalOnProperty` 등).
3. 같은 인터페이스의 아무 일도 안 하는 구현을 `@ConditionalOnMissingBean`으로 등록한다.
4. 쓰는 쪽은 인터페이스를 평범하게 `@Autowired` 한다.

켜고 끄는 판단이 **빈 등록 쪽**에 모이고, 호출부에는 null 체크가 남지 않는다.

## ObjectProvider를 써도 되는 경우

no-op 구현을 만드는 게 현실적이지 않을 때만 쓴다. 예: Spring AI `VectorStore`처럼 덩치 큰 외부 인터페이스를 통째로 감싸야 하는 경우. 이때도 왜 대체 빈을 못 쓰는지 사용자에게 설명한다.

## 함께 지킬 것

대체 빈 구현을 쓸 때도 람다는 쓰지 않는다 (`java-no-lambdas` skill).
