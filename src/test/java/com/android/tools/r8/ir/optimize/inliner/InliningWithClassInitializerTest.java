// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;

public class InliningWithClassInitializerTest extends TestBase {

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("In A.<clinit>()", "In B.<clinit>()", "In B.other()");

    testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expectedOutput);

    CodeInspector inspector =
        testForR8(Backend.DEX)
            .addInnerClasses(InliningWithClassInitializerTest.class)
            .addKeepMainRule(TestClass.class)
            .enableInliningAnnotations()
            .enableMergeAnnotations()
            .run(TestClass.class)
            .assertSuccessWithOutput(expectedOutput)
            .inspector();

    ClassSubject classA = inspector.clazz(A.class);
    assertThat(classA, isPresent());

    ClassSubject classB = inspector.clazz(B.class);
    assertThat(classB, isPresent());

    MethodSubject inlineableMethod = classB.uniqueMethodWithName("inlineable");
    assertThat(inlineableMethod, not(isPresent()));

    MethodSubject otherMethod = classB.uniqueMethodWithName("other");
    assertThat(otherMethod, isPresent());

    MethodSubject mainMethod = inspector.clazz(TestClass.class).mainMethod();
    assertThat(mainMethod, invokesMethod(otherMethod));
  }

  static class TestClass {

    public static void main(String[] args) {
      // Should be inlined since the call to `B.other()` will ensure that the static initalizer in
      // A will continue to be executed even after inlining.
      B.inlineable();
    }
  }

  @NeverMerge
  static class A {

    static {
      System.out.println("In A.<clinit>()");
    }
  }

  static class B extends A {

    static {
      System.out.println("In B.<clinit>()");
    }

    static void inlineable() {
      other();
    }

    @NeverInline
    static void other() {
      System.out.println("In B.other()");
    }
  }
}
