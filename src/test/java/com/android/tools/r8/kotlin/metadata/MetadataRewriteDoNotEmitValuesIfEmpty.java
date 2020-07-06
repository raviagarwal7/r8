// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.AnnotationSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteDoNotEmitValuesIfEmpty extends KotlinMetadataTestBase {

  private final Set<String> nullableFieldKeys = Sets.newHashSet("pn", "xs", "xi");

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  private final TestParameters parameters;

  public MetadataRewriteDoNotEmitValuesIfEmpty(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  @Test
  public void testKotlinStdLib() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramFiles(ToolHelper.getKotlinStdlibJar())
        .setMinApi(parameters.getApiLevel())
        .addKeepAllClassesRule()
        .addKeepKotlinMetadata()
        .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
        .compile()
        .inspect(this::inspectEmptyValuesAreNotPresent);
  }

  private void inspectEmptyValuesAreNotPresent(CodeInspector inspector) {
    boolean seenNullableField = false;
    boolean seenMetadataWithoutNullableField = false;
    for (FoundClassSubject clazz : inspector.allClasses()) {
      AnnotationSubject annotation = clazz.annotation("kotlin.Metadata");
      if (annotation.isPresent()) {
        boolean seenNullableFieldForAnnotation = false;
        for (DexAnnotationElement element : annotation.getAnnotation().elements) {
          if (nullableFieldKeys.contains(element.name.toString())) {
            if (element.value.isDexValueInt()) {
              assertNotEquals(0, element.value.asDexValueInt().value);
            } else {
              String value = element.value.asDexValueString().value.toString();
              assertNotEquals("", value);
            }
            seenNullableField = true;
            seenNullableFieldForAnnotation = true;
          }
        }
        seenMetadataWithoutNullableField |= !seenNullableFieldForAnnotation;
      }
    }
    assertTrue(seenNullableField);
    assertTrue(seenMetadataWithoutNullableField);
  }
}
