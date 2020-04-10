package com.braintreepayments.apollo_tracing_uploader;

import mdg.engine.proto.Reports;

public interface TraceProducer {
  void submit(Reports.Trace trace);
}
