// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.ClassTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.FormalTypeParameter;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.GenericSignature.Parser;
import com.android.tools.r8.graph.GenericSignature.ReturnType;
import com.android.tools.r8.graph.GenericSignature.TypeSignature;
import com.android.tools.r8.graph.GenericSignature.WildcardIndicator;
import com.android.tools.r8.graph.GenericSignatureTestClassA.I;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenericSignatureTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public GenericSignatureTest(TestParameters parameters) {}

  @Test
  public void test() throws Exception {
    AndroidApp app =
        testForD8()
            .debug()
            .addProgramClassesAndInnerClasses(
                GenericSignatureTestClassA.class,
                GenericSignatureTestClassB.class,
                GenericSignatureTestClassCY.class,
                GenericSignatureTestClassCYY.class)
            .compile()
            .app;
    AppView<AppInfoWithLiveness> appView = computeAppViewWithLiveness(app);
    DexItemFactory factory = appView.dexItemFactory();
    CodeInspector inspector = new CodeInspector(appView.appInfo().app());

    ClassSubject a = inspector.clazz(GenericSignatureTestClassA.class);
    assertThat(a, isPresent());
    ClassSubject y = inspector.clazz(GenericSignatureTestClassA.Y.class);
    assertThat(y, isPresent());
    ClassSubject yy = inspector.clazz(GenericSignatureTestClassA.Y.YY.class);
    assertThat(yy, isPresent());
    ClassSubject zz = inspector.clazz(GenericSignatureTestClassA.Y.ZZ.class);
    assertThat(zz, isPresent());
    ClassSubject b = inspector.clazz(GenericSignatureTestClassB.class);
    assertThat(b, isPresent());
    ClassSubject cy = inspector.clazz(GenericSignatureTestClassCY.class);
    assertThat(cy, isPresent());
    ClassSubject cyy = inspector.clazz(GenericSignatureTestClassCYY.class);
    assertThat(cyy, isPresent());
    ClassSubject i = inspector.clazz(I.class);
    assertThat(cyy, isPresent());

    DexEncodedMethod method;

    ClassSignature classSignature;
    ClassTypeSignature classTypeSignature;
    FieldTypeSignature fieldTypeSignature;
    MethodTypeSignature methodTypeSignature;
    List<FieldTypeSignature> typeArguments;
    FieldTypeSignature typeArgument;
    TypeSignature parameterSignature;
    TypeSignature elementSignature;
    ReturnType returnType;
    TypeSignature returnTypeSignature;

    //
    // Testing ClassSignature
    //

    // class <T:GenericSignatureTestClassA<T>.Y>CYY<T extends A<T>.Y> extends CY<T>
    DexClass clazz = cyy.getDexProgramClass();
    assertNotNull(clazz);
    classSignature = Parser.toClassSignature(clazz, appView);
    assertNotNull(classSignature);

    assertEquals(1, classSignature.formalTypeParameters.size());
    FormalTypeParameter formalTypeParameter = classSignature.formalTypeParameters.get(0);
    assertEquals("T", formalTypeParameter.name);
    assertNull(formalTypeParameter.interfaceBounds);
    assertTrue(formalTypeParameter.classBound.isClassTypeSignature());
    ClassTypeSignature classBoundSignature = formalTypeParameter.classBound.asClassTypeSignature();
    assertEquals(y.getDexProgramClass().type, classBoundSignature.innerTypeSignature.type);
    assertEquals(1, classBoundSignature.typeArguments.size());
    assertEquals(
        "T", classBoundSignature.typeArguments.get(0).asTypeVariableSignature().typeVariable);

    assertTrue(classSignature.superInterfaceSignatures.isEmpty());
    classTypeSignature = classSignature.superClassSignature;
    assertEquals(cy.getDexProgramClass().type, classTypeSignature.type);
    typeArguments = classTypeSignature.typeArguments;
    assertEquals(1, typeArguments.size());
    typeArgument = typeArguments.get(0);
    assertTrue(typeArgument.isTypeVariableSignature());
    assertEquals("T", typeArgument.asTypeVariableSignature().typeVariable);

    //
    // Testing FieldTypeSignature
    //

    FieldSubject yyInZZ = zz.uniqueFieldWithName("yy");
    assertThat(yyInZZ, isPresent());
    DexEncodedField field = yyInZZ.getField();
    assertNotNull(field);

    fieldTypeSignature = Parser.toFieldTypeSignature(field, appView);
    assertNotNull(fieldTypeSignature);

    // field type: A$Y$YY
    assertTrue(fieldTypeSignature.isClassTypeSignature());
    check_A_Y_YY(a, y, yy, fieldTypeSignature.asClassTypeSignature());

    //
    // Testing MethodTypeSignature
    //

    // A$Y$YY newYY([B<T>)
    MethodSubject newYY = zz.uniqueMethodWithName("newYY");
    assertThat(newYY, isPresent());
    method = newYY.getMethod();
    assertNotNull(method);

    methodTypeSignature = Parser.toMethodTypeSignature(method, appView);
    assertNotNull(methodTypeSignature);

    assertEquals(1, methodTypeSignature.formalTypeParameters.size());
    FormalTypeParameter methodFormalParameter = methodTypeSignature.formalTypeParameters.get(0);
    assertTrue(methodFormalParameter.classBound.isClassTypeSignature());
    assertEquals(
        y.getDexProgramClass().getType(),
        methodFormalParameter.classBound.asClassTypeSignature().innerTypeSignature.type);
    assertNotNull(methodFormalParameter.interfaceBounds);
    assertEquals(1, methodFormalParameter.interfaceBounds.size());
    FieldTypeSignature interfaceBound = methodFormalParameter.interfaceBounds.get(0);
    assertTrue(interfaceBound.isClassTypeSignature());
    assertEquals(i.getDexProgramClass().getType(), interfaceBound.asClassTypeSignature().type);

    // return type: A$Y$YY
    returnType = methodTypeSignature.returnType();
    assertFalse(returnType.isVoidDescriptor());
    returnTypeSignature = returnType.typeSignature();
    assertTrue(returnTypeSignature.isFieldTypeSignature());
    assertTrue(returnTypeSignature.asFieldTypeSignature().isClassTypeSignature());
    check_A_Y_YY(a, y, yy, returnTypeSignature.asFieldTypeSignature().asClassTypeSignature());

    // type of 1st argument: [B<T>
    assertEquals(1, methodTypeSignature.typeSignatures.size());
    parameterSignature = methodTypeSignature.getParameterTypeSignature(0);
    assertNotNull(parameterSignature);
    assertTrue(parameterSignature.isFieldTypeSignature());
    assertTrue(parameterSignature.asFieldTypeSignature().isArrayTypeSignature());
    elementSignature =
        parameterSignature.asFieldTypeSignature().asArrayTypeSignature().elementSignature;
    assertTrue(elementSignature.isFieldTypeSignature());
    assertTrue(elementSignature.asFieldTypeSignature().isClassTypeSignature());
    classTypeSignature = elementSignature.asFieldTypeSignature().asClassTypeSignature();
    assertEquals(b.getDexProgramClass().type, classTypeSignature.type);

    // Function<A$Y$ZZ<TT>, A$Y$YY> convertToYY(Supplier<A$Y$ZZ<TT>>
    MethodSubject convertToYY = zz.uniqueMethodWithName("convertToYY");
    assertThat(convertToYY, isPresent());
    method = convertToYY.getMethod();
    assertNotNull(method);

    methodTypeSignature = GenericSignature.Parser.toMethodTypeSignature(method, appView);
    assertNotNull(methodTypeSignature);

    // return type: Function<A$Y$ZZ<TT>, A$Y$YY>
    returnType = methodTypeSignature.returnType();
    assertFalse(returnType.isVoidDescriptor());
    returnTypeSignature = returnType.typeSignature();
    assertTrue(returnTypeSignature.isFieldTypeSignature());
    assertTrue(returnTypeSignature.asFieldTypeSignature().isClassTypeSignature());
    classTypeSignature = returnTypeSignature.asFieldTypeSignature().asClassTypeSignature();
    DexType functionType =
        factory.createType(DescriptorUtils.javaTypeToDescriptor(Function.class.getTypeName()));
    assertEquals(functionType, classTypeSignature.type);

    typeArguments = classTypeSignature.typeArguments;
    assertEquals(2, typeArguments.size());

    typeArgument = typeArguments.get(0);
    assertTrue(typeArgument.isClassTypeSignature());
    check_A_Y_ZZ(a, y, zz, typeArgument.asClassTypeSignature());

    typeArgument = typeArguments.get(1);
    assertTrue(typeArgument.isClassTypeSignature());
    check_A_Y_YY(a, y, yy, typeArgument.asClassTypeSignature());

    // type of 1st argument: Supplier<A$Y$ZZ<TT>>
    assertEquals(1, methodTypeSignature.typeSignatures.size());
    parameterSignature = methodTypeSignature.getParameterTypeSignature(0);
    check_supplier(factory, a, y, zz, parameterSignature);

    // void boo(Supplier<A$Y$ZZ<TT>>)
    MethodSubject boo = zz.uniqueMethodWithName("boo");
    assertThat(boo, isPresent());
    method = boo.getMethod();
    assertNotNull(method);

    // return type: void
    methodTypeSignature = Parser.toMethodTypeSignature(method, appView);
    assertNotNull(methodTypeSignature);
    returnType = methodTypeSignature.returnType();
    assertTrue(returnType.isVoidDescriptor());

    // type of 1st argument: Supplier<A$Y$ZZ<TT>>
    assertEquals(1, methodTypeSignature.typeSignatures.size());
    parameterSignature = methodTypeSignature.getParameterTypeSignature(0);
    check_supplier(factory, a, y, zz, parameterSignature);

    // check_A_Y_foo for star, negative and positive wildcards
    check_A_Y_foo_bar_baz(y, appView);
  }

  private void check_A_Y(ClassSubject a, ClassSubject y, ClassTypeSignature signature) {
    assertEquals(a.getDexProgramClass().type, signature.type);
    List<FieldTypeSignature> typeArguments = signature.typeArguments;
    assertEquals(1, typeArguments.size());
    FieldTypeSignature typeArgument = typeArguments.get(0);
    assertTrue(typeArgument.isTypeVariableSignature());
    assertEquals("T", typeArgument.asTypeVariableSignature().typeVariable);
    assertEquals(y.getDexProgramClass().type, signature.innerTypeSignature.type);
  }

  private void check_A_Y_YY(
      ClassSubject a, ClassSubject y, ClassSubject yy, ClassTypeSignature signature) {
    check_A_Y(a, y, signature);
    assertEquals(
        yy.getDexProgramClass().type, signature.innerTypeSignature.innerTypeSignature.type);
  }

  private void check_A_Y_ZZ(
      ClassSubject a, ClassSubject y, ClassSubject zz, ClassTypeSignature signature) {
    check_A_Y(a, y, signature);
    ClassTypeSignature innerMost = signature.innerTypeSignature.innerTypeSignature;
    assertEquals(zz.getDexProgramClass().type, innerMost.type);
    List<FieldTypeSignature> typeArguments = innerMost.typeArguments;
    assertEquals(1, typeArguments.size());
    FieldTypeSignature typeArgument = typeArguments.get(0);
    assertTrue(typeArgument.isTypeVariableSignature());
    assertEquals("TT", typeArgument.asTypeVariableSignature().typeVariable);
  }

  private void check_A_Y_foo_bar_baz(ClassSubject y, AppView<AppInfoWithLiveness> appView) {
    checkMethodWildCard(y.uniqueMethodWithName("foo"), appView, WildcardIndicator.POSITIVE);
    checkMethodWildCard(y.uniqueMethodWithName("bar"), appView, WildcardIndicator.NEGATIVE);
    // Check for star
    checkFieldTypeSignature(
        y.uniqueMethodWithName("baz"),
        appView,
        typeSignature -> {
          assertTrue(typeSignature.isStar());
        });
  }

  private void checkMethodWildCard(
      MethodSubject methodSubject,
      AppView<AppInfoWithLiveness> appView,
      WildcardIndicator indicator) {
    checkFieldTypeSignature(
        methodSubject,
        appView,
        typeSignature -> {
          assertTrue(typeSignature.isTypeVariableSignature());
          assertEquals(indicator, typeSignature.getWildcardIndicator());
        });
  }

  private void checkFieldTypeSignature(
      MethodSubject methodSubject,
      AppView<AppInfoWithLiveness> appView,
      Consumer<FieldTypeSignature> fieldTypeConsumer) {
    MethodTypeSignature methodTypeSignature =
        Parser.toMethodTypeSignature(methodSubject.getMethod(), appView);
    TypeSignature typeSignature = methodTypeSignature.returnType.typeSignature;
    FieldTypeSignature fieldTypeSignature = typeSignature.asFieldTypeSignature();
    assertTrue(fieldTypeSignature.isClassTypeSignature());
    ClassTypeSignature classTypeSignature = fieldTypeSignature.asClassTypeSignature();
    assertFalse(classTypeSignature.isArgument());
    assertEquals(1, classTypeSignature.typeArguments.size());
    FieldTypeSignature typeArgument = classTypeSignature.typeArguments.get(0);
    fieldTypeConsumer.accept(typeArgument);
  }

  private void check_supplier(
      DexItemFactory factory,
      ClassSubject a,
      ClassSubject y,
      ClassSubject zz,
      TypeSignature signature) {
    assertNotNull(signature);
    assertTrue(signature.isFieldTypeSignature());
    assertTrue(signature.asFieldTypeSignature().isClassTypeSignature());
    ClassTypeSignature classTypeSignature = signature.asFieldTypeSignature().asClassTypeSignature();
    DexType supplierType =
        factory.createType(DescriptorUtils.javaTypeToDescriptor(Supplier.class.getTypeName()));
    assertEquals(supplierType, classTypeSignature.type);
    List<FieldTypeSignature> typeArguments = classTypeSignature.typeArguments;
    assertEquals(1, typeArguments.size());
    FieldTypeSignature typeArgument = typeArguments.get(0);
    assertTrue(typeArgument.isClassTypeSignature());
    check_A_Y_ZZ(a, y, zz, typeArgument.asClassTypeSignature());
  }
}

//
// TODO(b/129925954): Once unified, these would be stale comments.
// Borrowed from ...naming.signature.GenericSignatureRenamingTest
// and then extended a bit to explore more details, e.g., MethodTypeSignature.
//

class GenericSignatureTestClassA<T> {

  interface I {}

  class Y {

    class YY {}

    class ZZ<TT> extends YY {
      public YY yy;

      <R extends Y & I> YY newYY(GenericSignatureTestClassB... bs) {
        return new YY();
      }

      Function<ZZ<TT>, YY> convertToYY(Supplier<ZZ<TT>> zzSupplier) {
        return zz -> {
          if (System.currentTimeMillis() > 0) {
            return zzSupplier.get().newYY();
          } else {
            return zz.newYY();
          }
        };
      }

      void boo(Supplier<ZZ<TT>> zzSupplier) {
        convertToYY(zzSupplier).apply(this);
      }
    }

    ZZ<T> zz() {
      return new ZZ<T>();
    }

    List<? extends T> foo() {
      return null;
    }

    List<? super T> bar() {
      return null;
    }

    List<?> baz() {
      return null;
    }
  }

  class Z extends Y {}

  static class S {}

  Y newY() {
    return new Y();
  }

  Z newZ() {
    return new Z();
  }

  Y.ZZ<T> newZZ() {
    return new Y().zz();
  }
}

class GenericSignatureTestClassB<T extends GenericSignatureTestClassA<T>> {}

class GenericSignatureTestClassCY<T extends GenericSignatureTestClassA<T>.Y> {}

class GenericSignatureTestClassCYY<T extends GenericSignatureTestClassA<T>.Y>
    extends GenericSignatureTestClassCY<T> {}
