package com.example.janggi2.presentation.debug

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.janggi2.data.imageprocessing.PieceDetector
import com.example.janggi2.domain.model.Player
import com.example.janggi2.domain.model.Position

/**
 * 선 검출 디버그 화면
 *
 * 검출된 선, 교차점, 기물을 시각적으로 확인할 수 있는 디버그 화면입니다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LineDetectionDebugScreen(
    viewModel: LineDetectionDebugViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // 이미지 선택 런처
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.analyzeImage(it) }
    }

    // 에러 표시
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    // 검증 다이얼로그 표시
    val selectedPosition = uiState.selectedPosition
    if (uiState.showVerificationDialog && selectedPosition != null) {
        val verification = viewModel.getSelectedVerification()
        if (verification != null) {
            PieceVerificationDialog(
                position = selectedPosition,
                verification = verification,
                onVerify = { pieceType, player ->
                    viewModel.verifyIntersection(selectedPosition, pieceType, player)
                },
                onDismiss = { viewModel.dismissVerificationDialog() }
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Line Detection Debug") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { imagePickerLauncher.launch("image/*") }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Select Image"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                uiState.isLoading -> {
                    LoadingContent()
                }
                uiState.debugBitmap != null -> {
                    DebugImageContent(
                        uiState = uiState,
                        onIntersectionClick = { position ->
                            viewModel.onIntersectionClick(position)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 검증 통계 카드
                    VerificationStatsCard(viewModel.getVerificationStats())
                }
                else -> {
                    EmptyContent(onSelectImage = { imagePickerLauncher.launch("image/*") })
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "이미지 분석 중...",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun EmptyContent(onSelectImage: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.PhotoLibrary,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "이미지를 선택하여 선 검출 결과를 확인하세요",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onSelectImage) {
            Icon(
                imageVector = Icons.Default.PhotoLibrary,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("이미지 선택")
        }
    }
}

@Composable
private fun DebugImageContent(
    uiState: LineDetectionDebugViewModel.DebugUiState,
    onIntersectionClick: (Position) -> Unit
) {
    // 줌 및 팬 상태
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 디버그 이미지 (줌 가능, 클릭 가능)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(450.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .onGloballyPositioned { coordinates ->
                        containerSize = coordinates.size
                    }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale > 1f) {
                                Offset(
                                    x = offset.x + pan.x,
                                    y = offset.y + pan.y
                                )
                            } else {
                                Offset.Zero
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                uiState.debugBitmap?.let { bitmap ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offset.x,
                                translationY = offset.y
                            )
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Debug Image",
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { coordinates ->
                                    imageSize = coordinates.size
                                },
                            contentScale = ContentScale.Fit
                        )

                        // 교차점 클릭 오버레이
                        if (scale >= 1.5f) {  // 확대시에만 표시
                            IntersectionOverlay(
                                bitmap = bitmap,
                                intersectionGrid = uiState.intersectionGrid,
                                verifications = uiState.intersectionVerifications,
                                containerSize = containerSize,
                                onIntersectionClick = onIntersectionClick
                            )
                        }
                    }
                }

                // 안내 메시지
                if (scale < 1.5f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "확대하면 교차점 클릭 가능",
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        // 줌 컨트롤
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Zoom: ${String.format("%.1f", scale)}x",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            IconButton(
                onClick = {
                    scale = 1f
                    offset = Offset.Zero
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset Zoom",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 통계 정보
        uiState.stats?.let { stats ->
            StatsCard(stats)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 범례
        LegendCard()
    }
}

@Composable
private fun StatsCard(stats: LineDetectionDebugViewModel.DetectionStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Detection Statistics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 선 검출 결과
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("Vertical Lines", "${stats.verticalLineCount}/9")
                StatItem("Horizontal Lines", "${stats.horizontalLineCount}/10")
                StatItem("Intersections", "${stats.intersectionCount}")
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 기물 검출 결과
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem("Total Pieces", "${stats.pieceCount}")
                StatItem("CHO (Red)", "${stats.choCount}", Color.Red)
                StatItem("HAN (Blue)", "${stats.hanCount}", Color.Blue)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 신뢰도
            Text(
                text = "Line Confidence",
                style = MaterialTheme.typography.bodySmall
            )
            LinearProgressIndicator(
                progress = { stats.lineConfidence },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )
            Text(
                text = "${(stats.lineConfidence * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )

            if (stats.pieceCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Average Piece Confidence",
                    style = MaterialTheme.typography.bodySmall
                )
                LinearProgressIndicator(
                    progress = { stats.avgPieceConfidence },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                )
                Text(
                    text = "${(stats.avgPieceConfidence * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

@Composable
private fun LegendCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Legend",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                LegendItem(Color.Green, "Board Lines")
                LegendItem(Color.Blue, "Intersections")
                LegendItem(Color.Red, "CHO Pieces")
                LegendItem(Color(0xFF0066FF), "HAN Pieces")
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .background(color, RoundedCornerShape(2.dp))
                .border(1.dp, Color.Black.copy(alpha = 0.2f), RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

/**
 * 교차점 클릭 오버레이
 */
@Composable
private fun IntersectionOverlay(
    bitmap: android.graphics.Bitmap,
    intersectionGrid: com.example.janggi2.data.imageprocessing.IntersectionCalculator.IntersectionGrid?,
    verifications: Map<Position, LineDetectionDebugViewModel.IntersectionVerification>,
    containerSize: IntSize,
    onIntersectionClick: (Position) -> Unit
) {
    if (intersectionGrid == null) return

    val density = LocalDensity.current

    // 이미지가 컨테이너에 맞춰질 때의 스케일 계산
    val imageAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
    val containerAspect = containerSize.width.toFloat() / containerSize.height.toFloat()

    val (displayWidth, displayHeight, offsetX, offsetY) = if (imageAspect > containerAspect) {
        // 이미지가 더 넓음 - 가로 맞춤
        val w = containerSize.width.toFloat()
        val h = containerSize.width.toFloat() / imageAspect
        val ox = 0f
        val oy = (containerSize.height - h) / 2f
        listOf(w, h, ox, oy)
    } else {
        // 이미지가 더 높음 - 세로 맞춤
        val h = containerSize.height.toFloat()
        val w = containerSize.height.toFloat() * imageAspect
        val ox = (containerSize.width - w) / 2f
        val oy = 0f
        listOf(w, h, ox, oy)
    }

    val scaleX = displayWidth / bitmap.width
    val scaleY = displayHeight / bitmap.height

    Box(modifier = Modifier.fillMaxSize()) {
        intersectionGrid.intersections.forEach { intersection ->
            val screenX = offsetX + intersection.pixelX * scaleX
            val screenY = offsetY + intersection.pixelY * scaleY

            val verification = verifications[intersection.position]
            val hasDetectedPiece = verification?.detectedPiece != null
            val isVerified = verification?.isVerified == true
            val isCorrect = verification?.isCorrect

            // 교차점 색상 결정
            val bgColor = when {
                isVerified && isCorrect == true -> Color.Green.copy(alpha = 0.7f)
                isVerified && isCorrect == false -> Color.Red.copy(alpha = 0.7f)
                hasDetectedPiece -> Color.Yellow.copy(alpha = 0.5f)
                else -> Color.White.copy(alpha = 0.3f)
            }

            // 교차점 ID 계산 (row * 9 + col)
            val intersectionId = verification?.intersectionId
                ?: (intersection.position.row * 9 + intersection.position.col)

            with(density) {
                Box(
                    modifier = Modifier
                        .offset(
                            x = screenX.toDp() - 12.dp,
                            y = screenY.toDp() - 12.dp
                        )
                        .size(24.dp)
                        .background(bgColor, CircleShape)
                        .border(1.dp, Color.White, CircleShape)
                        .clickable { onIntersectionClick(intersection.position) },
                    contentAlignment = Alignment.Center
                ) {
                    // 고유 번호 표시 (0-89)
                    Text(
                        text = "$intersectionId",
                        fontSize = 7.sp,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 기물 검증 다이얼로그
 */
@Composable
private fun PieceVerificationDialog(
    position: Position,
    verification: LineDetectionDebugViewModel.IntersectionVerification,
    onVerify: (PieceDetector.PieceType?, Player?) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedPieceType by remember { mutableStateOf<PieceDetector.PieceType?>(verification.verifiedPieceType ?: verification.detectedPiece?.pieceType) }
    var selectedPlayer by remember { mutableStateOf<Player?>(verification.verifiedPlayer ?: verification.detectedPiece?.player) }

    // 교차점 ID 계산
    val intersectionId = verification.intersectionId

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("교차점 #$intersectionId (${position.col}, ${position.row}) 검증")
        },
        text = {
            Column {
                // AI 검출 결과 표시
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "AI 검출 결과",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (verification.detectedPiece != null) {
                            Text(
                                text = "${verification.detectedPiece.player}: ${verification.detectedPiece.pieceType}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "신뢰도: ${(verification.detectedPiece.confidence * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "기물 없음",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 분석 상세 정보 카드
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "분석 상세",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = verification.analysisDetails?.analysisReason ?: "분석 정보 없음",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 진영 선택
                Text(
                    text = "실제 진영",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedPlayer == null,
                        onClick = {
                            selectedPlayer = null
                            selectedPieceType = null
                        },
                        label = { Text("없음") }
                    )
                    FilterChip(
                        selected = selectedPlayer == Player.CHO,
                        onClick = { selectedPlayer = Player.CHO },
                        label = { Text("초(楚)") }
                    )
                    FilterChip(
                        selected = selectedPlayer == Player.HAN,
                        onClick = { selectedPlayer = Player.HAN },
                        label = { Text("한(漢)") }
                    )
                }

                // 기물 선택 (진영이 선택된 경우만)
                if (selectedPlayer != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "실제 기물",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // 기물 목록
                    Column {
                        // 첫 번째 행: 왕, 사, 상, 마
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = selectedPieceType == PieceDetector.PieceType.GENERAL,
                                onClick = { selectedPieceType = PieceDetector.PieceType.GENERAL },
                                label = { Text("왕", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = selectedPieceType == PieceDetector.PieceType.GUARD,
                                onClick = { selectedPieceType = PieceDetector.PieceType.GUARD },
                                label = { Text("사", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = selectedPieceType == PieceDetector.PieceType.ELEPHANT,
                                onClick = { selectedPieceType = PieceDetector.PieceType.ELEPHANT },
                                label = { Text("상", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = selectedPieceType == PieceDetector.PieceType.HORSE,
                                onClick = { selectedPieceType = PieceDetector.PieceType.HORSE },
                                label = { Text("마", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))

                        // 두 번째 행: 차, 포, 졸
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilterChip(
                                selected = selectedPieceType == PieceDetector.PieceType.CHARIOT,
                                onClick = { selectedPieceType = PieceDetector.PieceType.CHARIOT },
                                label = { Text("차", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = selectedPieceType == PieceDetector.PieceType.CANNON,
                                onClick = { selectedPieceType = PieceDetector.PieceType.CANNON },
                                label = { Text("포", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = selectedPieceType == PieceDetector.PieceType.SOLDIER,
                                onClick = { selectedPieceType = PieceDetector.PieceType.SOLDIER },
                                label = { Text("졸", fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onVerify(selectedPieceType, selectedPlayer) }
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("확인")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}

/**
 * 검증 통계 카드
 */
@Composable
private fun VerificationStatsCard(stats: LineDetectionDebugViewModel.VerificationStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Verification Progress",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // 진행률 표시
            LinearProgressIndicator(
                progress = { stats.verifiedCount.toFloat() / stats.totalIntersections },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "${stats.verifiedCount}/${stats.totalIntersections}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "검증됨",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.Green,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${stats.correctCount}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Green
                        )
                    }
                    Text(
                        text = "정확",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = Color.Red,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "${stats.incorrectCount}",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Red
                        )
                    }
                    Text(
                        text = "오류",
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(stats.accuracy * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "정확도",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (stats.piecesFound > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "발견된 기물: ${stats.piecesFound}개",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}
