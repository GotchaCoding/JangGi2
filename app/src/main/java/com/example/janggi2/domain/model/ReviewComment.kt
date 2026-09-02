package com.example.janggi2.domain.model

/**
 * AI 리뷰를 복기하다가 "검토"로 갈라져 나가 둬 본 수순 하나에 남긴 댓글.
 *
 * @param reviewId 이 댓글이 딸린 [GameReview]가 저장된 game_reviews 행의 id.
 * @param message 댓글 내용.
 * @param branchStartIndex 원래 기보의 moveHistory 에서 이 수순이 갈라져 나간 지점
 *   (0부터 시작하는 인덱스). 댓글을 다시 열어 볼 때, 원래 기보의 이 지점까지 재생한
 *   뒤 [moves] 를 이어 붙여 그때 본 수순을 그대로 되살립니다.
 * @param moves "검토"를 누른 지점(원래 기보의 그 수)부터 댓글을 남기기까지 실제로
 *   둬 본 수순. 원래 기보의 실제 수와는 별개로, 이 지점에서 갈라져 나간 가지입니다.
 * @param createdAt 댓글을 저장한 시각(epoch millis).
 */
data class ReviewComment(
    val id: Long = 0,
    val reviewId: Long,
    val message: String,
    val branchStartIndex: Int,
    val moves: List<Move>,
    val createdAt: Long
)
