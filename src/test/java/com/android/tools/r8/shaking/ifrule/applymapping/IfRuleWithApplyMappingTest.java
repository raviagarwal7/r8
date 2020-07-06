// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

public class IfRuleWithApplyMappingTest extends TestBase {

  private static Path mappingFile;

  @Before
  public void setup() throws Exception {
    // Mapping file that describes that Runnable has been renamed to A.
    mappingFile = temp.newFolder().toPath().resolve("mapping.txt");
    FileUtils.writeTextFile(
        mappingFile, Runnable.class.getTypeName() + " -> " + A.class.getTypeName() + ":");
  }

  @Test
  public void testProguard() throws Exception {
    testForProguard()
        .addProgramClasses(IfRuleWithApplyMappingTestClass.class)
        .addKeepMainRule(IfRuleWithApplyMappingTestClass.class)
        .addKeepRules(
            "-if class " + IfRuleWithApplyMappingTestClass.class.getTypeName(),
            "-keep class " + IfRuleWithApplyMappingTestClass.class.getTypeName() + " {",
            "  public void method(" + Runnable.class.getTypeName() + ");",
            "}",
            "-applymapping " + mappingFile.toAbsolutePath())
        .compile()
        .inspect(this::inspect);
  }

  @Ignore("b/117403482")
  @Test
  public void testR8() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(IfRuleWithApplyMappingTestClass.class)
        .addKeepMainRule(IfRuleWithApplyMappingTestClass.class)
        .addKeepRules(
            "-if class " + IfRuleWithApplyMappingTestClass.class.getTypeName(),
            "-keep class " + IfRuleWithApplyMappingTestClass.class.getTypeName() + " {",
            "  public void method(" + Runnable.class.getTypeName() + ");",
            "}",
            "-applymapping " + mappingFile.toAbsolutePath())
        .compile()
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    MethodSubject methodSubject =
        inspector.clazz(IfRuleWithApplyMappingTestClass.class).uniqueMethodWithName("method");
    assertThat(methodSubject, isPresent());
    assertEquals(
        A.class.getTypeName(), methodSubject.getMethod().method.proto.parameters.toSourceString());
  }
}

class IfRuleWithApplyMappingTestClass {

  public static void main(String[] args) {}

  public void method(Runnable obj) {}
}

class A {}
