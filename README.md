# JangGi2

안드로이드 장기 앱. Kotlin + Jetpack Compose, 기력 판단과 힌트는 네이티브
Fairy-Stockfish 엔진을 씁니다.

## 받아서 빌드하기

엔진 소스가 **git 서브모듈**로 들어 있어서 그냥 클론하면 네이티브 빌드가 실패합니다.
서브모듈까지 함께 받아야 합니다.

```bash
git clone --recurse-submodules -b master git@github.com:GotchaCoding/JangGi2.git
cd JangGi2
./gradlew assembleDebug
```

이미 서브모듈 없이 클론했다면:

```bash
git submodule update --init --recursive
```

`local.properties` 는 저장소에 넣지 않습니다(안드로이드 스튜디오가 만들어 줍니다).
명령줄로만 빌드한다면 SDK 경로를 직접 적어 주세요.

```
sdk.dir=/path/to/Android/sdk
```

## 테스트

```bash
./gradlew testDebugUnitTest          # JVM 단위 테스트 - 네이티브 없이 돕니다
./gradlew connectedDebugAndroidTest  # 기기 필요 - 엔진을 실제로 부르는 테스트
```

계측 테스트에는 **두 규칙 구현을 대조하는** `RulesAgreementTest` 가 있습니다. 이 앱은
화면용 코틀린 이동 규칙(`domain/rules/`)과 엔진 규칙을 둘 다 가지고 있는데, 이 테스트가
무작위 대국을 두며 매 국면 양쪽 합법수가 같은지 비교합니다. 규칙을 손볼 때 꼭 돌려
보세요.

## 구조

| 경로 | 내용 |
|---|---|
| `domain/rules/` | 이동·장군·승패 판정. 화면이 곧바로 쓰는 빠른 경로입니다 |
| `domain/rules/RepetitionJudge.kt` | 반복 규칙만 엔진에 맡깁니다 (판 하나로는 알 수 없어서) |
| `data/ai/` | 엔진 호출, FEN·UCI 변환 |
| `app/src/main/cpp/` | JNI 브리지와 CMake 설정 |
| `app/src/main/cpp/fairystockfish/` | 서브모듈 - Fairy-Stockfish 원본 |

엔진 변형은 `janggimodern` 입니다. 빅장 없음, 기물 점수제, 장군·수 반복 금지가 그 정의에
들어 있습니다.
