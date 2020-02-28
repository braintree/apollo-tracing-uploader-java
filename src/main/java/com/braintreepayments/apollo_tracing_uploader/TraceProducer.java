package com.braintreepayments.apollo_tracing_uploader;

import java.util.function.Consumer;

import mdg.engine.proto.Reports;

public abstract class TraceProducer {
  protected final FullTracesReportBuilder reportBuilder = new FullTracesReportBuilder();
  protected final Consumer<Reports.ReportHeader.Builder> customizeReportHeader;
  protected final Uploader uploader;

  public TraceProducer(Consumer<Reports.ReportHeader.Builder> customizeReportHeader, Uploader uploader) {
    this.customizeReportHeader = customizeReportHeader;
    this.uploader = uploader;
  }

  public abstract void submit(Reports.Trace trace);
}
