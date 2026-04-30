package com.aicamera.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class SchedulerService {
    // 여러 스케줄링 작업을 처리할 수 있도록 코어 스레드 수를 2개로 설정
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /**
     * 주기적인 작업을 스케줄러에 등록합니다.
     * @param task 실행할 작업 (Runnable)
     * @param initialDelay 초기 지연 시간
     * @param period 반복 주기
     * @param unit 시간 단위
     */
    public void scheduleTask(Runnable task, long initialDelay, long period, TimeUnit unit) {
        scheduler.scheduleAtFixedRate(task, initialDelay, period, unit);
        System.out.println("[SchedulerService] " + task.getClass().getSimpleName() + " 작업이 " + period + " " + unit.toString() + " 주기로 스케줄되었습니다.");
    }

    /**
     * 스케줄러를 종료합니다. 웹 애플리케이션 종료 시 호출되어야 합니다.
     */
    public void shutdown() {
        System.out.println("[SchedulerService] 스케줄러를 종료합니다...");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(1, TimeUnit.MINUTES)) scheduler.shutdownNow();
        } catch (InterruptedException e) { scheduler.shutdownNow(); }
        System.out.println("[SchedulerService] 스케줄러가 성공적으로 종료되었습니다.");
    }
}