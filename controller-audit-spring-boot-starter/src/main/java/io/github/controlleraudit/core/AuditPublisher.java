package io.github.controlleraudit.core;

import io.github.controlleraudit.annotation.Mode;
import io.github.controlleraudit.config.ControllerAuditProperties;
import io.github.controlleraudit.model.AuditEvent;
import io.github.controlleraudit.sink.AuditSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fans events out to every {@link AuditSink}. SYNC events are written on the caller thread; ASYNC events go
 * through a bounded queue drained in batches by background workers. A failing sink never breaks a request.
 */
public class AuditPublisher implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(AuditPublisher.class);

    private final List<AuditSink> sinks;
    private final ControllerAuditProperties.Async cfg;
    private final BlockingQueue<AuditEvent> queue;
    private final ExecutorService workers;
    private final AtomicLong dropped = new AtomicLong();
    private volatile boolean running = true;

    public AuditPublisher(List<AuditSink> sinks, ControllerAuditProperties.Async cfg) {
        this.sinks = List.copyOf(sinks);
        this.cfg = cfg;
        this.queue = new LinkedBlockingQueue<>(Math.max(1, cfg.getQueueCapacity()));
        int threads = Math.max(1, cfg.getWorkerThreads());
        AtomicInteger idx = new AtomicInteger();
        this.workers = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "controller-audit-" + idx.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        for (int i = 0; i < threads; i++) {
            workers.submit(this::runWorker);
        }
        if (this.sinks.isEmpty()) {
            log.warn("controller-audit: no AuditSink beans found, audit events will be discarded");
        }
    }

    public void publish(AuditEvent event, Mode mode) {
        if (sinks.isEmpty()) {
            return;
        }
        if (mode == Mode.SYNC || !running) {
            writeAll(List.of(event));
            return;
        }
        switch (cfg.getOverflowPolicy()) {
            case BLOCK -> {
                try {
                    queue.put(event);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
            case CALLER_RUNS -> {
                if (!queue.offer(event)) {
                    writeAll(List.of(event));
                }
            }
            default -> {
                if (!queue.offer(event)) {
                    long n = dropped.incrementAndGet();
                    if (n == 1 || n % 1000 == 0) {
                        log.warn("controller-audit: queue full, {} event(s) dropped so far", n);
                    }
                }
            }
        }
    }

    public long getDroppedCount() {
        return dropped.get();
    }

    public int getQueueSize() {
        return queue.size();
    }

    private void runWorker() {
        List<AuditEvent> batch = new ArrayList<>(Math.max(1, cfg.getBatchSize()));
        while (true) {
            try {
                AuditEvent first = queue.poll(cfg.getIdlePollMs(), TimeUnit.MILLISECONDS);
                if (first == null) {
                    if (!running) {
                        return;
                    }
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, Math.max(0, cfg.getBatchSize() - 1));
                writeAll(batch);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                drainRemaining();
                return;
            } finally {
                batch.clear();
            }
        }
    }

    private void drainRemaining() {
        List<AuditEvent> rest = new ArrayList<>();
        queue.drainTo(rest);
        if (!rest.isEmpty()) {
            writeAll(rest);
        }
    }

    private void writeAll(List<AuditEvent> batch) {
        for (AuditSink sink : sinks) {
            try {
                sink.writeBatch(batch);
            } catch (Exception ex) {
                log.warn("controller-audit: sink {} failed for {} event(s): {}", sink.name(), batch.size(), ex.toString());
            }
        }
    }

    @Override
    public void destroy() {
        running = false;
        workers.shutdown();
        try {
            if (!workers.awaitTermination(cfg.getShutdownTimeoutMs(), TimeUnit.MILLISECONDS)) {
                workers.shutdownNow();
                drainRemaining();
            }
        } catch (InterruptedException ex) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
            drainRemaining();
        }
    }
}
