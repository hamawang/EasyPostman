package com.laker.postman.startup;

import com.laker.postman.frame.MainFrame;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.swing.SwingWorker;
import java.util.concurrent.CompletableFuture;

/**
 * 无 Splash 模式的主窗口后台启动任务。
 */
@RequiredArgsConstructor
@Slf4j
class NoSplashStartupWorker extends SwingWorker<Void, Void> {
    private final StartupCoordinator startupCoordinator;
    private final CompletableFuture<MainFrame> visibleMainFrame;

    @Override
    protected Void doInBackground() {
        try {
            startupCoordinator.initializePluginRuntimeAfterHostReady();
            return null;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize GUI runtime", e);
        }
    }

    @Override
    protected void done() {
        try {
            MainFrame mainFrame = visibleMainFrame.join();
            // 主窗口可能在后台初始化期间已被关闭；此时不要再弹启动错误或装载内容。
            if (mainFrame.isStartupCloseRequested()) {
                mainFrame.exitBeforeMainContentReady();
                return;
            }
            get();
            if (mainFrame.markStartupRuntimeReady()) {
                mainFrame.exitBeforeMainContentReady();
                return;
            }
            long menuStartedAt = System.nanoTime();
            mainFrame.installMainMenu();
            log.info("GUI startup stage complete: main menu installed in {} ms",
                    (System.nanoTime() - menuStartedAt) / 1_000_000);
            startupCoordinator.runAfterMainContentReady(
                    mainFrame,
                    startupCoordinator::scheduleBackgroundTasks,
                    StartupFailureHandler::showStartupErrorAndExit
            );
            mainFrame.loadMainContentAsync();
        } catch (Throwable e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            StartupFailureHandler.showStartupErrorAndExit(e);
        }
    }
}
