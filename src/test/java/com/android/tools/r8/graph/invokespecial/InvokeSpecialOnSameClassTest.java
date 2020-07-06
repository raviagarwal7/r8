// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.invokespecial;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeSpecialOnSameClassTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvokeSpecialOnSameClassTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws IOException, CompilationFailedException, ExecutionException {
    try {
      testForRuntime(parameters.getRuntime(), parameters.getApiLevel())
          .addProgramClasses(Main.class)
          .addProgramClassFileData(getClassWithTransformedInvoked())
          .run(parameters.getRuntime(), Main.class)
          .assertSuccessWithOutputLines("Hello World!");
      // TODO(b/110175213): Remove when fixed.
      assertTrue(parameters.isCfRuntime());
    } catch (CompilationFailedException compilation) {
      assertThat(
          compilation.getCause().getMessage(),
          containsString("Failed to compile unsupported use of invokespecial"));
    }
  }

  private byte[] getClassWithTransformedInvoked() throws IOException {
    return transformer(A.class)
        .transformMethodInsnInMethod(
            "bar",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              continuation.visitMethodInsn(INVOKESPECIAL, owner, name, descriptor, isInterface);
            })
        .transform();
  }

  public static class A {

    public void foo() {
      System.out.println("Hello World!");
    }

    public void bar() {
      foo(); // Will be rewritten to invoke-special A.foo()
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new A().bar();
    }
  }
}
