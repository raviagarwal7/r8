// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b158429654;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.regress.b158429654.innerpackage.InnerClass;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InliningNonAccessible extends TestBase {
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public InliningNonAccessible(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testCompileToInvalidFileD8() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(OuterAbstract.class, OuterImpl.class, InnerClass.class)
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .noMinification()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("42");
  }

  static class Main {
    public static void main(String[] args) {
      OuterImpl.register(args);
      InnerClass inner = new InnerClass();
      inner.foobar();
      System.out.println("42");
    }
  }
}
