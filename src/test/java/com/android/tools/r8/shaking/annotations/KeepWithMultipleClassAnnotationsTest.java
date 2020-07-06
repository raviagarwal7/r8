// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.annotations;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepWithMultipleClassAnnotationsTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public KeepWithMultipleClassAnnotationsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testA() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(KeepWithMultipleClassAnnotationsTest.class)
        .addKeepRules(
            "-keep @" + A.class.getTypeName() + " class * {",
            "  public static void main(java.lang.String[]);",
            "}")
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  @Test
  public void testAB() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(KeepWithMultipleClassAnnotationsTest.class)
        .addKeepRules(
            "-keep @" + A.class.getTypeName() + " @" + B.class.getTypeName() + " class * {",
            "  public static void main(java.lang.String[]);",
            "}")
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  @Test
  public void testABC() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(KeepWithMultipleClassAnnotationsTest.class)
        .addKeepRules(
            "-keep @"
                + A.class.getTypeName()
                + " @"
                + B.class.getTypeName()
                + " @"
                + C.class.getTypeName()
                + " class * {",
            "  public static void main(java.lang.String[]);",
            "}")
        .allowUnusedProguardConfigurationRules()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspect(inspector -> assertTrue(inspector.allClasses().isEmpty()));
  }

  @A
  @B
  static class TestClass {

    public static void main(String[] args) {
      System.out.println("Hello world!");
    }
  }

  @Retention(RetentionPolicy.CLASS)
  @Target({ElementType.TYPE})
  @interface A {}

  @Retention(RetentionPolicy.CLASS)
  @Target({ElementType.TYPE})
  @interface B {}

  @Retention(RetentionPolicy.CLASS)
  @Target({ElementType.TYPE})
  @interface C {}
}
