package com.example.janggi2.domain.usecase

import com.example.janggi2.fake.FakeAuthRepository
import com.example.janggi2.fake.FakeGameRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 계정 삭제는 되돌릴 수 없으므로 순서가 핵심입니다: 기보를 먼저 지우고 계정을 지웁니다.
 * 뒤집으면 uid 를 잃어 클라우드에 남은 문서를 영영 지울 수 없게 됩니다.
 */
class DeleteAccountUseCaseTest {

    /** 호출 순서를 기록하는 저장소들. */
    private class RecordingGameRepository(private val log: MutableList<String>) : FakeGameRepository() {
        override suspend fun purgeAllUserData() {
            log.add("purge")
        }
    }

    private class RecordingAuthRepository(
        private val log: MutableList<String>
    ) : FakeAuthRepository() {
        override suspend fun deleteAccount(reAuthIdToken: String): Result<Unit> {
            log.add("deleteAccount")
            return Result.success(Unit)
        }
    }

    /** 데이터 삭제가 실패하는 저장소. */
    private class FailingGameRepository : FakeGameRepository() {
        override suspend fun purgeAllUserData() {
            throw IllegalStateException("network down")
        }
    }

    @Test
    fun `기보를 먼저 지우고 그 다음에 계정을 지운다`() = runTest {
        val log = mutableListOf<String>()
        val useCase = DeleteAccountUseCase(
            RecordingAuthRepository(log),
            RecordingGameRepository(log)
        )

        val result = useCase("token")

        assertTrue(result.isSuccess)
        assertEquals(listOf("purge", "deleteAccount"), log)
    }

    @Test
    fun `데이터 삭제가 실패하면 계정은 지우지 않는다`() = runTest {
        val log = mutableListOf<String>()
        val useCase = DeleteAccountUseCase(
            RecordingAuthRepository(log),
            FailingGameRepository()
        )

        val result = useCase("token")

        assertTrue(result.isFailure)
        // 계정이 남아 있어야 사용자가 다시 시도할 수 있습니다.
        assertFalse(log.contains("deleteAccount"))
    }
}
