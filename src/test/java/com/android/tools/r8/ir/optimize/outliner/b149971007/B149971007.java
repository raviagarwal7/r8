// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.outliner.b149971007;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dexsplitter.SplitterTestBase;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.utils.InternalOptions.OutlineOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class B149971007 extends SplitterTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public B149971007(TestParameters parameters) {
    this.parameters = parameters;
  }

  private boolean invokesOutline(MethodSubject method, String outlineClassName) {
    assertThat(method, isPresent());
    for (InstructionSubject instruction : method.asFoundMethodSubject().instructions()) {
      if (instruction.isInvoke()
          && instruction.getMethod().holder.toSourceString().equals(outlineClassName)) {
        return true;
      }
    }
    return false;
  }

  private boolean referenceFeatureClass(FoundMethodSubject method) {
    for (InstructionSubject instruction : method.instructions()) {
      if (instruction.isInvoke()
          && instruction.getMethod().holder.toSourceString().endsWith("FeatureClass")) {
        return true;
      }
    }
    return false;
  }

  private void checkOutlineFromFeature(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(OutlineOptions.CLASS_NAME);
    assertThat(clazz, isPresent());
    assertEquals(2, clazz.allMethods().size());
    assertTrue(clazz.allMethods().stream().anyMatch(this::referenceFeatureClass));
  }

  @Test
  public void testWithoutSplit() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(TestClass.class, FeatureAPI.class, FeatureClass.class)
            .addKeepClassAndMembersRules(TestClass.class)
            .addKeepClassAndMembersRules(FeatureClass.class)
            .setMinApi(parameters.getApiLevel())
            .addOptionsModification(options -> options.outline.threshold = 2)
            .compile()
            .inspect(this::checkOutlineFromFeature);

    // Check that parts of method1, ..., method4 in FeatureClass was outlined.
    ClassSubject featureClass = compileResult.inspector().clazz(FeatureClass.class);
    assertThat(featureClass, isPresent());
    String outlineClassName =
        ClassNameMapper.mapperFromString(compileResult.getProguardMap())
            .getObfuscatedToOriginalMapping()
            .inverse
            .get(OutlineOptions.CLASS_NAME);
    assertTrue(invokesOutline(featureClass.uniqueMethodWithName("method1"), outlineClassName));
    assertTrue(invokesOutline(featureClass.uniqueMethodWithName("method2"), outlineClassName));
    assertTrue(invokesOutline(featureClass.uniqueMethodWithName("method3"), outlineClassName));
    assertTrue(invokesOutline(featureClass.uniqueMethodWithName("method4"), outlineClassName));

    compileResult.run(parameters.getRuntime(), TestClass.class).assertSuccessWithOutput("123456");
  }

  private void checkNoOutlineFromFeature(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(OutlineOptions.CLASS_NAME);
    assertThat(clazz, isPresent());
    assertEquals(1, clazz.allMethods().size());
    assertTrue(clazz.allMethods().stream().noneMatch(this::referenceFeatureClass));
  }

  @Test
  public void testWithSplit() throws Exception {
    Path featureCode = temp.newFile("feature.zip").toPath();

    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(TestClass.class, FeatureAPI.class)
            .addKeepClassAndMembersRules(TestClass.class)
            .addKeepClassAndMembersRules(FeatureClass.class)
            .setMinApi(parameters.getApiLevel())
            .addFeatureSplit(
                builder -> simpleSplitProvider(builder, featureCode, temp, FeatureClass.class))
            .addOptionsModification(options -> options.outline.threshold = 2)
            .compile()
            .inspect(this::checkNoOutlineFromFeature);

    // Check that parts of method1, ..., method4 in FeatureClass was not outlined.
    CodeInspector featureInspector = new CodeInspector(featureCode);
    ClassSubject featureClass = featureInspector.clazz(FeatureClass.class);
    assertThat(featureClass, isPresent());
    String outlineClassName =
        ClassNameMapper.mapperFromString(compileResult.getProguardMap())
            .getObfuscatedToOriginalMapping()
            .inverse
            .get(OutlineOptions.CLASS_NAME);
    assertFalse(invokesOutline(featureClass.uniqueMethodWithName("method1"), outlineClassName));
    assertFalse(invokesOutline(featureClass.uniqueMethodWithName("method2"), outlineClassName));
    assertFalse(invokesOutline(featureClass.uniqueMethodWithName("method3"), outlineClassName));
    assertFalse(invokesOutline(featureClass.uniqueMethodWithName("method4"), outlineClassName));

    // Run the code without the feature code present.
    compileResult.run(parameters.getRuntime(), TestClass.class).assertSuccessWithOutput("12");

    // Run the code with the feature code present.
    compileResult
        .addRunClasspathFiles(featureCode)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput("123456");
  }

  public static class TestClass {

    public static void main(String[] args) throws Exception {
      method1(1, 2);
      if (FeatureAPI.hasFeature()) {
        FeatureAPI.feature(3);
      }
    }

    public static void method1(int i1, int i2) {
      System.out.print(i1 + "" + i2);
    }

    public static void method2(int i1, int i2) {
      System.out.print(i1 + "" + i2);
    }
  }

  public static class FeatureAPI {
    private static String featureClassName() {
      return FeatureAPI.class.getPackage().getName() + ".B149971007$FeatureClass";
    }

    public static boolean hasFeature() {
      try {
        Class.forName(featureClassName());
      } catch (ClassNotFoundException e) {
        return false;
      }
      return true;
    }

    public static void feature(int i)
        throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
            IllegalAccessException {
      Class<?> featureClass = Class.forName(featureClassName());
      Method featureMethod = featureClass.getMethod("feature", int.class);
      featureMethod.invoke(null, i);
    }
  }

  public static class FeatureClass {
    private int i;

    FeatureClass(int i) {
      this.i = i;
    }

    public int getI() {
      return i;
    }

    public static void feature(int i) {
      method1(i, i + 1);
      method3(new FeatureClass(i + 2), new FeatureClass(i + 3));
    }

    public static void method1(int i1, int i2) {
      System.out.print(i1 + "" + i2);
    }

    public static void method2(int i1, int i2) {
      System.out.print(i1 + "" + i2);
    }

    public static void method3(FeatureClass fc1, FeatureClass fc2) {
      System.out.print(fc1.getI() + "" + fc2.getI());
    }

    public static void method4(FeatureClass fc1, FeatureClass fc2) {
      System.out.print(fc1.getI() + "" + fc2.getI());
    }
  }
}
