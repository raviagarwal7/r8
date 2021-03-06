// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.singletarget.two;

public class OtherSubSubClassTwo extends OtherAbstractSubClassTwo {

  @Override
  public void overriddenInTwoSubTypes() {
    System.out.println(OtherSubSubClassTwo.class.getCanonicalName());
  }

  @Override
  public void abstractOverriddenInTwoSubTypes() {
    System.out.println(OtherSubSubClassTwo.class.getCanonicalName());
  }
}
