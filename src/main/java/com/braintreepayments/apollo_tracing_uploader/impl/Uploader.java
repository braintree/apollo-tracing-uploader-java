package com.braintreepayments.apollo_tracing_uploader.impl;

import mdg.engine.proto.Reports;

@FunctionalInterface
public interface Uploader {
  void upload(Reports.FullTracesReport report);
}
