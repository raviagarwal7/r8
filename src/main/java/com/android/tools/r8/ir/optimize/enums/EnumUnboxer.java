// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClass.FieldSetter;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue.DexValueInt;
import com.android.tools.r8.graph.DexValue.DexValueNull;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.GraphLense.NestedGraphLense;
import com.android.tools.r8.graph.RewrittenPrototypeDescription;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.ArgumentInfoCollection;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.RewrittenTypeInfo;
import com.android.tools.r8.ir.analysis.type.ArrayTypeLatticeElement;
import com.android.tools.r8.ir.analysis.type.ClassTypeLatticeElement;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.CodeOptimization;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.ir.conversion.PostOptimization;
import com.android.tools.r8.ir.optimize.info.FieldOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback.OptimizationInfoFixer;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class EnumUnboxer implements PostOptimization {

  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory factory;
  // Map the enum candidates with their dependencies, i.e., the methods to reprocess for the given
  // enum if the optimization eventually decides to unbox it.
  private final Map<DexType, Set<DexEncodedMethod>> enumsUnboxingCandidates;

  private EnumUnboxingRewriter enumUnboxerRewriter;

  private final boolean debugLogEnabled;
  private final Map<DexType, Reason> debugLogs;

  public EnumUnboxer(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    if (appView.options().testing.enableEnumUnboxingDebugLogs) {
      debugLogEnabled = true;
      debugLogs = new ConcurrentHashMap<>();
    } else {
      debugLogEnabled = false;
      debugLogs = null;
    }
    enumsUnboxingCandidates = new EnumUnboxingCandidateAnalysis(appView, this).findCandidates();
  }

  public void analyzeEnums(IRCode code) {
    // Enum <clinit> and <init> are analyzed in between the two processing phases using optimization
    // feedback. Methods valueOf and values are generated by javac and are analyzed differently.
    DexClass dexClass = appView.definitionFor(code.method.method.holder);
    if (dexClass.isEnum()
        && (code.method.isInitializer()
            || appView.dexItemFactory().enumMethods.isValueOfMethod(code.method.method, dexClass)
            || appView.dexItemFactory().enumMethods.isValuesMethod(code.method.method, dexClass))) {
      return;
    }
    analyzeEnumsInMethod(code);
  }

  private void markEnumAsUnboxable(Reason reason, DexProgramClass enumClass) {
    assert enumClass.isEnum();
    reportFailure(enumClass.type, reason);
    enumsUnboxingCandidates.remove(enumClass.type);
  }

  private DexProgramClass getEnumUnboxingCandidateOrNull(TypeLatticeElement lattice) {
    if (lattice.isClassType()) {
      DexType classType = lattice.asClassType().getClassType();
      return getEnumUnboxingCandidateOrNull(classType);
    }
    if (lattice.isArrayType()) {
      ArrayTypeLatticeElement arrayLattice = lattice.asArrayType();
      if (arrayLattice.getBaseType().isClassType()) {
        DexType classType = arrayLattice.getBaseType().asClassType().getClassType();
        return getEnumUnboxingCandidateOrNull(classType);
      }
    }
    return null;
  }

  private DexProgramClass getEnumUnboxingCandidateOrNull(DexType anyType) {
    if (!enumsUnboxingCandidates.containsKey(anyType)) {
      return null;
    }
    return appView.definitionForProgramType(anyType);
  }

  private void analyzeEnumsInMethod(IRCode code) {
    Set<DexType> eligibleEnums = Sets.newIdentityHashSet();
    for (BasicBlock block : code.blocks) {
      for (Instruction instruction : block.getInstructions()) {
        Value outValue = instruction.outValue();
        if (outValue != null) {
          DexProgramClass enumClass = getEnumUnboxingCandidateOrNull(outValue.getType());
          if (enumClass != null) {
            Reason reason = validateEnumUsages(code, outValue, enumClass);
            if (reason == Reason.ELIGIBLE) {
              if (instruction.isCheckCast()) {
                // We are doing a type check, which typically means the in-value is of an upper
                // type and cannot be dealt with.
                markEnumAsUnboxable(Reason.DOWN_CAST, enumClass);
              }
              eligibleEnums.add(enumClass.type);
            }
          }
          if (outValue.getType().isNullType()) {
            addNullDependencies(outValue.uniqueUsers(), eligibleEnums);
          }
        }
        // If we have a ConstClass referencing directly an enum, it cannot be unboxed, except if
        // the constClass is in an enum valueOf method (in this case the valueOf method will be
        // removed or the enum will be marked as non unboxable).
        if (instruction.isConstClass()) {
          ConstClass constClass = instruction.asConstClass();
          if (enumsUnboxingCandidates.containsKey(constClass.getValue())) {
            markEnumAsUnboxable(
                Reason.CONST_CLASS, appView.definitionForProgramType(constClass.getValue()));
          }
        } else if (instruction.isInvokeStatic()) {
          // TODO(b/150370354): Since we temporary allow enum unboxing on enums with values and
          // valueOf static methods only if such methods are unused, such methods cannot be
          // called. the long term solution is to simply move called methods to a companion class,
          // as any static helper method, and remove these checks.
          DexMethod invokedMethod = instruction.asInvokeStatic().getInvokedMethod();
          DexProgramClass enumClass = getEnumUnboxingCandidateOrNull(invokedMethod.holder);
          if (enumClass != null) {
            if (factory.enumMethods.isValueOfMethod(invokedMethod, enumClass)) {
              markEnumAsUnboxable(Reason.VALUE_OF_INVOKE, enumClass);
            } else if (factory.enumMethods.isValuesMethod(invokedMethod, enumClass)) {
              markEnumAsUnboxable(Reason.VALUES_INVOKE, enumClass);
            } else {
              assert false; // We do not allow any other static call in unboxing candidates.
            }
          }
        }
      }
      for (Phi phi : block.getPhis()) {
        DexProgramClass enumClass = getEnumUnboxingCandidateOrNull(phi.getType());
        if (enumClass != null) {
          Reason reason = validateEnumUsages(code, phi, enumClass);
          if (reason == Reason.ELIGIBLE) {
            eligibleEnums.add(enumClass.type);
          }
        }
        if (phi.getType().isNullType()) {
          addNullDependencies(phi.uniqueUsers(), eligibleEnums);
        }
      }
    }
    if (!eligibleEnums.isEmpty()) {
      for (DexType eligibleEnum : eligibleEnums) {
        Set<DexEncodedMethod> dependencies = enumsUnboxingCandidates.get(eligibleEnum);
        // If dependencies is null, it means the enum is not eligible (It has been marked as
        // unboxable by this thread or another one), so we do not need to record dependencies.
        if (dependencies != null) {
          dependencies.add(code.method);
        }
      }
    }
  }

  private void addNullDependencies(Set<Instruction> uses, Set<DexType> eligibleEnums) {
    for (Instruction use : uses) {
      if (use.isInvokeMethod()) {
        InvokeMethod invokeMethod = use.asInvokeMethod();
        DexMethod invokedMethod = invokeMethod.getInvokedMethod();
        for (DexType paramType : invokedMethod.proto.parameters.values) {
          if (enumsUnboxingCandidates.containsKey(paramType)) {
            eligibleEnums.add(paramType);
          }
        }
        if (invokeMethod.isInvokeMethodWithReceiver()) {
          DexProgramClass enumClass = getEnumUnboxingCandidateOrNull(invokedMethod.holder);
          if (enumClass != null) {
            markEnumAsUnboxable(Reason.ENUM_METHOD_CALLED_WITH_NULL_RECEIVER, enumClass);
          }
        }
      }
      if (use.isFieldPut()) {
        DexType type = use.asFieldInstruction().getField().type;
        if (enumsUnboxingCandidates.containsKey(type)) {
          eligibleEnums.add(type);
        }
      }
    }
  }

  private Reason validateEnumUsages(IRCode code, Value value, DexProgramClass enumClass) {
    for (Instruction user : value.uniqueUsers()) {
      Reason reason = instructionAllowEnumUnboxing(user, code, enumClass, value);
      if (reason != Reason.ELIGIBLE) {
        markEnumAsUnboxable(reason, enumClass);
        return reason;
      }
    }
    for (Phi phi : value.uniquePhiUsers()) {
      for (Value operand : phi.getOperands()) {
        if (getEnumUnboxingCandidateOrNull(operand.getType()) != enumClass) {
          markEnumAsUnboxable(Reason.INVALID_PHI, enumClass);
          return Reason.INVALID_PHI;
        }
      }
    }
    return Reason.ELIGIBLE;
  }

  public void unboxEnums(
      PostMethodProcessor.Builder postBuilder,
      ExecutorService executorService,
      OptimizationFeedbackDelayed feedback)
      throws ExecutionException {
    // At this point the enumsToUnbox are no longer candidates, they will all be unboxed.
    if (enumsUnboxingCandidates.isEmpty()) {
      return;
    }
    ImmutableSet<DexType> enumsToUnbox = ImmutableSet.copyOf(this.enumsUnboxingCandidates.keySet());
    NestedGraphLense enumUnboxingLens = new TreeFixer(enumsToUnbox).fixupTypeReferences();
    enumUnboxerRewriter = new EnumUnboxingRewriter(appView, enumsToUnbox);
    appView.setUnboxedEnums(enumUnboxerRewriter.getEnumsToUnbox());
    if (enumUnboxingLens != null) {
      appView.setGraphLense(enumUnboxingLens);
      appView.setAppInfo(
          appView
              .appInfo()
              .rewrittenWithLens(appView.appInfo().app().asDirect(), enumUnboxingLens));

      // Update optimization info.
      feedback.fixupOptimizationInfos(
          appView,
          executorService,
          new OptimizationInfoFixer() {
            @Override
            public void fixup(DexEncodedField field) {
              FieldOptimizationInfo optimizationInfo = field.getOptimizationInfo();
              if (optimizationInfo.isMutableFieldOptimizationInfo()) {
                optimizationInfo
                    .asMutableFieldOptimizationInfo()
                    .fixupClassTypeReferences(appView.graphLense()::lookupType, appView)
                    .fixupAbstractValue(appView, appView.graphLense());
              } else {
                assert optimizationInfo.isDefaultFieldOptimizationInfo();
              }
            }

            @Override
            public void fixup(DexEncodedMethod method) {
              MethodOptimizationInfo optimizationInfo = method.getOptimizationInfo();
              if (optimizationInfo.isUpdatableMethodOptimizationInfo()) {
                optimizationInfo
                    .asUpdatableMethodOptimizationInfo()
                    .fixupClassTypeReferences(appView.graphLense()::lookupType, appView)
                    .fixupAbstractReturnValue(appView, appView.graphLense())
                    .fixupInstanceInitializerInfo(appView, appView.graphLense());
              } else {
                assert optimizationInfo.isDefaultMethodOptimizationInfo();
              }
            }
          });
    }
    postBuilder.put(this);
    postBuilder.mapDexEncodedMethods(appView);
  }

  public void finishAnalysis() {
    for (DexType toUnbox : enumsUnboxingCandidates.keySet()) {
      DexProgramClass enumClass = appView.definitionForProgramType(toUnbox);
      assert enumClass != null;

      // Enum candidates have necessarily only one constructor matching enumMethods.constructor
      // signature.
      DexEncodedMethod initializer = enumClass.lookupDirectMethod(factory.enumMethods.constructor);
      if (initializer == null) {
        // This case typically happens when a programmer uses EnumSet/EnumMap without using the
        // enum keep rules. The code is incorrect in this case (EnumSet/EnumMap won't work).
        // We bail out.
        markEnumAsUnboxable(Reason.NO_INIT, enumClass);
        continue;
      }
      if (initializer.getOptimizationInfo().mayHaveSideEffects()) {
        markEnumAsUnboxable(Reason.INVALID_INIT, enumClass);
        continue;
      }

      if (enumClass.classInitializationMayHaveSideEffects(appView)) {
        markEnumAsUnboxable(Reason.INVALID_CLINIT, enumClass);
      }
    }
    if (debugLogEnabled) {
      reportEnumsAnalysis();
    }
  }

  private Reason instructionAllowEnumUnboxing(
      Instruction instruction, IRCode code, DexProgramClass enumClass, Value enumValue) {

    // All invokes in the library are invalid, besides a few cherry picked cases such as ordinal().
    if (instruction.isInvokeMethod()) {
      InvokeMethod invokeMethod = instruction.asInvokeMethod();
      if (invokeMethod.getInvokedMethod().holder.isArrayType()) {
        // The only valid methods is clone for values() to be correct.
        if (invokeMethod.getInvokedMethod().name == factory.cloneMethodName) {
          return Reason.ELIGIBLE;
        }
        return Reason.INVALID_INVOKE_ON_ARRAY;
      }
      DexEncodedMethod encodedSingleTarget =
          invokeMethod.lookupSingleTarget(appView, code.method.method.holder);
      if (encodedSingleTarget == null) {
        return Reason.INVALID_INVOKE;
      }
      DexMethod singleTarget = encodedSingleTarget.method;
      DexClass dexClass = appView.definitionFor(singleTarget.holder);
      if (dexClass == null) {
        return Reason.INVALID_INVOKE;
      }
      if (dexClass.isProgramClass()) {
        // All invokes in the program are generally valid, but specific care is required
        // for values() and valueOf().
        if (dexClass.isEnum() && factory.enumMethods.isValuesMethod(singleTarget, dexClass)) {
          return Reason.VALUES_INVOKE;
        }
        if (dexClass.isEnum() && factory.enumMethods.isValueOfMethod(singleTarget, dexClass)) {
          return Reason.VALUE_OF_INVOKE;
        }
        int offset = BooleanUtils.intValue(!encodedSingleTarget.isStatic());
        for (int i = 0; i < singleTarget.proto.parameters.size(); i++) {
          if (invokeMethod.inValues().get(offset + i) == enumValue) {
            if (singleTarget.proto.parameters.values[i].toBaseType(factory) != enumClass.type) {
              return Reason.GENERIC_INVOKE;
            }
          }
        }
        return Reason.ELIGIBLE;
      }
      if (dexClass.isClasspathClass()) {
        return Reason.INVALID_INVOKE;
      }
      assert dexClass.isLibraryClass();
      if (dexClass.type != factory.enumType) {
        return Reason.UNSUPPORTED_LIBRARY_CALL;
      }
      // TODO(b/147860220): Methods toString(), name(), compareTo(), EnumSet and EnumMap may be
      // interesting to model. A the moment rewrite only Enum#ordinal().
      if (debugLogEnabled) {
        if (singleTarget == factory.enumMethods.compareTo) {
          return Reason.COMPARE_TO_INVOKE;
        }
        if (singleTarget == factory.enumMethods.name) {
          return Reason.NAME_INVOKE;
        }
        if (singleTarget == factory.enumMethods.toString) {
          return Reason.TO_STRING_INVOKE;
        }
      }
      if (singleTarget != factory.enumMethods.ordinal) {
        return Reason.UNSUPPORTED_LIBRARY_CALL;
      }
      return Reason.ELIGIBLE;
    }

    // A field put is valid only if the field is not on an enum, and the field type and the valuePut
    // have identical enum type.
    if (instruction.isFieldPut()) {
      FieldInstruction fieldInstruction = instruction.asFieldInstruction();
      DexEncodedField field = appView.appInfo().resolveField(fieldInstruction.getField());
      if (field == null) {
        return Reason.INVALID_FIELD_PUT;
      }
      DexProgramClass dexClass = appView.definitionForProgramType(field.field.holder);
      if (dexClass == null) {
        return Reason.INVALID_FIELD_PUT;
      }
      if (dexClass.isEnum()) {
        return Reason.FIELD_PUT_ON_ENUM;
      }
      // The put value has to be of the field type.
      if (field.field.type != enumClass.type) {
        return Reason.TYPE_MISSMATCH_FIELD_PUT;
      }
      return Reason.ELIGIBLE;
    }

    // An If using enum as inValue is valid if it matches e == null
    // or e == X with X of same enum type as e. Ex: if (e == MyEnum.A).
    if (instruction.isIf()) {
      If anIf = instruction.asIf();
      assert (anIf.getType() == If.Type.EQ || anIf.getType() == If.Type.NE)
          : "Comparing a reference with " + anIf.getType().toString();
      // e == null.
      if (anIf.isZeroTest()) {
        return Reason.ELIGIBLE;
      }
      // e == MyEnum.X
      TypeLatticeElement leftType = anIf.lhs().getType();
      TypeLatticeElement rightType = anIf.rhs().getType();
      if (leftType.equalUpToNullability(rightType)) {
        assert leftType.isClassType();
        assert leftType.asClassType().getClassType() == enumClass.type;
        return Reason.ELIGIBLE;
      }
      return Reason.INVALID_IF_TYPES;
    }

    if (instruction.isArrayLength()) {
      // MyEnum[] array = ...; array.length; is valid.
      return Reason.ELIGIBLE;
    }

    if (instruction.isArrayGet()) {
      // MyEnum[] array = ...; array[0]; is valid.
      return Reason.ELIGIBLE;
    }

    if (instruction.isArrayPut()) {
      // MyEnum[] array; array[0] = MyEnum.A; is valid.
      // MyEnum[][] array2d; MyEnum[] array; array2d[0] = array; is valid.
      // MyEnum[]^N array; MyEnum[]^(N-1) element; array[0] = element; is valid.
      // We need to prove that the value to put in and the array have correct types.
      ArrayPut arrayPut = instruction.asArrayPut();
      assert arrayPut.getMemberType() == MemberType.OBJECT;
      TypeLatticeElement arrayType = arrayPut.array().getType();
      assert arrayType.isArrayType();
      assert arrayType.asArrayType().getBaseType().isClassType();
      ClassTypeLatticeElement arrayBaseType = arrayType.asArrayType().getBaseType().asClassType();
      TypeLatticeElement valueBaseType = arrayPut.value().getType();
      if (valueBaseType.isArrayType()) {
        assert valueBaseType.asArrayType().getBaseType().isClassType();
        assert valueBaseType.asArrayType().getNesting() == arrayType.asArrayType().getNesting() - 1;
        valueBaseType = valueBaseType.asArrayType().getBaseType();
      }
      if (arrayBaseType.equalUpToNullability(valueBaseType)
          && arrayBaseType.getClassType() == enumClass.type) {
        return Reason.ELIGIBLE;
      }
      return Reason.INVALID_ARRAY_PUT;
    }

    if (instruction.isAssume()) {
      Value outValue = instruction.outValue();
      return validateEnumUsages(code, outValue, enumClass);
    }

    // Return is used for valueOf methods.
    if (instruction.isReturn()) {
      DexType returnType = code.method.method.proto.returnType;
      if (returnType != enumClass.type && returnType.toBaseType(factory) != enumClass.type) {
        return Reason.IMPLICIT_UP_CAST_IN_RETURN;
      }
      return Reason.ELIGIBLE;
    }

    return Reason.OTHER_UNSUPPORTED_INSTRUCTION;
  }

  private void reportEnumsAnalysis() {
    assert debugLogEnabled;
    Reporter reporter = appView.options().reporter;
    reporter.info(
        new StringDiagnostic(
            "Unboxed enums (Unboxing succeeded "
                + enumsUnboxingCandidates.size()
                + "): "
                + Arrays.toString(enumsUnboxingCandidates.keySet().toArray())));
    StringBuilder sb = new StringBuilder();
    sb.append("Boxed enums (Unboxing failed ").append(debugLogs.size()).append("):\n");
    for (DexType enumType : debugLogs.keySet()) {
      sb.append("- ")
          .append(enumType)
          .append(": ")
          .append(debugLogs.get(enumType).toString())
          .append('\n');
    }
    reporter.info(new StringDiagnostic(sb.toString()));
  }

  void reportFailure(DexType enumType, Reason reason) {
    if (debugLogEnabled) {
      debugLogs.put(enumType, reason);
    }
  }

  public void rewriteCode(IRCode code) {
    if (enumUnboxerRewriter != null) {
      enumUnboxerRewriter.rewriteCode(code);
    }
  }

  public void synthesizeUtilityClass(
      DexApplication.Builder<?> appBuilder, IRConverter converter, ExecutorService executorService)
      throws ExecutionException {
    if (enumUnboxerRewriter != null) {
      enumUnboxerRewriter.synthesizeEnumUnboxingUtilityClass(
          appBuilder, converter, executorService);
    }
  }

  @Override
  public Set<DexEncodedMethod> methodsToRevisit() {
    Set<DexEncodedMethod> toReprocess = Sets.newIdentityHashSet();
    for (Set<DexEncodedMethod> methods : enumsUnboxingCandidates.values()) {
      toReprocess.addAll(methods);
    }
    return toReprocess;
  }

  @Override
  public Collection<CodeOptimization> codeOptimizationsForPostProcessing() {
    // Answers null so default optimization setup is performed.
    return null;
  }

  public enum Reason {
    ELIGIBLE,
    PINNED,
    DOWN_CAST,
    SUBTYPES,
    INTERFACE,
    INSTANCE_FIELD,
    GENERIC_INVOKE,
    UNEXPECTED_STATIC_FIELD,
    VIRTUAL_METHOD,
    UNEXPECTED_DIRECT_METHOD,
    CONST_CLASS,
    INVALID_PHI,
    NO_INIT,
    INVALID_INIT,
    INVALID_CLINIT,
    INVALID_INVOKE,
    INVALID_INVOKE_ON_ARRAY,
    IMPLICIT_UP_CAST_IN_RETURN,
    VALUE_OF_INVOKE,
    VALUES_INVOKE,
    COMPARE_TO_INVOKE,
    TO_STRING_INVOKE,
    NAME_INVOKE,
    UNSUPPORTED_LIBRARY_CALL,
    MISSING_INFO_MAP,
    INVALID_FIELD_PUT,
    INVALID_ARRAY_PUT,
    FIELD_PUT_ON_ENUM,
    TYPE_MISSMATCH_FIELD_PUT,
    INVALID_IF_TYPES,
    ENUM_METHOD_CALLED_WITH_NULL_RECEIVER,
    OTHER_UNSUPPORTED_INSTRUCTION;
  }

  private class TreeFixer {

    private final EnumUnboxingLens.Builder lensBuilder = EnumUnboxingLens.builder();
    private final Set<DexType> enumsToUnbox;

    private TreeFixer(Set<DexType> enumsToUnbox) {
      this.enumsToUnbox = enumsToUnbox;
    }

    private NestedGraphLense fixupTypeReferences() {
      // Fix all methods and fields using enums to unbox.
      for (DexProgramClass clazz : appView.appInfo().classes()) {
        if (enumsToUnbox.contains(clazz.type)) {
          assert clazz.instanceFields().size() == 0;
          clearEnumtoUnboxMethods(clazz);
        } else {
          clazz.getMethodCollection().replaceMethods(this::fixupMethod);
          fixupFields(clazz.staticFields(), clazz::setStaticField);
          fixupFields(clazz.instanceFields(), clazz::setInstanceField);
        }
      }
      for (DexType toUnbox : enumsToUnbox) {
        lensBuilder.map(toUnbox, factory.intType);
      }
      return lensBuilder.build(factory, appView.graphLense());
    }

    private void clearEnumtoUnboxMethods(DexProgramClass clazz) {
      // The compiler may have references to the enum methods, but such methods will be removed
      // and they cannot be reprocessed since their rewriting through the lensCodeRewriter/
      // enumUnboxerRewriter will generate invalid code.
      // To work around this problem we clear such methods, i.e., we replace the code object by
      // an empty throwing code object, so reprocessing won't take time and will be valid.
      for (DexEncodedMethod method : clazz.methods()) {
        method.setCode(
            appView.options().isGeneratingClassFiles()
                ? method.buildEmptyThrowingCfCode()
                : method.buildEmptyThrowingDexCode(),
            appView);
      }
    }

    private DexEncodedMethod fixupMethod(DexEncodedMethod encodedMethod) {
      DexMethod newMethod = fixupMethod(encodedMethod.method);
      if (newMethod != encodedMethod.method) {
        lensBuilder.move(encodedMethod.method, newMethod, encodedMethod.isStatic());
        return encodedMethod.toTypeSubstitutedMethod(newMethod);
      }
      return encodedMethod;
    }

    private void fixupFields(List<DexEncodedField> fields, FieldSetter setter) {
      if (fields == null) {
        return;
      }
      for (int i = 0; i < fields.size(); i++) {
        DexEncodedField encodedField = fields.get(i);
        DexField field = encodedField.field;
        DexType newType = fixupType(field.type);
        if (newType != field.type) {
          DexField newField = factory.createField(field.holder, newType, field.name);
          lensBuilder.move(field, newField);
          DexEncodedField newEncodedField = encodedField.toTypeSubstitutedField(newField);
          setter.setField(i, newEncodedField);
          if (encodedField.isStatic() && encodedField.hasExplicitStaticValue()) {
            assert encodedField.getStaticValue() == DexValueNull.NULL;
            newEncodedField.setStaticValue(DexValueInt.DEFAULT);
            // TODO(b/150593449): Support conversion from DexValueEnum to DexValueInt.
          }
        }
      }
    }

    private DexMethod fixupMethod(DexMethod method) {
      return factory.createMethod(method.holder, fixupProto(method.proto), method.name);
    }

    private DexProto fixupProto(DexProto proto) {
      DexType returnType = fixupType(proto.returnType);
      DexType[] arguments = fixupTypes(proto.parameters.values);
      return factory.createProto(returnType, arguments);
    }

    private DexType fixupType(DexType type) {
      if (type.isArrayType()) {
        DexType base = type.toBaseType(factory);
        DexType fixed = fixupType(base);
        if (base == fixed) {
          return type;
        }
        return type.replaceBaseType(fixed, factory);
      }
      if (type.isClassType() && enumsToUnbox.contains(type)) {
        DexType intType = factory.intType;
        lensBuilder.map(type, intType);
        return intType;
      }
      return type;
    }

    private DexType[] fixupTypes(DexType[] types) {
      DexType[] result = new DexType[types.length];
      for (int i = 0; i < result.length; i++) {
        result[i] = fixupType(types[i]);
      }
      return result;
    }
  }

  private static class EnumUnboxingLens extends NestedGraphLense {

    private final Map<DexMethod, RewrittenPrototypeDescription> prototypeChanges;

    EnumUnboxingLens(
        Map<DexType, DexType> typeMap,
        Map<DexMethod, DexMethod> methodMap,
        Map<DexField, DexField> fieldMap,
        BiMap<DexField, DexField> originalFieldSignatures,
        BiMap<DexMethod, DexMethod> originalMethodSignatures,
        GraphLense previousLense,
        DexItemFactory dexItemFactory,
        Map<DexMethod, RewrittenPrototypeDescription> prototypeChanges) {
      super(
          typeMap,
          methodMap,
          fieldMap,
          originalFieldSignatures,
          originalMethodSignatures,
          previousLense,
          dexItemFactory);
      this.prototypeChanges = prototypeChanges;
    }

    @Override
    public RewrittenPrototypeDescription lookupPrototypeChanges(DexMethod method) {
      // During the second IR processing enum unboxing is the only optimization rewriting
      // prototype description, if this does not hold, remove the assertion and merge
      // the two prototype changes.
      assert previousLense.lookupPrototypeChanges(method).isEmpty();
      return prototypeChanges.getOrDefault(method, RewrittenPrototypeDescription.none());
    }

    public static Builder builder() {
      return new Builder();
    }

    private static class Builder extends NestedGraphLense.Builder {

      private Map<DexMethod, RewrittenPrototypeDescription> prototypeChanges =
          new IdentityHashMap<>();

      public void move(DexMethod from, DexMethod to, boolean isStatic) {
        super.move(from, to);
        int offset = BooleanUtils.intValue(!isStatic);
        ArgumentInfoCollection.Builder builder = ArgumentInfoCollection.builder();
        for (int i = 0; i < from.proto.parameters.size(); i++) {
          DexType fromType = from.proto.parameters.values[i];
          DexType toType = to.proto.parameters.values[i];
          if (fromType != toType) {
            builder.addArgumentInfo(i + offset, new RewrittenTypeInfo(fromType, toType));
          }
        }
        RewrittenTypeInfo returnInfo =
            from.proto.returnType == to.proto.returnType
                ? null
                : new RewrittenTypeInfo(from.proto.returnType, to.proto.returnType);
        prototypeChanges.put(
            to, RewrittenPrototypeDescription.createForRewrittenTypes(returnInfo, builder.build()));
      }

      @Override
      public EnumUnboxingLens build(DexItemFactory dexItemFactory, GraphLense previousLense) {
        if (typeMap.isEmpty() && methodMap.isEmpty() && fieldMap.isEmpty()) {
          return null;
        }
        return new EnumUnboxingLens(
            typeMap,
            methodMap,
            fieldMap,
            originalFieldSignatures,
            originalMethodSignatures,
            previousLense,
            dexItemFactory,
            ImmutableMap.copyOf(prototypeChanges));
      }
    }
  }
}
