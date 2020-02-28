package com.braintreepayments.apollo_tracing_uploader;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class VariablesSanitizerTest {
  private Map<String, Object> variables;

  @Before
  public void setup() {
    Map<String, Object> nestedMap = new HashMap<>();
    nestedMap.put("cc", "4111111111111111");
    nestedMap.put("int", 123);
    nestedMap.put("list", Arrays.asList("str", Collections.singletonMap("foo", 123)));

    this.variables = Collections.singletonMap(
      "root", Collections.singletonMap(
        "nested", nestedMap
      )
    );
  }

  @Test
  public void valuesTo() {
    VariablesSanitizer transformer = VariablesSanitizer.valuesTo("[VAL]");

    Map<String, Object> expectedNestedMap = new HashMap<>();
    expectedNestedMap.put("cc", "[VAL]");
    expectedNestedMap.put("int", "[VAL]");
    expectedNestedMap.put("list", Arrays.asList("[VAL]", Collections.singletonMap("foo", "[VAL]")));

    Map<String, Object> expectedMap = Collections.singletonMap(
      "root", Collections.singletonMap(
        "nested", expectedNestedMap
      )
    );

    assertEquals(expectedMap, transformer.apply(variables));
  }

  @Test
  public void mapValues() {
    VariablesSanitizer transformer =
      VariablesSanitizer.mapValues(obj -> obj.getClass().getSimpleName());

    Map<String, Object> expectedNestedMap = new HashMap<>();
    expectedNestedMap.put("cc", "String");
    expectedNestedMap.put("int", "Integer");
    expectedNestedMap.put("list", Arrays.asList("String", Collections.singletonMap("foo", "Integer")));

    Map<String, Object> expectedMap = Collections.singletonMap(
      "root", Collections.singletonMap(
        "nested", expectedNestedMap
      )
    );

    assertEquals(expectedMap, transformer.apply(variables));
  }
}
