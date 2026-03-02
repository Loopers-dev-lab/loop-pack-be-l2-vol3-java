package com.loopers.domain.point;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.PointErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class PointServiceTest {

    private PointAccountRepository pointAccountRepository;
    private PointService pointService;

    @BeforeEach
    void setUp() {
        pointAccountRepository = Mockito.mock(PointAccountRepository.class);
        pointService = new PointService(pointAccountRepository);
    }

    @DisplayName("포인트 계정을 생성할 때,")
    @Nested
    class 생성 {

        @Test
        void 유효한_userId면_balance_0으로_생성된다() {
            // arrange
            when(pointAccountRepository.save(any(PointAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            PointAccount account = pointService.createAccount(1L);

            // assert
            assertThat(account.getBalance()).isEqualTo(0);
        }

        @Test
        void 생성_시_save가_호출된다() {
            // arrange
            when(pointAccountRepository.save(any(PointAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            pointService.createAccount(1L);

            // assert
            verify(pointAccountRepository).save(any(PointAccount.class));
        }
    }

    @DisplayName("포인트 계정을 조회할 때,")
    @Nested
    class 조회 {

        @Test
        void 존재하지_않는_계정이면_예외가_발생한다() {
            // arrange
            when(pointAccountRepository.findByUserId(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> pointService.getAccount(1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(PointErrorType.ACCOUNT_NOT_FOUND);
        }

        @Test
        void 존재하는_계정이면_반환한다() {
            // arrange
            PointAccount account = PointAccount.create(1L);
            when(pointAccountRepository.findByUserId(1L)).thenReturn(Optional.of(account));

            // act
            PointAccount result = pointService.getAccount(1L);

            // assert
            assertThat(result.getUserId()).isEqualTo(1L);
        }
    }

    @DisplayName("포인트를 사용할 때,")
    @Nested
    class 사용 {

        @Test
        void 존재하지_않는_계정이면_예외가_발생한다() {
            // arrange
            when(pointAccountRepository.findByUserId(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> pointService.use(1L, 5000))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(PointErrorType.ACCOUNT_NOT_FOUND);
        }

        @Test
        void 잔액이_부족하면_예외가_발생한다() {
            // arrange
            PointAccount account = PointAccount.create(1L);
            account.charge(3000);
            when(pointAccountRepository.findByUserId(1L)).thenReturn(Optional.of(account));

            // act & assert
            assertThatThrownBy(() -> pointService.use(1L, 5000))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(PointErrorType.INSUFFICIENT_BALANCE);
        }

        @Test
        void 유효한_요청이면_use가_호출된다() {
            // arrange
            PointAccount account = PointAccount.create(1L);
            account.charge(10000);
            when(pointAccountRepository.findByUserId(1L)).thenReturn(Optional.of(account));

            // act
            pointService.use(1L, 3000);

            // assert
            assertThat(account.getBalance()).isEqualTo(7000);
        }
    }

    @DisplayName("포인트를 충전할 때,")
    @Nested
    class 충전 {

        @Test
        void 존재하지_않는_계정이면_예외가_발생한다() {
            // arrange
            when(pointAccountRepository.findByUserId(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> pointService.charge(1L, 5000))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(PointErrorType.ACCOUNT_NOT_FOUND);
        }

        @Test
        void 유효한_요청이면_charge가_호출된다() {
            // arrange
            PointAccount account = PointAccount.create(1L);
            when(pointAccountRepository.findByUserId(1L)).thenReturn(Optional.of(account));

            // act
            pointService.charge(1L, 5000);

            // assert
            assertThat(account.getBalance()).isEqualTo(5000);
        }
    }

    @DisplayName("포인트를 적립할 때,")
    @Nested
    class 적립 {

        @Test
        void 금액_구간별_적립률이_적용된다() {
            // arrange — 100000 이상이면 3%
            PointAccount account = PointAccount.create(1L);
            when(pointAccountRepository.findByUserId(1L)).thenReturn(Optional.of(account));

            // act
            pointService.earn(1L, 100000);

            // assert — 100000 * 3% = 3000
            assertThat(account.getBalance()).isEqualTo(3000);
        }
    }
}
