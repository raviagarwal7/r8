// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.canonicalization;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.DexIndexedConsumer.ArchiveConsumer;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ir.optimize.canonicalization.ConstClassMain.Outer;
import com.android.tools.r8.ir.optimize.canonicalization.ConstClassMain.Outer.Inner;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class IncrementalA {
}

class IncrementalMain {
  public static void main(String... args) {
    System.out.println(IncrementalA.class.getSimpleName());
    System.out.println(IncrementalA.class.getSimpleName());
    System.out.println(IncrementalA.class.getSimpleName());
  }
}

class ConstClassMain {

  static class Outer {
    Outer() {
      System.out.println("Outer::<init>");
    }

    static class Inner {
      Inner() {
        System.out.println("Inner::<init>");
      }
    }
  }

  public static void main(String... args) {
    Class outer = Inner.class.getDeclaringClass();
    System.out.println(outer.getSimpleName());
    for (Class<?> inner : outer.getDeclaredClasses()) {
      System.out.println(inner.getSimpleName());
    }
    System.out.println(ConstClassMain.class.getSimpleName());
    System.out.println(Outer.class.getSimpleName());
    System.out.println(Inner.class.getSimpleName());
    System.out.println(ConstClassMain.class.getSimpleName().length());
    System.out.println(Outer.class.getSimpleName().length());
    System.out.println(Inner.class.getSimpleName().length());
  }
}

@RunWith(Parameterized.class)
public class ConstClassCanonicalizationTest extends TestBase {
  private static final String INCREMENTAL_OUTPUT = StringUtils.lines(
      "IncrementalA",
      "IncrementalA",
      "IncrementalA"
  );

  private static final Class<?> MAIN = ConstClassMain.class;
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "Outer",
      "Inner",
      "ConstClassMain",
      "Outer",
      "Inner",
      "14",
      "5",
      "5"
  );
  private static final int ORIGINAL_MAIN_COUNT = 2;
  private static final int ORIGINAL_OUTER_COUNT = 2;
  private static final int ORIGINAL_INNER_COUNT = 3;
  private static final int CANONICALIZED_MAIN_COUNT = 1;
  private static final int CANONICALIZED_OUTER_COUNT = 1;
  private static final int CANONICALIZED_INNER_COUNT = 1;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  private final TestParameters parameters;

  public ConstClassCanonicalizationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJVMOutput() throws Exception {
    assumeTrue("Only run JVM reference on CF runtimes", parameters.isCfRuntime());
    testForJvm()
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  @Test
  public void testD8_incremental() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.isDexRuntime());

    Path zipA = temp.newFile("a.zip").toPath().toAbsolutePath();
    testForD8()
        .release()
        .addProgramClasses(IncrementalA.class)
        .setMinApi(parameters.getRuntime())
        .setProgramConsumer(new ArchiveConsumer(zipA))
        .compile();
    testForD8()
        .release()
        .addProgramClasses(IncrementalMain.class)
        .setMinApi(parameters.getRuntime())
        .compile()
        .addRunClasspathFiles(zipA)
        .run(parameters.getRuntime(), IncrementalMain.class)
        .assertSuccessWithOutput(INCREMENTAL_OUTPUT)
        .inspect(inspector -> {
          ClassSubject main = inspector.clazz(IncrementalMain.class);
          assertThat(main, isPresent());
          MethodSubject mainMethod = main.mainMethod();
          assertThat(mainMethod, isPresent());
          assertEquals(
              3,
              mainMethod.streamInstructions().filter(InstructionSubject::isConstClass).count());
        });
  }

  private void test(
      TestRunResult result, int mainCount, int outerCount, int innerCount) throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    assertEquals(
        mainCount,
        mainMethod.streamInstructions().filter(i -> i.isConstClass(MAIN.getTypeName()))
            .count());
    assertEquals(
        outerCount,
        mainMethod.streamInstructions().filter(i -> i.isConstClass(Outer.class.getTypeName()))
            .count());
    assertEquals(
        innerCount,
        mainMethod.streamInstructions().filter(i -> i.isConstClass(Inner.class.getTypeName()))
            .count());
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.isDexRuntime());

    D8TestRunResult result =
        testForD8()
            .debug()
            .addProgramClassesAndInnerClasses(MAIN)
            .addOptionsModification(InternalOptions::disableNameReflectionOptimization)
            .setMinApi(parameters.getRuntime())
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, CANONICALIZED_MAIN_COUNT, ORIGINAL_OUTER_COUNT, ORIGINAL_INNER_COUNT);

    result =
        testForD8()
            .release()
            .addProgramClassesAndInnerClasses(MAIN)
            .addOptionsModification(InternalOptions::disableNameReflectionOptimization)
            .setMinApi(parameters.getRuntime())
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, CANONICALIZED_MAIN_COUNT, ORIGINAL_OUTER_COUNT, ORIGINAL_INNER_COUNT);
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(MAIN)
            .addKeepMainRule(MAIN)
            .addKeepAllAttributes()
            .noMinification()
            .addOptionsModification(InternalOptions::disableNameReflectionOptimization)
            .setMinApi(parameters.getRuntime())
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    // The number of expected const-class instructions differs because constant canonicalization is
    // only enabled for the DEX backend.
    int expectedMainCount =
        parameters.isCfRuntime() ? ORIGINAL_MAIN_COUNT : CANONICALIZED_MAIN_COUNT;
    int expectedOuterCount =
        parameters.isCfRuntime() ? ORIGINAL_OUTER_COUNT : CANONICALIZED_OUTER_COUNT;
    int expectedInnerCount =
        parameters.isCfRuntime() ? ORIGINAL_INNER_COUNT : CANONICALIZED_INNER_COUNT;
    test(result, expectedMainCount, expectedOuterCount, expectedInnerCount);
  }
}
