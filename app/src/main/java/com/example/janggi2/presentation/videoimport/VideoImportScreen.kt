package com.example.janggi2.presentation.videoimport

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.janggi2.domain.model.GameState
import com.example.janggi2.domain.model.Player
import com.example.janggi2.presentation.common.swipeToGoBack

/**
 * 동영상에서 기보를 불러오는 화면. 정지 프레임을 찾아 기존 사진 인식 파이프라인을
 * 프레임마다 돌리는 동안 진행률을 보여줍니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoImportScreen(
    onImportComplete: (GameState, Player) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: VideoImportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.onVideoSelected(it) }
    }

    // Auto-navigate on successful import
    LaunchedEffect(uiState.importedGameState) {
        uiState.importedGameState?.let { gameState ->
            onImportComplete(gameState, uiState.importedViewpoint)
        }
    }

    Scaffold(
        modifier = Modifier.swipeToGoBack(onNavigateBack),
        topBar = {
            TopAppBar(
                title = { Text("동영상에서 기보 불러오기") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when {
                uiState.isProcessing -> {
                    ProcessingView(
                        stepLabel = uiState.stepLabel,
                        completed = uiState.stepCompleted,
                        total = uiState.stepTotal,
                        etaSeconds = uiState.etaSeconds
                    )
                }
                uiState.error != null -> {
                    ErrorView(
                        error = uiState.error!!,
                        onRetry = { videoPickerLauncher.launch("video/*") },
                        onDismiss = viewModel::clearError
                    )
                }
                else -> {
                    SelectVideoView(
                        onSelectVideo = { videoPickerLauncher.launch("video/*") }
                    )
                }
            }
        }
    }
}

/**
 * 초기 상태 - 동영상 선택을 안내합니다.
 */
@Composable
private fun SelectVideoView(
    onSelectVideo: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.VideoLibrary,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "대국 동영상 불러오기",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "수마다 기물이 착수 지점에 멈추는 순간을 찾아 기보를 재구성합니다",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onSelectVideo,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.VideoLibrary,
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text("갤러리에서 동영상 선택")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "💡 팁",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "• 판 전체가 화면에 크고 또렷하게 보이는 영상이 좋습니다\n" +
                            "• 사람이 직접 두는 걸 녹화한 영상일수록 수 사이 간격이 " +
                            "여유로워 더 잘 인식됩니다\n" +
                            "• 아주 빠른 자동재생 영상은 일부 수를 놓칠 수 있습니다",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

/**
 * 처리 중 상태 - 지금 어느 세부 단계(영상 훑기/빈 구간 재확인/체크포인트 검증/기물
 * 인식)를 돌고 있는지, 그 단계의 진행률을 원형 게이지로, 예상 완료 시간을 글로
 * 보여줍니다.
 */
@Composable
private fun ProcessingView(
    stepLabel: String?,
    completed: Int,
    total: Int,
    etaSeconds: Long?
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        val fraction = if (total > 0) (completed.toFloat() / total).coerceIn(0f, 1f) else 0f

        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { fraction },
                modifier = Modifier.size(140.dp),
                strokeWidth = 10.dp
            )
            Text(
                text = "${(fraction * 100).toInt()}%",
                style = MaterialTheme.typography.headlineSmall
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = stepLabel ?: "준비 중...",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )

        if (total > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$completed / $total",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = etaSeconds?.let { "완료까지 약 ${formatEtaSeconds(it)}" } ?: "잠시만 기다려 주세요...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** 초 단위 예상 남은 시간을 "1분 30초" / "45초" 같은 문구로 바꿉니다. */
private fun formatEtaSeconds(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return if (minutes > 0) "${minutes}분 ${remainingSeconds}초" else "${remainingSeconds}초"
}

/**
 * 오류 상태 - 오류 메시지와 다시 시도 버튼을 보여줍니다.
 */
@Composable
private fun ErrorView(
    error: String,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "오류 발생",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onRetry,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("다시 시도")
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("취소")
            }
        }
    }
}
