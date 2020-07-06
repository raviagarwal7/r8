// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.kotlin.TestKotlinClass.AccessorKind;
import com.android.tools.r8.kotlin.TestKotlinClass.Visibility;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.function.Consumer;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class R8KotlinAccessorTest extends AbstractR8KotlinTestBase {

  private static final String JAVA_LANG_STRING = "java.lang.String";

  private static final TestKotlinCompanionClass ACCESSOR_COMPANION_PROPERTY_CLASS =
      new TestKotlinCompanionClass("accessors.Accessor")
          .addProperty("property", JAVA_LANG_STRING, Visibility.PRIVATE);

  private static final String PROPERTIES_PACKAGE_NAME = "properties";

  private static final TestKotlinCompanionClass COMPANION_PROPERTY_CLASS =
      new TestKotlinCompanionClass("properties.CompanionProperties")
          .addProperty("privateProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("protectedProp", JAVA_LANG_STRING, Visibility.PROTECTED)
          .addProperty("internalProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicProp", JAVA_LANG_STRING, Visibility.PUBLIC)
          .addProperty("primitiveProp", "int", Visibility.PUBLIC);

  private static final TestKotlinCompanionClass COMPANION_LATE_INIT_PROPERTY_CLASS =
      new TestKotlinCompanionClass("properties.CompanionLateInitProperties")
          .addProperty("privateLateInitProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("internalLateInitProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicLateInitProp", JAVA_LANG_STRING, Visibility.PUBLIC);

  private static final TestKotlinClass PROPERTY_ACCESS_FOR_INNER_CLASS =
      new TestKotlinClass("accessors.PropertyAccessorForInnerClass")
          .addProperty("privateProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("privateLateInitProp", JAVA_LANG_STRING, Visibility.PRIVATE);

  private static final TestKotlinClass PROPERTY_ACCESS_FOR_LAMBDA_CLASS =
      new TestKotlinClass("accessors.PropertyAccessorForLambda")
          .addProperty("property", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("indirectPropertyGetter", JAVA_LANG_STRING, Visibility.PRIVATE);

  private Consumer<InternalOptions> disableClassStaticizer =
      opts -> opts.enableClassStaticizer = false;

  @Parameterized.Parameters(name = "target: {0}, allowAccessModification: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(KotlinTargetVersion.values(), BooleanUtils.values());
  }

  public R8KotlinAccessorTest(
      KotlinTargetVersion targetVersion, boolean allowAccessModification) {
    super(targetVersion, allowAccessModification);
  }

  @Test
  public void testCompanionProperty_primitivePropertyIsAlwaysInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionPropertiesKt",
        "companionProperties_usePrimitiveProp");
    runTest(
        PROPERTIES_PACKAGE_NAME,
        mainClass,
        disableClassStaticizer,
        (app) -> {
          CodeInspector codeInspector = new CodeInspector(app);
          ClassSubject outerClass =
              checkClassIsKept(codeInspector, testedClass.getOuterClassName());
          String propertyName = "primitiveProp";
          FieldSubject fieldSubject = checkFieldIsKept(outerClass, "int", propertyName);
          assertTrue(fieldSubject.getField().accessFlags.isStatic());

          MemberNaming.MethodSignature getterAccessor =
              testedClass.getGetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);
          MemberNaming.MethodSignature setterAccessor =
              testedClass.getSetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);

          if (allowAccessModification) {
            assertTrue(fieldSubject.getField().accessFlags.isPublic());
            checkMethodIsRemoved(outerClass, getterAccessor);
            checkMethodIsRemoved(outerClass, setterAccessor);
          } else {
            assertTrue(fieldSubject.getField().accessFlags.isPrivate());
            checkMethodIsKept(outerClass, getterAccessor);
            checkMethodIsKept(outerClass, setterAccessor);
          }
        });
  }

  @Test
  public void testCompanionProperty_privatePropertyIsAlwaysInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionPropertiesKt",
        "companionProperties_usePrivateProp");
    runTest(
        PROPERTIES_PACKAGE_NAME,
        mainClass,
        disableClassStaticizer,
        (app) -> {
          CodeInspector codeInspector = new CodeInspector(app);
          ClassSubject outerClass =
              checkClassIsKept(codeInspector, testedClass.getOuterClassName());
          String propertyName = "privateProp";
          FieldSubject fieldSubject = checkFieldIsKept(outerClass, JAVA_LANG_STRING, propertyName);
          assertTrue(fieldSubject.getField().accessFlags.isStatic());

          MemberNaming.MethodSignature getterAccessor =
              testedClass.getGetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);
          MemberNaming.MethodSignature setterAccessor =
              testedClass.getSetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);
          if (allowAccessModification) {
            assertTrue(fieldSubject.getField().accessFlags.isPublic());

            checkMethodIsRemoved(outerClass, getterAccessor);
            checkMethodIsRemoved(outerClass, setterAccessor);
          } else {
            assertTrue(fieldSubject.getField().accessFlags.isPrivate());

            checkMethodIsKept(outerClass, getterAccessor);
            checkMethodIsKept(outerClass, setterAccessor);
          }
        });
  }

  @Test
  public void testCompanionProperty_internalPropertyIsAlwaysInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionPropertiesKt",
        "companionProperties_useInternalProp");
    runTest(
        PROPERTIES_PACKAGE_NAME,
        mainClass,
        disableClassStaticizer,
        (app) -> {
          CodeInspector codeInspector = new CodeInspector(app);
          ClassSubject outerClass =
              checkClassIsKept(codeInspector, testedClass.getOuterClassName());
          String propertyName = "internalProp";
          FieldSubject fieldSubject = checkFieldIsKept(outerClass, JAVA_LANG_STRING, propertyName);
          assertTrue(fieldSubject.getField().accessFlags.isStatic());

          MemberNaming.MethodSignature getterAccessor =
              testedClass.getGetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);
          MemberNaming.MethodSignature setterAccessor =
              testedClass.getSetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);

          if (allowAccessModification) {
            assertTrue(fieldSubject.getField().accessFlags.isPublic());
            checkMethodIsRemoved(outerClass, getterAccessor);
            checkMethodIsRemoved(outerClass, setterAccessor);
          } else {
            assertTrue(fieldSubject.getField().accessFlags.isPrivate());
            checkMethodIsKept(outerClass, getterAccessor);
            checkMethodIsKept(outerClass, setterAccessor);
          }
        });
  }

  @Test
  public void testCompanionProperty_publicPropertyIsAlwaysInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionPropertiesKt",
        "companionProperties_usePublicProp");
    runTest(
        PROPERTIES_PACKAGE_NAME,
        mainClass,
        disableClassStaticizer,
        (app) -> {
          CodeInspector codeInspector = new CodeInspector(app);
          ClassSubject outerClass =
              checkClassIsKept(codeInspector, testedClass.getOuterClassName());
          String propertyName = "publicProp";
          FieldSubject fieldSubject = checkFieldIsKept(outerClass, JAVA_LANG_STRING, propertyName);
          assertTrue(fieldSubject.getField().accessFlags.isStatic());

          MemberNaming.MethodSignature getterAccessor =
              testedClass.getGetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);
          MemberNaming.MethodSignature setterAccessor =
              testedClass.getSetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);

          if (allowAccessModification) {
            assertTrue(fieldSubject.getField().accessFlags.isPublic());
            checkMethodIsRemoved(outerClass, getterAccessor);
            checkMethodIsRemoved(outerClass, setterAccessor);
          } else {
            assertTrue(fieldSubject.getField().accessFlags.isPrivate());
            checkMethodIsKept(outerClass, getterAccessor);
            checkMethodIsKept(outerClass, setterAccessor);
          }
        });
  }

  @Test
  public void testCompanionLateInitProperty_privatePropertyIsAlwaysInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_LATE_INIT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionLateInitPropertiesKt",
        "companionLateInitProperties_usePrivateLateInitProp");
    runTest(
        PROPERTIES_PACKAGE_NAME,
        mainClass,
        disableClassStaticizer,
        (app) -> {
          CodeInspector codeInspector = new CodeInspector(app);
          ClassSubject outerClass =
              checkClassIsKept(codeInspector, testedClass.getOuterClassName());
          String propertyName = "privateLateInitProp";
          FieldSubject fieldSubject = checkFieldIsKept(outerClass, JAVA_LANG_STRING, propertyName);
          assertTrue(fieldSubject.getField().accessFlags.isStatic());

          MemberNaming.MethodSignature getterAccessor =
              testedClass.getGetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);
          MemberNaming.MethodSignature setterAccessor =
              testedClass.getSetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);
          if (allowAccessModification) {
            assertTrue(fieldSubject.getField().accessFlags.isPublic());
            checkMethodIsRemoved(outerClass, getterAccessor);
            checkMethodIsRemoved(outerClass, setterAccessor);
          } else {
            assertTrue(fieldSubject.getField().accessFlags.isPrivate());
            checkMethodIsKept(outerClass, getterAccessor);
            checkMethodIsKept(outerClass, setterAccessor);
          }
        });
  }

  @Test
  public void testCompanionLateInitProperty_internalPropertyIsAlwaysInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_LATE_INIT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionLateInitPropertiesKt",
        "companionLateInitProperties_useInternalLateInitProp");
    runTest(PROPERTIES_PACKAGE_NAME, mainClass, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject outerClass = checkClassIsKept(codeInspector, testedClass.getOuterClassName());
      String propertyName = "internalLateInitProp";
      FieldSubject fieldSubject = checkFieldIsKept(outerClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());
      assertTrue(fieldSubject.getField().accessFlags.isPublic());

      MemberNaming.MethodSignature getterAccessor = testedClass
          .getGetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);
      MemberNaming.MethodSignature setterAccessor = testedClass
          .getSetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);

      assertTrue(fieldSubject.getField().accessFlags.isPublic());
      checkMethodIsRemoved(outerClass, getterAccessor);
      checkMethodIsRemoved(outerClass, setterAccessor);
    });
  }

  @Test
  public void testCompanionLateInitProperty_publicPropertyIsAlwaysInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_LATE_INIT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionLateInitPropertiesKt",
        "companionLateInitProperties_usePublicLateInitProp");
    runTest(PROPERTIES_PACKAGE_NAME, mainClass, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject outerClass = checkClassIsKept(codeInspector, testedClass.getOuterClassName());
      String propertyName = "publicLateInitProp";
      FieldSubject fieldSubject = checkFieldIsKept(outerClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());
      assertTrue(fieldSubject.getField().accessFlags.isPublic());

      MemberNaming.MethodSignature getterAccessor = testedClass
          .getGetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);
      MemberNaming.MethodSignature setterAccessor = testedClass
          .getSetterAccessorForProperty(propertyName, AccessorKind.FROM_COMPANION);

      assertTrue(fieldSubject.getField().accessFlags.isPublic());
      checkMethodIsRemoved(outerClass, getterAccessor);
      checkMethodIsRemoved(outerClass, setterAccessor);
    });
  }

  @Test
  public void testAccessor() throws Exception {
    TestKotlinCompanionClass testedClass = ACCESSOR_COMPANION_PROPERTY_CLASS;
    String mainClass =
        addMainToClasspath("accessors.AccessorKt", "accessor_accessPropertyFromCompanionClass");
    runTest(
        "accessors",
        mainClass,
        disableClassStaticizer,
        app -> {
          // The classes are removed entirely as a result of member value propagation, inlining, and
          // the fact that the classes do not have observable side effects.
          CodeInspector codeInspector = new CodeInspector(app);
          checkClassIsRemoved(codeInspector, testedClass.getOuterClassName());
          checkClassIsRemoved(codeInspector, testedClass.getClassName());
        });
  }

  @Test
  @Ignore("b/74103342")
  public void testAccessorFromPrivate() throws Exception {
    TestKotlinCompanionClass testedClass = ACCESSOR_COMPANION_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("accessors.AccessorKt",
        "accessor_accessPropertyFromOuterClass");
    runTest("accessors", mainClass, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject outerClass = checkClassIsKept(codeInspector, testedClass.getOuterClassName());
      ClassSubject companionClass = checkClassIsKept(codeInspector, testedClass.getClassName());
      String propertyName = "property";
      FieldSubject fieldSubject = checkFieldIsKept(outerClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      // We cannot inline the getter because we don't know if NPE is preserved.
      MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
      checkMethodIsKept(companionClass, getter);

      // We should always inline the static accessor method.
      MemberNaming.MethodSignature getterAccessor =
          testedClass.getGetterAccessorForProperty(propertyName, AccessorKind.FROM_INNER);
      checkMethodIsRemoved(companionClass, getterAccessor);

      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
      }
    });
  }

  @Test
  public void testAccessorForInnerClassIsRemovedWhenNotUsed() throws Exception {
    String mainClass =
        addMainToClasspath(
            "accessors.PropertyAccessorForInnerClassKt", "noUseOfPropertyAccessorFromInnerClass");
    runTest(
        "accessors",
        mainClass,
        (app) -> {
          CodeInspector codeInspector = new CodeInspector(app);

          // Class is removed because the instantiation of the inner class has no side effects.
          checkClassIsRemoved(codeInspector, PROPERTY_ACCESS_FOR_INNER_CLASS.getClassName());
        });
  }

  @Test
  @Ignore("b/74103342")
  public void testPrivatePropertyAccessorForInnerClassCanBeInlined() throws Exception {
    TestKotlinClass testedClass = PROPERTY_ACCESS_FOR_INNER_CLASS;
    String mainClass = addMainToClasspath(testedClass.className + "Kt",
        "usePrivatePropertyAccessorFromInnerClass");
    runTest("accessors", mainClass, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject classSubject = checkClassIsKept(codeInspector, testedClass.getClassName());

      String propertyName = "privateProp";
      FieldSubject fieldSubject = checkFieldIsKept(classSubject, JAVA_LANG_STRING,
          propertyName);
      assertFalse(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getterAccessor =
          testedClass.getGetterAccessorForProperty(propertyName, AccessorKind.FROM_INNER);
      MemberNaming.MethodSignature setterAccessor =
          testedClass.getSetterAccessorForProperty(propertyName, AccessorKind.FROM_INNER);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsRemoved(classSubject, getterAccessor);
        checkMethodIsRemoved(classSubject, setterAccessor);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsKept(classSubject, getterAccessor);
        checkMethodIsKept(classSubject, setterAccessor);
      }
    });
  }

  @Test
  @Ignore("b/74103342")
  public void testPrivateLateInitPropertyAccessorForInnerClassCanBeInlined() throws Exception {
    TestKotlinClass testedClass = PROPERTY_ACCESS_FOR_INNER_CLASS;
    String mainClass = addMainToClasspath(testedClass.className + "Kt",
        "usePrivateLateInitPropertyAccessorFromInnerClass");
    runTest("accessors", mainClass, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject classSubject = checkClassIsKept(codeInspector, testedClass.getClassName());

      String propertyName = "privateLateInitProp";
      FieldSubject fieldSubject = checkFieldIsKept(classSubject, JAVA_LANG_STRING,
          propertyName);
      assertFalse(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getterAccessor =
          testedClass.getGetterAccessorForProperty(propertyName, AccessorKind.FROM_INNER);
      MemberNaming.MethodSignature setterAccessor =
          testedClass.getSetterAccessorForProperty(propertyName, AccessorKind.FROM_INNER);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsRemoved(classSubject, getterAccessor);
        checkMethodIsRemoved(classSubject, setterAccessor);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsKept(classSubject, getterAccessor);
        checkMethodIsKept(classSubject, setterAccessor);
      }
    });
  }

  @Test
  @Ignore("b/74103342")
  public void testAccessorForLambdaIsRemovedWhenNotUsed() throws Exception {
    TestKotlinClass testedClass = PROPERTY_ACCESS_FOR_LAMBDA_CLASS;
    String mainClass = addMainToClasspath(testedClass.className + "Kt",
        "noUseOfPropertyAccessorFromLambda");
    runTest("accessors", mainClass, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject classSubject = checkClassIsKept(codeInspector, testedClass.getClassName());
      String propertyName = "property";

      MemberNaming.MethodSignature getterAccessor =
          testedClass.getGetterAccessorForProperty(propertyName, AccessorKind.FROM_LAMBDA);
      MemberNaming.MethodSignature setterAccessor =
          testedClass.getSetterAccessorForProperty(propertyName, AccessorKind.FROM_LAMBDA);

      checkMethodIsRemoved(classSubject, getterAccessor);
      checkMethodIsRemoved(classSubject, setterAccessor);
    });
  }

  @Test
  @Ignore("b/74103342")
  public void testAccessorForLambdaCanBeInlined() throws Exception {
    TestKotlinClass testedClass = PROPERTY_ACCESS_FOR_LAMBDA_CLASS;
    String mainClass = addMainToClasspath(testedClass.className + "Kt",
        "usePropertyAccessorFromLambda");
    runTest("accessors", mainClass, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject classSubject = checkClassIsKept(codeInspector, testedClass.getClassName());
      String propertyName = "property";
      FieldSubject fieldSubject = checkFieldIsKept(classSubject, JAVA_LANG_STRING, propertyName);
      assertFalse(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getterAccessor =
          testedClass.getGetterAccessorForProperty(propertyName, AccessorKind.FROM_LAMBDA);
      MemberNaming.MethodSignature setterAccessor =
          testedClass.getSetterAccessorForProperty(propertyName, AccessorKind.FROM_LAMBDA);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsRemoved(classSubject, getterAccessor);
        checkMethodIsRemoved(classSubject, setterAccessor);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsKept(classSubject, getterAccessor);
        checkMethodIsKept(classSubject, setterAccessor);
      }
    });
  }

  @Test
  public void testStaticFieldAccessorWithJasmin() throws Exception {
    JasminBuilder jasminBuilder = new JasminBuilder();
    ClassBuilder classBuilder = jasminBuilder.addClass("Foo");
    classBuilder.addDefaultConstructor();
    classBuilder.addStaticField("aField", "I", "5");
    classBuilder.addMainMethod(
        ".limit stack 1",
        "invokestatic Foo$Inner/readField()V",
        "return"
    );
    classBuilder.addStaticMethod("access$field", Collections.emptyList(), "I",
        ".limit stack 1",
        "getstatic Foo.aField I",
        "ireturn");

    classBuilder = jasminBuilder.addClass("Foo$Inner");
    classBuilder.addDefaultConstructor();
    classBuilder.addStaticMethod("readField", Collections.emptyList(), "V",
        ".limit stack 2",
        "getstatic java/lang/System.out Ljava/io/PrintStream;",
        "invokestatic Foo/access$field()I",
        "invokevirtual java/io/PrintStream/println(I)V",
        "return"
    );

    Path javaOutput = writeToJar(jasminBuilder);
    ProcessResult javaResult = ToolHelper.runJava(javaOutput, "Foo");
    if (javaResult.exitCode != 0) {
      System.err.println(javaResult.stderr);
      Assert.fail();
    }

    AndroidApp app = compileWithR8(jasminBuilder.build(),
        keepMainProguardConfiguration("Foo") + "\n-dontobfuscate");
    String artOutput = runOnArt(app, "Foo");
    assertEquals(javaResult.stdout, artOutput);
  }
}
