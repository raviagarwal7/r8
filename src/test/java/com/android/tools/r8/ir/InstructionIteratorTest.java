// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir;

import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.smali.SmaliBuilder.MethodSignature;
import com.android.tools.r8.smali.SmaliTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.ListIterator;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class InstructionIteratorTest extends SmaliTestBase {
  @Rule
  public ExpectedException thrown = ExpectedException.none();

  /**
   * Simple test IR, which has three blocks:
   *
   * <p>First block: Argument instructions Second block: Add instruction Third block: Return
   * instruction
   */
  private IRCode simpleCode() {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    String returnType = "int";
    List<String> parameters = ImmutableList.of("int", "int");
    MethodSignature signature = builder.addStaticMethod(
        returnType,
        DEFAULT_METHOD_NAME,
        parameters,
        1,
        "    add-int             v0, p0, p1",
        "    return              p0"
    );

    AndroidApp application = buildApplication(builder);

    // Build the code, and split the code into three blocks.
    MethodSubject methodSubject = getMethodSubject(application, signature);
    IRCode code = methodSubject.buildIR();
    ListIterator<BasicBlock> blocks = code.listIterator();
    InstructionListIterator iter = blocks.next().listIterator(code);
    iter.nextUntil(i -> !i.isArgument());
    iter.previous();
    iter.split(code, 1, blocks);
    return code;
  }

  @Test
  public void removeBeforeNext() {
    IRCode code = simpleCode();

    ListIterator<BasicBlock> blocks = code.listIterator();
    InstructionListIterator instructions = blocks.next().listIterator(code);
    thrown.expect(IllegalStateException.class);
    instructions.remove();
  }

  @Test
  public void removeTwice() {
    IRCode code = simpleCode();

    ListIterator<BasicBlock> blocks = code.listIterator();
    blocks.next();
    InstructionListIterator instructions = blocks.next().listIterator(code);
    instructions.next();
    instructions.remove();
    thrown.expect(IllegalStateException.class);
    instructions.remove();
  }
}
