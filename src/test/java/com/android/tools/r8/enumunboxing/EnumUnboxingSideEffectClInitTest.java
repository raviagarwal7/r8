// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumUnboxingSideEffectClInitTest extends EnumUnboxingTestBase {
  private static final Class<?> ENUM_CLASS = MyEnum.class;
  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final KeepRule enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public EnumUnboxingSideEffectClInitTest(
      TestParameters parameters, boolean enumValueOptimization, KeepRule enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    Class<?> classToTest = MainEnum.class;
    testForR8(parameters.getBackend())
        .addInnerClasses(EnumUnboxingSideEffectClInitTest.class)
        .addKeepMainRule(classToTest)
        .addKeepRules(enumKeepRules.getKeepRule())
        .enableNeverClassInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .allowDiagnosticInfoMessages()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspectDiagnosticMessages(
            m -> {
              // The snap keep rule forces to keep the static MainEnum#e field, so the enum
              // cannot be unboxed anymore.
              if (enumKeepRules.toString().equals("snap")) {
                assertEnumIsBoxed(ENUM_CLASS, classToTest.getSimpleName(), m);
              } else {
                assertEnumIsUnboxed(ENUM_CLASS, classToTest.getSimpleName(), m);
              }
            })
        .run(parameters.getRuntime(), classToTest)
        .assertSuccessWithOutputLines("0");
  }

  @NeverClassInline
  enum MyEnum {
    A,
    B
  }

  @NeverClassInline
  enum MainEnum {
    INSTANCE;
    // The clinit of this enum needs to be reprocessed by the enum unboxer to rewrite MyEnum.a
    // and the static put instruction to the new field.
    static MyEnum e = System.currentTimeMillis() > 0 ? MyEnum.A : MyEnum.B;

    public static void main(String[] args) {
      System.out.println(e.ordinal());
    }
  }
}
