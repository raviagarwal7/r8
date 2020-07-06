// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedarguments;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedArgumentRemovalWithOverridingTest extends TestBase {

  private final boolean minification;
  private final TestParameters parameters;

  @Parameters(name = "{1}, minification: {0}")
  public static List<Object[]> params() {
    return buildParameters(BooleanUtils.values(), getTestParameters().withAllRuntimes().build());
  }

  public UnusedArgumentRemovalWithOverridingTest(boolean minification, TestParameters parameters) {
    this.minification = minification;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("Hello world!", "Hello world!");
    testForR8(parameters.getBackend())
        .addInnerClasses(UnusedArgumentRemovalWithOverridingTest.class)
        .addKeepMainRule(TestClass.class)
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .enableMergeAnnotations()
        .minification(minification)
        .setMinApi(parameters.getRuntime())
        .compile()
        .inspect(this::verify)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  private void verify(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(B.class);
    assertThat(classSubject, isPresent());

    MethodSubject methodSubject = classSubject.uniqueMethodWithName("greeting");
    assertThat(methodSubject, isPresent());
    assertEquals("java.lang.String", methodSubject.getMethod().method.proto.parameters.toString());
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new A().greeting("Hello world!"));
      System.out.println(new B().greeting("Hello world!"));
    }
  }

  @NeverClassInline
  @NeverMerge
  static class A {

    @NeverInline
    public String greeting(String used) {
      return used;
    }
  }

  @NeverClassInline
  static class B extends A {

    @NeverInline
    @Override
    public String greeting(String unused) {
      System.out.print("Hello ");
      return "world!";
    }
  }
}
