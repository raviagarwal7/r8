// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ToStringEnumUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final KeepRule enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public ToStringEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, KeepRule enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    Class<?> success = EnumNameToString.class;
    R8TestRunResult run =
        testForR8(parameters.getBackend())
            .addInnerClasses(ToStringEnumUnboxingTest.class)
            .addKeepMainRule(EnumNameToString.class)
            .enableNeverClassInliningAnnotations()
            .addKeepRules(enumKeepRules.getKeepRule())
            .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
            .allowDiagnosticInfoMessages()
            .setMinApi(parameters.getApiLevel())
            .compile()
            .inspectDiagnosticMessages(
                m ->
                    assertEnumIsUnboxed(
                        success.getDeclaredClasses()[0], success.getSimpleName(), m))
            .run(parameters.getRuntime(), success)
            .assertSuccess();
    assertLines2By2Correct(run.getStdOut());
  }

  static class EnumNameToString {

    @NeverClassInline
    enum MyEnum {
      A,
      B
    }

    @SuppressWarnings("ConstantConditions")
    public static void main(String[] args) {
      System.out.println(MyEnum.A.toString());
      System.out.println("A");
      System.out.println(MyEnum.A.name());
      System.out.println("A");
      System.out.println(MyEnum.B.toString());
      System.out.println("B");
      System.out.println(MyEnum.B.name());
      System.out.println("B");
      try {
        System.out.println(((MyEnum) null).toString());
      } catch (NullPointerException e) {
        System.out.println("npeToString " + e.getMessage());
        System.out.println("npeToString " + e.getMessage());
      }
      try {
        System.out.println(((MyEnum) null).name());
      } catch (NullPointerException e) {
        System.out.println("npeName " + e.getMessage());
        System.out.println("npeName " + e.getMessage());
      }
    }
  }
}
