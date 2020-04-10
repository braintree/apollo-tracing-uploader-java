package com.braintreepayments.apollo_tracing_uploader;

import mdg.engine.proto.Reports;

@FunctionalInterface
public interface Uploader {
  String APOLLO_TRACING_URL = "https://engine-report.apollodata.com/api/ingress/traces";
  String API_KEY_HEADER = "X-Api-Key";

  void upload(Reports.FullTracesReport report);
}
