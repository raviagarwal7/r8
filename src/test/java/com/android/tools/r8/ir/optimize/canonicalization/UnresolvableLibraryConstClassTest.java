// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.canonicalization;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class LibraryClass {
}

@NeverMerge
class ProgramClass1 extends LibraryClass {
}

class ProgramSubClass extends ProgramClass1 {
}

interface LibraryInterface {
  void foo();
}

class ProgramClass2 implements LibraryInterface {
  @Override
  public void foo() {
    System.out.println("ProgramClass2::foo");
  }
}

class TestMain {
  public static void main(String... args) {
    if (System.currentTimeMillis() < -6) {
      System.out.println(ProgramSubClass.class.getName());
    } else if (System.currentTimeMillis() < -5) {
      System.out.println(ProgramSubClass.class.getSimpleName());
    } else if (System.currentTimeMillis() < -4) {
      System.out.println(ProgramClass1.class.getName());
    } else if (System.currentTimeMillis() < -3) {
      System.out.println(ProgramClass1.class.getSimpleName());
    } else if (System.currentTimeMillis() < -2) {
      System.out.println(ProgramClass2.class.getName());
    } else if (System.currentTimeMillis() < -1) {
      System.out.println(ProgramClass2.class.getSimpleName());
    } else {
      System.out.println("No need to load any classes");
    }
  }
}

@RunWith(Parameterized.class)
public class UnresolvableLibraryConstClassTest extends TestBase {
  private static final Class<?> MAIN = TestMain.class;
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "No need to load any classes"
  );

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  private final TestParameters parameters;

  public UnresolvableLibraryConstClassTest(TestParameters parameters) {
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
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.isDexRuntime());

    testForD8()
        .release()
        .addProgramClasses(ProgramClass1.class, ProgramClass2.class, ProgramSubClass.class, MAIN)
        .addOptionsModification(InternalOptions::disableNameReflectionOptimization)
        .setMinApi(parameters.getRuntime())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addLibraryClasses(LibraryClass.class, LibraryInterface.class)
        .addLibraryFiles(ToolHelper.getDefaultAndroidJar())
        .addProgramClasses(ProgramClass1.class, ProgramClass2.class, ProgramSubClass.class, MAIN)
        .addKeepMainRule(MAIN)
        .noMinification()
        .enableMergeAnnotations()
        .addOptionsModification(InternalOptions::disableNameReflectionOptimization)
        .setMinApi(parameters.getRuntime())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject programClass1 = inspector.clazz(ProgramClass1.class);
    assertThat(programClass1, isPresent());
    ClassSubject programSubClass = inspector.clazz(ProgramSubClass.class);
    assertThat(programSubClass, isPresent());
    ClassSubject programClass2 = inspector.clazz(ProgramClass2.class);
    assertThat(programClass2, isPresent());

    ClassSubject mainClass = inspector.clazz(MAIN);
    assertThat(mainClass, isPresent());
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    // No canonicalization of const-class instructions.
    assertEquals(
        6,
        mainMethod.streamInstructions().filter(InstructionSubject::isConstClass).count());
  }
}
