// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.util.concurrent.ExecutionException;

public class ProguardTestRunResult extends TestRunResult<ProguardTestRunResult> {

  private final String proguardMap;

  public ProguardTestRunResult(
      AndroidApp app, TestRuntime runtime, ProcessResult result, String proguardMap) {
    super(app, runtime, result);
    this.proguardMap = proguardMap;
  }

  @Override
  protected ProguardTestRunResult self() {
    return this;
  }

  @Override
  public StackTrace getStackTrace() {
    return super.getStackTrace().retrace(proguardMap);
  }

  public StackTrace getOriginalStackTrace() {
    return super.getStackTrace();
  }

  @Override
  public CodeInspector inspector() throws IOException, ExecutionException {
    // See comment in base class.
    assertSuccess();
    assertNotNull(app);
    return new CodeInspector(app, proguardMap);
  }

  public <E extends Throwable> ProguardTestRunResult inspectOriginalStackTrace(
      ThrowingConsumer<StackTrace, E> consumer) throws E {
    consumer.accept(getOriginalStackTrace());
    return self();
  }
}
