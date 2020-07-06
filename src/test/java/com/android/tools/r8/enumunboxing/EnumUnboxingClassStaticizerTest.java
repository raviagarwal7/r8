// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EnumUnboxingClassStaticizerTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final KeepRule enumKeepRules;

  @Parameterized.Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public EnumUnboxingClassStaticizerTest(
      TestParameters parameters, boolean enumValueOptimization, KeepRule enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    R8TestRunResult run =
        testForR8(parameters.getBackend())
            .addInnerClasses(EnumUnboxingClassStaticizerTest.class)
            .addKeepMainRule(TestClass.class)
            .addKeepRules(enumKeepRules.getKeepRule())
            .enableNeverClassInliningAnnotations()
            .enableInliningAnnotations()
            .noMinification() // For assertions.
            .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
            .allowDiagnosticInfoMessages()
            .setMinApi(parameters.getApiLevel())
            .compile()
            .inspect(this::assertClassStaticized)
            .inspectDiagnosticMessages(
                m ->
                    assertEnumIsUnboxed(
                        UnboxableEnum.class,
                        EnumUnboxingClassStaticizerTest.class.getSimpleName(),
                        m))
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccess();
    assertLines2By2Correct(run.getStdOut());
  }

  private void assertClassStaticized(CodeInspector codeInspector) {
    if (parameters.isCfRuntime()) {
      // There is no class staticizer in Cf.
      assertThat(codeInspector.clazz(Companion.class).uniqueMethodWithName("method"), isPresent());
      return;
    }
    MethodSubject method = codeInspector.clazz(CompanionHost.class).uniqueMethodWithName("method");
    assertThat(method, isPresent());
    assertEquals("int", method.getMethod().method.proto.parameters.toString());
  }

  static class TestClass {
    public static void main(String[] args) {
      CompanionHost.toKeep();
      test(UnboxableEnum.A);
      System.out.println("0");
      test(UnboxableEnum.B);
      System.out.println("1");
      test(UnboxableEnum.C);
      System.out.println("2");
    }

    @NeverInline
    static void test(UnboxableEnum obj) {
      // The class staticizer will move Companion.method() into CompanionHost.method().
      // As a result of this transformation, this call site will need to be processed.
      // To keep track of this, the staticizer will keep a reference to the method
      // `void test(UnboxableEnum)`, but after enum unboxing this has been rewritten to
      // `void test(int)`. Therefore, the staticizer's internal structure needs to be
      // updated after enum unboxing.
      CompanionHost.COMPANION.method(obj);
    }
  }

  @NeverClassInline
  static class CompanionHost {
    static final Companion COMPANION = new Companion();

    @NeverInline
    static void toKeep() {
      System.out.println("Keep me!");
      System.out.println("Keep me!");
    }
  }

  @NeverClassInline
  static class Companion {
    @NeverInline
    public void method(UnboxableEnum obj) {
      System.out.println(obj.ordinal());
    }
  }

  @NeverClassInline
  enum UnboxableEnum {
    A,
    B,
    C;
  }
}
