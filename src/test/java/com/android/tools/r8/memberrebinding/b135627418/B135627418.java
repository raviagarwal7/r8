// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.memberrebinding.b135627418;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.desugar.DesugaredLibraryConfiguration;
import com.android.tools.r8.memberrebinding.b135627418.library.Drawable;
import com.android.tools.r8.memberrebinding.b135627418.library.DrawableWrapper;
import com.android.tools.r8.memberrebinding.b135627418.library.InsetDrawable;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InvokeInstructionSubject;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class B135627418 extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // Only run on dex, as the runtime library used in the test is built as dex.
    return getTestParameters().withDexRuntimes().build();
  }

  public B135627418(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void noInvokeToDrawableWrapper(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    assertTrue(
        classSubject
            .uniqueMethodWithName("main")
            .streamInstructions()
            .filter(InstructionSubject::isInvokeVirtual)
            .noneMatch(
                instruction ->
                    ((InvokeInstructionSubject) instruction)
                        .invokedMethod()
                        .holder
                        .getName()
                        .contains("DrawableWrapper")));
  }

  @Test
  public void dontRebindWithinLibrary() throws Exception {
    String packageName = getClass().getPackage().getName();

    // Build a runtime library without DrawableWrapper, where InsetDrawable inherits directly
    // from Drawable. Use the prefix rewriting to move the InsetDrawable from package runtime to
    // package library. This creates a mock of the structure of classes android.drawable.Drawable,
    // android.drawable.DrawableWrapper and android.drawable.InsetDrawable. The class
    // android.drawable.DrawableWrapper was introduced in API level 23 between
    // android.drawable.Drawable and android.drawable.InsetDrawable, so present at compile time,
    // but not at runtime on pre API level 23 devices.
    D8TestCompileResult runtimeLibrary =
        testForD8()
            .addProgramClasses(
                Drawable.class,
                com.android.tools.r8.memberrebinding.b135627418.runtime.InsetDrawable.class)
            .setMinApi(parameters.getRuntime())
            .addOptionsModification(
                options ->
                    options.desugaredLibraryConfiguration =
                        DesugaredLibraryConfiguration.withOnlyRewritePrefixForTesting(
                            ImmutableMap.of(packageName + ".runtime", packageName + ".library")))
            .compile();

    testForR8(parameters.getBackend())
        .addLibraryFiles(runtimeJar(parameters.getBackend()))
        .addLibraryClasses(Drawable.class, DrawableWrapper.class, InsetDrawable.class)
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters.getRuntime())
        .compile()
        .disassemble()
        .inspect(this::noInvokeToDrawableWrapper)
        .addRunClasspathFiles(runtimeLibrary.writeToZip())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("In InsetDrawable.setAlpha");
  }

  static class TestClass {

    public static void main(String[] args) {
      Drawable drawable;
      if (args.length == 0) {
        drawable = new InsetDrawable();
      } else {
        drawable = new InsetDrawable();
      }
      drawable.setAlpha(0);
    }
  }
}
