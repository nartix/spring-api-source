package com.ferozfaiz.uniapi.admincategorycontractfixture;

import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContract;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractFixture;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile;

/**
 * Applies the shared provider contract to deterministic capabilities/list/get behavior and
 * read-only rejection for the neutral flat profile.
 */
final class NeutralFlatAdminCategoryProviderContractTest implements AdminCategoryProviderContract {

  @Override
  public AdminCategoryProviderContractProfile profile() {
    return NeutralFlatAdminCategoryContractFixture.contractProfile();
  }

  @Override
  public AdminCategoryProviderContractFixture newFixture() {
    return new NeutralFlatAdminCategoryContractFixture();
  }
}
