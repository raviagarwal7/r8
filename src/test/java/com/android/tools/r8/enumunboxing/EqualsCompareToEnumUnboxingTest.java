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
public class EqualsCompareToEnumUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final KeepRule enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public EqualsCompareToEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, KeepRule enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    Class<?> success = EnumEqualscompareTo.class;
    R8TestRunResult run =
        testForR8(parameters.getBackend())
            .addInnerClasses(EqualsCompareToEnumUnboxingTest.class)
            .addKeepMainRule(EnumEqualscompareTo.class)
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

  static class EnumEqualscompareTo {

    @NeverClassInline
    enum MyEnum {
      A,
      B
    }

    public static void main(String[] args) {
      equalsTest();
      compareToTest();
    }

    @SuppressWarnings({"ConstantConditions", "EqualsWithItself", "ResultOfMethodCallIgnored"})
    private static void equalsTest() {
      System.out.println(MyEnum.A.equals(MyEnum.B));
      System.out.println(false);
      System.out.println(MyEnum.A.equals(MyEnum.A));
      System.out.println(true);
      System.out.println(MyEnum.A.equals(null));
      System.out.println(false);
      try {
        ((MyEnum) null).equals(null);
      } catch (NullPointerException npe) {
        System.out.println("npe " + npe.getMessage());
        System.out.println("npe " + npe.getMessage());
      }
    }

    @SuppressWarnings({"ConstantConditions", "EqualsWithItself", "ResultOfMethodCallIgnored"})
    private static void compareToTest() {
      System.out.println(MyEnum.B.compareTo(MyEnum.A) > 0);
      System.out.println(true);
      System.out.println(MyEnum.A.compareTo(MyEnum.B) < 0);
      System.out.println(true);
      System.out.println(MyEnum.A.compareTo(MyEnum.A) == 0);
      System.out.println(true);
      try {
        ((MyEnum) null).equals(null);
      } catch (NullPointerException npe) {
        System.out.println("npe " + npe.getMessage());
        System.out.println("npe " + npe.getMessage());
      }
      try {
        MyEnum.A.compareTo(null);
      } catch (NullPointerException npe) {
        System.out.println("npe " + npe.getMessage());
        System.out.println("npe " + npe.getMessage());
      }
    }
  }
}
