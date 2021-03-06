// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexType;

public class TypeSubject extends Subject {

  private final CodeInspector codeInspector;
  private final DexType dexType;

  TypeSubject(CodeInspector codeInspector, DexType dexType) {
    this.codeInspector = codeInspector;
    this.dexType = dexType;
  }

  @Override
  public boolean isPresent() {
    return true;
  }

  @Override
  public boolean isRenamed() {
    throw new Unreachable("Cannot determine if a type is renamed");
  }

  @Override
  public boolean isSynthetic() {
    throw new Unreachable("Cannot determine if a type is synthetic");
  }

  public boolean is(String type) {
    return dexType.equals(codeInspector.toDexType(type));
  }

  public boolean is(TypeSubject type) {
    return dexType == type.dexType;
  }

  public String toString() {
    return dexType.toSourceString();
  }
}
