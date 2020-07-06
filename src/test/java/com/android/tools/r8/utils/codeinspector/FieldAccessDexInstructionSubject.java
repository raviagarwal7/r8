// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.code.Instruction;

public class FieldAccessDexInstructionSubject extends DexInstructionSubject
    implements FieldAccessInstructionSubject {

  private final CodeInspector codeInspector;

  public FieldAccessDexInstructionSubject(
      CodeInspector codeInspector, Instruction instruction, MethodSubject method) {
    super(instruction, method);
    this.codeInspector = codeInspector;
    assert isFieldAccess();
  }

  @Override
  public TypeSubject holder() {
    return new TypeSubject(codeInspector, instruction.getField().holder);
  }

  @Override
  public TypeSubject type() {
    return new TypeSubject(codeInspector, instruction.getField().type);
  }

  @Override
  public String name() {
    return instruction.getField().name.toString();
  }
}
