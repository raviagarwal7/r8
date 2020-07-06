// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.StreamUtils;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ScriptEngineTest extends ScriptEngineTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ScriptEngineTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws IOException, CompilationFailedException, ExecutionException {
    Path path = temp.newFile("out.zip").toPath();
    testForR8(parameters.getBackend())
        .addInnerClasses(ScriptEngineTest.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters.getApiLevel())
        .addDataEntryResources(
            DataEntryResource.fromBytes(
                StringUtils.lines(MyScriptEngine1FactoryImpl.class.getTypeName()).getBytes(),
                "META-INF/services/" + ScriptEngineFactory.class.getTypeName(),
                Origin.unknown()))
        .addDataEntryResources(
            DataEntryResource.fromBytes(
                StringUtils.lines(MyScriptEngine2FactoryImpl.class.getTypeName()).getBytes(),
                "META-INF/services/" + ScriptEngineFactory.class.getTypeName(),
                Origin.unknown()))
        .apply(
            b -> {
              if (parameters.isDexRuntime()) {
                addRhinoForAndroid(b);
                b.allowDiagnosticWarningMessages();
              }
            })
        // TODO(b/136633154): This should work both with and without -dontobfuscate.
        .noMinification()
        // TODO(b/136633154): This should work both with and without -dontshrink.
        .noTreeShaking()
        .compile()
        .assertAllWarningMessagesMatch(
            anyOf(
                containsString("Missing class:"),
                containsString("it is required for default or static interface methods desugaring"),
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists.")))
        .writeToZip(path)
        .run(parameters.getRuntime(), TestClass.class)
        // TODO(b/136633154): This should provide 2 script engines on both runtimes. The use of
        //  the rhino-android library on Android will add the Rhino script engine, and the JVM
        //  comes with "Oracle Nashorn" included.
        .assertSuccessWithOutput(
            parameters.isCfRuntime()
                ? StringUtils.lines("MyEngine1", "MyEngine2", "Oracle Nashorn")
                : StringUtils.lines("Mozilla Rhino", "MyEngine1", "MyEngine2"));

    // TODO(b/136633154): On the JVM this should always be there as the service loading is in
    //  the library. On Android we should be able to rewrite the code and not have it.
    // Check that we still have META-INF/services/javax.script.ScriptEngineFactory.
    ZipFile zip = new ZipFile(path.toFile());
    ZipEntry entry = zip.getEntry("META-INF/services/" + ScriptEngineFactory.class.getTypeName());
    assertNotNull(entry);

    assertEquals(
        // For dex this also contains Rhino: com.sun.script.javascript.RhinoScriptEngineFactory.
        parameters.isCfRuntime() ? 2 : 3,
        StringUtils.splitLines(
                new String(StreamUtils.StreamToByteArrayClose(zip.getInputStream(entry))))
            .size());
  }

  static class TestClass {

    public static void main(String[] args) {
      List<String> factoryNames = new ArrayList<>();
      for (ScriptEngineFactory factory : new ScriptEngineManager().getEngineFactories()) {
        factoryNames.add(factory.getEngineName());
      }
      Collections.sort(factoryNames);
      for (String name : factoryNames) {
        System.out.println(name);
      }
    }
  }

  public static class MyScriptEngineFactoryBase implements ScriptEngineFactory {

    public final String variant;

    public MyScriptEngineFactoryBase(String variant) {
      this.variant = variant;
    }

    @Override
    public String getEngineName() {
      return "MyEngine" + variant;
    }

    @Override
    public String getEngineVersion() {
      return "0.1";
    }

    @Override
    public List<String> getExtensions() {
      return Collections.emptyList();
    }

    @Override
    public List<String> getMimeTypes() {
      List<String> result = new ArrayList<>();
      result.add("text/my-script-" + variant);
      return result;
    }

    @Override
    public List<String> getNames() {
      List<String> result = new ArrayList<>();
      result.add(getEngineName());
      return result;
    }

    @Override
    public String getLanguageName() {
      return "MyLanguage" + variant;
    }

    @Override
    public String getLanguageVersion() {
      return "0.1";
    }

    @Override
    public Object getParameter(String key) {
      return null;
    }

    @Override
    public String getMethodCallSyntax(String obj, String m, String... args) {
      return null;
    }

    @Override
    public String getOutputStatement(String toDisplay) {
      return null;
    }

    @Override
    public String getProgram(String... statements) {
      return null;
    }

    @Override
    public ScriptEngine getScriptEngine() {
      return new MyScriptEngine(variant);
    }
  }

  public static class MyScriptEngine1FactoryImpl extends MyScriptEngineFactoryBase {
    public MyScriptEngine1FactoryImpl() {
      super("1");
    }
  }

  public static class MyScriptEngine2FactoryImpl extends MyScriptEngineFactoryBase {
    public MyScriptEngine2FactoryImpl() {
      super("2");
    }
  }

  public static class MyScriptEngine implements ScriptEngine {

    public final String variant;

    public MyScriptEngine(String variant) {
      this.variant = variant;
    }

    @Override
    public Object eval(String script, ScriptContext context) throws ScriptException {
      throw new ScriptException("Not implemented");
    }

    @Override
    public Object eval(Reader reader, ScriptContext context) throws ScriptException {
      throw new ScriptException("Not implemented");
    }

    @Override
    public Object eval(String script) throws ScriptException {
      return "Engine " + variant + " evaluation of: " + script;
    }

    @Override
    public Object eval(Reader reader) throws ScriptException {
      throw new ScriptException("Not implemented");
    }

    @Override
    public Object eval(String script, Bindings n) throws ScriptException {
      throw new ScriptException("Not implemented");
    }

    @Override
    public Object eval(Reader reader, Bindings n) throws ScriptException {
      throw new ScriptException("Not implemented");
    }

    @Override
    public void put(String key, Object value) {}

    @Override
    public Object get(String key) {
      return null;
    }

    @Override
    public Bindings getBindings(int scope) {
      return null;
    }

    @Override
    public void setBindings(Bindings bindings, int scope) {}

    @Override
    public Bindings createBindings() {
      return null;
    }

    @Override
    public ScriptContext getContext() {
      return null;
    }

    @Override
    public void setContext(ScriptContext context) {}

    @Override
    public ScriptEngineFactory getFactory() {
      return null;
    }
  }
}
