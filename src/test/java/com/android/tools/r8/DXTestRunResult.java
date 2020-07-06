// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApp;

public class DXTestRunResult extends TestRunResult<DXTestRunResult> {

  public DXTestRunResult(AndroidApp app, TestRuntime runtime, ProcessResult result) {
    super(app, runtime, result);
  }

  @Override
  protected DXTestRunResult self() {
    return this;
  }
}
