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
    std::string getBestMove(int thinkTimeMs);

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
};

#endif // UCI_ENGINE_H
