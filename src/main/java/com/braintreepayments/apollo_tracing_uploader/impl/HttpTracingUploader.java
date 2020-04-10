package com.braintreepayments.apollo_tracing_uploader.impl;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPOutputStream;

import com.braintreepayments.apollo_tracing_uploader.Constants;
import com.braintreepayments.apollo_tracing_uploader.Uploader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import mdg.engine.proto.Reports;

public class HttpTracingUploader implements Uploader {
  private static final Logger logger = LoggerFactory.getLogger(HttpTracingUploader.class);

  private final String apiKey;
  private final URL url;
  private final int nRetries;
  private final int retryDelayMs;
  private final int connectTimeoutMs;
  private final int readTimeoutMs;
  private final ScheduledExecutorService executor;

  public HttpTracingUploader(String apiKey,
                             ScheduledExecutorService executor,
                             Duration retryDelay,
                             Duration readTimeout,
                             Duration connectTimeout,
                             int nRetries) {
    this.apiKey = apiKey;
    this.nRetries = nRetries;
    this.retryDelayMs = (int) retryDelay.toMillis();
    this.connectTimeoutMs = (int) connectTimeout.toMillis();
    this.readTimeoutMs = (int) readTimeout.toMillis();
    this.executor = executor;

    try {
      this.url = new URL(Constants.APOLLO_TRACING_URL);
    } catch (MalformedURLException e) {
      throw new RuntimeException(e);
    }
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  @Override
  public void upload(Reports.FullTracesReport report) {
    CompletableFuture<HttpURLConnection> future =
      new CompletableFuture<HttpURLConnection>().whenComplete(this::onComplete);

    executor.submit(() -> tryRequest(future, report, 0));
  }

  private void tryRequest(CompletableFuture<HttpURLConnection> future, Reports.FullTracesReport report, int retries) {
    try {
      future.complete(doRequest(report));
    } catch (IOException | HttpStatusException e) {
      if (retries >= nRetries) {
        future.completeExceptionally(new RetriesExceededException(e, retries));
      } else {
        logger.info("Exception uploading traces to Apollo (will retry)", e);
        executor.schedule(() -> tryRequest(future, report, retries + 1), retryDelayMs, TimeUnit.MILLISECONDS);
      }
    }
  }

  private HttpURLConnection doRequest(Reports.FullTracesReport report) throws IOException, HttpStatusException {
    HttpURLConnection conn = (HttpURLConnection) url.openConnection();

    conn.setConnectTimeout(connectTimeoutMs);
    conn.setReadTimeout(readTimeoutMs);
    conn.setRequestMethod("POST");
    conn.setRequestProperty("Content-Type", "application/octet-stream");
    conn.setRequestProperty("Content-Encoding", "gzip");
    conn.setRequestProperty(Constants.API_KEY_HEADER, apiKey);
    conn.setDoOutput(true);

    GZIPOutputStream reqBody = new GZIPOutputStream(conn.getOutputStream());
    reqBody.write(report.toByteArray());
    reqBody.flush();
    reqBody.close();

    int responseCode = conn.getResponseCode();

    if (responseCode != 200) {
      throw new HttpStatusException(responseCode);
    }

    return conn;
  }

  private void onComplete(HttpURLConnection conn, Throwable e) {
    if (e != null) {
      logger.error("Exception uploading traces to Apollo (giving up)", e);
    }
  }

  public static class Builder {
    private String _apiKey;
    private ScheduledExecutorService _executor = Executors.newScheduledThreadPool(10);
    private Duration _retryDelay = Duration.ofSeconds(1);
    private Duration _readTimeout = Duration.ofSeconds(3);
    private Duration _connectTimeout = Duration.ofMillis(500);
    private int _retries = 2;

    public HttpTracingUploader build() {
      assert _apiKey != null : "Missing apiKey(String)";

      return new HttpTracingUploader(_apiKey, _executor, _retryDelay, _readTimeout, _connectTimeout, _retries);
    }

    public Builder apiKey(String apikey) {
      this._apiKey = apikey;
      return this;
    }

    public Builder executor(ScheduledExecutorService executor) {
      this._executor = executor;
      return this;
    }

    public Builder retryDelay(Duration retryDelay) {
      this._retryDelay = retryDelay;
      return this;
    }

    public Builder readTimeout(Duration readTimeout) {
      this._readTimeout = readTimeout;
      return this;
    }

    public Builder connectTimeout(Duration connectTimeout) {
      this._connectTimeout = connectTimeout;
      return this;
    }

    public Builder retries(int retries) {
      this._retries = retries;
      return this;
    }
  }

  public class RetriesExceededException extends Exception {
    RetriesExceededException(Throwable cause, int retries) {
      super("Request to Apollo failed after " + retries + " retries", cause);
    }
  }

  public class HttpStatusException extends Exception {
    HttpStatusException(int statusCode) {
      super("Request to Apollo received unexpected status: " + statusCode);
    }
  }
}
