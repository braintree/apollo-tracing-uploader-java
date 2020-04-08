package com.braintreepayments.apollo_tracing_uploader;

import mdg.engine.proto.Reports;

public interface TraceProducer {
  String APOLLO_TRACING_URL = "https://engine-report.apollodata.com/api/ingress/traces";
  String API_KEY_HEADER = "X-Api-Key";

  void submit(Reports.Trace trace);
}
