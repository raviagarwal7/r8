// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.bridgeremoval.hoisting;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.bridgeremoval.hoisting.testclasses.NonReboundBridgeHoistingTestClasses;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NonReboundBridgeHoistingTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public NonReboundBridgeHoistingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClass.class)
        .addInnerClasses(NonReboundBridgeHoistingTestClasses.class)
        .addProgramClassFileData(
            transformer(C.class).setBridge(C.class.getDeclaredMethod("bridge")).transform())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableMergeAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject aClassSubject = inspector.clazz(NonReboundBridgeHoistingTestClasses.getClassA());
    assertThat(aClassSubject, isPresent());
    assertThat(aClassSubject.uniqueMethodWithName("m"), isPresent());
    assertThat(aClassSubject.uniqueMethodWithName("bridge"), not(isPresent()));

    ClassSubject bClassSubject = inspector.clazz(NonReboundBridgeHoistingTestClasses.B.class);
    assertThat(bClassSubject, isPresent());

    ClassSubject cClassSubject = inspector.clazz(C.class);
    assertThat(cClassSubject, isPresent());
    // TODO(b/153147967): This bridge should be hoisted to B.
    assertThat(cClassSubject.uniqueMethodWithName("bridge"), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      new C().bridge();
    }
  }

  @NeverClassInline
  static class C extends NonReboundBridgeHoistingTestClasses.B {

    // The invoke instruction in this bridge cannot be rewritten to target A.m(), since A is not
    // accessible in this context. It therefore points to B.m(), where there is no definition of the
    // method. As a result of this, we cannot move this bridge to A without also rewriting the
    // signature referenced from the invoke instruction.
    @NeverInline
    public /*bridge*/ void bridge() {
      m();
    }
  }
}
