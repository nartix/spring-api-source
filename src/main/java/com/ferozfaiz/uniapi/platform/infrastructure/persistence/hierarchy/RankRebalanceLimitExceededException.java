package com.ferozfaiz.uniapi.platform.infrastructure.persistence.hierarchy;

public class RankRebalanceLimitExceededException extends RuntimeException {

  private final String systemKey;
  private final long affectedRows;
  private final long maxRows;

  public RankRebalanceLimitExceededException(String systemKey, long affectedRows, long maxRows) {
    super(
      "Rank rebalance for system %s would affect %d rows, limit %d".formatted(
        systemKey,
        affectedRows,
        maxRows
      )
    );
    this.systemKey = systemKey;
    this.affectedRows = affectedRows;
    this.maxRows = maxRows;
  }

  public String systemKey() {
    return systemKey;
  }

  public long affectedRows() {
    return affectedRows;
  }

  public long maxRows() {
    return maxRows;
  }
}
