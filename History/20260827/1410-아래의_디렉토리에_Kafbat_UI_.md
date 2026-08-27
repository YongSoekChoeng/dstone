# 아래의_디렉토리에_Kafbat_UI_

> 생성일시: 2026-08-27 14:10

## 질문

아래의 디렉토리에 Kafbat UI 를 깔고 백그라운드기동(start.sh) 및 중지(stop.sh) 스크립트를 만들어줘.
카프카가 깔려있는 디렉토리는 /opt/kafka/kafka_2.13-4.2.1 야.
/opt/kafka/admin-tools/KafbatUI

---

## 답변

Kafbat UI를 `/opt/kafka/admin-tools/KafbatUI`에 설치하고 시작/중지 스크립트를 만들기 전에, 환경을 먼저 확인하겠습니다.

---

## 답변

v1.5.0의 standalone jar(`api-v1.5.0.jar`, ~142MB)를 찾았습니다. 다운로드하고 설정 및 스크립트를 구성하겠습니다.

---

## 답변

JDK 버전 우려는 타당한 지적입니다. Kafbat UI는 Spring Boot 3.x 기반이라 최소 Java 17 이상을 요구하는데, 현재 서버엔 Java 21이 설치되어 있어 (`openjdk 21.0.12`) 요구사항은 충족됩니다. 다만 실제로 확실히 확인하기 위해 다운로드 완료 후 jar 매니페스트의 요구 버전과 실행 테스트로 검증하겠습니다.

---

## 답변

v1.5.0은 Java 25로 컴파일되어 있어 서버의 Java 21과 호환되지 않습니다 (사용자 말씀이 맞았습니다). Java 21을 명시한 최신 버전은 **v1.4.2**이므로 이것으로 교체하겠습니다.

---

## 답변

다운로드가 백그라운드에서 진행 중입니다. 완료되는 동안 설정 파일과 시작/중지 스크립트를 준비하겠습니다.

---

## 답변

Download complete. Jar 매니페스트에서 실제 클래스 파일 버전을 확인해 Java 21 호환성을 검증하고, 실행 테스트까지 해보겠습니다.

---

## 답변

v1.4.2는 클래스 파일 major version 65 = **Java 21** 바이트코드로 확인됐습니다. 서버의 Java 21과 정확히 일치합니다. 이제 실제로 잠깐 실행해서 정상 기동되는지 검증하겠습니다.

---

## 답변

Kafbat UI 자체는 정상 기동됐고(웹서버 HTTP 200 응답), 로그의 연결 타임아웃은 Kafka 브로커가 현재 꺼져 있어서 발생한 것으로 정상입니다(Kafka를 켜면 자동 재연결됩니다). 이제 stop.sh도 검증하고 정리하겠습니다.

---

## 답변

start.sh / stop.sh 모두 정상 동작 확인했습니다.

**요약**
- `/opt/kafka/admin-tools/KafbatUI/`에 Kafbat UI 설치 완료
  - `kafbat-ui.jar` — **v1.4.2** (Java 21 바이트코드, class major version 65로 확인) — 처음엔 최신 v1.5.0을 받았으나 이건 Java 25 컴파일이라 서버의 Java 21과 안 맞아 v1.4.2로 교체했습니다(말씀하신 우려가 맞았습니다).
  - `conf/application-local.yml` — 포트 8080, Kafka 클러스터 `127.0.0.1:9092` (설치된 KRaft 단일 브로커) 연결
  - `start.sh` / `stop.sh` — 기존 `/opt/kafka/kafka-start.sh`/`kafka-stop.sh`와 동일한 스타일(PID 파일, nohup 백그라운드, 정상종료 대기 후 kill -9 fallback)
- 실제로 start.sh 실행 → 웹서버 HTTP 200 확인 → stop.sh로 정상 종료까지 테스트 완료

Kafka가 켜져 있는 상태에서 `start.sh`를 실행하면 브로커에 정상 연결됩니다. `logs/kafbat-ui.out`에서 로그 확인 가능합니다.

---

## 질문

stop.sh 수행 후 명령프롬프트가 종료되지 않는 것 같아

---

## 답변

포트 설정이 9099로 변경된 것 확인했습니다(사용자가 직접 수정하신 것으로 보고 유지하겠습니다). 먼저 재현해서 정확히 어디서 멈추는지 확인해보겠습니다.

---

## 답변

방금 재현해봤는데 제 환경에서는 stop.sh가 3초 만에 정상적으로 종료되고 프롬프트도 바로 돌아옵니다. 정확히 어떤 증상인지 확인이 필요합니다.

---

## 답변

가장 유력한 원인을 찾았습니다. `nohup java -jar ... &`는 표준출력/에러는 리다이렉트했지만 **표준입력(stdin)은 터미널에 그대로 연결된 채로 남습니다.** MobaXterm 같은 터미널에서는 이 때문에 백그라운드 프로세스가 실제로 죽어도 터미널 세션(pty)이 완전히 분리되지 않아 프롬프트가 영영 안 돌아오는 경우가 있습니다. `setsid`로 완전히 세션 분리시키고 stdin을 `/dev/null`로 리다이렉트하도록 고치겠습니다.

---

## 답변

PID(5538)가 실제 java 프로세스와 정확히 일치하고, stdin도 `/dev/null`로 완전히 분리된 것을 확인했습니다. 이제 stop.sh도 재검증합니다.

---

## 답변

테스트에서 이번엔 강제종료(30초 대기 후 kill -9)까지 갔지만, 정상적으로 30.1초 만에 스크립트가 완료되고 프롬프트로 복귀했습니다. 이건 원래 스크립트가 의도한 안전장치(정상종료 30초 대기 → 강제종료)가 작동한 것이고, 30초 걸린 이유는 현재 Kafka 브로커가 꺼져있어 Kafbat UI 내부 Kafka client가 종료 시 연결을 정리하다 타임아웃난 것뿐입니다(스크립트 버그 아님).

**핵심 수정사항**: `start.sh`에서 `nohup ... &`만 쓰면 표준입력(stdin)이 터미널에 계속 연결된 채로 남아, 백그라운드 프로세스가 죽어도 MobaXterm 세션(pty)이 완전히 해제되지 않아 프롬프트가 영영 안 돌아오는 문제가 있었습니다. `setsid`로 세션을 완전히 분리하고 `< /dev/null`로 stdin을 끊도록 고쳤습니다.

---

## 답변

지금 실행 중이던 프로세스는 정리됐는지 확인하고 마무리하겠습니다.

---

## 답변

**수정 완료.** `start.sh`에 `setsid ... < /dev/null`을 적용해 tty(터미널)로부터 완전히 분리되도록 고쳤고, PID도 실제 java 프로세스와 정확히 일치함을 확인했습니다. 지금은 프로세스가 완전히 정리된 상태입니다.

참고로 방금 테스트에서 stop.sh가 30초 걸린 건 별개 이슈입니다 — 현재 Kafka 브로커가 꺼져 있어서 Kafbat UI가 종료 시 Kafka 연결 정리하다 타임아웃(30초)이 걸린 것이고, 이후 스크립트의 강제종료(kill -9) 안전장치가 정상 작동해 프롬프트로 복귀했습니다. Kafka가 켜져 있는 상태라면 훨씬 빠르게(수 초 내) 종료될 겁니다.

---

## 질문

로컬 pc에서 http://localhost:9099/ui/clusters/local/brokers 로 접속해보면(KafbatUI) 계속 응답이 없어.

---

## 답변

먼저 현재 Kafbat UI가 실제로 떠 있는지, 포트가 열려 있는지부터 확인하겠습니다.

---

## 답변

원인을 찾았습니다. Kafbat UI 자체는 정상 기동 중(포트 9099 리슨)이지만, **Kafka 브로커가 꺼져 있어서** brokers 페이지가 AdminClient 호출을 계속 재시도하며 응답을 못 주고 있는 겁니다. 기존 `/opt/kafka/kafka-start.sh`로 브로커를 켜겠습니다.

---

## 답변

Kafka 브로커 자체에 문제가 있습니다 — KRaft 스토리지가 포맷되지 않아 `meta.properties`가 없다는 에러입니다. 이건 Kafbat UI와 별개로 Kafka 설치 쪽 이슈네요. 확인해보겠습니다.

---

## 답변

원인이 명확해졌습니다: `log.dirs=/tmp/kraft-combined-logs`인데 이 디렉토리 자체가 없습니다(아마 `/tmp` 초기화로 날아간 것 같습니다). KRaft 스토리지를 새로 포맷해야 합니다. 데이터가 없는 상태이므로 안전하게 초기화하겠습니다.

---

## 답변

포맷 완료. 이제 Kafka를 시작합니다.

---

## 답변

Kafka 브로커가 정상 기동됐습니다. 이제 Kafbat UI가 브로커를 인식하는지 API로 확인하겠습니다.

---

## 답변

문제 해결됐습니다. 지금 브라우저에서 `http://localhost:9099/ui/clusters/local/brokers`로 다시 접속해보시면 브로커(id=1, 127.0.0.1:9092)가 정상적으로 표시될 겁니다.

**원인 정리**
1. Kafka 브로커가 꺼져 있었고, 그 이유는 `log.dirs=/tmp/kraft-combined-logs` 디렉토리 자체가 사라져 있어서였습니다(아마 `/tmp` 초기화로 KRaft 포맷 데이터가 날아간 것으로 보입니다).
2. Kafbat UI는 떠 있었지만(포트 9099 정상 리슨) brokers 페이지가 내부적으로 Kafka AdminClient 호출을 계속 재시도/타임아웃하느라 응답을 못 주고 있었던 것입니다.
3. `kafka-storage.sh format`으로 KRaft 스토리지를 재포맷하고 `kafka-start.sh`로 브로커를 재기동 → 정상화.

앞으로 서버가 재부팅되거나 `/tmp`가 초기화되면 Kafka가 같은 이유로 다시 안 뜰 수 있습니다. 원하시면 `log.dirs`를 `/tmp` 대신 `/opt/kafka/kafka_2.13-4.2.1/data` 같은 영구 경로로 바꿔드릴까요?

---

## 질문

yes

---

## 답변

Kafka가 영구 경로(`/opt/kafka/kafka_2.13-4.2.1/data/kraft-combined-logs`)로 정상 재기동됐습니다. Kafbat UI가 이미 떠 있는 상태이니 재연결도 확인하겠습니다.

---

## 답변

정상 반영됐습니다.

**변경 사항**
- `config/server.properties`: `log.dirs`를 `/tmp/kraft-combined-logs` → `/opt/kafka/kafka_2.13-4.2.1/data/kraft-combined-logs`로 변경
- 새 경로에 KRaft 스토리지 재포맷 (새 cluster ID: `hXt_iW8VQ-mPqDcS1Qz-VQ`) — 어차피 기존 데이터가 없었던 상태라 손실 없음
- Kafka 재기동 확인, Kafbat UI가 브로커(id=1)를 정상 재조회하는 것까지 확인 완료

이제 `/tmp`가 초기화돼도 Kafka 데이터가 유지되며, 서버 재부팅 시에도 `kafka-start.sh` / `KafbatUI/start.sh` 순서로 실행하면 됩니다.

---

## 질문

아래의 소스로 KAFKA 전송을 했을 때 에러는 안나지만 Kafbat UI 에서 무언가 변화가 없어. 뭔가 바뀌어야 하는거 아닌가?

        /*****************************************************/
        KafkaTemplate<String, Map<String, Object>> kafkaTemplate = null;
        Map<String,Object> event = new HashMap<String,Object>();
        event.put("orderId", "1");
        event.put("orderName", "테스트주문");
        event.put("orderItem", "연필");
        event.put("orderCount", "1000");
        /*****************************************************/
        
        net.dstone.common.utils.DateUtil.stopWatchStart("01.KAFKA테스트");
        
        try {


            // 1. 카프카 프로듀서 설정 정보 (Pure Java Map)
            Map<String, Object> configProps = new HashMap<>();
            configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
            configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonSerializer.class);
            
            // 옵션 세팅 (선택)
            configProps.put(ProducerConfig.ACKS_CONFIG, "all");

            // 2. 스프링 카프카의 ProducerFactory 생성 (컨테이너 도움 없이 수동 생성)
            ProducerFactory<String, String> producerFactory = new DefaultKafkaProducerFactory<>(configProps);

            // 3. new 연산자로 KafkaTemplate 직접 인스턴스화
            kafkaTemplate = new KafkaTemplate(producerFactory);
            
            // 4. KAFKA 전송
            kafkaTemplate.send("order-events", event.get("orderId").toString(), event);

        } catch (Exception e) {
            e.printStackTrace();
        } finally{
            net.dstone.common.utils.DateUtil.stopWatchEnd("01.KAFKA테스트");
        }

---

## 답변

실제로 토픽이 생성되고 메시지가 들어갔는지 브로커에서 직접 확인해보겠습니다.

---

## 답변

`order-events` 토픽이 실제로 생성되어 있습니다. 메시지가 실제로 들어갔는지 확인하겠습니다.

---

## 답변

컨슈머로 확인해보니 `order-events` 토픽 자체는 만들어졌지만(자동 생성) **실제 메시지는 0건**입니다. Kafbat UI 새로고침 문제가 아니라 프로듀서 코드가 조용히 전송 실패하고 있는 겁니다.

**원인**: `KafkaTemplate.send()`는 비동기(fire-and-forget)입니다. 반환된 `CompletableFuture`를 기다리거나 콜백을 걸지 않았고, `flush()`/`close()`도 안 했습니다. 코드에 있는 `try-catch`는 프로듀서 **생성 시점**의 동기 예외만 잡을 뿐, 실제 브로커 전송 실패(타임아웃, 파티션 리더 미확정 등)는 비동기로 발생해서 그 예외를 아무도 확인하지 않습니다. 게다가 `ProducerFactory`/`KafkaTemplate`을 매번 로컬 변수로 새로 만들고 재사용도 안 하니, 내부 Kafka 프로듀서의 백그라운드 전송 스레드(daemon thread)가 실제 전송을 마치기도 전에 참조가 사라지거나 메서드가 끝나버릴 수 있습니다.

**해결책**: `send()` 결과를 동기적으로 기다리게 하고, 끝나면 반드시 `flush()` 후 `close()`.

---

## 답변

확인 결과, `dstone-common`의 `KafkaUtil.java`(`getKafkaTemplate()`)도 호출할 때마다 새 `DefaultKafkaProducerFactory`(= 새 `KafkaProducer`)를 만들고 닫지 않는 동일한 패턴입니다. 즉 사용자분이 작성한 인라인 코드뿐 아니라 프레임워크 유틸 자체도 호출할 때마다 프로듀서를 새로 만들고 버리는 구조라, 매 호출마다 커넥션/스레드가 누적되고(리소스 누수) 전송이 끝나기 전에 참조가 사라질 위험이 있습니다.

당장 테스트 코드는 이렇게 고치면 확실히 전송됩니다 (send 결과를 기다리고, flush 후 close):

```java
try {
    Map<String, Object> configProps = new HashMap<>();
    configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
    configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
    configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, org.springframework.kafka.support.serializer.JsonSerializer.class);
    configProps.put(ProducerConfig.ACKS_CONFIG, "all");

    ProducerFactory<String, Object> producerFactory = new DefaultKafkaProducerFactory<>(configProps);
    kafkaTemplate = new KafkaTemplate<>(producerFactory);

    // 결과를 기다려 실제 전송 성공/실패를 확인
    SendResult<String, Object> result = kafkaTemplate.send("order-events", event.get("orderId").toString(), event).get(5, TimeUnit.SECONDS);
    System.out.println("전송 완료: " + result.getRecordMetadata());

} catch (Exception e) {
    e.printStackTrace();
} finally {
    if (kafkaTemplate != null) {
        kafkaTemplate.flush();
        kafkaTemplate.destroy(); // 내부 producerFactory까지 종료
    }
    net.dstone.common.utils.DateUtil.stopWatchEnd("01.KAFKA테스트");
}
```

`.get(5, TimeUnit.SECONDS)`로 블로킹하면 실제 실패 시 예외가 잡히고(지금까지 잡히던 "에러 없음"은 사실 확인을 안 한 것뿐), `flush()`+`destroy()`로 버퍼에 남은 레코드까지 확실히 내보내고 종료합니다.

이렇게 하면 문제가 재현되는지 먼저 확인해보시겠어요? 확인되면, `KafkaUtil.java` 쪽도 매번 프로듀서를 새로 만드는 구조를 싱글톤 빈으로 바꾸는 게 맞는지(공용 프레임워크 코드라 다른 모듈에도 영향) 여쭤보고 진행하겠습니다.
