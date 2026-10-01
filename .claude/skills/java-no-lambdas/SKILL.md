---
name: java-no-lambdas
description: dstone 저장소의 Java 코드를 람다(`->`)와 메서드 참조(`::`) 없이 쓰는 코딩 스타일. Java 소스를 새로 만들거나 고치거나 리팩터링할 때(모든 모듈 — dstone-common/boot/batch/batchadmin/ai-engine) 사용한다. Write Java without lambdas or method references.
---

# Java는 람다 없이 쓰기

이 저장소의 Java 코드는 람다 식과 메서드 참조를 쓰지 않는다. 모듈을 가리지 않는 상시 규칙이다.

## 왜

사용자가 2026-09-20에 dstone-ai-engine의 람다를 모두 예전 스타일로 바꿔 달라고 했고, 앞으로 만드는 소스에도 계속 적용하라고 했다. 한 번 하고 끝나는 정리가 아니다.

## 이렇게 쓴다

- 함수형 인터페이스(`Comparator`, `Runnable`, `RowMapper`, `CompletableFuture` 콜백 등) → **익명 내부 클래스**
- Stream 체인 → 가능하면 **일반 for 문**

```java
// 쓰지 않는다
list.sort((a, b) -> a.getName().compareTo(b.getName()));
names.forEach(System.out::println);

// 이렇게 쓴다
list.sort(new Comparator<Item>() {
    @Override
    public int compare(Item a, Item b) {
        return a.getName().compareTo(b.getName());
    }
});
for (String name : names) {
    System.out.println(name);
}
```

## 기존 코드를 고칠 때

- 손대는 코드에 람다가 있으면, 따로 요청이 없어도 익명 클래스/반복문으로 바꾼다.
- 깊게 중첩된 Stream 파이프라인처럼 바꾸면 지나치게 장황해지는 경우에는 **말없이 람다를 남겨 두지 말고 사용자에게 물어본다.**

## 끝내기 전 확인

바꾼 Java 파일에 `->` 와 `::` 가 남아 있지 않은지 검색해 본다.
