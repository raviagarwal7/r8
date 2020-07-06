// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShaking17Test extends TreeShakingTest {

  @Parameters(name = "mode:{0}-{1} minify:{2}")
  public static Collection<Object[]> data() {
    List<Object[]> parameters = new ArrayList<>();
    for (MinifyMode minify : MinifyMode.values()) {
      parameters.add(new Object[] {Frontend.JAR, Backend.CF, minify});
      parameters.add(new Object[] {Frontend.JAR, Backend.DEX, minify});
      parameters.add(new Object[] {Frontend.DEX, Backend.DEX, minify});
    }
    return parameters;
  }

  public TreeShaking17Test(Frontend frontend, Backend backend, MinifyMode minify) {
    super("examples/shaking17", "shaking17.Shaking", frontend, backend, minify);
  }

  @Test
  public void test() throws Exception {
    runTest(
        TreeShaking17Test::abstractMethodRemains,
        null,
        null,
        ImmutableList.of("src/test/examples/shaking17/keep-rules.txt"));
  }

  private static void abstractMethodRemains(CodeInspector inspector) {
    ClassSubject programClass = inspector.clazz("shaking17.AbstractProgramClass");
    Assert.assertTrue(programClass.isPresent());
    // With call site optimization, the dynamic type of the argument of Shaking#callTheMethod is
    // SubClass, not AbstractProgramClass. Then, the resolution of the invocation is accurately
    // referring to SubClass#abstractMethod only, i.e., AbstractProgramClass#abstractMethod is no
    // longer live, hence shrunken.
    Assert.assertFalse(
        programClass.method("int", "abstractMethod", Collections.emptyList()).isPresent());
  }
}
