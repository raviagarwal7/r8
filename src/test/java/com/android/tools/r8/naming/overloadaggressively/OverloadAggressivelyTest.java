// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.overloadaggressively;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class OverloadAggressivelyTest extends TestBase {

  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return ToolHelper.getBackends();
  }

  public OverloadAggressivelyTest(Backend backend) {
    this.backend = backend;
  }

  private AndroidApp runR8(AndroidApp app, Class main, Path out, boolean overloadaggressively)
      throws Exception {
    R8Command command =
        ToolHelper.addProguardConfigurationConsumer(
                ToolHelper.prepareR8CommandBuilder(app),
                pgConfig -> {
                  pgConfig.setPrintMapping(true);
                  pgConfig.setPrintMappingFile(out.resolve(ToolHelper.DEFAULT_PROGUARD_MAP_FILE));
                })
            .addProguardConfiguration(
                ImmutableList.of(
                    keepMainProguardConfiguration(main),
                    overloadaggressively ? "-overloadaggressively" : ""),
                Origin.unknown())
            .setOutput(out, outputMode(backend))
            .addLibraryFiles(TestBase.runtimeJar(backend))
            .build();
    return ToolHelper.runR8(command, o -> {
      o.enableInlining = false;
      o.forceProguardCompatibility = true;
    });
  }

  private ProcessResult runRaw(AndroidApp app, String main) throws IOException {
    if (backend == Backend.DEX) {
      return runOnArtRaw(app, main);
    } else {
      assert backend == Backend.CF;
      return runOnJavaRaw(app, main, Collections.emptyList());
    }
  }

  private void fieldUpdater(boolean overloadaggressively) throws Exception {
    byte[][] classes = {
        ToolHelper.getClassAsBytes(FieldUpdater.class),
        ToolHelper.getClassAsBytes(A.class),
        ToolHelper.getClassAsBytes(B.class)
    };
    AndroidApp originalApp = buildAndroidApp(classes);
    Path out = temp.getRoot().toPath();
    AndroidApp processedApp = runR8(originalApp, FieldUpdater.class, out, overloadaggressively);

    CodeInspector codeInspector = new CodeInspector(processedApp);
    ClassSubject a = codeInspector.clazz(A.class.getCanonicalName());
    DexEncodedField f1 = a.field("int", "f1").getField();
    assertNotNull(f1);
    DexEncodedField f2 = a.field("java.lang.Object", "f2").getField();
    assertNotNull(f2);
    // TODO(b/72858955): due to the potential reflective access, they should have different names
    //   by R8's improved reflective access detection or via keep rules.
    assertEquals(overloadaggressively, f1.field.name == f2.field.name);
    DexEncodedField f3 = a.field(B.class.getCanonicalName(), "f3").getField();
    assertNotNull(f3);
    // TODO(b/72858955): ditto
    assertEquals(overloadaggressively, f1.field.name == f3.field.name);
    // TODO(b/72858955): ditto
    assertEquals(overloadaggressively, f2.field.name == f3.field.name);

    String main = FieldUpdater.class.getCanonicalName();
    ProcessResult javaOutput = runOnJavaRaw(main, classes);
    assertEquals(0, javaOutput.exitCode);
    ProcessResult output = runRaw(processedApp, main);
    // TODO(b/72858955): eventually, R8 should avoid this field resolution conflict.
    if (overloadaggressively) {
      assertNotEquals(0, output.exitCode);
      assertTrue(output.stderr.contains("ClassCastException"));
    } else {
      assertEquals(0, output.exitCode);
      assertEquals(javaOutput.stdout.trim(), output.stdout.trim());
      // ART may dump its own debugging info through stderr.
      // assertEquals(javaOutput.stderr.trim(), artOutput.stderr.trim());
    }
  }

  @Test
  public void testFieldUpdater_aggressively() throws Exception {
    fieldUpdater(true);
  }

  @Test
  public void testFieldUpdater_not_aggressively() throws Exception {
    fieldUpdater(false);
  }

  private void fieldResolution(boolean overloadaggressively) throws Exception {
    byte[][] classes = {
        ToolHelper.getClassAsBytes(FieldResolution.class),
        ToolHelper.getClassAsBytes(A.class),
        ToolHelper.getClassAsBytes(B.class)
    };
    AndroidApp originalApp = buildAndroidApp(classes);
    Path out = temp.getRoot().toPath();
    AndroidApp processedApp = runR8(originalApp, FieldResolution.class, out, overloadaggressively);

    CodeInspector codeInspector = new CodeInspector(processedApp);
    ClassSubject a = codeInspector.clazz(A.class.getCanonicalName());
    DexEncodedField f1 = a.field("int", "f1").getField();
    assertNotNull(f1);
    DexEncodedField f3 = a.field(B.class.getCanonicalName(), "f3").getField();
    assertNotNull(f3);
    // TODO(b/72858955): due to the potential reflective access, they should have different names
    //   by R8's improved reflective access detection or via keep rules.
    assertEquals(overloadaggressively, f1.field.name == f3.field.name);

    String main = FieldResolution.class.getCanonicalName();
    ProcessResult javaOutput = runOnJavaRaw(main, classes);
    assertEquals(0, javaOutput.exitCode);
    ProcessResult output = runRaw(processedApp, main);
    // TODO(b/72858955): R8 should avoid field resolution conflict even w/ -overloadaggressively.
    if (overloadaggressively) {
      assertNotEquals(0, output.exitCode);
      assertTrue(output.stderr.contains("IllegalArgumentException"));
    } else {
      assertEquals(0, output.exitCode);
      assertEquals(javaOutput.stdout.trim(), output.stdout.trim());
      // ART may dump its own debugging info through stderr.
      // assertEquals(javaOutput.stderr.trim(), artOutput.stderr.trim());
    }
  }

  @Test
  public void testFieldResolution_aggressively() throws Exception {
    fieldResolution(true);
  }

  @Test
  public void testFieldResolution_not_aggressively() throws Exception {
    fieldResolution(false);
  }

  private void methodResolution(boolean overloadaggressively) throws Exception {
    byte[][] classes = {
        ToolHelper.getClassAsBytes(MethodResolution.class),
        ToolHelper.getClassAsBytes(B.class)
    };
    AndroidApp originalApp = buildAndroidApp(classes);
    Path out = temp.getRoot().toPath();
    AndroidApp processedApp = runR8(originalApp, MethodResolution.class, out, overloadaggressively);

    CodeInspector codeInspector = new CodeInspector(processedApp);
    ClassSubject b = codeInspector.clazz(B.class.getCanonicalName());
    DexEncodedMethod m1 =
        b.method("int", "getF1", ImmutableList.of()).getMethod();
    assertNotNull(m1);
    DexEncodedMethod m2 =
        b.method("java.lang.Object", "getF2", ImmutableList.of()).getMethod();
    // TODO(b/72858955): due to the potential reflective access, they should have different names.
    assertEquals(overloadaggressively, m1.method.name == m2.method.name);
    DexEncodedMethod m3 =
        b.method("java.lang.String", "getF3", ImmutableList.of()).getMethod();
    assertNotNull(m3);
    // TODO(b/72858955): ditto
    assertEquals(overloadaggressively, m1.method.name == m3.method.name);
    // TODO(b/72858955): ditto
    assertEquals(overloadaggressively, m2.method.name == m3.method.name);

    String main = MethodResolution.class.getCanonicalName();
    ProcessResult javaOutput = runOnJavaRaw(main, classes);
    assertEquals(0, javaOutput.exitCode);
    ProcessResult output = runRaw(processedApp, main);
    // TODO(b/72858955): R8 should avoid method resolution conflict even w/ -overloadaggressively.
    if (overloadaggressively) {
      assertEquals(0, output.exitCode);
      assertNotEquals(javaOutput.stdout.trim(), output.stdout.trim());
    } else {
      assertEquals(0, output.exitCode);
      assertEquals(javaOutput.stdout.trim(), output.stdout.trim());
      // ART may dump its own debugging info through stderr.
      // assertEquals(javaOutput.stderr.trim(), artOutput.stderr.trim());
    }
  }

  @Test
  public void testMethodResolution_aggressively() throws Exception {
    assumeTrue(backend == Backend.CF);
    methodResolution(true);
  }

  @Test
  public void testMethodResolution_not_aggressively() throws Exception {
    methodResolution(false);
  }
}
