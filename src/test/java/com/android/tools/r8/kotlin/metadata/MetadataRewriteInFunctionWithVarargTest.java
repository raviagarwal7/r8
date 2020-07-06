// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.utils.codeinspector.Matchers.isExtensionFunction;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.KmFunctionSubject;
import com.android.tools.r8.utils.codeinspector.KmPackageSubject;
import com.android.tools.r8.utils.codeinspector.KmTypeProjectionSubject;
import com.android.tools.r8.utils.codeinspector.KmTypeSubject;
import com.android.tools.r8.utils.codeinspector.KmValueParameterSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInFunctionWithVarargTest extends KotlinMetadataTestBase {
  private static final String EXPECTED = StringUtils.lines("SomeClass::R8");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRewriteInFunctionWithVarargTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static Map<KotlinTargetVersion, Path> varargLibJarMap = new HashMap<>();

  @BeforeClass
  public static void createLibJar() throws Exception {
    String varargLibFolder = PKG_PREFIX + "/vararg_lib";
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path varargLibJar =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(getKotlinFileInTest(varargLibFolder, "lib"))
              .compile();
      varargLibJarMap.put(targetVersion, varargLibJar);
    }
  }

  @Test
  public void smokeTest() throws Exception {
    Path libJar = varargLibJarMap.get(targetVersion);

    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/vararg_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".vararg_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataInFunctionWithVararg() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(ToolHelper.getKotlinStdlibJar())
            .addProgramFiles(varargLibJarMap.get(targetVersion))
            // keep SomeClass#foo, since there is a method reference in the app.
            .addKeepRules("-keep class **.SomeClass { *** foo(...); }")
            // Keep LibKt, along with bar function.
            .addKeepRules("-keep class **.LibKt { *** bar(...); }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .addKeepAttributes(ProguardKeepAttributes.SIGNATURE)
            .addKeepAttributes(ProguardKeepAttributes.INNER_CLASSES)
            .addKeepAttributes(ProguardKeepAttributes.ENCLOSING_METHOD)
            .compile()
            .inspect(this::inspect)
            .writeToZip();

    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/vararg_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".vararg_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  private void inspect(CodeInspector inspector) {
    String className = PKG + ".vararg_lib.SomeClass";
    String libClassName = PKG + ".vararg_lib.LibKt";

    ClassSubject cls = inspector.clazz(className);
    assertThat(cls, isPresentAndNotRenamed());

    MethodSubject foo = cls.uniqueMethodWithName("foo");
    assertThat(foo, isPresentAndNotRenamed());

    ClassSubject libKt = inspector.clazz(libClassName);
    assertThat(libKt, isPresentAndNotRenamed());

    MethodSubject bar = libKt.uniqueMethodWithName("bar");
    assertThat(bar, isPresentAndNotRenamed());

    KmPackageSubject kmPackage = libKt.getKmPackage();
    assertThat(kmPackage, isPresent());

    // Unit bar(vararg String, (SomeClass, String) -> Unit)
    KmFunctionSubject kmFunction = kmPackage.kmFunctionWithUniqueName("bar");
    assertThat(kmFunction, not(isExtensionFunction()));
    List<KmValueParameterSubject> valueParameters = kmFunction.valueParameters();
    assertEquals(2, valueParameters.size());

    KmValueParameterSubject valueParameter = valueParameters.get(0);
    assertTrue(valueParameter.isVararg());
    assertEquals(KT_STRING, valueParameter.varargElementType().descriptor());

    assertEquals(KT_ARRAY, valueParameter.type().descriptor());
    List<KmTypeProjectionSubject> typeArguments = valueParameter.type().typeArguments();
    assertEquals(1, typeArguments.size());
    KmTypeSubject typeArgument = typeArguments.get(0).type();
    assertEquals(KT_STRING, typeArgument.descriptor());

    valueParameter = valueParameters.get(1);
    assertFalse(valueParameter.isVararg());
    typeArguments = valueParameter.type().typeArguments();
    assertEquals(3, typeArguments.size());

    typeArgument = typeArguments.get(0).type();
    assertEquals(cls.getFinalDescriptor(), typeArgument.descriptor());
    typeArgument = typeArguments.get(1).type();
    assertEquals(KT_STRING, typeArgument.descriptor());
    typeArgument = typeArguments.get(2).type();
    assertEquals(KT_UNIT, typeArgument.descriptor());
  }
}
