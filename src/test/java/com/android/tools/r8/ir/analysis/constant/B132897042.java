// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.constant;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class B132897042 extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  private final TestParameters parameters;

  public B132897042(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addLibraryClasses(LibClass.class)
        .addLibraryFiles(runtimeJar(parameters))
        .addProgramClasses(TestClass.class)
        .addKeepRules(StringUtils.lines(
            "-assumevalues class" + LibClass.class.getName() + " {",
            "  static int SDK_INT return 1..28;",
            "}"
        ))
        .noTreeShaking()
        .setMinApi(parameters.getRuntime())
        .compile()
        .assertNoMessages();
  }

  static class LibClass {
    static int SDK_INT;
  }

  static class TestClass {
    static void foo() {
      String v = "0";
      int x;
      switch (Integer.parseInt(v)) {
        case 0:
          x = LibClass.SDK_INT;
          break;
        default:
          x = 1;
      }
      if (x > 12) {
        System.out.println(x);
      }
    }
  }

}
