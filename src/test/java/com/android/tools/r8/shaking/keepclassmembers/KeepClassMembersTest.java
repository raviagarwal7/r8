// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.keepclassmembers;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbstract;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.shaking.forceproguardcompatibility.ProguardCompatibilityTestBase;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KeepClassMembersTest extends ProguardCompatibilityTestBase {

  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return ToolHelper.getBackends();
  }

  public KeepClassMembersTest(Backend backend) {
    this.backend = backend;
  }

  private void check(CodeInspector inspector, Class mainClass, Class<?> staticClass,
      boolean forceProguardCompatibility, boolean fromProguard) {
    assertTrue(inspector.clazz(mainClass).isPresent());
    ClassSubject staticClassSubject = inspector.clazz(staticClass);
    assertThat(staticClassSubject, isPresent());
    assertThat(staticClassSubject.method("int", "getA", ImmutableList.of()), isPresent());
    assertThat(staticClassSubject.method("int", "getB", ImmutableList.of()), not(isPresent()));
    assertThat(staticClassSubject.field("int", "a"), isPresent());
    assertThat(staticClassSubject.field("int", "b"), isPresent());
    assertThat(staticClassSubject.field("int", "c"), not(isPresent()));
    // Neither Proguard not R8 keeps any constructors.
    staticClassSubject.forAllMethods(method -> assertFalse(method.isInstanceInitializer()));
    assertThat(staticClassSubject.init(ImmutableList.of()), not(isPresent()));
    MethodSubject getIMethod = staticClassSubject.method("int", "getI", ImmutableList.of());
    FieldSubject iField = staticClassSubject.field("int", "i");
    if (forceProguardCompatibility) {
      if (fromProguard) {
        // Proguard keeps the instance method and it code even though the class does not have a
        // constructor, and therefore cannot be instantiated.
        assertThat(getIMethod, isPresent());
        assertThat(iField, isPresent());
      } else {
        // Force Proguard compatibility keeps the instance method, even though the class does
        // not have a constructor.
        assertThat(getIMethod, not(isAbstract()));
        // As the method is abstract the referenced field is not present.
        assertThat(iField, not(isPresent()));
      }
    } else {
      assertThat(getIMethod, not(isPresent()));
      assertThat(iField, not(isPresent()));
    }
    assertThat(staticClassSubject.method("int", "getJ", ImmutableList.of()), not(isPresent()));
    assertThat(staticClassSubject.field("int", "j"), not(isPresent()));
  }

  private void runTest(Class mainClass, Class<?> staticClass,
      boolean forceProguardCompatibility) throws Exception {
    String proguardConfig = String.join("\n", ImmutableList.of(
        "-keepclassmembers class **.PureStatic* {",
        "  public static int b;",
        "  public static int getA();",
        "}",
        "-keep class **.MainUsing* {",
        "  public static void main(java.lang.String[]);",
        "}",
        "-dontoptimize", "-dontobfuscate"
    ));
    CodeInspector inspector;
    inspector =
        new CodeInspector(
            compileWithR8(
                readClasses(ImmutableList.of(mainClass, staticClass)),
                proguardConfig,
                options -> options.forceProguardCompatibility = forceProguardCompatibility,
                backend));
    check(inspector, mainClass, staticClass, forceProguardCompatibility, false);

    if (isRunProguard()) {
      inspector = inspectProguard6Result(ImmutableList.of(mainClass, staticClass), proguardConfig);
      check(inspector, mainClass, staticClass, true, true);
    }
  }

  @Test
  public void regress69028743() throws Exception {
    runTest(MainUsingWithDefaultConstructor.class,
        PureStaticClassWithDefaultConstructor.class, false);
    runTest(MainUsingWithDefaultConstructor.class,
        PureStaticClassWithDefaultConstructor.class, true);
    runTest(MainUsingWithoutDefaultConstructor.class,
        PureStaticClassWithoutDefaultConstructor.class, false);
    runTest(MainUsingWithoutDefaultConstructor.class,
        PureStaticClassWithoutDefaultConstructor.class, true);
  }
}
