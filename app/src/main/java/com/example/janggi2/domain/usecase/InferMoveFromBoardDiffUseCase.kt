package com.example.janggi2.domain.usecase

import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position

/**
 * 두 판(수를 두기 전 -> 후)을 비교해 그 사이에 있었던 수 하나를 되짚어냅니다.
 *
 * 동영상 불러오기가 정지 프레임마다 인식한 판을 이걸로 잇습니다.
 *
 * @param expectedMover null 이면(기존 방식) 판 전체에서 정확히 두 칸만 달라야 합니다.
 *   두 진영이 영상에서 번갈아 움직이지 않고 겹쳐 찍히면(한쪽이 멈추는 순간 다른 쪽이
 *   이미 움직이기 시작해 흐릿하게 같이 잡히는 경우) 세 칸 이상 달라져 버려 실패합니다.
 *
 *   값이 있으면(동영상 불러오기가 쓰는 방식) **그 진영이 옮긴 기물의 출발·도착 칸만**
 *   봅니다 - 같은 프레임에 상대 진영 쪽이 움직이는 중이라 달리 보이는 칸은 그 진영
 *   소유가 아니므로 무시합니다. [before] 는 상대 진영 쪽은 아직 원래 그대로인,
 *   지금까지 확정된 판(`GameState.board`)을 넘겨야 이 필터가 뜻이 있습니다 - 아직
 *   확정되지 않은 다른 정지 후보끼리 비교하면 상대 진영 쪽도 "달라진 것"으로 보여
 *   걸러지지 않습니다.
 *
 * 두 칸(진영 필터 후에는 그 진영 소유 두 칸)이 아니면(수를 놓쳤거나, 아직 다 멈추지
 * 않았거나, 두 수가 겹친 경우) `null` 을 돌려주고 호출부가 다음 후보를 시도합니다 -
 * "일단 되는 데까지만 넣는다"가 여기서 적용됩니다.
 *
 * [expectedMover] 가 있을 때는 도착 칸에 인식된 기물의 **종류**는 확인하지 않습니다 -
 * 출발 칸에서 이미 그 기물의 정체(예: 마)를 알고 있고, 기물은 이동하면서 종류가 바뀔
 * 수 없으므로 도착 칸 인식이 비슷하게 생긴 다른 기물(마/상 등)로 오분류돼도 원래
 * 정체를 그대로 신뢰합니다. 도착 칸의 **진영(색)** 만 맞으면 됩니다 - 완전히 다른 색
 * 기물이 그 자리에 나타난 것처럼 보이면(진짜 다른 사건) 그건 걸러냅니다.
 *
 * 한 수 쉼(패스)은 판이 그대로라 이 방식으로는 원리상 구분할 수 없습니다.
 */
fun inferMoveFromBoardDiff(
    before: Map<Position, Piece>,
    after: Map<Position, Piece>,
    expectedMover: Player? = null
): Move? {
    val allChanged = (before.keys + after.keys).distinct().filter { before[it] != after[it] }

    if (expectedMover == null) {
        if (allChanged.size != 2) return null
        val (first, second) = allChanged
        val from = if (after[first] == null) first else second
        val to = if (from == first) second else first
        val movedPiece = before[from] ?: return null
        val landedPiece = after[to] ?: return null
        if (landedPiece.player != movedPiece.player || landedPiece::class != movedPiece::class) {
            return null
        }
        val capturedPiece = before[to]
        return Move(from = from, to = to, capturedPiece = capturedPiece, movedPiece = movedPiece)
    }

    val moverChanged = allChanged.filter { pos ->
        before[pos]?.player == expectedMover || after[pos]?.player == expectedMover
    }
    if (moverChanged.size != 2) return null

    val toCandidates = moverChanged.filter { after[it]?.player == expectedMover }
    if (toCandidates.size != 1) return null
    val to = toCandidates.first()
    val from = moverChanged.first { it != to }

    val movedPiece = before[from] ?: return null
    if (movedPiece.player != expectedMover) return null
    // 도착 칸의 기물 종류는 보지 않습니다 - 출발 칸에서 이미 정체를 알고, 이동 중
    // 종류가 바뀔 수 없으므로 도착 칸 인식(마/상 등)이 틀려도 movedPiece 를 그대로
    // 씁니다. after[to] 가 있다는 것(=그 진영 색이 맞다는 것)만 확인합니다.
    if (after[to]?.player != expectedMover) return null

    val capturedPiece = before[to]
    return Move(from = from, to = to, capturedPiece = capturedPiece, movedPiece = movedPiece)
}
