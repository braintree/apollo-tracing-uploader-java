package integration;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.braintreepayments.apollo_tracing_uploader.TracingUploadInstrumentation;
import com.braintreepayments.apollo_tracing_uploader.VariablesSanitizer;
import com.braintreepayments.apollo_tracing_uploader.impl.ScheduledBatchingTraceProducer;
import com.coxautodev.graphql.tools.GraphQLQueryResolver;
import com.coxautodev.graphql.tools.GraphQLResolver;
import com.coxautodev.graphql.tools.SchemaParser;

import org.junit.Test;

import graphql.ExecutionInput;
import graphql.GraphQL;
import graphql.execution.AbortExecutionException;
import graphql.schema.GraphQLSchema;
import mdg.engine.proto.Reports;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EndToEndTest {
  private GraphQLSchema schema = SchemaParser.newParser()
    .schemaString("type Query {\n"
                  + "  echo(str: String!): String!\n"
                  + "  users: [User!]!\n"
                  + "  err: Boolean!\n"
                  + "}\n"
                  + "type User {\n"
                  + "  id: Int!\n"
                  + "}")
    .resolvers(new MinimalQueryResolver(), new MinimalQueryResolver.UserResolver())
    .build()
    .makeExecutableSchema();

  @Test
  public void testInstrumentation() {
    List<Reports.FullTracesReport> uploadedReports = new ArrayList<>();

    ScheduledBatchingTraceProducer producer = ScheduledBatchingTraceProducer.newBuilder()
      .batchingWindow(Duration.ofSeconds(1))
      .threadPoolSize(1)
      .customizeHeader(header -> header.setService("service"))
      .uploader(uploadedReports::add)
      .build();

    TracingUploadInstrumentation instrumentation = TracingUploadInstrumentation.newBuilder()
      .sendTracesIf(() -> true)
      .customizeTrace((trace, context) -> trace.setClientName("client " + context.toString()))
      .sanitizeVariables(VariablesSanitizer.valuesTo("X"))
      .producer(producer)
      .build();

    GraphQL graphQL = GraphQL
      .newGraphQL(schema)
      .instrumentation(instrumentation)
      .build();

    ExecutionInput echoInput = ExecutionInput
      .newExecutionInput()
      .operationName("EchoOp")
      .query("query EchoOp($msg: String!) { echo(str: $msg) }")
      .variables(Collections.singletonMap("msg", "Hello!"))
      .build();

    Instant startTime = Instant.now();

    graphQL.execute(echoInput);
    graphQL.execute("{ echo(str: \"secret variable\") }");
    graphQL.execute("{ myUsers: users { id } }");
    graphQL.execute("{ err }");
    graphQL.execute("");

    producer.shutdown();

    assertEquals(1, uploadedReports.size());

    Reports.FullTracesReport fullTracesReport = uploadedReports.get(0);

    Reports.ReportHeader header = fullTracesReport.getHeader();

    assertEquals("service", header.getService());

    Map<String, Reports.Traces> tracesPerQuery = fullTracesReport.getTracesPerQueryMap();

    assertEquals(5, tracesPerQuery.size());

    Reports.Trace usersTrace = tracesPerQuery.get("# -\nquery {users {id}}").getTrace(0);
    Reports.Trace errTrace = tracesPerQuery.get("# -\nquery {err}").getTrace(0);
    Reports.Trace echoTrace = tracesPerQuery.get("# EchoOp\nquery EchoOp($var1:String!) {echo(str:$var1)}").getTrace(0);
    Reports.Trace inlineEchoTrace = tracesPerQuery.get("# -\nquery {echo(str:\"\")}").getTrace(0);
    Reports.Trace garbageTrace = tracesPerQuery.get("# -\n").getTrace(0);

    tracesPerQuery.forEach((key, traces) -> {
      List<Reports.Trace> traceList = traces.getTraceList();
      assertEquals(1, traceList.size());
      Reports.Trace trace = traceList.get(0);
      long durationNs = trace.getDurationNs();

      assertTrue(durationNs > 0);

      Arrays.asList(trace.getStartTime(), trace.getEndTime()).forEach(time -> {
        assertTrue(time.getNanos() >= 0 && time.getNanos() < 1e9);
        assertTrue(time.getSeconds() >= startTime.getEpochSecond());
      });

      long traceStart = TimeUnit.SECONDS.toNanos(trace.getStartTime().getSeconds()) + trace.getStartTime().getNanos();
      long traceEnd = TimeUnit.SECONDS.toNanos(trace.getEndTime().getSeconds()) + trace.getEndTime().getNanos();

      assertTrue(traceStart < traceEnd);

      assertTrue(trace.getClientName().startsWith("client graphql.GraphQLContext@"));
      trace.getDetails().getVariablesJsonMap().forEach((k, v) -> assertEquals("\"X\"", v));

      // not set in apollo-server, so not setting it here
      assertTrue(trace.getClientAddress().isEmpty());

      // only for federated
      assertTrue(trace.getCachePolicy().getAllFields().isEmpty());
      assertTrue(trace.getQueryPlan().getAllFields().isEmpty());

      // We don't want to leak inline variables, but also Apollo doesn't seem to send raw query either:
      // https://github.com/apollographql/apollo-server/blob/815c77afe847c84a1215f06b06e188eba2a2f8d2/packages/apollo-engine-reporting/src/extension.ts#L263-L267
      assertTrue(trace.getDetails().getRawQuery().isEmpty());
      assertFalse(trace.toString().contains("secret"));
    });

    assertEquals("query {echo(str:\"\")}", inlineEchoTrace.getSignature());
    assertEquals("query EchoOp($var1:String!) {echo(str:$var1)}", echoTrace.getSignature());
    assertEquals("EchoOp", echoTrace.getDetails().getOperationName());

    Arrays.asList(echoTrace, inlineEchoTrace).forEach(trace -> {
      Reports.Trace.Node echoRoot = trace.getRoot();

      assertEquals(0, echoRoot.getErrorCount());
      assertEquals(1, echoRoot.getChildCount());

      Reports.Trace.Node echoChild = echoRoot.getChild(0);
      assertTrue(echoChild.getEndTime() > echoChild.getStartTime());
      assertEquals("echo", echoChild.getResponseName());
      assertEquals("Query", echoChild.getParentType());
      assertEquals("String!", echoChild.getType());
      assertEquals(0, echoChild.getErrorCount());
      assertEquals(0, echoChild.getChildCount());
    });

    Reports.Trace.Details usersDetails = usersTrace.getDetails();
    Reports.Trace.Node usersRoot = usersTrace.getRoot();

    assertTrue(usersDetails.getOperationName().isEmpty());
    assertEquals("query {users {id}}", usersTrace.getSignature());
    assertEquals(0, usersDetails.getVariablesJsonCount());
    assertEquals(0, usersRoot.getErrorCount());
    assertEquals(1, usersRoot.getChildCount());

    Reports.Trace.Node usersChild = usersRoot.getChild(0);
    assertTrue(usersChild.getEndTime() > usersChild.getStartTime());
    assertEquals("myUsers", usersChild.getResponseName());
    assertEquals("users", usersChild.getOriginalFieldName());
    assertEquals("Query", usersChild.getParentType());
    assertEquals("[User!]!", usersChild.getType());
    assertEquals(0, usersChild.getIndex());
    assertEquals(0, usersChild.getErrorCount());
    assertEquals(2, usersChild.getChildCount());

    assertEquals(Arrays.asList(0, 1),
                 usersChild.getChildList().stream().map(Reports.Trace.Node::getIndex).sorted().collect(Collectors.toList()));

    usersChild.getChildList().forEach(idIndexChild -> {
      assertEquals(1, idIndexChild.getChildCount());

      Reports.Trace.Node idChild = idIndexChild.getChild(0);

      assertTrue(idChild.getEndTime() > idChild.getStartTime());
      assertEquals("id", idChild.getResponseName());
      assertEquals("id", idChild.getOriginalFieldName());
      assertEquals("User", idChild.getParentType());
      assertEquals("Int!", idChild.getType());
      assertEquals(0, idChild.getErrorCount());
      assertEquals(0, idChild.getChildCount());
    });

    Reports.Trace.Details errDetails = errTrace.getDetails();
    Reports.Trace.Node errRoot = errTrace.getRoot();

    assertTrue(errDetails.getOperationName().isEmpty());
    assertEquals("query {err}", errTrace.getSignature());
    assertEquals(0, errDetails.getVariablesJsonCount());
    assertEquals(0, errRoot.getErrorCount());
    assertEquals(1, errRoot.getChildCount());

    Reports.Trace.Node errChild = errRoot.getChild(0);
    assertTrue(errChild.getEndTime() > errChild.getStartTime());
    assertEquals("err", errChild.getResponseName());
    assertEquals("Query", errChild.getParentType());
    assertEquals("Boolean!", errChild.getType());
    assertEquals(1, errChild.getErrorCount());
    assertEquals(0, errChild.getChildCount());

    Reports.Trace.Error errError = errChild.getError(0);
    assertTrue(errError.getMessage().contains("Exception while fetching data (/err)"));
    assertTrue(errError.getJson().contains("\"message\":\"Exception while fetching data"));

    Reports.Trace.Details garbageDetails = garbageTrace.getDetails();
    Reports.Trace.Node garbageRoot = garbageTrace.getRoot();

    assertTrue(garbageDetails.getOperationName().isEmpty());
    assertEquals("", garbageTrace.getSignature());
    assertEquals(0, garbageDetails.getVariablesJsonCount());
    assertEquals(1, garbageRoot.getErrorCount());
    assertEquals(0, garbageRoot.getChildCount());

    Reports.Trace.Error garbageError = garbageRoot.getError(0);
    assertTrue(garbageError.getMessage().contains("Invalid Syntax"));
    assertTrue(garbageError.getJson().contains("{\"message\":\"Invalid Syntax"));
  }

  @Test
  public void testInstrumentationDisabled() {
    List<Reports.FullTracesReport> uploadedReports = new ArrayList<>();

    ScheduledBatchingTraceProducer producer = ScheduledBatchingTraceProducer.newBuilder()
      .batchingWindow(Duration.ofSeconds(1))
      .threadPoolSize(1)
      .uploader(uploadedReports::add)
      .build();

    TracingUploadInstrumentation noopInstrumentation = TracingUploadInstrumentation.newBuilder()
      .sendTracesIf(() -> false) // Disable traces
      .producer(producer)
      .build();

    GraphQL graphQL = GraphQL
      .newGraphQL(schema)
      .instrumentation(noopInstrumentation)
      .build();

    graphQL.execute("{ echo(str: \"hello\") }");

    producer.shutdown();

    assertEquals(0, uploadedReports.size());
  }

  private static class MinimalQueryResolver implements GraphQLQueryResolver {
    public String getEcho(String str) throws InterruptedException {
      Thread.sleep(1);
      return str;
    }

    public List<User> getUsers() throws InterruptedException {
      Thread.sleep(5);
      return Arrays.asList(new User(0), new User(1));
    }

    public boolean getErr() throws InterruptedException {
      Thread.sleep(10);
      throw new AbortExecutionException();
    }

    static class User {
      final int id;

      User(int id) {
        this.id = id;
      }
    }

    static class UserResolver implements GraphQLResolver<User> {
      public int getId(User user) throws InterruptedException {
        Thread.sleep(3);
        return user.id;
      }
    }
  }
}
