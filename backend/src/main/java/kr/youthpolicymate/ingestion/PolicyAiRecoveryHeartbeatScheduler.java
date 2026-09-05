package kr.youthpolicymate.ingestion;

import kr.youthpolicymate.ingestion.PolicyAiRecoveryHeartbeat.Cancellation;
import kr.youthpolicymate.ingestion.PolicyAiRecoveryHeartbeat.HeartbeatScheduler;

import java.time.Duration;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

// 종료 대기 중에는 기존 확인의 갱신을 유지하고, 기한 이후의 응답은 적용하지 못하게 한다.
public final class PolicyAiRecoveryHeartbeatScheduler implements HeartbeatScheduler, AutoCloseable {
    private final Object monitor = new Object();
    private final ScheduledThreadPoolExecutor executor;
    private final Set<Registration> active = new HashSet<>();
    private final long shutdownGraceNanos;
    private boolean stopping;
    private long shutdownStartedAt;

    public PolicyAiRecoveryHeartbeatScheduler(int threads, Duration shutdownGrace) {
        if (threads < 1) throw new IllegalArgumentException("AI 복구 heartbeat 실행 스레드는 1개 이상이어야 합니다.");
        Objects.requireNonNull(shutdownGrace, "AI 복구 heartbeat 종료 대기 시간이 필요합니다.");
        if (shutdownGrace.isNegative()) {
            throw new IllegalArgumentException("AI 복구 heartbeat 종료 대기 시간은 음수일 수 없습니다.");
        }
        this.shutdownGraceNanos = shutdownGrace.toNanos();
        this.executor = new ScheduledThreadPoolExecutor(threads,
                Thread.ofPlatform().name("ai-recovery-heartbeat-", 0).daemon(true).factory());
        executor.setRemoveOnCancelPolicy(true);
        executor.setContinueExistingPeriodicTasksAfterShutdownPolicy(false);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    }

    @Override
    public Cancellation schedule(Duration initialDelay, Duration delay, Runnable heartbeat) {
        Objects.requireNonNull(initialDelay, "AI 복구 heartbeat 최초 대기 시간이 필요합니다.");
        Objects.requireNonNull(delay, "AI 복구 heartbeat 반복 주기가 필요합니다.");
        Objects.requireNonNull(heartbeat, "AI 복구 heartbeat 작업이 필요합니다.");
        synchronized (monitor) {
            if (stopping) throw stopped();
            var registration = new Registration(heartbeat);
            registration.future = executor.scheduleWithFixedDelay(
                    registration::run, initialDelay.toNanos(), delay.toNanos(), TimeUnit.NANOSECONDS);
            active.add(registration);
            return registration;
        }
    }

    public void beginShutdown() {
        synchronized (monitor) {
            if (!stopping) {
                stopping = true;
                shutdownStartedAt = System.nanoTime();
                monitor.notifyAll();
            }
        }
    }

    @Override
    public void close() {
        beginShutdown();
        boolean interrupted = false;
        synchronized (monitor) {
            try {
                long remaining;
                while (!active.isEmpty() && (remaining = remainingNanos()) > 0) {
                    TimeUnit.NANOSECONDS.timedWait(monitor, remaining);
                }
            } catch (InterruptedException interruption) {
                interrupted = true;
            } finally {
                for (Registration registration : active) {
                    registration.stoppedByShutdown = true;
                    registration.future.cancel(false);
                }
                active.clear();
                monitor.notifyAll();
                executor.shutdown();
            }
        }
        try {
            if (!interrupted) executor.awaitTermination(Math.max(0, remainingNanos()), TimeUnit.NANOSECONDS);
        } catch (InterruptedException interruption) {
            interrupted = true;
        } finally {
            if (interrupted) Thread.currentThread().interrupt();
        }
    }

    public boolean isTerminated() {
        return executor.isTerminated();
    }

    private long remainingNanos() {
        return shutdownGraceNanos - (System.nanoTime() - shutdownStartedAt);
    }

    private static RejectedExecutionException stopped() {
        return new RejectedExecutionException("AI 복구 heartbeat 실행기가 종료되어 확인 결과를 적용할 수 없습니다.");
    }

    private final class Registration implements Cancellation {
        private final Runnable heartbeat;
        private ScheduledFuture<?> future;
        private boolean completed;
        private boolean stoppedByShutdown;

        private Registration(Runnable heartbeat) {
            this.heartbeat = heartbeat;
        }

        private void run() {
            synchronized (monitor) {
                if (completed || stoppedByShutdown) return;
                if (stopping && remainingNanos() <= 0) {
                    stoppedByShutdown = true;
                    future.cancel(false);
                    active.remove(this);
                    monitor.notifyAll();
                    return;
                }
            }
            heartbeat.run();
        }

        @Override
        public void cancel() {
            synchronized (monitor) {
                if (stopping && remainingNanos() <= 0 && !completed) stoppedByShutdown = true;
                completed = true;
                future.cancel(false);
                active.remove(this);
                monitor.notifyAll();
            }
        }

        @Override
        public void verifyCompletion() {
            synchronized (monitor) {
                if (stoppedByShutdown) throw stopped();
            }
        }
    }
}
