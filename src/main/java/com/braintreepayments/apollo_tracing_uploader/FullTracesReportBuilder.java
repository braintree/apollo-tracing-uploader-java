package com.braintreepayments.apollo_tracing_uploader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import mdg.engine.proto.Reports;

public class FullTracesReportBuilder {
  public Reports.FullTracesReport build(List<Reports.Trace> traceList,
                                        Consumer<Reports.ReportHeader.Builder> customizeReportHeader) {
    Map<String, Reports.Traces.Builder> tracesPerQuery = new HashMap<>();

    traceList.forEach(trace -> {
      String operationName = Optional.ofNullable(trace.getDetails().getOperationName())
        .filter(s -> !s.isEmpty())
        .orElse("-");

      String key = "# " + operationName + "\n" + trace.getSignature();

      tracesPerQuery.compute(key, (k, v) -> {
        Reports.Traces.Builder tracesBuilder = v == null ? Reports.Traces.newBuilder() : v;
        return tracesBuilder.addTrace(trace);
      });
    });

    Map<String, Reports.Traces> traces = tracesPerQuery
      .entrySet()
      .stream()
      .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().build()));

    Reports.ReportHeader.Builder headerBuilder = Reports.ReportHeader.newBuilder();

    customizeReportHeader.accept(headerBuilder);

    return Reports.FullTracesReport.newBuilder()
      .setHeader(headerBuilder)
      .putAllTracesPerQuery(traces)
      .build();
  }
}
