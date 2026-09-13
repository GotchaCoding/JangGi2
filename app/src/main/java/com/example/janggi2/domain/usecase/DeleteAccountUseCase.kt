package com.example.janggi2.domain.usecase

import com.example.janggi2.domain.repository.AuthRepository
import com.example.janggi2.domain.repository.GameRepository
import javax.inject.Inject

/**
 * 계정과 그에 딸린 데이터를 모두 지웁니다. Play 정책상 계정을 만들 수 있는 앱은
 * 앱 안에서 삭제할 수 있는 경로를 제공해야 합니다.
 *
 * 순서가 중요합니다: 기보를 먼저 지우고 계정을 나중에 지웁니다. 계정을 먼저 지우면
 * uid 를 잃어 클라우드에 남은 문서를 찾을 수도, 지울 수도 없게 됩니다.
 */
class DeleteAccountUseCase @Inject constructor(
    private val authRepository: AuthRepository,
    private val gameRepository: GameRepository
) {
    /**
     * @param reAuthIdToken 재인증용 구글 ID 토큰. Firebase 는 로그인이 오래된 세션의
     * 계정 삭제를 거부합니다.
     */
    suspend operator fun invoke(reAuthIdToken: String): Result<Unit> = try {
        gameRepository.purgeAllUserData()
        authRepository.deleteAccount(reAuthIdToken)
    } catch (e: Exception) {
        // 데이터 삭제에 실패하면 계정은 건드리지 않습니다 - 계정이 남아 있어야
        // 사용자가 다시 시도할 수 있습니다.
        Result.failure(e)
    }
}
