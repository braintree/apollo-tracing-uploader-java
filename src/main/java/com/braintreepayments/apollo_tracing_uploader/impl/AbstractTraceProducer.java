package com.braintreepayments.apollo_tracing_uploader.impl;

import java.util.function.Consumer;

import com.braintreepayments.apollo_tracing_uploader.FullTracesReportBuilder;
import com.braintreepayments.apollo_tracing_uploader.TraceProducer;
import com.braintreepayments.apollo_tracing_uploader.Uploader;

import mdg.engine.proto.Reports;

public abstract class AbstractTraceProducer implements TraceProducer {
  protected final FullTracesReportBuilder reportBuilder = new FullTracesReportBuilder();
  protected final Consumer<Reports.ReportHeader.Builder> customizeReportHeader;
  protected final Uploader uploader;

  public AbstractTraceProducer(Consumer<Reports.ReportHeader.Builder> customizeReportHeader, Uploader uploader) {
    this.customizeReportHeader = customizeReportHeader;
    this.uploader = uploader;
  }
}
