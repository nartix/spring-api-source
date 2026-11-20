package com.ferozfaiz.uniapi.admincategorycontractfixture;

import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContract;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractFixture;
import com.ferozfaiz.uniapi.category.contract.AdminCategoryProviderContractProfile;

/**
 * Applies the shared provider contract to capabilities/list/create behavior, unsupported sibling
 * rejection, and rollback for the neutral restricted profile.
 */
final class NeutralRestrictedAdminCategoryProviderContractTest
  implements AdminCategoryProviderContract
{

  @Override
  public AdminCategoryProviderContractProfile profile() {
    return NeutralRestrictedAdminCategoryContractFixture.profile();
  }

  @Override
  public AdminCategoryProviderContractFixture newFixture() {
    return new NeutralRestrictedAdminCategoryContractFixture();
  }
}
