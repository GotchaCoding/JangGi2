package com.example.janggi2.presentation.game.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.janggi2.domain.model.Move
import com.example.janggi2.domain.model.Piece
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position
import com.example.janggi2.domain.model.ReviewComment
import com.example.janggi2.ui.theme.JangGi2Theme

/**
 * AI 리뷰를 복기하다가 "검토"로 갈라져 나가 둬 본 수순에 댓글을 남기는 영역.
 *
 * 복기 조작 버튼(복기 종료 등) 바로 아래에 자리합니다 - "검토"를 누르면 판이 살아있는
 * 대국으로 바뀌어 그 자리의 버튼 줄 자체가 [GameControls]로 바뀌지만, 화면에서
 * 이 영역이 있는 자리는 그대로입니다.
 *
 * @param canWrite "검토"를 눌러 갈라져 나간 뒤인지([GameUiState.reviewBranchStartIndex]
 *   있음) - AI 리뷰를 그냥 열람하는 중(아직 "검토"를 안 누른 상태)에는 댓글을 쓰는
 *   입력창 자체를 아예 안 보여줍니다(댓글 목록은 그대로 보입니다). "검토"로 수를
 *   둬 본 뒤에만 입력창이 나타납니다.
 * @param onCommentClick 댓글을 눌렀을 때 - 그 댓글을 남길 때 봤던 수순을 그대로 엽니다.
 */
@Composable
fun ReviewCommentsPanel(
    comments: List<ReviewComment>,
    commentInput: String,
    canWrite: Boolean,
    onCommentInputChange: (String) -> Unit,
    onSaveComment: () -> Unit,
    onCommentClick: (ReviewComment) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            text = "댓글",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // AI 리뷰를 그냥 열람하는 중(검토 전)에는 이 입력 영역 자체를 안 보여줍니다 -
        // "검토"로 수를 둬 본 뒤에만 댓글을 쓸 수 있습니다.
        if (canWrite) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = commentInput,
                    onValueChange = onCommentInputChange,
                    placeholder = { Text("이 수순에 댓글 남기기") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Button(
                    onClick = onSaveComment,
                    enabled = commentInput.isNotBlank()
                ) {
                    Text("저장")
                }
            }
        }

        if (comments.isEmpty()) {
            Text(
                text = "아직 댓글이 없습니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            // 바깥(GameScreen)이 이미 verticalScroll 이 걸린 Column이라, LazyColumn을
            // 그 안에 무제한 높이로 두면 "infinity maximum height constraints" 예외로
            // 앱이 죽습니다(실측 확인) - heightIn(max=...)로 자체적으로 유한한 높이
            // 제약을 줘서, 댓글이 많아지면 이 목록 안에서만 스크롤되게 합니다.
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
            ) {
                items(comments, key = { it.id }) { comment ->
                    ReviewCommentRow(comment, onClick = { onCommentClick(comment) })
                    HorizontalDivider()
                }
            }
        }
    }
}

/**
 * 지금은 메시지만 보여줍니다 - 나중에 로그인·닉네임이 생기면 작성자도 같이
 * 보여줄 자리입니다. 수순은 텍스트로 안 보여주고, 눌러야 [onClick]으로 열립니다.
 */
@Composable
private fun ReviewCommentRow(comment: ReviewComment, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp)
    ) {
        Text(
            text = comment.message,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ReviewCommentsPanelPreview() {
    val soldier = Piece.Soldier(Player.CHO, Position(0, 3))
    JangGi2Theme {
        ReviewCommentsPanel(
            comments = listOf(
                ReviewComment(
                    id = 1,
                    reviewId = 1,
                    message = "여기서 졸을 미는 대신 상을 뛰면 더 좋아 보입니다",
                    branchStartIndex = 4,
                    moves = listOf(Move(Position(0, 3), Position(0, 4), movedPiece = soldier)),
                    createdAt = System.currentTimeMillis()
                )
            ),
            commentInput = "",
            canWrite = true,
            onCommentInputChange = {},
            onSaveComment = {},
            onCommentClick = {}
        )
    }
}
