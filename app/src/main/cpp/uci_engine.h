#ifndef UCI_ENGINE_H
#define UCI_ENGINE_H

#include <string>
#include <memory>
#include <sstream>
#include <deque>

#include "position.h"

namespace Stockfish {
    class Thread;
    namespace Search {
        struct LimitsType;
    }
}

using namespace Stockfish;

/**
 * UCI Engine wrapper for Fairy-Stockfish
 * Provides simplified interface for Android JNI integration
 */
class UciEngine {
public:
    UciEngine();
    ~UciEngine();

    // Initialize engine for Janggi variant
    bool initialize();

    // Set skill level (0-20)
    void setSkillLevel(int level);

    // Set position.
    // Format: "startpos [moves ...]" or "fen <6 fields> [moves ...]"
    // Squares are a1..i10 - see UCI::square(); this build never runs UCI::loop,
    // so CurrentProtocol stays UCI_GENERAL and ranks are 1-based.
    bool setPosition(const std::string& positionCommand);

    // Calculate best move with given time limit (milliseconds)
    // Returns move in UCI format (e.g., "a1b1", "a10b10")
    //
    // depth > 0 이면 시간 대신 그 깊이까지 탐색합니다([thinkTimeMs] 는 폭주를 막는
    // 상한으로만 쓰입니다). 시간으로 끊으면 같은 국면이라도 기기 부하에 따라 도달
    // 깊이가 달라져 매번 다른 수가 나옵니다 - 힌트처럼 늘 같은 답이 나와야 하는
    // 곳은 깊이로 끊어야 합니다. depth <= 0 이면 기존대로 시간으로만 끊습니다.
    std::string getBestMove(int thinkTimeMs, int depth = 0);

    // getBestMove 와 같은 탐색을 하되, 평가 점수도 함께 돌려줍니다. AI 리뷰가
    // 국면마다 "둘 차례인 쪽" 관점 점수가 필요해서 추가했습니다 - 기존
    // getBestMove 호출부(AI 착수·힌트)는 그대로 두고 이 경로만 씁니다.
    // Returns "<uci move> cp<n>" | "<uci move> mate<n>" | "" (합법수 없음)
    std::string getBestMoveWithScore(int thinkTimeMs, int depth = 0);

    // 장군 반복·수 반복처럼 국면 이력이 있어야 가릴 수 있는 판정.
    // 인자 형식은 setPosition 과 같고, 수순을 재생해야 엔진이 이력을 갖습니다.
    // Returns "none" | "win" | "loss" | "draw" - 모두 *둘 차례인 쪽* 관점입니다.
    std::string gameOutcome(const std::string& positionCommand);

    // 합법 수를 공백으로 이어 돌려줍니다 (예: "a1a2 b1c3").
    // 앱 화면은 코틀린 규칙을 쓰고, 이건 두 구현을 대조하는 테스트 전용입니다.
    std::string legalMoves(const std::string& positionCommand);

    // Check if engine is initialized
    bool isReady() const { return initialized; }

private:
    bool initialized;
    Stockfish::Position* position;
    Stockfish::Thread* mainThread;
    StateListPtr states;

    // Initialize Stockfish subsystems
    void initStockfish();

    // Look up the Janggi variant, or nullptr if it is missing
    const Stockfish::Variant* janggiVariant();

    // positionCommand 를 [pos] 로 재생합니다. [states] 는 국면 이력을 담는 곳이라
    // 반복 판정이 거슬러 올라갈 수 있도록 pos 보다 오래 살아 있어야 합니다.
    // 멤버가 아닌 지역 Position 을 넘기면 탐색 중에도 안전하게 질의할 수 있습니다.
    bool buildPosition(const std::string& positionCommand,
                       Stockfish::Position& pos,
                       StateListPtr& states);
};

#endif // UCI_ENGINE_H
