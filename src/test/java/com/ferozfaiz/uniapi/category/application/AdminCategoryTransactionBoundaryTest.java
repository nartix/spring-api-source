package com.ferozfaiz.uniapi.category.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ferozfaiz.uniapi.category.application.model.AdminCategoryModels;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryCreateValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryListValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryParentOptionsValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPatchValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryPlacementValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryReplaceValidationInput;
import com.ferozfaiz.uniapi.category.application.policy.runtime.AdminCategoryPolicyInputs.AdminCategoryTreeValidationInput;
import com.ferozfaiz.uniapi.category.application.registration.AdminCategorySystemDefinition.Operation;
import com.ferozfaiz.uniapi.category.support.AdminCategoryRuntimeTestFixture;
import com.ferozfaiz.uniapi.shared.pagination.PageResult;
import com.ferozfaiz.uniapi.shared.pagination.SortCriteria;
import com.ferozfaiz.uniapi.shared.pagination.SortDirection;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

class AdminCategoryTransactionBoundaryTest {

  @Test
  void sixReadsAreReadOnlyAndEightMutationsUseTheClassWriteBoundary() {
    Map<String, Class<?>[]> reads = Map.of(
      "capabilities",
      new Class<?>[] { String.class },
      "list",
      new Class<?>[] { String.class, AdminCategoryListValidationInput.class },
      "tree",
      new Class<?>[] { String.class, AdminCategoryTreeValidationInput.class },
      "get",
      new Class<?>[] { String.class, Long.class },
      "breadcrumb",
      new Class<?>[] { String.class, Long.class },
      "parentOptions",
      new Class<?>[] { String.class, AdminCategoryParentOptionsValidationInput.class }
    );
    for (var entry : reads.entrySet()) {
      assertThat(transactional(entry.getKey(), entry.getValue()).readOnly())
        .as(entry.getKey())
        .isTrue();
    }

    assertThat(
      AdminCategoryApplicationService.class.getAnnotation(Transactional.class).readOnly()
    ).isFalse();
    assertThat(
      List.of("create", "update", "patch", "delete", "move", "reorder", "activate", "deactivate")
    ).allSatisfy(name ->
      assertThat(
        java.util.Arrays.stream(AdminCategoryApplicationService.class.getMethods())
          .filter(method -> method.getName().equals(name))
          .findFirst()
          .orElseThrow()
          .getAnnotation(Transactional.class)
      )
        .as(name)
        .isNull()
    );
  }

  @Test
  void selectedPortsJoinReadOnlyAndWriteTransactions() {
    var fixture = AdminCategoryRuntimeTestFixture.harness();
    var manager = new RecordingTransactionManager();
    var service = proxied(fixture.service(), manager);
    var observations = new java.util.ArrayList<String>();
    when(fixture.ports().categoryRead().list(org.mockito.ArgumentMatchers.any())).thenAnswer(
      invocation -> {
        observations.add(
          "read:" +
            TransactionSynchronizationManager.isActualTransactionActive() +
            ":" +
            TransactionSynchronizationManager.isCurrentTransactionReadOnly()
        );
        return page();
      }
    );
    when(fixture.ports().basicMutation().create(org.mockito.ArgumentMatchers.any())).thenAnswer(
      invocation -> {
        observations.add(
          "write:" +
            TransactionSynchronizationManager.isActualTransactionActive() +
            ":" +
            TransactionSynchronizationManager.isCurrentTransactionReadOnly()
        );
        return category();
      }
    );

    service.list("catalogue", list(fixture));
    service.create("catalogue", create(fixture));

    assertThat(observations).containsExactly("read:true:true", "write:true:false");
    assertThat(manager.commits).isEqualTo(2);
    assertThat(manager.rollbacks).isZero();
  }

  @Test
  void providerFailureAndOuterFailureRollbackWithoutASecondInvocation() {
    var fixture = AdminCategoryRuntimeTestFixture.harness();
    var manager = new RecordingTransactionManager();
    var service = proxied(fixture.service(), manager);
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    when(fixture.ports().basicMutation().create(org.mockito.ArgumentMatchers.any())).thenAnswer(
      invocation -> {
        calls.incrementAndGet();
        throw new IllegalStateException("provider failure");
      }
    );

    assertThatThrownBy(() -> service.create("catalogue", create(fixture))).isInstanceOf(
      IllegalStateException.class
    );
    assertThat(manager.rollbacks).isEqualTo(1);
    assertThat(calls).hasValue(1);

    org.mockito.Mockito.reset(fixture.ports().basicMutation());
    when(fixture.ports().basicMutation().create(org.mockito.ArgumentMatchers.any())).thenAnswer(
      invocation -> {
        calls.incrementAndGet();
        return category();
      }
    );
    TransactionTemplate outer = new TransactionTemplate(manager);
    assertThatThrownBy(() ->
      outer.executeWithoutResult(ignored -> {
        service.create("catalogue", create(fixture));
        throw new IllegalStateException("outer failure");
      })
    ).isInstanceOf(IllegalStateException.class);
    assertThat(manager.rollbacks).isEqualTo(2);
    assertThat(calls).hasValue(2);
  }

  private static Transactional transactional(String name, Class<?>[] parameterTypes) {
    try {
      Method method = AdminCategoryApplicationService.class.getMethod(name, parameterTypes);
      return method.getAnnotation(Transactional.class);
    } catch (NoSuchMethodException failure) {
      throw new AssertionError(failure);
    }
  }

  private static AdminCategoryApplicationService proxied(
    AdminCategoryApplicationService target,
    RecordingTransactionManager manager
  ) {
    ProxyFactory proxyFactory = new ProxyFactory(target);
    proxyFactory.addAdvice(
      new TransactionInterceptor(manager, new AnnotationTransactionAttributeSource())
    );
    return (AdminCategoryApplicationService) proxyFactory.getProxy();
  }

  private static AdminCategoryListValidationInput list(
    AdminCategoryRuntimeTestFixture.Harness fixture
  ) {
    return new AdminCategoryListValidationInput(
      fixture.definition(),
      null,
      null,
      null,
      null,
      null,
      null,
      List.of(),
      null
    );
  }

  private static AdminCategoryCreateValidationInput create(
    AdminCategoryRuntimeTestFixture.Harness fixture
  ) {
    return new AdminCategoryCreateValidationInput(
      fixture.definition(),
      null,
      null,
      null,
      null,
      "Storage",
      "storage",
      true
    );
  }

  private static PageResult<AdminCategoryModels.CategoryNode> page() {
    return new PageResult<>(
      List.of(new AdminCategoryModels.CategoryNode(category(), 0, false)),
      0,
      20,
      1,
      1,
      true,
      true,
      SortCriteria.of("id", SortDirection.ASC)
    );
  }

  private static AdminCategoryModels.Category category() {
    var timestamp = OffsetDateTime.parse("2026-07-14T00:00:00Z");
    return new AdminCategoryModels.Category(
      10L,
      null,
      1_000_000_000L,
      "Storage",
      "storage",
      true,
      timestamp,
      timestamp
    );
  }

  private static final class RecordingTransactionManager
    extends AbstractPlatformTransactionManager
  {

    private int commits;
    private int rollbacks;

    @Override
    protected Object doGetTransaction() {
      return new Object();
    }

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {}

    @Override
    protected void doCommit(DefaultTransactionStatus status) {
      commits++;
    }

    @Override
    protected void doRollback(DefaultTransactionStatus status) {
      rollbacks++;
    }
  }
}
