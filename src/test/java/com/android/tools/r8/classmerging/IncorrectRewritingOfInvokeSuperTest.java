// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/133501933. */
@RunWith(Parameterized.class)
public class IncorrectRewritingOfInvokeSuperTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public IncorrectRewritingOfInvokeSuperTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(IncorrectRewritingOfInvokeSuperTest.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(options -> options.enableUnusedInterfaceRemoval = false)
        .enableInliningAnnotations()
        .noMinification()
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccess();
  }

  static class TestClass {

    public static void main(String[] args) {
      new B() {}.m(new SubArgType());
    }
  }

  interface I<U> {

    void m(U arg);
  }

  abstract static class A implements I<ArgType> {

    @NeverInline
    @Override
    public void m(ArgType x) {
      if (x == null) {
        throw new RuntimeException();
      }
    }
  }

  static class B extends A {

    @NeverInline
    @Override
    public final void m(ArgType x) {
      super.m(x);
    }
  }

  static class ArgType {}

  static class SubArgType extends ArgType {}
}
