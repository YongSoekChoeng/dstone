# 방금전에_dstonebootknowl

> 생성일시: 2026-11-12 16:01

## 질문

방금전에 dstone-boot>knowledge>검색 · 문서 화면에서
"대한민국 수도가 어디야?" 라는 질문을 했는데 wsl 이 죽은 것 같은데 원인을 찾아봐.

---

## 답변

WSL 상태와 로그부터 확인하겠습니다.

---

## 답변

Previous boot ended at 15:50:01. Let me look at the journal end and app logs.

---

## 답변

저널은 15:50:01에서 OOM 기록 없이 끊겼습니다. sysstat 기록과 앱 로그를 보겠습니다.

---

## 답변

질문은 두 번 기록돼 있습니다(15:37:22, 15:56:47). 두 시점 전후의 knowledge·boot 로그를 보겠습니다.

---

## 답변

문서에 "8. WSL에서 GPU 미사용으로 인한 과부하 문제" 절이 이미 있습니다. 그 절과 Ollama 로그를 보겠습니다.

---

## 답변

Ollama 로그가 15:56까지 기록돼 있습니다. 끝부분을 보겠습니다.

---

## 답변

15:56:47에 Ollama가 bge-m3를 올리는 도중 로그가 끊겼습니다. 이전에 성공한 로드와 비교해 보겠습니다.

---

## 답변

A kernel panic file exists. Let me read it.

---

## 답변

원인이 나왔습니다. 커널 패닉 기록에 하드웨어 Machine Check Exception이 남아 있습니다. 시각과 Windows 쪽 WHEA 기록을 맞춰 보겠습니다.

---

## 답변

## 원인

**WSL 커널이 Machine Check Exception(MCE)으로 패닉을 일으켜 VM이 재시작됐습니다.** 질문을 보낸 뒤 5초 만에 일어났습니다.

| 시각 | 무슨 일이 있었나 | 근거 |
|---|---|---|
| 15:52:08 | Windows가 가상 메모리 부족을 경고했습니다. 사용량은 vmmemWSL 11.2GB, eclipse 3.1GB, oracle 2.2GB였습니다 | Windows 시스템 이벤트 2004 |
| 15:56:47 | 검색 "대한민국 수도는 어디야?" 요청이 boot에서 knowledge로 넘어갔습니다 | `LOGS/dstone-boot/execution/execution.log:1490` |
| 15:56:47 | 질문을 임베딩하려고 Ollama가 bge-m3를 CPU에 다시 올리기 시작했습니다. mmap 없이 1.1GB를 새로 할당합니다 | `/opt/ollama/logs/ollama-server.out` 끝부분 (`CPU model buffer size = 1097.52 MiB`에서 끊김) |
| **15:56:52** | `mce: Machine Check Exception ... Processor Context Corrupt` → `Kernel panic - Fatal local machine check` | `C:\Users\jysn007\AppData\Local\Temp\wsl-crashes\kernel-panic-*.txt` |
| 15:57:29 | WSL이 다시 부팅됐습니다 | `journalctl --list-boots` |

이 MCE가 CPU 고장일 가능성은 낮다고 봅니다. 진짜 하드웨어 MCE였다면 Windows 쪽 WHEA-Logger에 기록이 남는데, 기록이 하나도 없습니다. 패닉 기록의 `microcode ffffffff`는 가상 CPU라는 뜻이고요. 그래서 **Hyper-V가 게스트 메모리를 받쳐 주지 못해 MCE를 게스트에 주입했을 가능성이 가장 높습니다.** 다만 이건 추정이고 확인은 못 했습니다. 이렇게 보는 근거는 다음과 같습니다.

- 호스트 RAM은 16GB인데 `.wslconfig`가 WSL에 `memory=12GB`를 주고 있습니다. 여기에 Eclipse와 Oracle까지 떠 있어 패닉 4분 전에 Windows가 이미 메모리 부족을 경고했습니다.
- WSL 안의 swap도 가득 찬 상태였습니다. 15:50 기준 4GB 중 96%를 쓰고 있었고, Ollama 로그의 `free_swap`은 39MB까지 내려갔습니다.
- 질문 자체는 상관이 없습니다. 검색할 때마다 bge-m3를 메모리에 다시 올리는 것이 방아쇠였습니다. Ollama는 기본적으로 5분 동안 쓰지 않으면 모델을 내리기 때문에, 이번처럼 시간이 지나 검색하면 1.1GB를 새로 할당합니다.

참고로 앞서 15:37:22에 보낸 같은 질문도 boot 로그에 응답 기록이 없습니다. 그 사이 다른 세션이 앱을 재기동해서(15:50~15:53) 이 건은 원인을 따로 가리지 못했습니다.

## 재발을 막는 방법 (아직 적용 안 함)

1. **`.wslconfig`의 `memory=12GB`를 8GB 정도로 줄이기.** 가장 효과가 큽니다. 호스트 16GB에서 Eclipse와 Oracle을 함께 쓰면 12GB는 너무 많습니다. 바꾼 뒤에는 `wsl --shutdown`이 필요합니다.
2. **Ollama `OLLAMA_KEEP_ALIVE`를 늘리기** (예: `30m` 또는 `-1`). 검색할 때마다 1.1GB를 다시 할당하는 일이 없어집니다. bge-m3는 약 1.2GB를 계속 차지하게 됩니다. 설정은 `/usr/local/bin/start-ollama.sh`에서 합니다.
3. 필요하면 Windows 페이지 파일을 늘리고, 쓰지 않을 때는 Oracle을 끄기.

1번과 2번을 적용하고 `docs/software/14.ollama.md`에 이 문제 해결 내용을 추가할까요?
