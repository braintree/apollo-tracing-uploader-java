package com.braintreepayments.apollo_tracing_uploader;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import mdg.engine.proto.Reports;

class NodePath {
  private final List<Segment> segments;

  NodePath(List<Segment> segments) {
    this.segments = segments;
  }

  static NodePath root() {
    return new NodePath(Collections.emptyList());
  }

  static <T> NodePath fromList(List<T> objects) {
    List<Segment> segments = objects.stream()
      .map(Object::toString)
      .map(Segment::parse)
      .collect(Collectors.toList());

    return new NodePath(segments);
  }

  Reports.Trace.Node.Builder getChild(Reports.Trace.Node.Builder node) {
    return getChild(node, segments);
  }

  Reports.Trace.Node.Builder getChild(Reports.Trace.Node.Builder node, List<Segment> segments) {
    if (segments.isEmpty()) {
      return node;
    }

    Segment head = segments.get(0);
    List<Segment> tail = segments.subList(1, segments.size());

    return getChild(head.getChild(node), tail);
  }

  interface Segment {
    static Segment parse(String str) {
      if (str.matches("^\\d+$")) {
        return new ListIndex(Integer.parseInt(str));
      } else {
        return new FieldName(str);
      }
    }

    default Reports.Trace.Node.Builder getChild(Reports.Trace.Node.Builder parent) {
      return parent
        .getChildBuilderList()
        .stream()
        .filter(this::matchesNode)
        .findFirst()
        .orElseGet(() -> initializeNode(parent.addChildBuilder()));
    }

    boolean matchesNode(Reports.Trace.Node.Builder node);
    Reports.Trace.Node.Builder initializeNode(Reports.Trace.Node.Builder parent);
  }

  private static class ListIndex implements Segment {
    private final int index;

    ListIndex(int index) {
      this.index = index;
    }

    @Override
    public boolean matchesNode(Reports.Trace.Node.Builder node) {
      return Objects.equals(node.getIndex(), index);
    }

    @Override
    public Reports.Trace.Node.Builder initializeNode(Reports.Trace.Node.Builder node) {
      return node.setIndex(index);
    }
  }

  private static class FieldName implements Segment {
    private final String name;

    FieldName(String name) {
      this.name = name;
    }

    @Override
    public boolean matchesNode(Reports.Trace.Node.Builder node) {
      return Objects.equals(node.getResponseName(), name);
    }

    @Override
    public Reports.Trace.Node.Builder initializeNode(Reports.Trace.Node.Builder node) {
      return node.setResponseName(name);
    }
  }
}
