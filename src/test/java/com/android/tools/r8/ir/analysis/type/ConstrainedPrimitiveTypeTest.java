// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import static com.android.tools.r8.ir.analysis.type.TypeElement.getDouble;
import static com.android.tools.r8.ir.analysis.type.TypeElement.getFloat;
import static com.android.tools.r8.ir.analysis.type.TypeElement.getInt;
import static com.android.tools.r8.ir.analysis.type.TypeElement.getLong;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.analysis.AnalysisTestBase;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.Streams;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ConstrainedPrimitiveTypeTest extends AnalysisTestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public ConstrainedPrimitiveTypeTest(TestParameters parameters) throws Exception {
    super(parameters, TestClass.class);
  }

  @Test
  public void testOutput() throws Exception {
    String expectedOutput =
        StringUtils.lines("1", "2", "3", "1.0", "1.0", "2.0", "1", "1", "2", "1.0", "1.0", "2.0");

    testForJvm().addTestClasspath().run(TestClass.class).assertSuccessWithOutput(expectedOutput);

    testForR8(parameters.getBackend())
        .addInnerClasses(ConstrainedPrimitiveTypeTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  @Test
  public void testIntWithInvokeUser() throws Exception {
    buildAndCheckIR("intWithInvokeUserTest", testInspector(getInt(), 1));
  }

  @Test
  public void testIntWithIndirectInvokeUser() throws Exception {
    buildAndCheckIR("intWithIndirectInvokeUserTest", testInspector(getInt(), 2));
  }

  @Test
  public void testFloatWithInvokeUser() throws Exception {
    buildAndCheckIR("floatWithInvokeUserTest", testInspector(getFloat(), 1));
  }

  @Test
  public void testFloatWithIndirectInvokeUser() throws Exception {
    buildAndCheckIR("floatWithIndirectInvokeUserTest", testInspector(getFloat(), 2));
  }

  @Test
  public void testLongWithInvokeUser() throws Exception {
    buildAndCheckIR("longWithInvokeUserTest", testInspector(getLong(), 1));
  }

  @Test
  public void testLongWithIndirectInvokeUser() throws Exception {
    buildAndCheckIR("longWithIndirectInvokeUserTest", testInspector(getLong(), 2));
  }

  @Test
  public void testDoubleWithInvokeUser() throws Exception {
    buildAndCheckIR("doubleWithInvokeUserTest", testInspector(getDouble(), 1));
  }

  @Test
  public void testDoubleWithIndirectInvokeUser() throws Exception {
    buildAndCheckIR("doubleWithIndirectInvokeUserTest", testInspector(getDouble(), 2));
  }

  private static Consumer<IRCode> testInspector(
      TypeElement expectedType, int expectedNumberOfConstNumberInstructions) {
    return code -> {
      for (Instruction instruction : code.instructions()) {
        if (instruction.isConstNumber()) {
          ConstNumber constNumberInstruction = instruction.asConstNumber();
          assertEquals(expectedType, constNumberInstruction.getOutType());
        }
      }

      assertEquals(
          expectedNumberOfConstNumberInstructions,
          Streams.stream(code.instructionIterator()).filter(Instruction::isConstNumber).count());
    };
  }

  static class TestClass {

    public static void main(String[] args) {
      boolean unknownButTrue = args.length >= 0;
      boolean unknownButFalse = args.length < 0;
      intWithInvokeUserTest();
      intWithIndirectInvokeUserTest(unknownButTrue);
      intWithIndirectInvokeUserTest(unknownButFalse);
      floatWithInvokeUserTest();
      floatWithIndirectInvokeUserTest(unknownButTrue);
      floatWithIndirectInvokeUserTest(unknownButFalse);
      longWithInvokeUserTest();
      longWithIndirectInvokeUserTest(unknownButTrue);
      longWithIndirectInvokeUserTest(unknownButFalse);
      doubleWithInvokeUserTest();
      doubleWithIndirectInvokeUserTest(unknownButTrue);
      doubleWithIndirectInvokeUserTest(unknownButFalse);
    }

    @NeverInline
    public static void intWithInvokeUserTest() {
      int x = 1;
      System.out.println(Integer.toString(x));
    }

    @NeverInline
    public static void intWithIndirectInvokeUserTest(boolean unknown) {
      int x;
      if (unknown) {
        x = 2;
      } else {
        x = 3;
      }
      System.out.println(Integer.toString(x));
    }

    @NeverInline
    public static void floatWithInvokeUserTest() {
      float x = 1f;
      System.out.println(Float.toString(x));
    }

    @NeverInline
    public static void floatWithIndirectInvokeUserTest(boolean unknown) {
      float x;
      if (unknown) {
        x = 1f;
      } else {
        x = 2f;
      }
      System.out.println(Float.toString(x));
    }

    @NeverInline
    public static void longWithInvokeUserTest() {
      long x = 1L;
      System.out.println(Long.toString(x));
    }

    @NeverInline
    public static void longWithIndirectInvokeUserTest(boolean unknown) {
      long x;
      if (unknown) {
        x = 1L;
      } else {
        x = 2L;
      }
      System.out.println(Long.toString(x));
    }

    @NeverInline
    public static void doubleWithInvokeUserTest() {
      double x = 1.0;
      System.out.println(Double.toString(x));
    }

    @NeverInline
    public static void doubleWithIndirectInvokeUserTest(boolean unknown) {
      double x;
      if (unknown) {
        x = 1f;
      } else {
        x = 2f;
      }
      System.out.println(Double.toString(x));
    }
  }
}
