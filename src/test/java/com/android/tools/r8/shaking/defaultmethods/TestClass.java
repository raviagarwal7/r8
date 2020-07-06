// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.defaultmethods;

public class TestClass {

  public void useInterfaceMethod() {
    InterfaceWithDefaultMethods iface =
        System.currentTimeMillis() >= 0
            ? new ClassImplementingInterface()
            : new OtherClassImplementingInterface();
    System.out.println(iface.method());
  }

  public static void main(String[] args) {
  }
}
