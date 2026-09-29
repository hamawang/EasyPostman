package com.laker.postman.frame;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 管理主窗口启动期间的一次性事件和回调通知。
 */
class MainFrameStartupLifecycle {
    enum CloseAction {
        NORMAL_EXIT,
        WAIT_FOR_RUNTIME,
        EXIT_BEFORE_CONTENT
    }

    private boolean mainContentLoaded;
    private boolean mainContentLoadRequested;
    private boolean runtimeReady;
    private boolean startupCloseRequested;
    private Throwable mainContentLoadFailure;
    private boolean startupShellPainted;
    private final List<Runnable> mainContentLoadedCallbacks = new ArrayList<>();
    private final List<Consumer<Throwable>> mainContentLoadFailedCallbacks = new ArrayList<>();
    private final List<Runnable> startupShellPaintedCallbacks = new ArrayList<>();

    synchronized CloseAction onCloseRequested() {
        if (mainContentLoaded) {
            return CloseAction.NORMAL_EXIT;
        }
        startupCloseRequested = true;
        return runtimeReady ? CloseAction.EXIT_BEFORE_CONTENT : CloseAction.WAIT_FOR_RUNTIME;
    }

    synchronized boolean markRuntimeReady() {
        runtimeReady = true;
        return startupCloseRequested;
    }

    synchronized boolean isStartupCloseRequested() {
        return startupCloseRequested;
    }

    synchronized boolean markMainContentLoadRequested() {
        if (mainContentLoaded || mainContentLoadRequested) {
            return false;
        }
        mainContentLoadRequested = true;
        return true;
    }

    void markMainContentLoaded() {
        List<Runnable> callbacksToRun;
        synchronized (this) {
            if (mainContentLoaded) {
                return;
            }
            mainContentLoaded = true;
            callbacksToRun = new ArrayList<>(mainContentLoadedCallbacks);
            mainContentLoadedCallbacks.clear();
        }
        runLater(callbacksToRun);
    }

    void markMainContentLoadFailed(Throwable throwable) {
        List<Consumer<Throwable>> callbacksToRun;
        synchronized (this) {
            if (mainContentLoadFailure != null) {
                return;
            }
            mainContentLoadFailure = throwable;
            callbacksToRun = new ArrayList<>(mainContentLoadFailedCallbacks);
            mainContentLoadFailedCallbacks.clear();
        }
        for (Consumer<Throwable> callback : callbacksToRun) {
            SwingUtilities.invokeLater(() -> callback.accept(throwable));
        }
    }

    void markStartupShellPainted() {
        List<Runnable> callbacksToRun;
        synchronized (this) {
            if (startupShellPainted) {
                return;
            }
            startupShellPainted = true;
            callbacksToRun = new ArrayList<>(startupShellPaintedCallbacks);
            startupShellPaintedCallbacks.clear();
        }
        runLater(callbacksToRun);
    }

    void whenMainContentLoaded(Runnable callback) {
        if (callback == null) {
            return;
        }
        if (shouldRunMainContentLoadedCallbackNow(callback)) {
            SwingUtilities.invokeLater(callback);
        }
    }

    private synchronized boolean shouldRunMainContentLoadedCallbackNow(Runnable callback) {
        if (mainContentLoaded) {
            return true;
        }
        mainContentLoadedCallbacks.add(callback);
        return false;
    }

    void whenMainContentLoadFailed(Consumer<Throwable> callback) {
        if (callback == null) {
            return;
        }
        Throwable failure = registerMainContentLoadFailureCallback(callback);
        if (failure != null) {
            SwingUtilities.invokeLater(() -> callback.accept(failure));
        }
    }

    private synchronized Throwable registerMainContentLoadFailureCallback(Consumer<Throwable> callback) {
        if (mainContentLoadFailure != null) {
            return mainContentLoadFailure;
        }
        mainContentLoadFailedCallbacks.add(callback);
        return null;
    }

    void whenStartupShellPainted(Runnable callback) {
        if (callback == null) {
            return;
        }
        if (shouldRunStartupShellPaintedCallbackNow(callback)) {
            SwingUtilities.invokeLater(callback);
        }
    }

    private synchronized boolean shouldRunStartupShellPaintedCallbackNow(Runnable callback) {
        if (startupShellPainted) {
            return true;
        }
        startupShellPaintedCallbacks.add(callback);
        return false;
    }

    private void runLater(List<Runnable> callbacks) {
        for (Runnable callback : callbacks) {
            SwingUtilities.invokeLater(callback);
        }
    }
}
