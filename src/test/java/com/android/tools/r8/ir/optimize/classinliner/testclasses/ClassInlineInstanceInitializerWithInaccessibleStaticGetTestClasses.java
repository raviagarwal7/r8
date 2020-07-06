// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.testclasses;

import com.android.tools.r8.NeverMerge;

public class ClassInlineInstanceInitializerWithInaccessibleStaticGetTestClasses {

  @NeverMerge
  public static class CandidateBase {

    public final String f;

    public CandidateBase() {
      if (Environment.VALUE) {
        f = "Hello";
      } else {
        f = " world!";
      }
    }
  }

  public static class Environment {

    /*package-private*/ static boolean VALUE;

    public static void setValue(boolean value) {
      VALUE = value;
    }
  }
}
