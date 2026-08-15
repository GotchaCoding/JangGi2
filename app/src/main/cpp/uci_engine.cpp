#include "uci_engine.h"
#include "misc.h"
#include "types.h"
#include "bitboard.h"
#include "evaluate.h"
#include "position.h"
#include "search.h"
#include "thread.h"
#include "tt.h"
#include "uci.h"
#include "piece.h"
#include "variant.h"
#include "movegen.h"

#include <android/log.h>
#include <sstream>

#define LOG_TAG "UciEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace Stockfish;

static bool globalStockfishInitialized = false;

UciEngine::UciEngine() : initialized(false), position(nullptr), mainThread(nullptr) {
    initStockfish();
}

UciEngine::~UciEngine() {
    if (position) {
        delete position;
        position = nullptr;
    }
}

void UciEngine::initStockfish() {
    if (!globalStockfishInitialized) {
        LOGD("Initializing Stockfish global state");

        LOGD("Step 1: pieceMap.init()");
        pieceMap.init();

        LOGD("Step 2: variants.init()");
        variants.init();

        // Debug: Check if janggi variant is available
        auto keys = variants.get_keys();
        LOGD("Available variants count: %zu", keys.size());
        bool janggiFound = false;
        for (const auto& key : keys) {
            if (key == "janggi" || key == "janggitraditional" ||
                key == "janggimodern" || key == "janggicasual") {
                LOGD("Found janggi variant: %s", key.c_str());
                janggiFound = true;
            }
        }
        if (!janggiFound) {
            LOGE("WARNING: No janggi variant found!");
        }

        LOGD("Step 3: UCI::init(Options)");
        UCI::init(Options);

        LOGD("Step 4: Bitboards::init() - this may take a while...");
        auto start_time = std::chrono::high_resolution_clock::now();
        Bitboards::init();
        auto end_time = std::chrono::high_resolution_clock::now();
        auto duration = std::chrono::duration_cast<std::chrono::milliseconds>(end_time - start_time);
        LOGD("Bitboards::init() completed in %lld ms", duration.count());

        LOGD("Step 5: Position::init()");
        Position::init();

        LOGD("Step 6: Bitbases::init()");
        Bitbases::init();

        globalStockfishInitialized = true;
        LOGD("Stockfish global initialization complete");
    }
}

// 앱은 빅장을 쓰지 않고 기물 점수로 승부를 가립니다. "janggimodern" 이 바로 그
// 규칙입니다 — variant.cpp 의 janggi_modern_variant() 가 bikjangRule=false,
// materialCounting=JANGGI_MATERIAL 이고, 주석에 카카오 장기 호환이라고 적혀 있습니다.
static constexpr const char* VARIANT_NAME = "janggimodern";

const Variant* UciEngine::janggiVariant() {
    auto it = variants.find(VARIANT_NAME);
    if (it == variants.end()) {
        LOGE("Variant not found: %s", VARIANT_NAME);
        return nullptr;
    }
    return it->second;
}

bool UciEngine::initialize() {
    if (initialized) {
        LOGD("Engine already initialized");
        return true;
    }

    const Variant* variant = janggiVariant();
    if (!variant) return false;

    // Initialize threads
    Options["Threads"] = std::string("1");
    Threads.set(1);
    mainThread = Threads.main();

    // Set variant
    Options["UCI_Variant"] = std::string(VARIANT_NAME);

    // Create position with starting FEN
    states = StateListPtr(new std::deque<StateInfo>(1));
    position = new Position();
    position->set(variant, variant->startFen, false, &states->back(), mainThread);

    initialized = true;
    LOGD("UCI Engine initialized for Janggi");
    return true;
}

void UciEngine::setSkillLevel(int level) {
    if (level < 0) level = 0;
    if (level > 20) level = 20;

    std::ostringstream oss;
    oss << level;
    Options["Skill Level"] = oss.str();
    LOGD("Skill level set to %d", level);
}

bool UciEngine::setPosition(const std::string& positionCommand) {
    if (!initialized) {
        LOGE("Engine not initialized");
        return false;
    }
    return buildPosition(positionCommand, *position, states);
}

bool UciEngine::buildPosition(const std::string& positionCommand,
                              Position& pos,
                              StateListPtr& outStates) {
    // "startpos [moves ...]" 또는 "fen <6개 필드> [moves ...]"
    std::istringstream iss(positionCommand);
    std::string token;
    iss >> token;

    const Variant* variant = janggiVariant();
    if (!variant) return false;

    std::string fen;
    if (token == "startpos") {
        fen = variant->startFen;
        iss >> token;  // 다음 토큰이 있다면 "moves"
    } else if (token == "fen") {
        // FEN 은 공백으로 나뉜 6개 필드입니다. "moves" 가 나오면 거기서 멈춥니다.
        for (int i = 0; i < 6 && iss >> token; ++i) {
            if (token == "moves") break;
            if (!fen.empty()) fen += ' ';
            fen += token;
        }
        if (fen.empty()) {
            LOGE("Empty FEN in position command");
            return false;
        }
        if (token != "moves") iss >> token;
    } else {
        LOGE("Unknown position command: %s", token.c_str());
        return false;
    }

    outStates = StateListPtr(new std::deque<StateInfo>(1));
    pos.set(variant, fen, false, &outStates->back(), mainThread);

    // Check for moves
    if (token == "moves") {
        while (iss >> token) {
            Move move = UCI::to_move(pos, token);
            if (move == MOVE_NONE) {
                LOGE("Invalid move: %s", token.c_str());
                return false;
            }

            outStates->emplace_back();
            pos.do_move(move, outStates->back());
        }
    }

    return true;
}

std::string UciEngine::gameOutcome(const std::string& positionCommand) {
    if (!initialized) {
        LOGE("Engine not initialized");
        return "none";
    }

    Position pos;
    StateListPtr localStates;
    if (!buildPosition(positionCommand, pos, localStates)) return "none";

    // is_immediate_game_end 는 변형이 그 자리에서 끝내는 경우(빅장 등),
    // is_optional_game_end 는 장군 반복·수 반복·n-fold 를 봅니다
    // (position.cpp 의 "n-fold repetition" 구간). janggimodern 은
    // perpetualCheckIllegal·moveRepetitionIllegal 이 켜져 있습니다.
    Value result = VALUE_DRAW;
    if (!pos.is_immediate_game_end(result) && !pos.is_optional_game_end(result)) {
        return "none";
    }

    if (result == VALUE_DRAW) return "draw";
    return result > VALUE_DRAW ? "win" : "loss";
}

std::string UciEngine::legalMoves(const std::string& positionCommand) {
    if (!initialized) {
        LOGE("Engine not initialized");
        return "";
    }

    Position pos;
    StateListPtr localStates;
    if (!buildPosition(positionCommand, pos, localStates)) return "";

    std::string moves;
    for (const auto& m : MoveList<LEGAL>(pos)) {
        if (!moves.empty()) moves += ' ';
        moves += UCI::move(pos, m);
    }
    return moves;
}

std::string UciEngine::getBestMove(int thinkTimeMs) {
    if (!initialized || !position) {
        LOGE("Engine not ready");
        return "";
    }

    // Setup search limits.
    // startTime 은 LimitsType 생성자가 초기화하지 않는 유일한 멤버라 반드시 넣어야
    // 합니다. 원래 엔진은 UCI::go() 가 맨 먼저 넣어주는데 여기서는 그 경로를 타지
    // 않습니다. 빠뜨리면 스택 쓰레기값이 들어가고, 그게 현재 시각보다 크면 경과
    // 시간이 음수가 되어 movetime 을 영영 넘지 못해 탐색이 끝나지 않습니다.
    Search::LimitsType limits;
    limits.startTime = now();
    limits.movetime = TimePoint(thinkTimeMs);

    // Start search
    StateListPtr searchStates(new std::deque<StateInfo>(1));
    Threads.start_thinking(*position, searchStates, limits, false);

    // Wait for search to complete
    Threads.main()->wait_for_search_finished();

    // Get best move
    if (Threads.main()->rootMoves.empty()) {
        LOGE("No legal moves");
        return "";
    }

    Move bestMove = Threads.main()->rootMoves[0].pv[0];

    if (bestMove == MOVE_NONE) {
        LOGE("No best move found");
        return "";
    }

    std::string moveStr = UCI::move(*position, bestMove);
    LOGD("Best move: %s", moveStr.c_str());
    return moveStr;
}
