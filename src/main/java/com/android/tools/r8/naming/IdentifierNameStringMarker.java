// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.naming.IdentifierNameStringUtils.identifyIdentifier;
import static com.android.tools.r8.naming.IdentifierNameStringUtils.inferMemberOrTypeFromNameString;
import static com.android.tools.r8.naming.IdentifierNameStringUtils.isReflectionMethod;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexItemBasedValueString;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlock.ThrowingInfo;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.TextPosition;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.Streams;
import it.unimi.dsi.fastutil.objects.Object2BooleanMap;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.stream.Collectors;

public class IdentifierNameStringMarker {

  private final AppView<AppInfoWithLiveness> appView;
  private final Object2BooleanMap<DexReference> identifierNameStrings;

  public IdentifierNameStringMarker(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    // Note that this info is only available at AppInfoWithLiveness.
    this.identifierNameStrings = appView.appInfo().identifierNameStrings;
  }

  public void decoupleIdentifierNameStringsInFields() {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      for (DexEncodedField field : clazz.staticFields()) {
        decoupleIdentifierNameStringInStaticField(field);
      }
    }
  }

  private void decoupleIdentifierNameStringInStaticField(DexEncodedField encodedField) {
    assert encodedField.accessFlags.isStatic();
    if (!identifierNameStrings.containsKey(encodedField.field)) {
      return;
    }
    DexValue staticValue = encodedField.getStaticValue();
    if (!(staticValue instanceof DexValueString)) {
      return;
    }
    DexString original = ((DexValueString) staticValue).getValue();
    DexReference itemBasedString = inferMemberOrTypeFromNameString(appView, original);
    if (itemBasedString != null) {
      encodedField.setStaticValue(new DexItemBasedValueString(itemBasedString));
    }
  }

  public void decoupleIdentifierNameStringsInMethod(DexEncodedMethod encodedMethod, IRCode code) {
    if (!code.hasConstString) {
      return;
    }
    ThrowingInfo throwingInfo = ThrowingInfo.defaultForConstString(appView.options());
    DexType originHolder = code.method.method.holder;
    ListIterator<BasicBlock> blocks = code.listIterator();
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      InstructionListIterator iterator = block.listIterator();
      while (iterator.hasNext()) {
        Instruction instruction = iterator.next();
        // v_n <- "x.y.z" // in.definition
        // ...
        // ... <- ... v_n ..
        // ...
        // this.fld <- v_n // fieldPut
        //
        //   ~>
        //
        // ...
        // v_n' <- DexItemBasedString("Lx/y/z;") // decoupled
        // this.fld <- v_n' // fieldPut
        if (instruction.isStaticPut() || instruction.isInstancePut()) {
          FieldInstruction fieldPut = instruction.asFieldInstruction();
          DexField field = fieldPut.getField();
          if (!identifierNameStrings.containsKey(field)) {
            continue;
          }
          Value in = instruction.isStaticPut()
              ? instruction.asStaticPut().inValue()
              : instruction.asInstancePut().value();
          if (!in.isConstString()) {
            warnUndeterminedIdentifierIfNecessary(field, originHolder, instruction, null);
            continue;
          }
          DexString original = in.getConstInstruction().asConstString().getValue();
          DexReference itemBasedString = inferMemberOrTypeFromNameString(appView, original);
          if (itemBasedString == null) {
            warnUndeterminedIdentifierIfNecessary(field, originHolder, instruction, original);
            continue;
          }
          // Move the cursor back to $fieldPut
          assert iterator.peekPrevious() == fieldPut;
          iterator.previous();
          // Prepare $decoupled just before $fieldPut
          Value newIn = code.createValue(in.getTypeLattice(), in.getLocalInfo());
          DexItemBasedConstString decoupled =
              new DexItemBasedConstString(newIn, itemBasedString, throwingInfo);
          decoupled.setPosition(fieldPut.getPosition());
          // If the current block has catch handler, split into two blocks.
          // Because const-string we're about to add is also a throwing instr, we need to split
          // before adding it.
          BasicBlock blockWithFieldInstruction =
              block.hasCatchHandlers() ? iterator.split(code, blocks) : block;
          if (blockWithFieldInstruction != block) {
            // If we split, add const-string at the end of the currently visiting block.
            iterator = block.listIterator(block.getInstructions().size());
            iterator.previous();
            iterator.add(decoupled);
            // Restore the cursor and block.
            iterator = blockWithFieldInstruction.listIterator();
            assert iterator.peekNext() == fieldPut;
            iterator.next();
            block = blockWithFieldInstruction;
          } else {
            // Otherwise, just add it to the current block at the position of the iterator.
            iterator.add(decoupled);
            // Restore the cursor.
            assert iterator.peekNext() == fieldPut;
            iterator.next();
          }
          if (instruction.isStaticPut()) {
            StaticPut staticPut = instruction.asStaticPut();
            iterator.replaceCurrentInstruction(new StaticPut(newIn, field));
          } else {
            assert instruction.isInstancePut();
            InstancePut instancePut = instruction.asInstancePut();
            iterator.replaceCurrentInstruction(new InstancePut(field, instancePut.object(), newIn));
          }
          encodedMethod.getMutableOptimizationInfo().markUseIdentifierNameString();
        } else if (instruction.isInvokeMethod()) {
          InvokeMethod invoke = instruction.asInvokeMethod();
          DexMethod invokedMethod = invoke.getInvokedMethod();
          if (!identifierNameStrings.containsKey(invokedMethod)) {
            continue;
          }
          List<Value> ins = invoke.arguments();
          Value[] changes = new Value [ins.size()];
          if (isReflectionMethod(appView.dexItemFactory(), invokedMethod)) {
            DexReference itemBasedString = identifyIdentifier(invoke, appView);
            if (itemBasedString == null) {
              warnUndeterminedIdentifierIfNecessary(invokedMethod, originHolder, instruction, null);
              continue;
            }
            DexType returnType = invoke.getReturnType();
            boolean isClassForName =
                returnType.descriptor == appView.dexItemFactory().classDescriptor;
            boolean isReferenceFieldUpdater =
                returnType.descriptor == appView.dexItemFactory().referenceFieldUpdaterDescriptor;
            int positionOfIdentifier = isClassForName ? 0 : (isReferenceFieldUpdater ? 2 : 1);
            Value in = invoke.arguments().get(positionOfIdentifier);
            // Move the cursor back to $invoke
            assert iterator.peekPrevious() == invoke;
            iterator.previous();
            // Prepare $decoupled just before $invoke
            Value newIn = code.createValue(in.getTypeLattice(), in.getLocalInfo());
            DexItemBasedConstString decoupled =
                new DexItemBasedConstString(newIn, itemBasedString, throwingInfo);
            decoupled.setPosition(invoke.getPosition());
            changes[positionOfIdentifier] = newIn;
            // If the current block has catch handler, split into two blocks.
            // Because const-string we're about to add is also a throwing instr, we need to split
            // before adding it.
            BasicBlock blockWithInvoke =
                block.hasCatchHandlers() ? iterator.split(code, blocks) : block;
            if (blockWithInvoke != block) {
              // If we split, add const-string at the end of the currently visiting block.
              iterator = block.listIterator(block.getInstructions().size());
              iterator.previous();
              iterator.add(decoupled);
              // Restore the cursor and block.
              iterator = blockWithInvoke.listIterator();
              assert iterator.peekNext() == invoke;
              iterator.next();
              block = blockWithInvoke;
            } else {
              // Otherwise, just add it to the current block at the position of the iterator.
              iterator.add(decoupled);
              // Restore the cursor.
              assert iterator.peekNext() == invoke;
              iterator.next();
            }
          } else {
            // For general invoke. Multiple arguments can be string literals to be renamed.
            for (int i = 0; i < ins.size(); i++) {
              Value in = ins.get(i);
              if (!in.isConstString()) {
                warnUndeterminedIdentifierIfNecessary(
                    invokedMethod, originHolder, instruction, null);
                continue;
              }
              DexString original = in.getConstInstruction().asConstString().getValue();
              DexReference itemBasedString = inferMemberOrTypeFromNameString(appView, original);
              if (itemBasedString == null) {
                warnUndeterminedIdentifierIfNecessary(
                    invokedMethod, originHolder, instruction, original);
                continue;
              }
              // Move the cursor back to $invoke
              assert iterator.peekPrevious() == invoke;
              iterator.previous();
              // Prepare $decoupled just before $invoke
              Value newIn = code.createValue(in.getTypeLattice(), in.getLocalInfo());
              DexItemBasedConstString decoupled =
                  new DexItemBasedConstString(newIn, itemBasedString, throwingInfo);
              decoupled.setPosition(invoke.getPosition());
              changes[i] = newIn;
              // If the current block has catch handler, split into two blocks.
              // Because const-string we're about to add is also a throwing instr, we need to split
              // before adding it.
              BasicBlock blockWithInvoke =
                  block.hasCatchHandlers() ? iterator.split(code, blocks) : block;
              if (blockWithInvoke != block) {
                // If we split, add const-string at the end of the currently visiting block.
                iterator = block.listIterator(block.getInstructions().size());
                iterator.previous();
                iterator.add(decoupled);
                // Restore the cursor and block.
                iterator = blockWithInvoke.listIterator();
                assert iterator.peekNext() == invoke;
                iterator.next();
                block = blockWithInvoke;
              } else {
                // Otherwise, just add it to the current block at the position of the iterator.
                iterator.add(decoupled);
                // Restore the cursor.
                assert iterator.peekNext() == invoke;
                iterator.next();
              }
            }
          }
          if (!Arrays.stream(changes).allMatch(Objects::isNull)) {
            List<Value> newIns =
                Streams.mapWithIndex(
                    ins.stream(),
                    (in, index) -> changes[(int) index] != null ? changes[(int) index] : in)
                .collect(Collectors.toList());
            iterator.replaceCurrentInstruction(
                Invoke.create(
                    invoke.getType(),
                    invokedMethod,
                    invokedMethod.proto,
                    invoke.outValue(),
                    newIns));
            encodedMethod.getMutableOptimizationInfo().markUseIdentifierNameString();
          }
        }
      }
    }
  }

  private void warnUndeterminedIdentifierIfNecessary(
      DexReference member,
      DexType originHolder,
      Instruction instruction,
      DexString original) {
    assert member.isDexField() || member.isDexMethod();
    // Only issue warnings for -identifiernamestring rules explicitly added by the user.
    boolean matchedByExplicitRule = identifierNameStrings.getBoolean(member);
    if (!matchedByExplicitRule) {
      return;
    }
    DexClass originClass = appView.definitionFor(originHolder);
    // If the origin is a library class, it is out of developers' control.
    if (originClass != null && originClass.isNotProgramClass()) {
      return;
    }
    // Undetermined identifiers matter only if minification is enabled.
    if (!appView.options().getProguardConfiguration().isObfuscating()) {
      return;
    }
    Origin origin = appView.appInfo().originFor(originHolder);
    String kind = member instanceof DexField ? "field" : "method";
    String originalMessage = original == null ? "what identifier string flows to "
        : "what '" + original.toString() + "' refers to, which flows to ";
    String message =
        "Cannot determine " + originalMessage + member.toSourceString()
            + " that is specified in -identifiernamestring rules."
            + " Thus, not all identifier strings flowing to that " + kind
            + " are renamed, which can cause resolution failures at runtime.";
    StringDiagnostic diagnostic =
        instruction.getPosition().line >= 1
            ? new StringDiagnostic(message, origin,
            new TextPosition(0L, instruction.getPosition().line, 1))
            : new StringDiagnostic(message, origin);
    appView.options().reporter.warning(diagnostic);
  }
}
