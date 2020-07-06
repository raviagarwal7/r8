// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.b130791310;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class TestClass {
  SomeLogic instance;

  public void create() {
    instance = new SomeLogic(new Object());
  }

  void caller() {
    if (instance.someMethod(new SomeClass()) == null) {
      System.out.println("TestMain#caller");
    }
  }

  public static void main(String[] args) {
    TestClass m = new TestClass();
    m.create();
    m.caller();
  }
}

class SomeLogic {
  Object otherField;

  public SomeLogic(Object o) {
    otherField = o;
  }

  public SomeInterface someMethod(SomeClass list) {
    System.out.println("list is" + ((list == null) ? "" : " not") + " null");
    return null;
  }
}

interface SomeInterface {
  byte foo();
}

@NeverClassInline
class SomeClass implements SomeInterface {
  @Override
  public byte foo() {
    return 0x8;
  }
}

// SomeInterface has a single implementer SomeClass: vertical merging candidate.
// If -keepnames specifies a member whose signature can be changed due to vertical merging, their
// names are not preserved because that rule allows shrinking (and optimization) implicitly.
// According to b/130791310, Proguard (via AGP) still keeps that name, but this reproduction shows
// that both R8 and Proguard ignores -keepnames. It turns out that, as per
// https://android.googlesource.com/platform/sdk/+/master/files/proguard-android-optimize.txt#13
// class merging is disabled for Proguard.
@RunWith(Parameterized.class)
public class B130791310 extends TestBase {
  private static final Class<?> MAIN = TestClass.class;
  private static final List<Class<?>> CLASSES =
      ImmutableList.of(MAIN, SomeLogic.class, SomeInterface.class, SomeClass.class);
  private static final String RULES = StringUtils.lines(
      "-keepnames class **.SomeLogic {",
      "  **.SomeInterface someMethod(**.SomeClass);",
      "}"
  );

  private final boolean enableClassMerging;
  private final boolean onlyForceInlining;
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{2}, enable class merging: {0}, only force inlining: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        BooleanUtils.values(),
        getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public B130791310(
      boolean enableClassMerging, boolean onlyForceInlining, TestParameters parameters) {
    this.enableClassMerging = enableClassMerging;
    this.onlyForceInlining = onlyForceInlining;
    this.parameters = parameters;
  }

  private void inspect(CodeInspector inspector, boolean isR8) {
    ClassSubject holder = inspector.clazz(SomeLogic.class);
    assertThat(holder, isPresentAndNotRenamed());
    MethodSubject someMethod = holder.uniqueMethodWithName("someMethod");
    if (isR8) {
      if (onlyForceInlining) {
        assertThat(someMethod, isPresentAndNotRenamed());
      } else {
        assertThat(someMethod, not(isPresent()));
      }
    } else {
      if (enableClassMerging) {
        // Note that the method is not entirely gone, but merged to the implementer, along with some
        // method signature modification.
        assertThat(someMethod, not(isPresent()));
      } else {
        assertThat(someMethod, isPresentAndNotRenamed());
      }
    }
  }

  @Test
  public void testProguard() throws Exception {
    assumeFalse(onlyForceInlining);
    assumeTrue(parameters.isCfRuntime());
    testForProguard()
        .addProgramClasses(CLASSES)
        .addKeepClassAndMembersRules(MAIN)
        .addKeepRules(RULES)
        .addTestingAnnotationsAsProgramClasses()
        .setMinApi(parameters.getApiLevel())
        .apply(
            builder -> {
              if (!enableClassMerging) {
                builder.addKeepRules("-optimizations !class/merging/*");
              }
            })
        .compile()
        .inspect(inspector -> inspect(inspector, false));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(CLASSES)
        .addKeepClassAndMembersRules(MAIN)
        .addKeepRules(RULES)
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(o -> o.enableVerticalClassMerging = enableClassMerging)
        .apply(
            builder -> {
              if (onlyForceInlining) {
                builder.addOptionsModification(
                    o -> o.testing.validInliningReasons = ImmutableSet.of(Reason.FORCE));
              }
            })
        .compile()
        .inspect(inspector -> inspect(inspector, true));
  }
}
