// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer.trivial;

import com.android.tools.r8.NeverInline;

public class SimpleWithGetter {
  private static SimpleWithGetter INSTANCE = new SimpleWithGetter();

  static SimpleWithGetter getInstance() {
    return INSTANCE;
  }

  @NeverInline
  String foo() {
    return bar("SimpleWithGetter::foo()");
  }

  @NeverInline
  String bar(String other) {
    return "SimpleWithGetter::bar(" + other + ")";
  }
}
