// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ComparisonEnumUnboxingTest extends EnumUnboxingTestBase {

  private static final Class<?>[] INPUTS = new Class<?>[] {NullCheck.class, EnumComparison.class};

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final KeepRule enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public ComparisonEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, KeepRule enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    R8TestCompileResult compile =
        testForR8(parameters.getBackend())
            .addInnerClasses(ComparisonEnumUnboxingTest.class)
            .addKeepMainRules(INPUTS)
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .addKeepRules(enumKeepRules.getKeepRule())
            .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
            .allowDiagnosticInfoMessages()
            .setMinApi(parameters.getApiLevel())
            .compile()
            .inspect(
                inspector -> {
                  assertEquals(3, inspector.clazz(NullCheck.class).allMethods().size());
                  assertEquals(2, inspector.clazz(EnumComparison.class).allMethods().size());
                });
    for (Class<?> input : INPUTS) {
      R8TestRunResult run =
          compile
              .inspectDiagnosticMessages(
                  m -> assertEnumIsUnboxed(input.getDeclaredClasses()[0], input.getSimpleName(), m))
              .run(parameters.getRuntime(), input)
              .assertSuccess();
      assertLines2By2Correct(run.getStdOut());
    }
  }

  @SuppressWarnings("ConstantConditions")
  static class NullCheck {

    @NeverClassInline
    enum MyEnum {
      A,
      B
    }

    public static void main(String[] args) {
      System.out.println(nullCheck(MyEnum.A));
      System.out.println(false);
      System.out.println(nullCheck(MyEnum.B));
      System.out.println(false);
      System.out.println(nullCheck(null));
      System.out.println(true);
      System.out.println(onlyNull());
      System.out.println(true);
    }

    // This method has no outValue of type MyEnum but still needs to be reprocessed.
    @NeverInline
    static boolean onlyNull() {
      return nullCheck(null);
    }

    // Do not resolve the == with constants after inlining.
    @NeverInline
    static boolean nullCheck(MyEnum e) {
      return e == null;
    }
  }

  static class EnumComparison {

    @NeverClassInline
    enum MyEnum {
      A,
      B
    }

    public static void main(String[] args) {
      System.out.println(check(MyEnum.A));
      System.out.println(false);
      System.out.println(check(MyEnum.B));
      System.out.println(true);
    }

    // Do not resolve the == with constants after inlining.
    @NeverInline
    static boolean check(MyEnum e) {
      return e == MyEnum.B;
    }
  }
}
