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

    // Set position from UCI notation
    // Format: "startpos" or "startpos moves a0b0 c1d2 ..."
    bool setPosition(const std::string& positionCommand);

    // Calculate best move with given time limit (milliseconds)
    // Returns move in UCI format (e.g., "a0b0")
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

    // Parse UCI moves and apply to position
    bool applyMoves(const std::string& moves);
};

#endif // UCI_ENGINE_H
