// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.callsites.dynamicupperboundtype;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.optimize.info.CallSiteOptimizationInfo;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InvokeDirectPositiveTest extends TestBase {
  private static final Class<?> MAIN = Main.class;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private final TestParameters parameters;

  public InvokeDirectPositiveTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(InvokeDirectPositiveTest.class)
        .addKeepMainRule(MAIN)
        .enableMergeAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(
            o -> {
              o.testing.callSiteOptimizationInfoInspector = this::callSiteOptimizationInfoInspect;
            })
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutputLines("Sub1")
        .inspect(this::inspect);
  }

  private void callSiteOptimizationInfoInspect(ProgramMethod method) {
    String methodName = method.getReference().name.toString();
    assert methodName.equals("<init>") || methodName.equals("test")
        : "Unexpected revisit: " + method.toSourceString();
    CallSiteOptimizationInfo callSiteOptimizationInfo =
        method.getDefinition().getCallSiteOptimizationInfo();
    TypeElement upperBoundType;
    if (methodName.equals("test")) {
      upperBoundType = callSiteOptimizationInfo.getDynamicUpperBoundType(1);
    } else {
      // TODO(b/139246447): should avoid visiting <init>, which is trivial, default init!
      // For testing purpose, `Base` is not merged and kept. The system correctly caught that, when
      // the default initializer is invoked, the receiver had a refined type, `Sub1`.
      upperBoundType = callSiteOptimizationInfo.getDynamicUpperBoundType(0);
    }
    assert upperBoundType.isDefinitelyNotNull();
    assert upperBoundType.isClassType()
        && upperBoundType.asClassType().getClassType().toSourceString().endsWith("$Sub1");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject main = inspector.clazz(MAIN);
    assertThat(main, isPresent());

    MethodSubject test = main.uniqueMethodWithName("test");
    assertThat(test, isPresent());
    // Can optimize branches since the type of `arg` is Sub1.
    assertTrue(test.streamInstructions().noneMatch(InstructionSubject::isIf));
  }

  @NeverMerge
  static class Base {}
  static class Sub1 extends Base {}
  static class Sub2 extends Base {}

  @NeverClassInline
  static class Main {
    public static void main(String... args) {
      Main obj = new Main();
      obj.test(new Sub1()); // calls test with Sub1.
    }

    @NeverInline
    private void test(Base arg) {
      if (arg instanceof Sub1) {
        System.out.println("Sub1");
      } else if (arg instanceof Sub2) {
        System.out.println("Sub2");
      }
    }
  }
}