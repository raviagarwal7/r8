// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;

public class B87341268 extends TestBase {
  @Test
  public void test() throws Exception {
    AndroidApp app = compileWithD8(readClasses(TestClassForB87341268.class));
    CodeInspector inspector = new CodeInspector(app);
    ClassSubject clazz = inspector.clazz(TestClassForB87341268.class);
    assertThat(clazz, isPresent());
  }
}

class TestClassForB87341268 {
  int loop(String arg) {
    long[] array = { 0L, 1L, 2L };
    int length = -1;
    while (true) {
      try {
        length = arg.length();
      } catch (Exception e) {
        System.err.println(e.getMessage());
        break;
      }
    }
    return length;
  }
}
