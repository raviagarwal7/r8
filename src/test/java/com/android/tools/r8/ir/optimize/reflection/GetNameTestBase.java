// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.reflection;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Streams;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public abstract class GetNameTestBase extends TestBase {
  static final String CLASS_DESCRIPTOR = "Ljava/lang/Class;";
  static final String STRING_DESCRIPTOR = "Ljava/lang/String;";

  protected final TestParameters parameters;
  protected final boolean enableMinification;
  Path mapping;

  @Parameterized.Parameters(name = "{0} minification: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public GetNameTestBase(TestParameters parameters, boolean enableMinification) {
    this.parameters = parameters;
    this.enableMinification = enableMinification;
  }

  protected void configure(InternalOptions options) {
    options.testing.forceNameReflectionOptimization = true;
  }

  Path createNewMappingPath() throws IOException {
    mapping = temp.newFile(ToolHelper.DEFAULT_PROGUARD_MAP_FILE).toPath();
    return mapping;
  }

  static boolean isNameReflection(DexMethod method) {
    return method.holder.toDescriptorString().equals(CLASS_DESCRIPTOR)
        && method.getArity() == 0
        && method.proto.returnType.toDescriptorString().equals(STRING_DESCRIPTOR)
        && method.name.toString().startsWith("get")
        && method.name.toString().endsWith("Name");
  }

  protected static long countGetName(MethodSubject method) {
    return Streams.stream(method.iterateInstructions(instructionSubject -> {
      if (instructionSubject.isInvoke()) {
        return isNameReflection(instructionSubject.getMethod());
      }
      return false;
    })).count();
  }
}
