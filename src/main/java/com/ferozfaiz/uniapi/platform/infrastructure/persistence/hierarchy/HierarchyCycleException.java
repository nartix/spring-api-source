package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

public class HierarchyCycleException extends IllegalArgumentException {

  public HierarchyCycleException(Long nodeId, Long parentId) {
    super(
      "Moving node %d under node %d would create a hierarchy cycle".formatted(nodeId, parentId)
    );
  }
}
