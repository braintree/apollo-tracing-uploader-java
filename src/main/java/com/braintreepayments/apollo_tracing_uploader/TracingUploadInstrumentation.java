package com.braintreepayments.apollo_tracing_uploader;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.execution.instrumentation.InstrumentationContext;
import graphql.execution.instrumentation.SimpleInstrumentation;
import graphql.execution.instrumentation.SimpleInstrumentationContext;
import graphql.execution.instrumentation.parameters.InstrumentationExecuteOperationParameters;
import graphql.execution.instrumentation.parameters.InstrumentationExecutionParameters;
import graphql.execution.instrumentation.parameters.InstrumentationFieldFetchParameters;
import mdg.engine.proto.Reports;

/**
 * An instrumentation for uploading Apollo tracing metrics to the Apollo Graph Manager.
 * <p>
 * To build one, use the provided builder from {@link TracingUploadInstrumentation#newBuilder()}.
 */
public class TracingUploadInstrumentation extends SimpleInstrumentation {
  private final BiConsumer<Reports.Trace.Builder, Object> customizeTrace;
  private final VariablesSanitizer sanitizeVariables;
  private final TraceProducer producer;
  private final Supplier<Boolean> sendTracesIf;

  public TracingUploadInstrumentation(BiConsumer<Reports.Trace.Builder, Object> customizeTrace,
                                      VariablesSanitizer sanitizeVariables,
                                      TraceProducer producer,
                                      Supplier<Boolean> sendTracesIf) {
    this.customizeTrace = customizeTrace;
    this.sanitizeVariables = sanitizeVariables;
    this.producer = producer;
    this.sendTracesIf = sendTracesIf;
  }

  public static Builder newBuilder() {
    return new Builder();
  }

  @Override
  public TracingUploadInstrumentationState createState() {
    boolean noop = !sendTracesIf.get();
    return new TracingUploadInstrumentationState(customizeTrace, sanitizeVariables, noop);
  }

  @Override
  public ExecutionInput instrumentExecutionInput(ExecutionInput executionInput,
                                                 InstrumentationExecutionParameters parameters) {
    TracingUploadInstrumentationState state = parameters.getInstrumentationState();

    if (state.noop) {
      return executionInput;
    } else {
      return state.instrumentExecutionInput(executionInput);
    }
  }

  @Override
  public InstrumentationContext<ExecutionResult> beginExecution(InstrumentationExecutionParameters parameters) {
    TracingUploadInstrumentationState state = parameters.getInstrumentationState();

    if (state.noop) {
      return SimpleInstrumentationContext.noOp();
    } else {
      return state.beginExecution(parameters);
    }
  }

  @Override
  public InstrumentationContext<ExecutionResult> beginExecuteOperation(InstrumentationExecuteOperationParameters parameters) {
    TracingUploadInstrumentationState state = parameters.getInstrumentationState();

    if (state.noop) {
      return SimpleInstrumentationContext.noOp();
    } else {
      return state.beginExecuteOperation(parameters);
    }
  }

  @Override
  public InstrumentationContext<Object> beginFieldFetch(InstrumentationFieldFetchParameters parameters) {
    TracingUploadInstrumentationState state = parameters.getInstrumentationState();

    if (state.noop) {
      return SimpleInstrumentationContext.noOp();
    } else {
      return state.beginFieldFetch(parameters);
    }
  }

  @Override
  public CompletableFuture<ExecutionResult> instrumentExecutionResult(ExecutionResult executionResult,
                                                                      InstrumentationExecutionParameters parameters) {
    TracingUploadInstrumentationState state = parameters.getInstrumentationState();

    if (!state.noop) {
      producer.submit(state.build());
    }

    return CompletableFuture.completedFuture(executionResult);
  }

  public static class Builder {
    private BiConsumer<Reports.Trace.Builder, Object> _customizeTrace = (tr, ctx) -> {};
    private VariablesSanitizer _sanitizeVariables = VariablesSanitizer.valuesTo("[FILTERED]");
    private TraceProducer _producer;
    private Supplier<Boolean> _sendTracesIf = () -> true;

    public TracingUploadInstrumentation build() {
      assert _producer != null : "Missing producer(TraceProducer)";

      return new TracingUploadInstrumentation(_customizeTrace, _sanitizeVariables, _producer, _sendTracesIf);
    }

    /**
     * Register a function for customizing your trace protobuf message.
     *
     * @param traceConsumer A {@link BiConsumer} that accepts a {@link mdg.engine.proto.Reports.Trace.Builder} and
     *                      context object. This should be used to set fields such as `clientName`, `clientVersion`, and
     *                      details about the HTTP request.
     * @return {@link Builder}
     */
    public Builder customizeTrace(BiConsumer<Reports.Trace.Builder, Object> traceConsumer) {
      this._customizeTrace = traceConsumer;
      return this;
    }

    /**
     * Register a {@link VariablesSanitizer} for sanitizing input variables before uploading traces. By default,
     * variable values will be replaced with "[FILTERED]".
     *
     * @param sanitizeVariables A {@link VariablesSanitizer} object.
     * @return {@link Builder}
     */
    public Builder sanitizeVariables(VariablesSanitizer sanitizeVariables) {
      this._sanitizeVariables = sanitizeVariables;
      return this;
    }

    /**
     * Register a {@link TraceProducer} for receiving {@link mdg.engine.proto.Reports.Trace} messages. This object is
     * typically responsible for batching messages before compiling them into a
     * {@link mdg.engine.proto.Reports.FullTracesReport} and sending them to Apollo.
     *
     * @param producer A {@link TraceProducer} object.
     * @return {@link Builder}
     */
    public Builder producer(TraceProducer producer) {
      this._producer = producer;
      return this;
    }

    /**
     * Register a function for determining whether to send traces to Apollo. Sends 100% of traces by default.
     *
     * @param sendTracesIf A {@link Supplier} function.
     * @return {@link Builder}
     */
    public Builder sendTracesIf(Supplier<Boolean> sendTracesIf) {
      this._sendTracesIf = sendTracesIf;
      return this;
    }
  }
}
