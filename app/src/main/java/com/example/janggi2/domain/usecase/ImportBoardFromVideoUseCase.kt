package com.example.janggi2.domain.usecase

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.example.janggi2.data.imageprocessing.BoardRecognitionService
import com.example.janggi2.data.imageprocessing.video.VideoStillFrameFinder
import com.example.janggi2.domain.model.DetectedPiece
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.ImportedBoardState
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import com.example.janggi2.domain.rules.MoveValidator
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
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
     * @param stillFramesRecognized 기물 인식에 성공한 정지 프레임 수.
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
 * 파이프라인([BoardRecognitionService.extractFromBitmap])을 돌리고, 수를 재구성합니다.
 *
 * **핵심 아이디어**: [VideoStillFrameFinder] 가 주는 각 프레임은 화면의 "N/전체" 수
 * 번호를 OCR로 읽어 붙여줍니다. N이 홀수면 방금 초가 두었다는 뜻이므로 그 프레임의
 * **한(HAN) 쪽 기물은 확실히 안정적**입니다(한은 이번에 움직이지 않았으니까요) - 반대로
 * N이 짝수면 **초(CHO) 쪽 기물이 안정적**입니다.
 *
 * **한 수 = 지금까지 확정된 판 vs 그 수의 체크포인트 프레임 하나만 비교**합니다.
 * 예를 들어 19수(초)는 오직 20수 프레임(초 쪽이 안정적인 첫 지점)의 초 쪽 배치를,
 * "지금까지 확정된 판"(0수부터 18수까지 실제로 검증해 반영한 판)의 초 쪽과 비교해서
 * 되짚습니다 - **18수 프레임 자체를 다시 읽지 않습니다.** 원본 프레임 두 장을 직접
 * 비교하던 예전 방식은, 그중 한 장(예: 18수 프레임)의 인식이 흔들리면(기물이 아직
 * 착수 중이라 다른 기물로 잘못 읽히는 등) 그 오류가 그대로 수 계산에 끼어들었습니다.
 * "지금까지 확정된 판"은 검증된 수만 순서대로 적용해서 만든 것이라, 어느 한 프레임의
 * 일회성 오인식이 다른 수 계산으로 번지지 않습니다.
 *
 * "이 수를 둔 기물이 완전히 멈췄는가"를 직접 판정할 필요가 없다는 게 핵심입니다 - 항상
 * **상대 진영**(이번에 안 움직인 쪽)의 위치만 읽으므로, 오늘 계속 발목 잡혔던 "착수
 * 완료 시점 오판" 문제를 원천적으로 피해갑니다.
 *
 * **시작 배치(0수)**: 이 앱의 기본 배치를 가정하지 않습니다 - 영상에 실제로 찍힌
 * 대기 화면(0수) 프레임을 그대로 인식해서 씁니다(마상 배치가 무엇이든 영상을 따라감).
 * 1수는 체크포인트가 2수 프레임이라 자연스럽게 되짚습니다. 2수의 체크포인트는
 * 3수 프레임입니다 - 1수 프레임은 화면 표시 특성상 가장 불안정한 자리라, 체크포인트로
 * 쓸 일 자체가 없어 자동으로 안 쓰입니다(특별한 예외 처리가 필요 없습니다).
 *
 * **한 수 쉼과 체크포인트 번호 생략**: 이 리플레이 화면은 한 수 쉼이 발생하면 그 수
 * 번호 표시 자체를 건너뛰고 카운터가 바로 다음 번호로 넘어갑니다(예: 30수가 한 수
 * 쉼이면 화면에 "30"이 아예 안 뜨고 "29" 다음 바로 "31"이 뜸). 그래서 position+1
 * 체크포인트 프레임이 없으면 position+2 프레임을 대신 씁니다 - 한 수 쉼은 양쪽 진영
 * 다 기물 변화가 없으므로 mover 쪽 상태는 position+2 프레임에서도 그대로 유효합니다.
 *
 * 체크포인트 프레임을 OCR이 통째로 못 읽었으면(position+1, position+2 프레임 둘 다
 * 없음) 그 수는 "no data"로 건너뛰고, 확정된 판은 그대로 유지한 채 다음 수로 넘어갑니다
 * - 그 사이 실제로 다른 수가 더 있었다면 다음 체크포인트와의 비교에서 자연히 걸러집니다.
 *
 * 사진 불러오기와 같은 방침입니다 - 인식이 안 되는 구간은 건너뛰고 되는 데까지만
 * 넣습니다. 별도 수동 보정 화면은 없습니다.
 *
 * **마지막 수**: 인식된 것 중 가장 큰 수 번호(`lastPosition`)는 위 루프 범위(1부터
 * `lastPosition - 1`까지)에 안 들어갑니다 - 그 수 자체를 확정할 다음 수 체크포인트가
 * 영상에 없기 때문입니다. 그래서 루프가 끝난 뒤 따로, 영상이 완전히 끝난 시점의
 * 진짜 마지막 프레임([VideoStillFrameFinder.findFinalFrame])을 체크포인트 삼아 이
 * 한 수만 추가로 재구성합니다 - 그 시점엔 더 이상 수가 없으므로 방금 둔 쪽 자신의
 * 위치를 읽어도 안전합니다(다른 수들과 달리 상대 진영을 볼 필요가 없습니다).
 */
class ImportBoardFromVideoUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stillFrameFinder: VideoStillFrameFinder,
    private val recognitionService: BoardRecognitionService
) {
    companion object {
        private const val TAG = "ImportBoardFromVideo"

        // TEMP DEBUG: 기보 분석에 실제로 쓰인 후보 이미지를 수 번호 순서대로 눈으로
        // 확인하기 위한 덤프 폴더 이름 - 인식 직후 recycle 되어 사라지는 원본 이미지를
        // 남겨 둡니다. 확인 끝나면 지울 코드입니다.
        private const val DEBUG_DUMP_DIR_NAME = "video_import_debug"
    }

    private val moveValidator = MoveValidator()

    /** OCR로 확인된 수 번호가 붙은, 인식된 판 하나(양쪽 진영 다 포함된 전체 판). */
    private data class RecognizedFrame(val position: Int, val board: Map<Position, Piece>)

    /**
     * TEMP DEBUG: 후보 프레임을 수 번호 + 어느 진영 기보 갱신에 쓰이는지를 붙여 PNG로
     * 저장합니다(실패해도 무시). 0수는 초·한 둘 다에 쓰이고, 1수는 표시 특성상
     * 불안정한 자리라 재구성에 아예 안 씁니다.
     */
    private fun dumpDebugFrame(position: Int, frame: Bitmap) {
        try {
            val label = when {
                position == 0 -> "초한"
                position == 1 -> "미사용"
                position % 2 == 0 -> "초"
                else -> "한"
            }
            val base = context.getExternalFilesDir(null) ?: return
            val dir = File(base, DEBUG_DUMP_DIR_NAME)
            dir.mkdirs()
            val target = File(dir, "%03d_%s.png".format(position, label))
            FileOutputStream(target).use { out ->
                frame.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } catch (e: Exception) {
            Log.w(TAG, "debug dump failed: $position", e)
        }
    }

    fun import(videoUri: Uri): Flow<VideoImportProgress> = flow {
        // TEMP DEBUG: 이번 실행 전에 이전 덤프를 지워서, 이전 영상 이미지와 섞이지 않게 합니다.
        // findStillFrames() 호출 전에 지워야 합니다 - 그 안에서도(빈 구간 재검색 시)
        // 디버그 이미지를 저장하는데, 여기서 늦게 지우면 방금 저장한 것까지 같이 지워집니다.
        context.getExternalFilesDir(null)?.let { base ->
            File(base, DEBUG_DUMP_DIR_NAME).deleteRecursively()
        }

        val frames = stillFrameFinder.findStillFrames(videoUri)
        Log.d(TAG, "Found ${frames.size} still frame candidates")
        if (frames.size < 2) {
            emit(VideoImportProgress.Failed("정지 구간을 찾지 못했습니다. 다른 영상으로 시도해 주세요."))
            return@flow
        }

        // 프레임마다 무거운 인식은 Default 로 튀었다가 emit 전에 반드시 돌아와야 합니다 -
        // Flow.emit 은 collect 와 같은 컨텍스트에서만 부를 수 있습니다(맥락 보존 규칙).
        val recognized = mutableListOf<RecognizedFrame>()
        var firstImportedState: ImportedBoardState? = null
        for ((index, frame) in frames.withIndex()) {
            dumpDebugFrame(frame.position, frame.bitmap)
            val importedState = withContext(Dispatchers.Default) {
                recognitionService.extractFromBitmap(frame.bitmap).getOrNull()?.let { toImportedBoardState(it) }
            }
            Log.d(
                TAG,
                "Frame $index/${frames.size} (position ${frame.position}): recognized " +
                    "${importedState?.detectedPieces?.size ?: 0} pieces"
            )
            if (importedState != null) {
                if (firstImportedState == null) firstImportedState = importedState
                recognized.add(RecognizedFrame(frame.position, importedState.toGameState().board))
            }
            frame.bitmap.recycle()
            emit(VideoImportProgress.Analyzing(index + 1, frames.size))
        }

        if (recognized.size < 2) {
            emit(VideoImportProgress.Failed("기물을 인식하지 못했습니다. 판이 화면에 크고 또렷하게 나오는 영상인지 확인해 주세요."))
            return@flow
        }

        // 사진 불러오기와 같은 이유로 viewpoint 는 첫 프레임에서만 정합니다.
        val viewpoint = firstImportedState!!.detectedBottomPlayer()

        // 0수(대기 화면)는 영상에 실제로 찍힌 시작 배치입니다 - 이 앱의 기본 배치를
        // 가정하지 않고, 그 프레임을 그대로 인식해서 씁니다(마상 배치가 무엇이든
        // 영상에 찍힌 그대로 따라갑니다).
        val position0 = recognized.firstOrNull { it.position == 0 }
        if (position0 == null) {
            emit(VideoImportProgress.Failed("영상 시작(0수) 프레임을 인식하지 못했습니다. 다른 영상으로 시도해 주세요."))
            return@flow
        }
        val startBoard = position0.board
        val recognizedByPosition = recognized.associateBy { it.position }

        var state = GameState(board = startBoard, currentPlayer = Player.CHO, startBoard = startBoard)
        var recovered = 0
        var skipped = 0
        val lastPosition = recognized.maxOf { it.position }
        val maxAttempt = lastPosition - 1

        for (position in 1..maxAttempt) {
            val mover = if (position % 2 == 1) Player.CHO else Player.HAN
            // 보통 체크포인트는 position+1 프레임입니다. 그런데 이 리플레이 화면은 한
            // 수 쉼이 발생하면 그 수 번호 표시 자체를 건너뛰고 카운터가 바로 다음
            // 번호로 넘어갑니다(예: 30수가 한 수 쉼이면 화면에 "30"이 아예 안 뜨고
            // "29" 다음 바로 "31"이 뜸) - 그래서 position+1 프레임이 없으면 position+2
            // 프레임을 대신 씁니다. 한 수 쉼은 양쪽 진영 다 기물 변화가 없으므로, mover
            // 쪽 상태는 position+2 프레임에서도 그대로 유효합니다. position+1 자체는
            // 이 루프의 다음 바퀴(position+1)에서 정상적으로(체크포인트=position+2) 다시
            // 처리되므로 여기서 따로 손댈 필요가 없습니다.
            val checkpoint = recognizedByPosition[position + 1] ?: recognizedByPosition[position + 2]
            if (checkpoint == null) {
                skipped++
                Log.d(TAG, "Position $position: no checkpoint frame at ${position + 1} or ${position + 2}, skipped")
                continue
            }

            val confirmedSide = state.board.filterValues { it.player == mover }
            val checkpointSide = checkpoint.board.filterValues { it.player == mover }

            if (confirmedSide == checkpointSide) {
                val pass = passMoveFor(state, position)
                if (pass != null) {
                    state = applyFoundMove(state, pass)
                    recovered++
                    logMove(position, pass, isPass = true)
                } else {
                    skipped++
                }
                continue
            }

            val move = findLegalMove(state.board, confirmedSide, checkpointSide, mover)
            if (move != null) {
                state = applyFoundMove(state, move)
                recovered++
                logMove(position, move, isPass = false)
            } else {
                skipped++
                Log.d(TAG, "Position $position: no clean move against checkpoint ${checkpoint.position}, skipped")
            }
        }
        // 마지막 수([lastPosition])는 위 루프가 다루지 않습니다 - 다음 수 체크포인트가
        // 없어서, 그 수를 둔 쪽 자신의 위치가 안정됐는지 확인해줄 프레임이 원래
        // 없습니다. 그래서 카운터가 [lastPosition]으로 막 바뀐 그 프레임(아직 착수
        // 애니메이션 중일 수 있음) 대신, 영상이 완전히 끝난 뒤의 진짜 마지막
        // 프레임([VideoStillFrameFinder.findFinalFrame])을 체크포인트로 씁니다 - 그
        // 시점엔 더 이상 아무 수도 없으므로 안정 여부를 몰라도 안전합니다.
        if (lastPosition > 0) {
            val finalMover = if (lastPosition % 2 == 1) Player.CHO else Player.HAN
            val finalFrameBitmap = stillFrameFinder.findFinalFrame(videoUri)
            val finalBoard = finalFrameBitmap?.let { bitmap ->
                dumpDebugFrame(lastPosition, bitmap)
                val board = withContext(Dispatchers.Default) {
                    recognitionService.extractFromBitmap(bitmap).getOrNull()?.let { toImportedBoardState(it) }
                }?.toGameState()?.board
                bitmap.recycle()
                board
            }

            if (finalBoard == null) {
                skipped++
                Log.d(TAG, "Position $lastPosition (final): could not read/recognize video's final frame, skipped")
            } else {
                val confirmedSide = state.board.filterValues { it.player == finalMover }
                val finalSide = finalBoard.filterValues { it.player == finalMover }
                if (confirmedSide == finalSide) {
                    val pass = passMoveFor(state, lastPosition)
                    if (pass != null) {
                        state = applyFoundMove(state, pass)
                        recovered++
                        logMove(lastPosition, pass, isPass = true)
                    } else {
                        skipped++
                    }
                } else {
                    val move = findLegalMove(state.board, confirmedSide, finalSide, finalMover)
                    if (move != null) {
                        state = applyFoundMove(state, move)
                        recovered++
                        logMove(lastPosition, move, isPass = false)
                    } else {
                        skipped++
                        Log.d(TAG, "Position $lastPosition (final): no clean move against final frame, skipped")
                    }
                }
            }
        }

        Log.d(TAG, "Recovered $recovered move(s) up to position $lastPosition ($skipped position(s) unmatched)")

        emit(VideoImportProgress.Finished(state, recovered, viewpoint, recognized.size))
    }

    /**
     * [beforeSide]->[afterSide] 사이(둘 다 [mover] 진영 기물만 있는 판) 단일 수를
     * 찾아, [beforeFull](양쪽 다 있는 전체 판)로 실제로 둘 수 있는 자리인지까지
     * 검증합니다. 잡은 기물은 같은 진영 필터로는 안 보이므로 [beforeFull]에서
     * 따로 찾아 채웁니다.
     */
    private fun findLegalMove(
        beforeFull: Map<Position, Piece>,
        beforeSide: Map<Position, Piece>,
        afterSide: Map<Position, Piece>,
        mover: Player
    ): Move? {
        val move = inferMoveFromBoardDiff(beforeSide, afterSide, expectedMover = mover) ?: return null
        val movedPiece = move.movedPiece ?: return null
        val gameState = GameState(board = beforeFull, currentPlayer = mover)
        if (!moveValidator.isValidMove(movedPiece, move.to, gameState)) return null

        val capturedPiece = beforeFull[move.to]?.takeIf { it.player != mover }
        return move.copy(capturedPiece = capturedPiece)
    }

    /** [GameRules.applyPass] 와 같은 방식(왕이 제자리로) 으로 한 수 쉼을 나타냅니다. */
    private fun passMoveFor(state: GameState, position: Int): Move? {
        val mover = if (position % 2 == 1) Player.CHO else Player.HAN
        val general = state.board.values.filterIsInstance<Piece.General>().firstOrNull { it.player == mover }
            ?: return null
        return Move(from = general.position, to = general.position, movedPiece = general)
    }

    /**
     * 찾은 수를 적용합니다. `GameState.applyMove` 는 지금 `currentPlayer` 를 무조건
     * 반대로 뒤집을 뿐이라(누가 실제로 뒀는지는 안 봄), `move.movedPiece.player`
     * 기준으로 직접 바로잡습니다.
     */
    private fun applyFoundMove(state: GameState, move: Move): GameState {
        val mover = move.movedPiece!!.player
        return state.applyMove(move).copy(currentPlayer = mover.opponent())
    }

    private fun logMove(position: Int, move: Move, isPass: Boolean) {
        if (isPass) {
            Log.d(TAG, "Position $position: ${move.movedPiece?.player} passed")
            return
        }
        Log.d(
            TAG,
            "Position $position: ${move.movedPiece?.player} moved " +
                "${move.from} -> ${move.to}" + (move.capturedPiece?.let { " (captured $it)" } ?: "")
        )
    }

    private fun toImportedBoardState(extracted: BoardRecognitionService.ExtractedText): ImportedBoardState? {
        val detectedPieces = ImportedBoardState.filterByConfidence(
            extracted.detectedPieces.associate { detected ->
                detected.position to DetectedPiece(piece = detected.piece, confidence = detected.confidence)
            }
        )
        if (detectedPieces.isEmpty()) return null

        return ImportedBoardState(
            detectedPieces = detectedPieces,
            overallConfidence = detectedPieces.values.map { it.confidence }.average().toFloat(),
            gridDetected = true
        )
    }
}
