// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping.sourcelibrary;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InvokeInstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.codeinspector.NewInstanceInstructionSubject;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApplyMappingTest extends TestBase {

  private static final String MAPPING = "test-mapping.txt";

  private static final Path MINIFICATION_JAR =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "minification" + FileUtils.JAR_EXTENSION);

  private static final Path NAMING001_JAR =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "naming001" + FileUtils.JAR_EXTENSION);

  private static final Path NAMING044_JAR =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "naming044" + FileUtils.JAR_EXTENSION);

  private static final Path APPLYMAPPING044_JAR =
      Paths.get(ToolHelper.EXAMPLES_BUILD_DIR, "applymapping044" + FileUtils.JAR_EXTENSION);

  private Path out;

  private Backend backend;

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return ToolHelper.getBackends();
  }

  public ApplyMappingTest(Backend backend) {
    this.backend = backend;
  }

  @Before
  public void setup() throws IOException {
    out = temp.newFolder("out").toPath();
  }

  @Test
  public void test044_apply() throws Exception {
    Path flag =
        Paths.get(ToolHelper.EXAMPLES_DIR, "applymapping044", "keep-rules-apply-mapping.txt");
    AndroidApp outputApp =
        runR8(
            getCommandForInstrumentation(out, flag, NAMING044_JAR, APPLYMAPPING044_JAR)
                .setDisableMinification(true)
                .setOutput(out, outputMode(Backend.CF))
                .build());

    // Make sure the given proguard map is indeed applied.
    CodeInspector inspector = createDexInspector(outputApp);
    MethodSubject main = inspector.clazz("applymapping044.Main").method(CodeInspector.MAIN);
    Iterator<InvokeInstructionSubject> iterator =
        main.iterateInstructions(InstructionSubject::isInvoke);
    // B#m() -> y#n()
    InvokeInstructionSubject m = iterator.next();
    assertEquals("naming044.y", m.holder().toString());
    assertEquals("n", m.invokedMethod().name.toString());
    // sub.SubB#n() -> z.y#m()
    InvokeInstructionSubject n = iterator.next();
    assertEquals("naming044.z.y", n.holder().toString());
    assertEquals("m", n.invokedMethod().name.toString());
    // Skip A#<init>
    iterator.next();
    // Skip B#<init>
    iterator.next();
    // B#f(A) -> y#p(x)
    InvokeInstructionSubject f = iterator.next();
    DexType a1 = f.invokedMethod().proto.parameters.values[0];
    assertEquals("naming044.x", a1.toString());
    assertEquals("p", f.invokedMethod().name.toString());
    // Skip AsubB#<init>
    iterator.next();
    // AsubB#f(A) -> AsubB#p(x)
    InvokeInstructionSubject overloaded_f = iterator.next();
    DexMethod aSubB_f = overloaded_f.invokedMethod();
    DexType a2 = aSubB_f.proto.parameters.values[0];
    assertEquals("naming044.x", a2.toString());
    assertEquals("p", aSubB_f.name.toString());
    // B#f == AsubB#f
    assertEquals(f.invokedMethod().name.toString(), aSubB_f.name.toString());
    // sub.SubB#<init>(int) -> z.y<init>(int)
    InvokeInstructionSubject subBinit = iterator.next();
    assertEquals("naming044.z.y", subBinit.holder().toString());
    // sub.SubB#f(A) -> SubB#p(x)
    InvokeInstructionSubject original_f = iterator.next();
    DexMethod subB_f = original_f.invokedMethod();
    DexType a3 = subB_f.proto.parameters.values[0];
    assertEquals(a2, a3);
    assertEquals("p", original_f.invokedMethod().name.toString());
  }

  private static CodeInspector createDexInspector(AndroidApp outputApp)
      throws IOException, ExecutionException {
    return new CodeInspector(outputApp);
  }

  @Test
  public void test_naming001_rule105() throws Exception {
    // keep rules to reserve D and E, along with a proguard map.
    Path flag = Paths.get(ToolHelper.EXAMPLES_DIR, "naming001", "keep-rules-105.txt");
    Path proguardMap = out.resolve(MAPPING);
    AndroidApp outputApp =
        runR8(
            ToolHelper.addProguardConfigurationConsumer(
                    getCommandForApps(out, flag, NAMING001_JAR).setDisableMinification(true),
                    pgConfig -> {
                      pgConfig.setPrintMapping(true);
                      pgConfig.setPrintMappingFile(proguardMap);
                    })
                .build());

    // Make sure the given proguard map is indeed applied.
    CodeInspector inspector = createDexInspector(outputApp);
    ClassSubject classD = inspector.clazz("naming001.D");
    assertThat(classD, isPresent());
    // D must not be renamed
    assertEquals("naming001.D", classD.getFinalName());
    MethodSubject main = classD.method(CodeInspector.MAIN);
    Iterator<InvokeInstructionSubject> iterator =
        main.iterateInstructions(InstructionSubject::isInvoke);
    // mapping-105 simply includes: naming001.D#keep -> peek
    // naming001.E extends D, hence its keep() should be renamed to peek as well.
    // Check E#<init> is not renamed.
    InvokeInstructionSubject init = iterator.next();
    assertEquals("Lnaming001/E;-><init>()V", init.invokedMethod().toSmaliString());
    // E#keep() should be replaced with peek by applying the map.
    InvokeInstructionSubject m = iterator.next();
    assertEquals("peek", m.invokedMethod().name.toSourceString());
  }

  @Test
  public void test_naming001_rule106() throws Exception {
    // keep rules just to rename E
    Path flag = Paths.get(ToolHelper.EXAMPLES_DIR, "naming001", "keep-rules-106.txt");
    Path proguardMap = out.resolve(MAPPING);
    AndroidApp outputApp =
        runR8(
            ToolHelper.addProguardConfigurationConsumer(
                    getCommandForApps(out, flag, NAMING001_JAR),
                    pgConfig -> {
                      pgConfig.disableShrinking();
                      pgConfig.setPrintMapping(true);
                      pgConfig.setPrintMappingFile(proguardMap);
                    })
                .build());

    // Make sure the given proguard map is indeed applied.
    CodeInspector inspector = createDexInspector(outputApp);
    MethodSubject main = inspector.clazz("naming001.D").method(CodeInspector.MAIN);

    Iterator<InstructionSubject> iterator = main.iterateInstructions();
    // naming001.E is renamed to a.a, so first instruction must be: new-instance La/a;
    NewInstanceInstructionSubject newInstance = null;
    while (iterator.hasNext()) {
      InstructionSubject instruction = iterator.next();
      if (instruction.isNewInstance()) {
        newInstance = (NewInstanceInstructionSubject) instruction;
        break;
      }
    }
    assertNotNull(newInstance);
    assertEquals( "La/a;", newInstance.getType().toSmaliString());
  }

  private R8Command.Builder getCommandForInstrumentation(
      Path out, Path flag, Path mainApp, Path instrApp) throws IOException {
    return R8Command.builder()
        .addLibraryFiles(runtimeJar(backend), mainApp)
        .addProgramFiles(instrApp)
        .setOutput(out, outputMode(backend))
        .addProguardConfigurationFiles(flag);
  }

  private R8Command.Builder getCommandForApps(Path out, Path flag, Path... jars)
      throws IOException {
    return R8Command.builder()
        .addLibraryFiles(runtimeJar(backend))
        .addProgramFiles(jars)
        .setOutput(out, outputMode(backend))
        .addProguardConfigurationFiles(flag);
  }

  private static AndroidApp runR8(R8Command command) throws CompilationFailedException {
    return ToolHelper.runR8(
        command,
        options -> {
          // Disable inlining to make this test not depend on inlining decisions.
          options.enableInlining = false;
        });
  }
}
