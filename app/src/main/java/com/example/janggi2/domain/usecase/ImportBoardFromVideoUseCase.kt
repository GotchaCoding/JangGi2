package com.example.janggi2.domain.usecase

import android.net.Uri
import android.util.Log
import com.example.janggi2.data.imageprocessing.BoardRecognitionService
import com.example.janggi2.data.imageprocessing.video.VideoStillFrameFinder
import com.example.janggi2.domain.model.DetectedPiece
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.ImportedBoardState
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

/** [ImportBoardFromVideoUseCase.import] 의 진행 상황. */
sealed class VideoImportProgress {
    data class Analyzing(val completed: Int, val total: Int) : VideoImportProgress()

    /**
     * @param viewpoint 영상 속에서 실제로 아래쪽에 있던 진영 - 사진 불러오기와 같은 이유로
     *   첫 프레임에서 한 번만 정합니다([ImportedBoardState.detectedBottomPlayer]).
     * @param stillFramesRecognized 기물 인식에 성공한 정지 프레임 수. [movesRecovered] 보다
     *   많이 남으면, 인식은 됐지만 그 진영 차례에 맞는 깔끔한 변화를 찾지 못해(아직
     *   덜 멈췄거나 오인식) 건너뛴 후보가 있었다는 뜻입니다.
     */
    data class Finished(
        val gameState: GameState,
        val movesRecovered: Int,
        val viewpoint: Player,
        val stillFramesRecognized: Int
    ) : VideoImportProgress()

    data class Failed(val message: String) : VideoImportProgress()
}

/**
 * 동영상에서 판이 멈춘 정지 프레임([VideoStillFrameFinder])만 골라 기존 사진 인식
 * 파이프라인([BoardRecognitionService.extractFromBitmap])을 돌리고, 지금까지 확정된
 * 판과 각 후보를 지금 차례인 진영 기준으로 비교([inferMoveFromBoardDiff])해 수를
 * 재구성합니다 - 상대 진영이 같은 프레임에서 움직이는 중이라도 무시합니다.
 *
 * 사진 불러오기와 같은 방침입니다 - 인식이 안 되는 구간은 건너뛰고 되는 데까지만
 * 넣습니다. 별도 수동 보정 화면은 없습니다.
 */
class ImportBoardFromVideoUseCase @Inject constructor(
    private val stillFrameFinder: VideoStillFrameFinder,
    private val recognitionService: BoardRecognitionService
) {
    companion object {
        private const val TAG = "ImportBoardFromVideo"
    }

    fun import(videoUri: Uri): Flow<VideoImportProgress> = flow {
        val frames = stillFrameFinder.findStillFrames(videoUri)
        Log.d(TAG, "Found ${frames.size} still frame candidates")
        if (frames.size < 2) {
            emit(VideoImportProgress.Failed("정지 구간을 찾지 못했습니다. 다른 영상으로 시도해 주세요."))
            return@flow
        }

        // 프레임마다 무거운 인식은 Default 로 튀었다가 emit 전에 반드시 돌아와야 합니다 -
        // Flow.emit 은 collect 와 같은 컨텍스트에서만 부를 수 있습니다(맥락 보존 규칙).
        val importedStates = mutableListOf<ImportedBoardState>()
        for ((index, frame) in frames.withIndex()) {
            val importedState = withContext(Dispatchers.Default) {
                recognitionService.extractFromBitmap(frame).getOrNull()?.let { toImportedBoardState(it) }
            }
            Log.d(TAG, "Frame $index/${frames.size}: recognized ${importedState?.detectedPieces?.size ?: 0} pieces")
            if (importedState != null) importedStates.add(importedState)
            frame.recycle()
            emit(VideoImportProgress.Analyzing(index + 1, frames.size))
        }

        if (importedStates.size < 2) {
            emit(VideoImportProgress.Failed("기물을 인식하지 못했습니다. 판이 화면에 크고 또렷하게 나오는 영상인지 확인해 주세요."))
            return@flow
        }

        // 사진 불러오기와 같은 이유로 viewpoint 는 첫 프레임에서만 정합니다 - 기물이 남아있는
        // 한 초/한 평균 행이 영상 도중 뒤집힐 일은 없습니다.
        val viewpoint = importedStates.first().detectedBottomPlayer()
        val boards = importedStates.map { it.toGameState().board }

        var state = GameState(board = boards.first(), currentPlayer = Player.CHO, startBoard = boards.first())
        var recovered = 0
        var skipped = 0
        for (candidateIndex in 1 until boards.size) {
            val candidate = boards[candidateIndex]
            val move = inferMoveFromBoardDiff(state.board, candidate, expectedMover = state.currentPlayer)
            if (move == null) {
                skipped++
                val mover = state.currentPlayer
                val relevant = (state.board.keys + candidate.keys).distinct()
                    .filter { state.board[it] != candidate[it] }
                    .filter { state.board[it]?.player == mover || candidate[it]?.player == mover }
                if (relevant.isNotEmpty()) {
                    val detail = relevant.joinToString { "$it: ${state.board[it]} -> ${candidate[it]}" }
                    Log.d(
                        TAG,
                        "Candidate frame $candidateIndex: no clean $mover move " +
                            "(${relevant.size} relevant cell(s): $detail)"
                    )
                }
                continue
            }
            Log.d(
                TAG,
                "Candidate frame $candidateIndex: ${state.currentPlayer} moved " +
                    "${move.from} -> ${move.to}" + (move.capturedPiece?.let { " (captured $it)" } ?: "")
            )
            state = state.applyMove(move)
            recovered++
        }
        Log.d(TAG, "Recovered $recovered move(s) from ${boards.size} candidate boards ($skipped candidate(s) unmatched)")

        emit(VideoImportProgress.Finished(state, recovered, viewpoint, importedStates.size))
    }

    private fun toImportedBoardState(extracted: BoardRecognitionService.ExtractedText): ImportedBoardState? {
        val detectedPieces = extracted.detectedPieces.associate { detected ->
            detected.position to DetectedPiece(piece = detected.piece, confidence = detected.confidence)
        }
        if (detectedPieces.isEmpty()) return null

        return ImportedBoardState(
            detectedPieces = detectedPieces,
            overallConfidence = detectedPieces.values.map { it.confidence }.average().toFloat(),
            gridDetected = true
        )
    }
}
