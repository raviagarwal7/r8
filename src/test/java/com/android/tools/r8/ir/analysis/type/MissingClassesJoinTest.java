// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.base.Throwables;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Test for b/130211035. */
@RunWith(Parameterized.class)
public class MissingClassesJoinTest extends TestBase {

  private static final String expectedOutput = StringUtils.lines("Hello world!");

  private final boolean allowTypeErrors;
  private final TestParameters parameters;

  @Parameters(name = "{1}, allow type errors: {0}")
  public static List<Object[]> data() {
    return buildParameters(BooleanUtils.values(), getTestParameters().withAllRuntimes().build());
  }

  public MissingClassesJoinTest(boolean allowTypeErrors, TestParameters parameters) {
    this.allowTypeErrors = allowTypeErrors;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    Path classpathFile =
        testForD8()
            .addProgramClasses(ASub2.class)
            .setMinApi(parameters.getRuntime())
            .compile()
            .writeToZip();

    if (parameters.isDexRuntime() && !allowTypeErrors) {
      testForD8()
          // Intentionally not adding ASub2 as a program class.
          .addProgramClasses(A.class, ASub1.class, Box.class, TestClass.class)
          .setMinApi(parameters.getRuntime())
          .compile()
          .addRunClasspathFiles(classpathFile)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutput(expectedOutput);
    }

    try {
      R8TestRunResult result =
          testForR8(parameters.getBackend())
              // Intentionally not adding ASub2 as a program class.
              .addProgramClasses(A.class, ASub1.class, Box.class, TestClass.class)
              .addKeepAllClassesRule()
              .addOptionsModification(options -> options.testing.allowTypeErrors = allowTypeErrors)
              .enableInliningAnnotations()
              .enableMergeAnnotations()
              .setMinApi(parameters.getRuntime())
              .compile()
              .addRunClasspathFiles(classpathFile)
              .run(parameters.getRuntime(), TestClass.class);

      // Compilation fails unless type errors are allowed.
      assertTrue(allowTypeErrors);

      // TestClass.main() does not type check, so it should have been replaced by `throw null`.
      // Note that, even if we do not replace the body of main() with `throw null`, the code would
      // still not work for the CF backend:
      //
      //     java.lang.VerifyError: Bad type on operand stack
      //     Exception Details:
      //       Location:
      //         MissingClassesJoinTest$TestClass.main([Ljava/lang/String;)V @28: putstatic
      //       Reason:
      //         Type 'java/lang/Object' (current frame, stack[0]) is not assignable to
      //         'com/android/tools/r8/ir/analysis/type/MissingClassesJoinTest$A'
      //       Current Frame:
      //         bci: @28
      //         flags: { }
      //         locals: { 'java/lang/Object' }
      //         stack: { 'java/lang/Object' }
      result.assertFailureWithErrorThatMatches(containsString("NullPointerException"));
    } catch (CompilationFailedException e) {
      // Compilation should only fail when type errors are not allowed.
      assertFalse(
          StringUtils.joinLines(
              "Test should only throw when type errors are not allowed",
              Throwables.getStackTraceAsString(e)),
          allowTypeErrors);

      // Verify that we fail with an assertion error.
      assertThat(e.getCause().getMessage(), containsString("AssertionError"));
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      A a;
      if (System.currentTimeMillis() < 0) {
        a = new ASub1();
      } else {
        a = new ASub2();
      }
      Box.field = a;
    }
  }

  @NeverMerge
  abstract static class A {}

  static class ASub1 extends A {}

  static class ASub2 extends A {}

  static class Box {

    static {
      System.out.println("Hello world!");
    }

    static A field;
  }
}
