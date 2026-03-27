package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.PaymentErrorCode;
import com.loopers.support.error.CoreException;
import java.time.LocalTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "maintenance")
@Slf4j
@Getter
@Setter
public class MaintenanceWindowFilter {

    private List<MaintenanceWindow> windows = List.of();

    public void checkMaintenanceWindow() {
        LocalTime now = LocalTime.now();
        for (MaintenanceWindow window : windows) {
            if (window.isInWindow(now)) {
                log.warn("[점검시간] {} ({}~{}) - 결제 요청 차단",
                        window.getDescription(), window.getStart(), window.getEnd());
                throw new CoreException(
                        PaymentErrorCode.MAINTENANCE_WINDOW,
                        "점검 종료 예정: " + window.getEnd());
            }
        }
    }

    @Getter
    @Setter
    public static class MaintenanceWindow {
        private String start;
        private String end;
        private String description;

        public boolean isInWindow(LocalTime now) {
            LocalTime startTime = LocalTime.parse(start);
            LocalTime endTime = LocalTime.parse(end);

            if (startTime.isBefore(endTime)) {
                return !now.isBefore(startTime) && now.isBefore(endTime);
            } else {
                // 자정을 넘는 경우: ex) 23:30~00:30
                return !now.isBefore(startTime) || now.isBefore(endTime);
            }
        }
    }
}
