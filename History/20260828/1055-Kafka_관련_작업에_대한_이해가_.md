# Kafka_관련_작업에_대한_이해가_

> 생성일시: 2026-08-28 10:55

## 질문

Kafka 관련 작업에 대한 이해가 필요해.
net.dstone.boot.sample.kafka.KafkaService.publish(String, String, Map<String, Object>) 를 통해서 kafka에 정보를 보내고
net.dstone.boot.sample.kafka.KafkaService.consume(Map<String, Object>) 를 통해서 kafka로부터 정보를 받는 것은 알겠어.
하지만 KafkaService.publish 에서 시작한 요청이 Kafka 에 어떻게 저장되고 동작되는지 그리고 kafbat 에서 어떻게 표현되는지 그리고 그 의미들은 무엇인지 알고싶어.
(publisher, topic, partition, broker, consumer그룹, consumer 등등...)
그리고 어떤 메카니즘으로 KafkaService.consume 으로 돌아오는지도 자세하게 설명해줘.

그리고 kafbat에서 보이는 Topics > __consumer_offsets 가 뭔지 모르겠어. 이것도 설명해줘.

---

## 답변

코드베이스의 실제 설정을 확인한 뒤 설명해 드리겠습니다.

---

## 답변

코드와 설정을 확인했습니다. 실제 이 프로젝트의 구현을 기준으로 전체 흐름을 설명드립니다.

## 0. 관련 설정 (실제 값)

`dstone-boot/conf/application.yml`:
```yaml
kafka:
  bootstrap-servers: localhost:9092
  producer:
    key-serializer: StringSerializer
    value-serializer: JsonSerializer
    acks: all
    properties: { enable.idempotence: true }
  consumer:
    group-id: order-service-group
    key/value-deserializer: ErrorHandlingDeserializer (내부적으로 String/Json Delegate)
    auto-offset-reset: earliest
    properties: { isolation.level: read_committed }
```

그리고 `KafkaService.java`의 `@KafkaListener(topics = "order-events", groupId = "inventory-service-group")` — **여기서 groupId를 명시적으로 지정**하고 있어서, yml의 `consumer.group-id: order-service-group`은 이 리스너에는 적용되지 않고 **`inventory-service-group`이 실제로 사용됩니다.** (yml의 group-id는 이 리스너 외에 groupId 미지정 리스너나 다른 KafkaConsumer 빈이 있을 때만 기본값으로 쓰입니다.) 나중에 컨슈머 그룹을 kafbat에서 찾을 때 헷갈릴 수 있는 지점이라 짚어드립니다.

---

## 1. publish() → Kafka 브로커까지의 흐름

```java
kafkaTemplate.send("order-events", "1", param);
```

1. **직렬화(Serialize)**: key `"1"`은 `StringSerializer`로, value(Map)는 `JsonSerializer`로 바이트 배열로 변환됩니다.
2. **파티셔닝(Partitioning)**: key가 존재하므로 `hash(key) % 파티션수` 공식으로 어느 파티션에 보낼지 결정됩니다. 코드 주석대로 "같은 orderId(key)는 항상 같은 파티션"에 들어가야 그 주문에 대한 이벤트 순서가 보장되는데, 지금 코드는 `key`로 고정값 `"1"`을 넘기고 있어서(진짜 orderId가 아님) **테스트 코드에서는 모든 메시지가 항상 같은 파티션 하나에만 쌓입니다.** 운영 코드라면 `param.get("orderId")`를 key로 써야 의도한 동작이 됩니다.
3. **버퍼링/배치**: 프로듀서는 내부적으로 `RecordAccumulator`에 메시지를 모았다가 배치로 전송합니다(짧은 지연으로 처리량 확보).
4. **브로커 전송 및 저장**: 해당 파티션의 **리더 브로커**가 메시지를 받아 로컬 디스크의 커밋 로그(세그먼트 파일)에 append합니다. 각 메시지에는 파티션 내에서 단조 증가하는 **offset**이 부여됩니다.
5. **복제(Replication)**: 파티션에 replication factor > 1이면, 팔로워 브로커들이 리더로부터 데이터를 복제합니다. `acks=all` 설정 때문에 프로듀서는 **ISR(In-Sync Replicas) 전체가 메시지를 받았다는 응답**을 받아야 성공(ack)으로 간주합니다 → 내구성(durability) 최우선 설정.
6. **멱등성(Idempotence)**: `enable.idempotence=true`이므로 프로듀서마다 PID(Producer ID)+시퀀스 번호가 붙어, 네트워크 재시도로 인한 중복 저장(같은 메시지 두 번 append)이 브로커 단에서 걸러집니다.

즉 `publish()` 호출 시점에 브로커에 물리적으로 저장이 완료되며, `consume()`은 이 저장된 로그를 나중에 별도로 "읽어가는" 완전히 비동기·분리된 과정입니다.

---

## 2. Kafka 저장 구조 (broker / topic / partition)

- **Broker**: Kafka 서버 프로세스 하나(현재 설정은 `localhost:9092` 단일 브로커).
- **Topic**: `order-events` — 메시지를 논리적으로 묶는 이름. 물리적 실체는 없고 파티션들의 집합.
- **Partition**: Topic은 1개 이상의 파티션으로 나뉩니다(안 만들어져 있으면 브로커의 auto-create 설정에 따라 기본 파티션 수로 자동 생성됨). 각 파티션은 **완전히 독립적인, append-only 로그**이며 자체 offset 시�퀀스를 가집니다. 파티션이 여러 개면 병렬 처리가 가능하지만, **순서 보장은 "같은 파티션 내에서만"** 성립합니다 — 그래서 key로 파티션을 고정시키는 전략(코드 주석)이 필요한 것입니다.
- **Segment**: 파티션 로그는 내부적으로 여러 세그먼트 파일로 쪼개져 저장되고, retention 설정(시간/용량)에 따라 오래된 세그먼트가 삭제됩니다.

kafbat-ui의 **Topics > order-events** 화면에서 보이는 Partitions 탭이 바로 이 구조를 그대로 보여주는 것이고, 각 파티션 행에 표시되는 숫자가 그 파티션의 현재 최신 offset(Log End Offset)입니다.

---

## 3. consume()으로 돌아오는 메커니즘

`@KafkaListener(topics = "order-events", groupId = "inventory-service-group")`가 붙으면 Spring이 애플리케이션 기동 시 다음을 자동 구성합니다.

1. **리스너 컨테이너 생성**: Spring Kafka가 `ConcurrentMessageListenerContainer`를 만들고, 내부적으로 실제 Apache Kafka `Consumer` 클라이언트를 하나(또는 concurrency 설정만큼 여러 개) 생성합니다.
2. **그룹 코디네이터 접속 & 파티션 할당**: 이 Consumer는 `inventory-service-group`이라는 **Consumer Group ID**로 브로커의 **Group Coordinator**(브로커 중 하나가 이 역할을 겸함)에 접속합니다. 코디네이터는 같은 그룹 내 컨슈머들에게 `order-events`의 파티션들을 분배합니다(파티션이 1개면 그 그룹 안 첫 번째 컨슈머만 담당하고 나머지는 idle).
3. **poll 루프**: Consumer는 내부적으로 무한 루프를 돌며 `poll()`을 호출해 할당받은 파티션에서 새 메시지를 가져옵니다. `auto-offset-reset: earliest`이므로, 이 그룹이 해당 파티션을 **처음** 구독하는 경우(커밋된 offset이 없는 경우) 로그의 맨 처음부터 읽습니다.
4. **역직렬화**: `ErrorHandlingDeserializer`가 실제 델리게이트(`StringDeserializer`/`JsonDeserializer`)로 역직렬화를 시도하고, 실패 시 예외를 메시지에 담아 리스너 앞단에서 처리(리스너 자체가 죽는 것을 방지)합니다. `spring.json.trusted.packages: "net.dstone.*"`는 JsonDeserializer가 역직렬화 허용할 패키지를 화이트리스트로 제한하는 보안 설정입니다.
5. **메서드 호출**: 역직렬화된 값이 `KafkaService.consume(Map<String,Object> param)`의 파라미터로 그대로 바인딩되어 **리플렉션으로 이 메서드가 호출**됩니다. 이 시점이 사용자가 말한 "consume으로 돌아오는" 순간입니다 — 실제로는 별도 스레드(리스너 컨테이너 스레드)에서 poll → 역직렬화 → 메서드 invoke 순으로 진행되는 것이지, publish 스레드와 직접 연결된 흐름이 아닙니다.
6. **Offset 커밋**: 처리가 끝나면(기본은 auto-commit, 명시적 설정 없으므로 Spring Kafka 기본값인 `ackMode=BATCH` 방식으로 poll 배치 처리 완료 후 자동 커밋) 그 그룹의 "다음에 읽을 위치"가 갱신됩니다. 이 커밋 정보가 바로 다음 섹션의 `__consumer_offsets`에 기록됩니다.
7. `isolation.level: read_committed`: 프로듀서 쪽 트랜잭션(현재 코드는 트랜잭션 API를 쓰진 않지만 옵션이 켜져 있음)이 있을 경우, 커밋되지 않은(진행 중이거나 abort된) 트랜잭션 메시지는 이 컨슈머에게 보이지 않게 필터링합니다.

---

## 4. kafbat-ui에서 각 요소의 의미

| kafbat 화면 | 의미 |
|---|---|
| **Brokers** | 클러스터를 구성하는 Kafka 서버 목록(현재는 `localhost:9092` 1대) |
| **Topics > order-events** | 논리적 토픽 하나 |
| └ Partitions | 그 토픽의 물리적 파티션들, 각각 리더 브로커/ISR/현재 offset 범위(Start~End) 표시 |
| └ Messages 탭 | 특정 파티션의 실제 저장된 레코드(key/value/offset/timestamp)를 브라우저에서 조회 |
| **Consumers** | 등록된 컨슈머 그룹 목록 — 여기서 `inventory-service-group`을 찾아야 지금 이 `consume()` 리스너의 상태(Lag, 담당 파티션, 마지막 커밋 offset)를 볼 수 있습니다. `order-service-group`이 아님에 주의. |
| └ Lag | (파티션의 최신 offset) − (그 그룹이 커밋한 offset) = 아직 못 읽은 메시지 수 |

---

## 5. `__consumer_offsets`란?

이건 **사용자가 만든 토픽이 아니라 Kafka 브로커가 클러스터 생성 시 자동으로 만드는 내부(internal) 시스템 토픽**입니다.

- **역할**: 위 3단계 "Offset 커밋"에서 각 컨슈머 그룹이 "토픽 X의 파티션 Y까지 읽었다"고 커밋하는 정보를 저장하는 저장소입니다. 옛날 Kafka(0.8 이전)는 이 정보를 Zookeeper에 저장했지만, 지금은 Kafka 자체 토픽에 저장하도록 바뀌었습니다.
- **메시지 형태**: key = `(group.id, topic, partition)` 조합, value = `(committed offset, 메타데이터, 커밋 시각)`. 예를 들어 `(inventory-service-group, order-events, 0)` → `offset=17` 같은 식입니다.
- **왜 "토픽"으로 보이는가**: 내부적으로 일반 토픽과 동일한 파티션/로그 구조를 그대로 재사용해서 구현했기 때문에 kafbat 같은 UI의 Topics 목록에도 함께 노출됩니다. 기본적으로 **50개 파티션**을 갖고 생성되며(`offsets.topic.num.partitions` 설정), 어떤 그룹의 offset이 그중 몇 번 파티션에 저장될지는 `hash(group.id) % 50`으로 결정됩니다.
- **compact 정책**: 이 토픽은 `cleanup.policy=compact`로 설정되어 있어서, 오래된 이력을 다 남기지 않고 **key(그룹+토픽+파티션)당 가장 최신 값만** 남도록 백그라운드에서 로그 컴팩션이 일어납니다 — 커밋은 계속 갱신되는 값이지 이력을 쌓을 필요가 없기 때문입니다.
- **직접 다룰 필요 없음**: 사람이 이 토픽에 직접 쓰거나 읽을 일은 없습니다. kafbat의 **Consumers** 탭이 이 내부 토픽을 대신 조회해서 그룹별 Lag/커밋 offset을 사람이 보기 편하게 가공해 보여주는 것이므로, offset 관련 정보를 확인하고 싶다면 `__consumer_offsets`를 직접 열어보기보다 **Consumers > inventory-service-group**을 보시면 됩니다.

---

**요약 그림**

```
publish("order-events","1",param)
   └▶ Producer(직렬화→파티션결정→배치) ─▶ Broker(파티션 리더, ISR 복제, acks=all 응답)
                                                 │  append to log(offset N)
                                                 ▼
                                     order-events 파티션 로그(디스크)
                                                 │  (비동기, 별개 흐름)
                          Consumer(inventory-service-group) poll()
                                                 │  역직렬화
                                                 ▼
                                        KafkaService.consume(param) 호출
                                                 │
                                                 ▼
                          offset 커밋 → __consumer_offsets 토픽에 기록
```

---

## 질문

지금 dstone-boot 프로젝트가 dstone-common 을 참조한다고 할 때 아래와 같이 basePackages 를 잡는게 맞아?
아니면 basePackages={"net.dstone"} 하는게 맞아?
혹은 basePackages={"net.dstone.common", "net.dstone.boot"} 하는게 맞을까?

@SpringBootApplication
@ComponentScan(basePackages={"net.dstone.boot"})
public class DstoneBootApplication{
    
}

---

## 답변

결론부터 말씀드리면 **지금 코드 그대로(`basePackages={"net.dstone.boot"}`)가 맞습니다.** 실제로 프로젝트에 이미 그렇게 되어 있고(`DstoneBootApplication.java:21`), 이건 우연이 아니라 이 프로젝트의 설계 패턴입니다.

## 근거

1. **`dstone-common`은 컴포넌트 스캔 대상이 아니라 명시적 `@Import` 대상으로 설계됨**

   `net.dstone.boot.common.config.Config`를 보면:
   ```java
   @EnableAsync
   @Configuration
   @Import({
       ConfigAspect.class, ConfigDatasource.class, ConfigEnc.class, ConfigKafka.class,
       ConfigListener.class, ConfigMapper.class, ConfigMq.class,
       net.dstone.common.config.ConfigProperty.class,   // ← common 모듈의 빈
       ConfigRedis.class, ConfigSecurity.class, ConfigSwagger.class,
       ConfigTransaction.class, ConfigWebMvc.class, ConfigWebSocket.class,
       net.dstone.common.websocket.controller.WebSocketController.class  // ← common 모듈의 빈
   })
   public class Config extends BaseObject{ }
   ```
   `net.dstone.common`에 있는 `ConfigProperty`, `WebSocketController` 같은 `@Configuration`/`@Component` 클래스들이 컴포넌트 스캔이 아니라 **명시적으로 이름을 나열해서 `@Import`** 되고 있습니다. 즉 "common의 어떤 빈을 쓸지"를 boot 쪽에서 화이트리스트로 관리하는 구조이지, `net.dstone.common`을 통째로 스캔 범위에 넣는 방식이 아닙니다. 그리고 이 `Config` 클래스 자체는 boot 애플리케이션이 `basePackages={"net.dstone.boot"}` 스캔으로 찾아서 로드합니다.

2. **`BaseController`/`BaseService`/`BaseDao`는 common에 있지만 추상 클래스**

   `net.dstone.common.biz.BaseController/BaseService/BaseDao`에 `@Controller`/`@Service`/`@Repository`가 붙어 있지만 전부 `abstract class`입니다. Spring의 컴포넌트 스캐너는 기본적으로 추상 클래스는 빈 후보에서 제외하므로, 설령 `net.dstone.common`을 스캔 범위에 넣어도 이 추상 클래스들 자체는 빈으로 등록되지 않습니다. 실제 빈이 되는 건 이를 상속하면서 자기 자신에게도 다시 `@Service`/`@Controller`를 붙인 `net.dstone.boot` 쪽 구체 클래스들입니다(예: `KafkaService`도 `extends BaseService` + 자체 `@Service`). 이 클래스들은 이미 `net.dstone.boot` 안에 있으니 지금 스캔 범위로 충분히 커버됩니다.

## 다른 두 옵션이 안 좋은 이유

- **`basePackages={"net.dstone"}`**: `net.dstone.common`까지 통째로 스캔하게 되는데, 그러면 common의 빈들이 (1) `@ComponentScan`에 의해 한 번, (2) `Config.java`의 명시적 `@Import`에 의해 또 한 번, 이중으로 후보에 오릅니다. Spring이 같은 클래스를 스캔 vs 명시적 등록으로 각각 잡아내면 빈 정의 충돌 가능성이 생기는데(`spring.main.allow-bean-definition-overriding: true`가 켜져 있어서 에러로 죽진 않겠지만), 애초에 "필요한 것만 골라 쓴다"는 설계 의도를 깨는 방식입니다. 또한 `dstone-common`은 `dstone-batch`, `dstone-batchadmin`에서도 공유하는 라이브러리라, 모듈마다 필요한 common 구성요소가 다를 수 있는데(boot는 시큐리티/카프카/레디스 다 쓰지만 batch는 시큐리티 제외 등) 이런 모듈별 선택권을 `@Import` 화이트리스트로 관리하는 것이 지금 아키텍처의 핵심입니다.
- **`basePackages={"net.dstone.common","net.dstone.boot"}`**: 위와 같은 이유로 불필요하며, 이미 명시적 `@Import`로 해결된 것을 다시 광범위 스캔으로 중복 처리하는 것입니다.

정리하면, `net.dstone.common`의 구성요소는 "스캔으로 우연히 주워지는 것"이 아니라 `Config.java`의 `@Import` 목록에 **의도적으로 추가**해야 살아나는 구조이므로, `@ComponentScan`은 지금처럼 `net.dstone.boot`로 좁게 유지하는 것이 이 코드베이스의 설계와 일치합니다.
