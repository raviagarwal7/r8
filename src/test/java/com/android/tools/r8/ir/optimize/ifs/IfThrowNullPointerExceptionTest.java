// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.ifs;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfThrowNullPointerExceptionTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection params() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public IfThrowNullPointerExceptionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJVM() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(getExpectedStdout());
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addInnerClasses(IfThrowNullPointerExceptionTest.class)
        .release()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(getExpectedStdout());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(IfThrowNullPointerExceptionTest.class)
        .addKeepClassAndMembersRules(TestClass.class)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(getExpectedStdout());
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(classSubject, isPresent());
    inspectMethod(inspector, classSubject, "testThrowNPE", false, true);
    inspectMethod(inspector, classSubject, "testThrowNPEWithMessage", true, canUseRequireNonNull());
    inspectMethod(inspector, classSubject, "testThrowNull", false, true);
  }

  private void inspectMethod(
      CodeInspector inspector,
      ClassSubject classSubject,
      String methodName,
      boolean isNPEWithMessage,
      boolean shouldBeOptimized) {
    MethodSubject methodSubject = classSubject.uniqueMethodWithName(methodName);
    assertThat(methodSubject, isPresent());

    IRCode code = methodSubject.buildIR();
    if (shouldBeOptimized) {
      assertEquals(1, code.blocks.size());

      BasicBlock entryBlock = code.entryBlock();
      assertEquals(
          3 + BooleanUtils.intValue(isNPEWithMessage), entryBlock.getInstructions().size());
      assertTrue(entryBlock.getInstructions().getFirst().isArgument());
      assertTrue(entryBlock.getInstructions().getLast().isReturn());

      Instruction nullCheckInstruction =
          entryBlock.getInstructions().get(1 + BooleanUtils.intValue(isNPEWithMessage));
      if (canUseRequireNonNull()) {
        assertTrue(nullCheckInstruction.isInvokeStatic());
        if (isNPEWithMessage) {
          assertEquals(
              inspector.getFactory().objectsMethods.requireNonNullWithMessage,
              nullCheckInstruction.asInvokeStatic().getInvokedMethod());
        } else {
          assertEquals(
              inspector.getFactory().objectsMethods.requireNonNull,
              nullCheckInstruction.asInvokeStatic().getInvokedMethod());
        }
      } else {
        assertFalse(isNPEWithMessage);
        assertTrue(nullCheckInstruction.isInvokeVirtual());
        assertEquals(
            inspector.getFactory().objectMembers.getClass,
            nullCheckInstruction.asInvokeVirtual().getInvokedMethod());
      }
    } else {
      assertEquals(3, code.blocks.size());
    }
  }

  private String getExpectedStdout() {
    if (parameters.isCfRuntime() || canUseRequireNonNull() || isDalvik()) {
      return StringUtils.lines("Caught NPE: null", "Caught NPE: x was null", "Caught NPE: null");
    }
    return StringUtils.lines(
        "Caught NPE: Attempt to invoke virtual method 'java.lang.Class java.lang.Object.getClass()'"
            + " on a null object reference",
        "Caught NPE: x was null",
        "Caught NPE: Attempt to invoke virtual method 'java.lang.Class java.lang.Object.getClass()'"
            + " on a null object reference");
  }

  private boolean canUseRequireNonNull() {
    return parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.K);
  }

  private boolean isDalvik() {
    return parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().getVersion().isDalvik();
  }

  static class TestClass {

    public static void main(String[] args) {
      testThrowNPE(new Object());
      testThrowNPEWithMessage(new Object());
      testThrowNull(new Object());

      try {
        testThrowNPE(null);
      } catch (NullPointerException e) {
        System.out.println("Caught NPE: " + e.getMessage());
      }
      try {
        testThrowNPEWithMessage(null);
      } catch (NullPointerException e) {
        System.out.println("Caught NPE: " + e.getMessage());
      }
      try {
        testThrowNull(null);
      } catch (NullPointerException e) {
        System.out.println("Caught NPE: " + e.getMessage());
      }
    }

    static void testThrowNPE(Object x) {
      if (x == null) {
        throw new NullPointerException();
      }
    }

    static void testThrowNPEWithMessage(Object x) {
      if (x == null) {
        throw new NullPointerException("x was null");
      }
    }

    static void testThrowNull(Object x) {
      if (x == null) {
        throw null;
      }
    }
  }
}
