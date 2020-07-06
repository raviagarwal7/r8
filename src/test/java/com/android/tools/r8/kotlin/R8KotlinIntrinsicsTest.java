// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class R8KotlinIntrinsicsTest extends AbstractR8KotlinTestBase {

  private static final TestKotlinDataClass KOTLIN_INTRINSICS_CLASS =
      new TestKotlinDataClass("kotlin.jvm.internal.Intrinsics");

  @Parameterized.Parameters(name = "target: {0}, allowAccessModification: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(KotlinTargetVersion.values(), BooleanUtils.values());
  }

  public R8KotlinIntrinsicsTest(
      KotlinTargetVersion targetVersion, boolean allowAccessModification) {
    super(targetVersion, allowAccessModification);
  }

  @Test
  public void testParameterNullCheckIsInlined() throws Exception {
    final String extraRules = keepClassMethod("intrinsics.IntrinsicsKt",
        new MethodSignature("expectsNonNullParameters",
            "java.lang.String", Lists.newArrayList("java.lang.String", "java.lang.String")));

    runTest("intrinsics", "intrinsics.IntrinsicsKt", extraRules, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject intrinsicsClass = checkClassIsKept(
          codeInspector, KOTLIN_INTRINSICS_CLASS.getClassName());

      checkMethodsPresence(intrinsicsClass,
          ImmutableMap.<MethodSignature, Boolean>builder()
              .put(new MethodSignature("throwParameterIsNullException",
                      "void", Collections.singletonList("java.lang.String")),
                  true)
              .put(new MethodSignature("checkParameterIsNotNull",
                      "void", Lists.newArrayList("java.lang.Object", "java.lang.String")),
                  allowAccessModification ? false /* should be inlined*/ : true)
              .build());
    });
  }
}
