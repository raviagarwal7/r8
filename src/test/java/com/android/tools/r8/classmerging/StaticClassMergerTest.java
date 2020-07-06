// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.AssumeMayHaveSideEffects;
import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticClassMergerTest extends TestBase {

  static class StaticMergeCandidateA {

    @AssumeMayHaveSideEffects
    @NeverInline
    public static String m() {
      return "StaticMergeCandidateA.m()";
    }
  }

  static class StaticMergeCandidateB {

    @AssumeMayHaveSideEffects
    @NeverInline
    public static String m() {
      return "StaticMergeCandidateB.m()";
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(StaticMergeCandidateA.m());
      System.out.print(StaticMergeCandidateB.m());
    }
  }

  private final String EXPECTED =
      StringUtils.joinLines("StaticMergeCandidateA.m()", "StaticMergeCandidateB.m()");

  private final TestParameters parameters;

  public StaticClassMergerTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws IOException, CompilationFailedException, ExecutionException {
    testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
        .addInnerClasses(StaticClassMergerTest.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testStaticClassIsRemoved() throws Exception {
    CodeInspector inspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(StaticClassMergerTest.class)
            .addKeepMainRule(TestClass.class)
            .noMinification()
            .enableInliningAnnotations()
            .enableSideEffectAnnotations()
            .setMinApi(parameters.getApiLevel())
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(EXPECTED)
            .inspector();

    // Check that one of the two static merge candidates has been removed
    List<FoundClassSubject> classes =
        inspector.allClasses().stream()
            .filter(clazz -> clazz.getOriginalName().contains("StaticMergeCandidate"))
            .collect(Collectors.toList());
    assertEquals(1, classes.size());

    // Check that the remaining static merge candidate has two methods.
    FoundClassSubject remaining = classes.get(0);
    assertEquals(2, remaining.allMethods().size());
  }
}
