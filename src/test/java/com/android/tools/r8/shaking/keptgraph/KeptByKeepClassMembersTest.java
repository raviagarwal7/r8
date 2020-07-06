// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keptgraph;

import com.android.tools.r8.NeverInline;

public class KeptByKeepClassMembersTest {

  public static void main(String[] args) {
    bar();
  }

  @NeverInline
  static void bar() {
    System.out.println("called bar");
  }

  @NeverInline
  static void baz() {
    System.out.println("called baz");
  }
}
