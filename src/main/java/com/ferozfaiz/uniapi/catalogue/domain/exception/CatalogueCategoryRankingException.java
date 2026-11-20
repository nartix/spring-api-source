package com.ferozfaiz.uniapi.catalogue.domain.exception;

public class CatalogueCategoryRankingException extends CatalogueException {

  private final String code;

  private CatalogueCategoryRankingException(
    String code,
    String messageKey,
    String defaultMessage,
    Object... messageArgs
  ) {
    super(messageKey, defaultMessage, messageArgs);
    this.code = code;
  }

  public static CatalogueCategoryRankingException rebalanceLimitExceeded(
    long affectedRows,
    long maxRows
  ) {
    return new CatalogueCategoryRankingException(
      "CATEGORY_RANK_REBALANCE_REJECTED",
      "catalogue.domain.category.ranking.rebalance-limit",
      "Category rank rebalance would affect %d rows, limit %d".formatted(affectedRows, maxRows),
      affectedRows,
      maxRows
    );
  }

  public String code() {
    return code;
  }
}
