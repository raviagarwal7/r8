// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.code.Aput;
import com.android.tools.r8.code.AputBoolean;
import com.android.tools.r8.code.AputByte;
import com.android.tools.r8.code.AputChar;
import com.android.tools.r8.code.AputObject;
import com.android.tools.r8.code.AputShort;
import com.android.tools.r8.code.AputWide;
import com.android.tools.r8.code.ArrayLength;
import com.android.tools.r8.code.CheckCast;
import com.android.tools.r8.code.Const;
import com.android.tools.r8.code.Const16;
import com.android.tools.r8.code.Const4;
import com.android.tools.r8.code.ConstClass;
import com.android.tools.r8.code.ConstHigh16;
import com.android.tools.r8.code.ConstString;
import com.android.tools.r8.code.ConstStringJumbo;
import com.android.tools.r8.code.ConstWide;
import com.android.tools.r8.code.ConstWide16;
import com.android.tools.r8.code.ConstWide32;
import com.android.tools.r8.code.ConstWideHigh16;
import com.android.tools.r8.code.Goto;
import com.android.tools.r8.code.IfEq;
import com.android.tools.r8.code.IfEqz;
import com.android.tools.r8.code.IfGe;
import com.android.tools.r8.code.IfGez;
import com.android.tools.r8.code.IfGt;
import com.android.tools.r8.code.IfGtz;
import com.android.tools.r8.code.IfLe;
import com.android.tools.r8.code.IfLez;
import com.android.tools.r8.code.IfLt;
import com.android.tools.r8.code.IfLtz;
import com.android.tools.r8.code.IfNe;
import com.android.tools.r8.code.IfNez;
import com.android.tools.r8.code.Iget;
import com.android.tools.r8.code.IgetBoolean;
import com.android.tools.r8.code.IgetByte;
import com.android.tools.r8.code.IgetChar;
import com.android.tools.r8.code.IgetObject;
import com.android.tools.r8.code.IgetShort;
import com.android.tools.r8.code.IgetWide;
import com.android.tools.r8.code.InstanceOf;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.InvokeCustom;
import com.android.tools.r8.code.InvokeCustomRange;
import com.android.tools.r8.code.InvokeDirect;
import com.android.tools.r8.code.InvokeDirectRange;
import com.android.tools.r8.code.InvokeInterface;
import com.android.tools.r8.code.InvokeInterfaceRange;
import com.android.tools.r8.code.InvokeStatic;
import com.android.tools.r8.code.InvokeStaticRange;
import com.android.tools.r8.code.InvokeSuper;
import com.android.tools.r8.code.InvokeSuperRange;
import com.android.tools.r8.code.InvokeVirtual;
import com.android.tools.r8.code.InvokeVirtualRange;
import com.android.tools.r8.code.Iput;
import com.android.tools.r8.code.IputBoolean;
import com.android.tools.r8.code.IputByte;
import com.android.tools.r8.code.IputChar;
import com.android.tools.r8.code.IputObject;
import com.android.tools.r8.code.IputShort;
import com.android.tools.r8.code.IputWide;
import com.android.tools.r8.code.MonitorEnter;
import com.android.tools.r8.code.MonitorExit;
import com.android.tools.r8.code.MulDouble;
import com.android.tools.r8.code.MulDouble2Addr;
import com.android.tools.r8.code.MulFloat;
import com.android.tools.r8.code.MulFloat2Addr;
import com.android.tools.r8.code.MulInt;
import com.android.tools.r8.code.MulInt2Addr;
import com.android.tools.r8.code.MulIntLit16;
import com.android.tools.r8.code.MulIntLit8;
import com.android.tools.r8.code.MulLong;
import com.android.tools.r8.code.MulLong2Addr;
import com.android.tools.r8.code.NewArray;
import com.android.tools.r8.code.NewInstance;
import com.android.tools.r8.code.Nop;
import com.android.tools.r8.code.PackedSwitch;
import com.android.tools.r8.code.Return;
import com.android.tools.r8.code.ReturnObject;
import com.android.tools.r8.code.ReturnVoid;
import com.android.tools.r8.code.Sget;
import com.android.tools.r8.code.SgetBoolean;
import com.android.tools.r8.code.SgetByte;
import com.android.tools.r8.code.SgetChar;
import com.android.tools.r8.code.SgetObject;
import com.android.tools.r8.code.SgetShort;
import com.android.tools.r8.code.SgetWide;
import com.android.tools.r8.code.SparseSwitch;
import com.android.tools.r8.code.Sput;
import com.android.tools.r8.code.SputBoolean;
import com.android.tools.r8.code.SputByte;
import com.android.tools.r8.code.SputChar;
import com.android.tools.r8.code.SputObject;
import com.android.tools.r8.code.SputShort;
import com.android.tools.r8.code.SputWide;
import com.android.tools.r8.code.Throw;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.code.SingleConstant;
import com.android.tools.r8.ir.code.WideConstant;

public class DexInstructionSubject implements InstructionSubject {

  protected final Instruction instruction;
  protected final MethodSubject method;

  public DexInstructionSubject(Instruction instruction, MethodSubject method) {
    this.instruction = instruction;
    this.method = method;
  }

  @Override
  public DexInstructionSubject asDexInstruction() {
    return this;
  }

  @Override
  public boolean isFieldAccess() {
    return isInstanceGet() || isInstancePut() || isStaticGet() || isStaticPut();
  }

  @Override
  public boolean isInstanceGet() {
    return instruction instanceof Iget
        || instruction instanceof IgetBoolean
        || instruction instanceof IgetByte
        || instruction instanceof IgetShort
        || instruction instanceof IgetChar
        || instruction instanceof IgetWide
        || instruction instanceof IgetObject;
  }

  @Override
  public boolean isInstancePut() {
    return instruction instanceof Iput
        || instruction instanceof IputBoolean
        || instruction instanceof IputByte
        || instruction instanceof IputShort
        || instruction instanceof IputChar
        || instruction instanceof IputWide
        || instruction instanceof IputObject;
  }

  @Override
  public boolean isStaticGet() {
    return instruction instanceof Sget
        || instruction instanceof SgetBoolean
        || instruction instanceof SgetByte
        || instruction instanceof SgetShort
        || instruction instanceof SgetChar
        || instruction instanceof SgetWide
        || instruction instanceof SgetObject;
  }

  @Override
  public boolean isStaticPut() {
    return instruction instanceof Sput
        || instruction instanceof SputBoolean
        || instruction instanceof SputByte
        || instruction instanceof SputShort
        || instruction instanceof SputChar
        || instruction instanceof SputWide
        || instruction instanceof SputObject;
  }

  @Override
  public DexField getField() {
    assert isFieldAccess();
    return instruction.getField();
  }

  @Override
  public boolean isInvoke() {
    return isInvokeVirtual()
        || isInvokeInterface()
        || isInvokeDirect()
        || isInvokeSuper()
        || isInvokeStatic();
  }

  @Override
  public boolean isInvokeVirtual() {
    return instruction instanceof InvokeVirtual || instruction instanceof InvokeVirtualRange;
  }

  @Override
  public boolean isInvokeInterface() {
    return instruction instanceof InvokeInterface || instruction instanceof InvokeInterfaceRange;
  }

  @Override
  public boolean isInvokeStatic() {
    return instruction instanceof InvokeStatic || instruction instanceof InvokeStaticRange;
  }

  @Override
  public boolean isInvokeSpecial() {
    return false;
  }

  public boolean isInvokeCustom() {
    return instruction instanceof InvokeCustom || instruction instanceof InvokeCustomRange;
  }

  public boolean isInvokeSuper() {
    return instruction instanceof InvokeSuper || instruction instanceof InvokeSuperRange;
  }

  public boolean isInvokeDirect() {
    return instruction instanceof InvokeDirect || instruction instanceof InvokeDirectRange;
  }

  @Override
  public DexMethod getMethod() {
    assert isInvoke();
    return instruction.getMethod();
  }

  @Override
  public boolean isNop() {
    return instruction instanceof Nop;
  }

  @Override
  public boolean isConstNumber() {
    return instruction instanceof Const
        || instruction instanceof Const4
        || instruction instanceof Const16
        || instruction instanceof ConstHigh16
        || instruction instanceof ConstWide
        || instruction instanceof ConstWide16
        || instruction instanceof ConstWide32
        || instruction instanceof ConstWideHigh16;
  }

  @Override
  public boolean isConstNumber(long value) {
    return isConstNumber() && getConstNumber() == value;
  }

  @Override
  public boolean isConstNull() {
    return isConst4() && isConstNumber(0);
  }

  @Override
  public boolean isConstString(JumboStringMode jumboStringMode) {
    return instruction instanceof ConstString
        || (jumboStringMode == JumboStringMode.ALLOW && instruction instanceof ConstStringJumbo);
  }

  @Override
  public boolean isConstString(String value, JumboStringMode jumboStringMode) {
    return (instruction instanceof ConstString
            && ((ConstString) instruction).BBBB.toSourceString().equals(value))
        || (jumboStringMode == JumboStringMode.ALLOW
            && instruction instanceof ConstStringJumbo
            && ((ConstStringJumbo) instruction).BBBBBBBB.toSourceString().equals(value));
  }

  @Override
  public boolean isJumboString() {
    return instruction instanceof ConstStringJumbo;
  }

  @Override public long getConstNumber() {
    assert isConstNumber();
    if (instruction instanceof SingleConstant) {
      return ((SingleConstant) instruction).decodedValue();
    }
    assert instruction instanceof WideConstant;
    return ((WideConstant) instruction).decodedValue();
  }

  @Override
  public String getConstString() {
    if (instruction instanceof ConstString) {
      return ((ConstString) instruction).BBBB.toSourceString();
    }
    if (instruction instanceof ConstStringJumbo) {
      return ((ConstStringJumbo) instruction).BBBBBBBB.toSourceString();
    }
    return null;
  }

  @Override
  public boolean isConstClass() {
    return instruction instanceof ConstClass;
  }

  @Override
  public boolean isConstClass(String type) {
    return isConstClass() && ((ConstClass) instruction).getType().toString().equals(type);
  }

  @Override
  public boolean isGoto() {

    return instruction instanceof Goto;
  }

  @Override
  public boolean isIfNez() {
    return instruction instanceof IfNez;
  }

  @Override
  public boolean isIfEqz() {
    return instruction instanceof IfEqz;
  }

  @Override
  public boolean isReturn() {
    return instruction instanceof Return;
  }

  @Override
  public boolean isReturnVoid() {
    return instruction instanceof ReturnVoid;
  }

  @Override
  public boolean isReturnObject() {
    return instruction instanceof ReturnObject;
  }

  @Override
  public boolean isThrow() {
    return instruction instanceof Throw;
  }

  @Override
  public boolean isNewInstance() {
    return instruction instanceof NewInstance;
  }

  @Override
  public boolean isNewInstance(String type) {
    return isNewInstance()
        && ((NewInstance) instruction).getType().toString().equals(type);
  }

  @Override
  public boolean isCheckCast() {
    return instruction instanceof CheckCast;
  }

  @Override
  public boolean isCheckCast(String type) {
    return isCheckCast() && ((CheckCast) instruction).getType().toString().equals(type);
  }

  @Override
  public boolean isInstanceOf() {
    return instruction instanceof InstanceOf;
  }

  @Override
  public boolean isInstanceOf(String type) {
    return isInstanceOf() && ((InstanceOf) instruction).getType().toString().equals(type);
  }

  public boolean isConst4() {
    return instruction instanceof Const4;
  }

  @Override
  public boolean isIf() {
    return instruction instanceof IfEq
        || instruction instanceof IfEqz
        || instruction instanceof IfGe
        || instruction instanceof IfGez
        || instruction instanceof IfGt
        || instruction instanceof IfGtz
        || instruction instanceof IfLe
        || instruction instanceof IfLez
        || instruction instanceof IfLt
        || instruction instanceof IfLtz
        || instruction instanceof IfNe
        || instruction instanceof IfNez;
  }

  @Override
  public boolean isSwitch() {
    return isPackedSwitch() || isSparseSwitch();
  }

  @Override
  public boolean isPackedSwitch() {
    return instruction instanceof PackedSwitch;
  }

  @Override
  public boolean isSparseSwitch() {
    return instruction instanceof SparseSwitch;
  }

  @Override
  public boolean isMultiplication() {
    return instruction instanceof MulInt
        || instruction instanceof MulIntLit8
        || instruction instanceof MulIntLit16
        || instruction instanceof MulInt2Addr
        || instruction instanceof MulFloat
        || instruction instanceof MulFloat2Addr
        || instruction instanceof MulLong
        || instruction instanceof MulLong2Addr
        || instruction instanceof MulDouble
        || instruction instanceof MulDouble2Addr;
  }

  @Override
  public boolean isNewArray() {
    return instruction instanceof NewArray;
  }

  @Override
  public boolean isArrayLength() {
    return instruction instanceof ArrayLength;
  }

  @Override
  public boolean isArrayPut() {
    return instruction instanceof Aput
        || instruction instanceof AputBoolean
        || instruction instanceof AputByte
        || instruction instanceof AputChar
        || instruction instanceof AputObject
        || instruction instanceof AputShort
        || instruction instanceof AputWide;
  }

  @Override
  public boolean isMonitorEnter() {
    return instruction instanceof MonitorEnter;
  }

  @Override
  public boolean isMonitorExit() {
    return instruction instanceof MonitorExit;
  }

  @Override
  public int size() {
    return instruction.getSize();
  }

  @Override
  public InstructionOffsetSubject getOffset(MethodSubject methodSubject) {
    return new InstructionOffsetSubject(instruction.getOffset());
  }

  @Override
  public MethodSubject getMethodSubject() {
    return method;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof DexInstructionSubject
        && instruction.equals(((DexInstructionSubject) other).instruction);
  }

  @Override
  public int hashCode() {
    return instruction.hashCode();
  }

  @Override
  public String toString() {
    return instruction.toString();
  }
}
