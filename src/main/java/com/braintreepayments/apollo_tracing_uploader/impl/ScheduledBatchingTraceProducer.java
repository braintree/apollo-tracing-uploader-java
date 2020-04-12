package com.braintreepayments.apollo_tracing_uploader.impl;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import com.braintreepayments.apollo_tracing_uploader.FullTracesReportBuilder;
import com.braintreepayments.apollo_tracing_uploader.TraceProducer;
import com.braintreepayments.apollo_tracing_uploader.Uploader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mdg.engine.proto.Reports;

/**
 * An in-process implementation of {@link TraceProducer} that uses a {@link ScheduledExecutorService} to occasionally
 * collect traces into a {@link mdg.engine.proto.Reports.FullTracesReport} protobuf message and pass it to a
 * {@link Uploader}.
 * <p>
 * {@link ScheduledBatchingTraceProducer#shutdown} should be called at application shutdown to prevent dropped metrics.
 */
public class ScheduledBatchingTraceProducer extends AbstractTraceProducer {
  private final Logger logger = LoggerFactory.getLogger(ScheduledBatchingTraceProducer.class);
  private final int threadPoolSize;
  private final BlockingQueue<Reports.Trace> queue;
  private final ScheduledExecutorService executor;

  public static Builder newBuilder() {
    return new Builder();
  }

  public ScheduledBatchingTraceProducer(Consumer<Reports.ReportHeader.Builder> customizeReportHeader,
                                        Uploader uploader,
                                        int threadPoolSize,
                                        Duration batchingWindow,
                                        BlockingQueue<Reports.Trace> queue) {
    super(customizeReportHeader, uploader);

    this.threadPoolSize = threadPoolSize;
    this.queue = queue;
    this.executor = Executors.newScheduledThreadPool(threadPoolSize);

    onEachWorker(() -> executor
      .scheduleAtFixedRate(this::safePerform, 0, batchingWindow.toMillis(), TimeUnit.MILLISECONDS));
  }

  @Override
  public void submit(Reports.Trace trace) {
    try {
      queue.add(trace);
    } catch (Exception e) {
      logger.error("Error submitting to queue", e);
    }
  }

  public void shutdown() {
    onEachWorker(() -> executor.submit(this::flushQueue));
    executor.shutdown();

    try {
      executor.awaitTermination(30, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      logger.error("Interrupted during shutdown", e);
    }
  }

  private void safePerform() {
    try {
      perform();
    } catch (Exception e) {
      logger.error("Error during perform", e);
    }
  }

  private void perform() {
    List<Reports.Trace> traces = new ArrayList<>();
    queue.drainTo(traces);

    if (traces.isEmpty()) {
      return;
    }

    uploader.upload(reportBuilder.build(traces, customizeReportHeader));
  }

  private void flushQueue() {
    while (!queue.isEmpty()) {
      safePerform();
    }
  }

  private void onEachWorker(Runnable runnable) {
    IntStream.range(0, threadPoolSize).forEach(i -> runnable.run());
  }

  public static class Builder {
    private Consumer<Reports.ReportHeader.Builder> _customizeReportHeader;
    private Uploader _uploader;
    private int _threadPoolSize = 10;
    private Duration _batchingWindow = Duration.ofSeconds(10);
    private BlockingQueue<Reports.Trace> _queue = new ArrayBlockingQueue<>(4096);

    public ScheduledBatchingTraceProducer build() {
      return new ScheduledBatchingTraceProducer(_customizeReportHeader, _uploader, _threadPoolSize, _batchingWindow, _queue);
    }

    public Builder customizeHeader(Consumer<Reports.ReportHeader.Builder> customizeReportHeader) {
      this._customizeReportHeader = customizeReportHeader;
      return this;
    }

    public Builder uploader(Uploader uploader) {
      this._uploader = uploader;
      return this;
    }

    public Builder threadPoolSize(int threadPoolSize) {
      this._threadPoolSize = threadPoolSize;
      return this;
    }

    public Builder batchingWindow(Duration batchingWindow) {
      this._batchingWindow = batchingWindow;
      return this;
    }

    public Builder queue(BlockingQueue<Reports.Trace> queue) {
      this._queue = queue;
      return this;
    }
  }
}
