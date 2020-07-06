// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.b123068484;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.naming.b123068484.data.Concrete1;
import com.android.tools.r8.naming.b123068484.runner.Runner;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class FieldRenamingTest extends TestBase {
  private static final Class<?> MAIN = Runner.class;
  private static final Class<?> CONCRETE1 = Concrete1.class;
  private static List<Path> CLASSES;
  private static final String EXPECTED_OUTPUT = StringUtils.lines("Runner");

  private final Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Object[] data() {
    return ToolHelper.getBackends();
  }

  public FieldRenamingTest(Backend backend) {
    this.backend = backend;
  }

  @BeforeClass
  public static void setUpClass() throws Exception {
    CLASSES = ImmutableList.<Path>builder()
        .addAll(ToolHelper.getClassFilesForTestPackage(MAIN.getPackage()))
        .addAll(ToolHelper.getClassFilesForTestPackage(CONCRETE1.getPackage()))
        .build();
  }

  @Test
  public void testProguard() throws Exception {
    Path inJar = temp.newFile("input.jar").toPath().toAbsolutePath();
    writeClassFilesToJar(inJar, CLASSES);
    testForProguard()
        .addProgramFiles(inJar)
        .addKeepMainRule(MAIN)
        .run(MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(this::inspect);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(backend)
        .addProgramFiles(CLASSES)
        .addKeepMainRule(MAIN)
        .run(MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    FieldSubject fld = inspector.clazz(CONCRETE1.getTypeName().replace("Concrete1", "Abs"))
        .uniqueFieldWithName("strField");
    assertThat(fld, isPresent());

    ClassSubject main = inspector.clazz(MAIN);
    assertThat(main, isPresent());
    MethodSubject methodSubject = main.mainMethod();
    assertThat(methodSubject, isPresent());

    methodSubject
        .iterateInstructions(InstructionSubject::isInstanceGet)
        .forEachRemaining(instructionSubject -> {
          String fieldName = instructionSubject.getField().name.toString();
          // All of those field references will be renamed.
          assertNotEquals("strField", fieldName);
          assertEquals(fld.getFinalName(), fieldName);
        });
  }

}
