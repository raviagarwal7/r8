// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.redundantfieldloadelimination;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RequiredFieldLoadAfterFieldStoreTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public RequiredFieldLoadAfterFieldStoreTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(RequiredFieldLoadAfterFieldStoreTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters.getRuntime())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class TestClass {

    public static void main(String[] args) {
      A a = new A();
      test(a, a);
    }

    @NeverInline
    static void test(A a, A b) {
      a.greeting = "Unexpected";
      b.greeting = "Hello world!";
      System.out.println(a.greeting); // Should print "Hello world!"
    }
  }

  static class A {

    String greeting;
  }
}
