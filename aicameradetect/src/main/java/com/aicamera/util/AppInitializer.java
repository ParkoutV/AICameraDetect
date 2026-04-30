package com.aicamera.util;

import com.aicamera.tasks.TempVideoWatchdogTask;

import java.util.concurrent.TimeUnit;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class AppInitializer implements ServletContextListener {

    private SchedulerService schedulerService;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        // 웹 애플리케이션이 시작될 때 ConfigUtil을 초기화합니다.
        ConfigUtil.init(sce.getServletContext());

        // 공용 스케줄러 서비스를 생성하고 Watchdog 작업을 등록합니다.
        schedulerService = new SchedulerService();
        // 1분 후에 시작하여, 매 1분마다 주기적으로 실행 (타임아웃 감지는 Task 내부에서 5분으로 처리)
        schedulerService.scheduleTask(new TempVideoWatchdogTask(), 1, 1, TimeUnit.MINUTES);
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // 웹 애플리케이션이 종료될 때 필요한 정리 작업을 수행할 수 있습니다.
        if (schedulerService != null) {
            schedulerService.shutdown();
        }
    }
}