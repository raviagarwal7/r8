// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retrace;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.Before;

public abstract class RetraceTestBase extends TestBase {
  protected TestParameters parameters;
  protected CompilationMode mode;
  protected boolean compat;

  public RetraceTestBase(TestParameters parameters, CompilationMode mode, boolean compat) {
    this.parameters = parameters;
    this.mode = mode;
    this.compat = compat;
  }

  public StackTrace expectedStackTrace;

  public void configure(R8TestBuilder<?> builder) {}

  public void inspect(CodeInspector inspector) {}

  public Collection<Class<?>> getClasses() {
    return ImmutableList.of(getMainClass());
  }

  public abstract Class<?> getMainClass();

  @Before
  public void setup() throws Exception {
    // Get the expected stack trace by running on the JVM.
    expectedStackTrace =
        testForJvm()
            .addTestClasspath()
            .run(CfRuntime.getSystemRuntime(), getMainClass())
            .assertFailure()
            .map(StackTrace::extractFromJvm);
  }

  public void runTest(List<String> keepRules, BiConsumer<StackTrace, StackTrace> checker)
      throws Exception {

    R8TestRunResult result =
        (compat ? testForR8Compat(parameters.getBackend()) : testForR8(parameters.getBackend()))
            .setMode(mode)
            .addProgramClasses(getClasses())
            .addKeepMainRule(getMainClass())
            .addKeepRules(keepRules)
            .apply(this::configure)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .inspect(this::inspect)
            .run(parameters.getRuntime(), getMainClass())
            .assertFailure();

    // Extract actual stack trace and retraced stack trace from failed run result.
    StackTrace actualStackTrace;
    if (parameters.isCfRuntime()) {
      actualStackTrace = StackTrace.extractFromJvm(result.getStdErr());
    } else {
      actualStackTrace =
          StackTrace.extractFromArt(result.getStdErr(), parameters.getRuntime().asDex().getVm());
    }
    StackTrace retracedStackTrace = actualStackTrace.retrace(result.proguardMap());
    checker.accept(actualStackTrace, retracedStackTrace);
  }
}
