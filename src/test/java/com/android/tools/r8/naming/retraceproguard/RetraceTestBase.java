// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retraceproguard;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.Before;

public abstract class RetraceTestBase extends TestBase {
  protected Backend backend;
  protected CompilationMode mode;
  protected boolean compat;

  public RetraceTestBase(Backend backend, CompilationMode mode, boolean compat) {
    this.backend = backend;
    this.mode = mode;
    this.compat = compat;
  }

  public StackTrace expectedStackTrace;

  public void configure(R8TestBuilder builder) {}

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
            .run(getMainClass())
            .assertFailure()
            .map(StackTrace::extractFromJvm);
  }

  public void runTest(List<String> keepRules, BiConsumer<StackTrace, StackTrace> checker)
      throws Exception {

    R8TestRunResult result =
        (compat ? testForR8Compat(backend) : testForR8(backend))
            .setMode(mode)
            .enableProguardTestOptions()
            .addProgramClasses(getClasses())
            .addKeepMainRule(getMainClass())
            .addKeepRules(keepRules)
            .apply(this::configure)
            .run(getMainClass())
            .assertFailure();

    // Extract actual stack trace and retraced stack trace from failed run result.
    // TODO(122940268): Remove test code when fixed.
    System.out.println("<--- TEST RESULT START --->");
    System.out.println(result);
    System.out.println("<--- TEST RESULT END --->");
    StackTrace actualStackTrace = StackTrace.extractFromArt(result.getStdErr());
    StackTrace retracedStackTrace =
        actualStackTrace.retrace(result.proguardMap(), temp.newFolder().toPath());

    checker.accept(actualStackTrace, retracedStackTrace);
  }
}
