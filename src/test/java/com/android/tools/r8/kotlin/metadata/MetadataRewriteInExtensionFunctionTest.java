// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.utils.codeinspector.Matchers.isExtensionFunction;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.KmClassSubject;
import com.android.tools.r8.utils.codeinspector.KmFunctionSubject;
import com.android.tools.r8.utils.codeinspector.KmPackageSubject;
import com.android.tools.r8.utils.codeinspector.KmTypeProjectionSubject;
import com.android.tools.r8.utils.codeinspector.KmTypeSubject;
import com.android.tools.r8.utils.codeinspector.KmValueParameterSubject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInExtensionFunctionTest extends KotlinMetadataTestBase {
  private static final String EXPECTED = StringUtils.lines("do stuff", "do stuff", "do stuff");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRewriteInExtensionFunctionTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static final Map<KotlinTargetVersion, Path> extLibJarMap = new HashMap<>();

  @BeforeClass
  public static void createLibJar() throws Exception {
    String extLibFolder = PKG_PREFIX + "/extension_function_lib";
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path extLibJar =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(getKotlinFileInTest(extLibFolder, "B"))
              .compile();
      extLibJarMap.put(targetVersion, extLibJar);
    }
  }

  @Test
  public void smokeTest() throws Exception {
    Path libJar = extLibJarMap.get(targetVersion);

    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/extension_function_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".extension_function_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  @Ignore("b/154300326")
  public void testMetadataInExtensionFunction_merged() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(extLibJarMap.get(targetVersion))
            // Keep the B class and its interface (which has the doStuff method).
            .addKeepRules("-keep class **.B")
            .addKeepRules("-keep class **.I { <methods>; }")
            // Keep the BKt extension function which requires metadata
            // to be called with Kotlin syntax from other kotlin code.
            .addKeepRules("-keep class **.BKt { <methods>; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .addKeepAttributes(ProguardKeepAttributes.SIGNATURE)
            .addKeepAttributes(ProguardKeepAttributes.INNER_CLASSES)
            .addKeepAttributes(ProguardKeepAttributes.ENCLOSING_METHOD)
            .compile()
            .inspect(this::inspectMerged)
            .writeToZip();

    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/extension_function_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".extension_function_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  private void inspectMerged(CodeInspector inspector) {
    String superClassName = PKG + ".extension_function_lib.Super";
    String bClassName = PKG + ".extension_function_lib.B";

    assertThat(inspector.clazz(superClassName), not(isPresent()));

    ClassSubject impl = inspector.clazz(bClassName);
    assertThat(impl, isPresentAndNotRenamed());
    // API entry is kept, hence the presence of Metadata.
    KmClassSubject kmClass = impl.getKmClass();
    assertThat(kmClass, isPresent());
    List<ClassSubject> superTypes = kmClass.getSuperTypes();
    assertTrue(superTypes.stream().noneMatch(
        supertype -> supertype.getFinalDescriptor().contains("Super")));

    inspectExtensions(inspector);
  }

  @Test
  public void testMetadataInExtensionFunction_renamed() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(ToolHelper.getKotlinStdlibJar())
            .addProgramFiles(extLibJarMap.get(targetVersion))
            // Keep the B class and its interface (which has the doStuff method).
            .addKeepRules("-keep class **.B")
            .addKeepRules("-keep class **.I { <methods>; }")
            // Keep Super, but allow minification.
            .addKeepRules("-keep,allowobfuscation class **.Super")
            // Keep the BKt extension function which requires metadata
            // to be called with Kotlin syntax from other kotlin code.
            .addKeepRules("-keep class **.BKt { <methods>; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .addKeepAttributes(ProguardKeepAttributes.SIGNATURE)
            .addKeepAttributes(ProguardKeepAttributes.INNER_CLASSES)
            .addKeepAttributes(ProguardKeepAttributes.ENCLOSING_METHOD)
            .compile()
            .inspect(this::inspectRenamed)
            .writeToZip();

    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/extension_function_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".extension_function_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  private void inspectRenamed(CodeInspector inspector) {
    String superClassName = PKG + ".extension_function_lib.Super";
    String bClassName = PKG + ".extension_function_lib.B";

    ClassSubject sup = inspector.clazz(superClassName);
    assertThat(sup, isPresentAndRenamed());

    ClassSubject impl = inspector.clazz(bClassName);
    assertThat(impl, isPresentAndNotRenamed());
    // API entry is kept, hence the presence of Metadata.
    KmClassSubject kmClass = impl.getKmClass();
    assertThat(kmClass, isPresent());
    List<ClassSubject> superTypes = kmClass.getSuperTypes();
    assertTrue(superTypes.stream().noneMatch(
        supertype -> supertype.getFinalDescriptor().contains("Super")));
    assertTrue(superTypes.stream().anyMatch(
        supertype -> supertype.getFinalDescriptor().equals(sup.getFinalDescriptor())));

    inspectExtensions(inspector);
  }

  private void inspectExtensions(CodeInspector inspector) {
    String bClassName = PKG + ".extension_function_lib.B";
    String bKtClassName = PKG + ".extension_function_lib.BKt";

    ClassSubject impl = inspector.clazz(bClassName);
    assertThat(impl, isPresent());

    ClassSubject bKt = inspector.clazz(bKtClassName);
    assertThat(bKt, isPresentAndNotRenamed());
    // API entry is kept, hence the presence of Metadata.
    KmPackageSubject kmPackage = bKt.getKmPackage();
    assertThat(kmPackage, isPresent());

    KmFunctionSubject kmFunction = kmPackage.kmFunctionExtensionWithUniqueName("extension");
    assertThat(kmFunction, isExtensionFunction());
    KmTypeSubject kmTypeSubject = kmFunction.receiverParameterType();
    assertEquals(impl.getFinalDescriptor(), kmTypeSubject.descriptor());

    kmFunction = kmPackage.kmFunctionExtensionWithUniqueName("csHash");
    assertThat(kmFunction, isExtensionFunction());
    kmTypeSubject = kmFunction.receiverParameterType();
    assertEquals(KT_CHAR_SEQUENCE, kmTypeSubject.descriptor());
    kmTypeSubject = kmFunction.returnType();
    assertEquals(KT_LONG, kmTypeSubject.descriptor());

    kmFunction = kmPackage.kmFunctionExtensionWithUniqueName("longArrayHash");
    assertThat(kmFunction, isExtensionFunction());
    kmTypeSubject = kmFunction.receiverParameterType();
    assertEquals(KT_LONG_ARRAY, kmTypeSubject.descriptor());
    kmTypeSubject = kmFunction.returnType();
    assertEquals(KT_LONG, kmTypeSubject.descriptor());

    // fun B.myApply(apply: B.() -> Unit): Unit
    // https://github.com/JetBrains/kotlin/blob/master/spec-docs/function-types.md#extension-functions
    kmFunction = kmPackage.kmFunctionExtensionWithUniqueName("myApply");
    assertThat(kmFunction, isExtensionFunction());
    kmTypeSubject = kmFunction.receiverParameterType();
    assertEquals(impl.getFinalDescriptor(), kmTypeSubject.descriptor());

    List<KmValueParameterSubject> valueParameters = kmFunction.valueParameters();
    assertEquals(1, valueParameters.size());

    KmValueParameterSubject valueParameter = valueParameters.get(0);
    assertEquals(KT_FUNCTION1, valueParameter.type().descriptor());
    List<KmTypeProjectionSubject> typeArguments = valueParameter.type().typeArguments();
    assertEquals(2, typeArguments.size());
    KmTypeSubject typeArgument = typeArguments.get(0).type();
    assertEquals(impl.getFinalDescriptor(), typeArgument.descriptor());
    typeArgument = typeArguments.get(1).type();
    assertEquals(KT_UNIT, typeArgument.descriptor());
  }
}
