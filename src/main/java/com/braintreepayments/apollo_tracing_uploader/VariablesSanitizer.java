package com.braintreepayments.apollo_tracing_uploader;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public interface VariablesSanitizer extends UnaryOperator<Map<String, Object>> {
  static <T> VariablesSanitizer valuesTo(final T value) {
    return mapValues(val -> value);
  }

  static <T> VariablesSanitizer mapValues(final Function<Object, T> valueFunc) {
    return new VariablesSanitizer() {
      @Override
      public Map<String, Object> apply(Map<String, Object> stringObjectMap) {
        //noinspection unchecked
        return (Map) transform(stringObjectMap);
      }

      private Object transform(Object v) {
        if (v instanceof Map) {
          Map<?, ?> vMap = (Map) v;

          return vMap
            .entrySet()
            .stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> transform(entry.getValue())));
        } else if (v instanceof List) {
          List<?> vList = (List) v;
          return vList.stream().map(this::transform).collect(Collectors.toList());
        } else {
          return valueFunc.apply(v);
        }
      }
    };
  }
}
