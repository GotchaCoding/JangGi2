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

bool UciEngine::initialize() {
    if (initialized) {
        LOGD("Engine already initialized");
        return true;
    }

    // Get Janggi variant
    auto variantIt = variants.find("janggi");
    if (variantIt == variants.end()) {
        LOGE("Janggi variant not found");
        return false;
    }
    const Variant* janggiVariant = variantIt->second;

    // Initialize threads
    Options["Threads"] = std::string("1");
    Threads.set(1);
    mainThread = Threads.main();

    // Set variant
    Options["UCI_Variant"] = std::string("janggi");

    // Create position with starting FEN
    states = StateListPtr(new std::deque<StateInfo>(1));
    position = new Position();
    position->set(janggiVariant, janggiVariant->startFen, false, &states->back(), mainThread);

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

    // Parse position command (e.g., "startpos" or "startpos moves a0b0 c1d2")
    std::istringstream iss(positionCommand);
    std::string token;
    iss >> token;

    if (token != "startpos") {
        LOGE("Only startpos supported currently");
        return false;
    }

    // Get Janggi variant
    auto variantIt = variants.find("janggi");
    if (variantIt == variants.end()) {
        LOGE("Janggi variant not found");
        return false;
    }
    const Variant* janggiVariant = variantIt->second;

    // Reset to starting position
    states = StateListPtr(new std::deque<StateInfo>(1));
    position->set(janggiVariant, janggiVariant->startFen, false, &states->back(), mainThread);

    // Check for moves
    if (iss >> token && token == "moves") {
        while (iss >> token) {
            Move move = UCI::to_move(*position, token);
            if (move == MOVE_NONE) {
                LOGE("Invalid move: %s", token.c_str());
                return false;
            }

            states->emplace_back();
            position->do_move(move, states->back());
        }
    }

    return true;
}

std::string UciEngine::getBestMove(int thinkTimeMs) {
    if (!initialized || !position) {
        LOGE("Engine not ready");
        return "";
    }

    // Setup search limits
    Search::LimitsType limits;
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
