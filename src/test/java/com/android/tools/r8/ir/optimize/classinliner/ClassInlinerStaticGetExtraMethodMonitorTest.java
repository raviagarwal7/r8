// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * This is a reproduction of b/147411673 where we inline classes and remove monitor instructions.
 */
@RunWith(Parameterized.class)
public class ClassInlinerStaticGetExtraMethodMonitorTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassInlinerStaticGetExtraMethodMonitorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ClassInlinerStaticGetExtraMethodMonitorTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .noMinification()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("20000");
  }

  private void inspect(CodeInspector inspector) {
    assertThat(inspector.clazz(Container.class).uniqueMethodWithName("increment"), isPresent());
  }

  static class TestClass {

    private static volatile Thread t1 = new Thread(TestClass::produce1);
    private static volatile Thread t2 = new Thread(TestClass::produce2);

    @NeverInline
    static void produce1() {
      Container instance = Container.getInstance();
      for (int i = 0; i < 10000; i++) {
        synchronizeOnExtraMethod(instance);
      }
    }

    @NeverInline
    static void produce2() {
      Container instance = Container.getInstance();
      for (int i = 0; i < 10000; i++) {
        synchronizeOnExtraMethod(instance);
      }
    }

    @NeverInline
    static void synchronizeOnExtraMethod(Container container) {
      synchronized (container) {
        container.increment();
      }
    }

    public static void main(String[] args) {
      t1.start();
      t2.start();
      while (t1.isAlive() || t2.isAlive()) {}
      System.out.println(Container.counter);
    }
  }

  static class Container {

    static Container INSTANCE = new Container();
    public static int counter = 0;

    static Container getInstance() {
      return INSTANCE;
    }

    @NeverInline
    final void increment() {
      counter += 1;
    }
  }
}
