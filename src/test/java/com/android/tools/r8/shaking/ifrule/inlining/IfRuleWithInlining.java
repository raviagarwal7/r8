// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.inlining;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.shaking.forceproguardcompatibility.ProguardCompatibilityTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

class A {
  static public int a() {
    try {
      String p = A.class.getPackage().getName();
      Class.forName(p + ".D");
    } catch (ClassNotFoundException e) {
      return 2;
    }
    return 1;
  }
}

class D {
}

class Main {
  public static void main(String[] args) {
    System.out.print("" + A.a());
  }
}

@RunWith(Parameterized.class)
public class IfRuleWithInlining extends ProguardCompatibilityTestBase {
  private static final List<Class<?>> CLASSES = ImmutableList.of(A.class, D.class, Main.class);

  private final Shrinker shrinker;
  private final boolean neverInlineMethod;

  public IfRuleWithInlining(Shrinker shrinker, boolean neverInlineMethod) {
    this.shrinker = shrinker;
    this.neverInlineMethod = neverInlineMethod;
  }

  @Parameters(name = "shrinker: {0} neverInlineMethod: {1}")
  public static Collection<Object[]> data() {
    // We don't run this on Proguard, as triggering inlining in Proguard is out of our control.
    return buildParameters(
        ImmutableList.of(Shrinker.R8, Shrinker.R8_CF), BooleanUtils.values());
  }

  private void check(AndroidApp app) throws Exception {
    CodeInspector inspector = new CodeInspector(app);
    ClassSubject clazzA = inspector.clazz(A.class);
    assertThat(clazzA, isPresent());
    // A.a should not be inlined.
    assertThat(clazzA.method("int", "a", ImmutableList.of()), isPresent());
    assertThat(inspector.clazz(D.class), isPresent());
    ProcessResult result;
    if (shrinker == Shrinker.R8) {
      result = runOnArtRaw(app, Main.class.getName());
    } else {
      assert shrinker == Shrinker.R8_CF;
      Path file = File.createTempFile("junit", ".zip", temp.getRoot()).toPath();
      app.writeToZip(file, OutputMode.ClassFile);
      result = ToolHelper.runJava(file, Main.class.getName());
    }
    assertEquals(0, result.exitCode);
    assertEquals("1", result.stdout);
  }

  @Test
  public void testMergedClassMethodInIfRule() throws Exception {
    List<String> config =
        ImmutableList.of(
            "-keep class **.Main { public static void main(java.lang.String[]); }",
            neverInlineMethod ? "-neverinline class **.A { int a(); }" : "",
            "-if class **.A { static int a(); }",
            "-keep class **.D",
            "-dontobfuscate");
    check(runShrinker(shrinker, CLASSES, config));
  }
}
