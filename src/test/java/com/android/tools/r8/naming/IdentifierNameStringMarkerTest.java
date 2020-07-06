// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.code.AputObject;
import com.android.tools.r8.code.Const4;
import com.android.tools.r8.code.ConstClass;
import com.android.tools.r8.code.ConstString;
import com.android.tools.r8.code.InvokeDirect;
import com.android.tools.r8.code.InvokeStatic;
import com.android.tools.r8.code.InvokeVirtual;
import com.android.tools.r8.code.IputObject;
import com.android.tools.r8.code.NewArray;
import com.android.tools.r8.code.ReturnVoid;
import com.android.tools.r8.code.SgetObject;
import com.android.tools.r8.code.SputObject;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.smali.SmaliBuilder.MethodSignature;
import com.android.tools.r8.smali.SmaliTestBase;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;

public class IdentifierNameStringMarkerTest extends SmaliTestBase {

  private final String CLASS_NAME = "Example";
  private final static String BOO = "Boo";

  @Test
  public void instancePut_singleUseOperand() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    builder.addInstanceField("aClassName", "Ljava/lang/String;");
    MethodSignature init = builder.addInitializer(ImmutableList.of(), 1,
        "invoke-direct {p0}, Ljava/lang/Object;-><init>()V",
        "const-string v0, \"" + BOO + "\"",
        "iput-object v0, p0, LExample;->aClassName:Ljava/lang/String;",
        "return-void");

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class " + CLASS_NAME + " { java.lang.String aClassName; }",
        "-keep class " + CLASS_NAME,
        "-keepclassmembers,allowobfuscation class " + CLASS_NAME + " { !static <fields>; }",
        "-dontoptimize");
    CodeInspector inspector = compileWithR8(builder, pgConfigs).inspector();

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    DexEncodedMethod method = getMethod(inspector, init);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    checkInstructions(
        code,
        ImmutableList.of(
            InvokeDirect.class, ConstString.class, IputObject.class, ReturnVoid.class));
    ConstString constString = (ConstString) code.instructions[1];
    assertEquals(BOO, constString.getString().toString());
  }

  @Test
  public void instancePut_sharedOperand() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    builder.addInstanceField("aClassName", "Ljava/lang/String;");
    MethodSignature init = builder.addInitializer(ImmutableList.of(), 2,
        "invoke-direct {p0}, Ljava/lang/Object;-><init>()V",
        "sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "const-string v1, \"" + BOO + "\"",
        "invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "iput-object v1, p0, LExample;->aClassName:Ljava/lang/String;",
        "return-void");

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class " + CLASS_NAME + " { java.lang.String aClassName; }",
        "-keep class " + CLASS_NAME,
        "-keepclassmembers,allowobfuscation class " + CLASS_NAME + " { !static <fields>; }",
        "-dontoptimize");
    CodeInspector inspector = compileWithR8(builder, pgConfigs).inspector();

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    DexEncodedMethod method = getMethod(inspector, init);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    checkInstructions(
        code,
        ImmutableList.of(
            InvokeDirect.class,
            SgetObject.class,
            ConstString.class,
            InvokeVirtual.class,
            ConstString.class,
            IputObject.class,
            ReturnVoid.class));
    ConstString constString = (ConstString) code.instructions[2];
    assertEquals(BOO, constString.getString().toString());
    constString = (ConstString) code.instructions[4];
    assertEquals(BOO, constString.getString().toString());
  }

  @Test
  public void instancePut_sharedOperand_renamed() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    builder.addInstanceField("aClassName", "Ljava/lang/String;");
    MethodSignature init = builder.addInitializer(ImmutableList.of(), 2,
        "invoke-direct {p0}, Ljava/lang/Object;-><init>()V",
        "sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "const-string v1, \"" + BOO + "\"",
        "invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "iput-object v1, p0, LExample;->aClassName:Ljava/lang/String;",
        "return-void");
    builder.addClass(BOO);

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class " + CLASS_NAME + " { java.lang.String aClassName; }",
        "-keep class " + CLASS_NAME,
        "-keepclassmembers,allowobfuscation class " + CLASS_NAME + " { !static <fields>; }",
        "-keep,allowobfuscation class " + BOO,
        "-dontoptimize");
    CodeInspector inspector = compileWithR8(builder, pgConfigs).inspector();

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    DexEncodedMethod method = getMethod(inspector, init);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    checkInstructions(
        code,
        ImmutableList.of(
            InvokeDirect.class,
            SgetObject.class,
            ConstString.class,
            InvokeVirtual.class,
            ConstString.class,
            IputObject.class,
            ReturnVoid.class));
    ConstString constString = (ConstString) code.instructions[2];
    assertEquals(BOO, constString.getString().toString());
    constString = (ConstString) code.instructions[4];
    assertNotEquals(BOO, constString.getString().toString());
  }

  @Test
  public void staticPut_singleUseOperand() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    builder.addStaticField("sClassName", "Ljava/lang/String;");
    MethodSignature clinit = builder.addStaticInitializer(1,
        "const-string v0, \"" + BOO + "\"",
        "sput-object v0, LExample;->sClassName:Ljava/lang/String;",
        "return-void");

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class " + CLASS_NAME + " { static java.lang.String sClassName; }",
        "-keep class " + CLASS_NAME,
        "-keepclassmembers,allowobfuscation class " + CLASS_NAME + " { static <fields>; }",
        "-dontoptimize");
    CodeInspector inspector = compileWithR8(builder, pgConfigs).inspector();

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    DexEncodedMethod method = getMethod(inspector, clinit);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    checkInstructions(code, ImmutableList.of(
        ConstString.class,
        SputObject.class,
        ReturnVoid.class));
    ConstString constString = (ConstString) code.instructions[0];
    assertEquals(BOO, constString.getString().toString());
  }

  @Test
  public void staticPut_sharedOperand() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    builder.addStaticField("sClassName", "Ljava/lang/String;");
    MethodSignature clinit = builder.addStaticInitializer(2,
        "sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "const-string v1, \"" + BOO + "\"",
        "invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "sput-object v1, LExample;->sClassName:Ljava/lang/String;",
        "return-void");

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class " + CLASS_NAME + " { static java.lang.String sClassName; }",
        "-keep class " + CLASS_NAME,
        "-keepclassmembers,allowobfuscation class " + CLASS_NAME + " { static <fields>; }",
        "-dontoptimize");
    CodeInspector inspector = compileWithR8(builder, pgConfigs).inspector();

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    DexEncodedMethod method = getMethod(inspector, clinit);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    checkInstructions(code, ImmutableList.of(
        SgetObject.class,
        ConstString.class,
        InvokeVirtual.class,
        ConstString.class,
        SputObject.class,
        ReturnVoid.class));
    ConstString constString = (ConstString) code.instructions[1];
    assertEquals(BOO, constString.getString().toString());
    constString = (ConstString) code.instructions[3];
    assertEquals(BOO, constString.getString().toString());
  }

  @Test
  public void staticPut_sharedOperand_renamed() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    builder.addStaticField("sClassName", "Ljava/lang/String;");
    MethodSignature clinit = builder.addStaticInitializer(2,
        "sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "const-string v1, \"" + BOO + "\"",
        "invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "sput-object v1, LExample;->sClassName:Ljava/lang/String;",
        "return-void");
    builder.addClass(BOO);

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class " + CLASS_NAME + " { static java.lang.String sClassName; }",
        "-keep class " + CLASS_NAME,
        "-keepclassmembers,allowobfuscation class " + CLASS_NAME + " { static <fields>; }",
        "-keep,allowobfuscation class " + BOO,
        "-dontoptimize");
    CodeInspector inspector = compileWithR8(builder, pgConfigs).inspector();

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    DexEncodedMethod method = getMethod(inspector, clinit);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    checkInstructions(code, ImmutableList.of(
        SgetObject.class,
        ConstString.class,
        InvokeVirtual.class,
        ConstString.class,
        SputObject.class,
        ReturnVoid.class));
    ConstString constString = (ConstString) code.instructions[1];
    assertEquals(BOO, constString.getString().toString());
    constString = (ConstString) code.instructions[3];
    assertNotEquals(BOO, constString.getString().toString());
  }

  @Test
  public void staticFieldWithTypeName() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    builder.addStaticField("sClassName", "Ljava/lang/String;", BOO);

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class " + CLASS_NAME + " { static java.lang.String sClassName; }",
        "-keep class " + CLASS_NAME + " { static java.lang.String sClassName; }",
        "-dontshrink");
    CodeInspector inspector = compileWithR8(builder, pgConfigs).inspector();

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    FieldSubject field = clazz.field("java.lang.String", "sClassName");
    assertTrue(field.isPresent());
    assertTrue(field.getStaticValue().isDexValueString());
    String defaultValue = field.getStaticValue().asDexValueString().getValue().toString();
    assertEquals(BOO, defaultValue);
  }

  @Test
  public void staticFieldWithTypeName_renamed() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    builder.addStaticField("sClassName", "Ljava/lang/String;", BOO);
    builder.addClass(BOO);

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class " + CLASS_NAME + " { static java.lang.String sClassName; }",
        "-keep class " + CLASS_NAME + " { static java.lang.String sClassName; }",
        "-keep,allowobfuscation class " + BOO,
        "-dontshrink");
    CodeInspector inspector = compileWithR8(builder, pgConfigs).inspector();

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    FieldSubject field = clazz.field("java.lang.String", "sClassName");
    assertTrue(field.isPresent());
    assertTrue(field.getStaticValue().isDexValueString());
    String defaultValue = field.getStaticValue().asDexValueString().getValue().toString();
    assertNotEquals(BOO, defaultValue);
  }

  @Test
  public void staticFieldWithFieldName_renamed() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    String fooInBoo = BOO + ".foo";
    builder.addStaticField("sFieldName", "Ljava/lang/String;", fooInBoo);
    builder.addClass(BOO);
    builder.addStaticField("foo", "Ljava/lang/String;");

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class " + CLASS_NAME + " { static java.lang.String sFieldName; }",
        "-keep class " + CLASS_NAME + " { static java.lang.String sFieldName; }",
        "-keep,allowobfuscation class " + BOO + " { <fields>; }",
        "-dontshrink");
    CodeInspector inspector = compileWithR8(builder, pgConfigs).inspector();

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    FieldSubject field = clazz.field("java.lang.String", "sFieldName");
    assertTrue(field.isPresent());
    assertTrue(field.getStaticValue().isDexValueString());
    String defaultValue = field.getStaticValue().asDexValueString().getValue().toString();
    assertNotEquals(fooInBoo, defaultValue);
  }

  @Test
  public void staticFieldWithMethodName_renamed() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    String fooInBoo = BOO + "#foo";
    builder.addStaticField("sMethodName", "Ljava/lang/String;", fooInBoo);
    builder.addClass(BOO);
    builder.addStaticMethod("void", "foo", ImmutableList.of(), 0, "return-void");

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class " + CLASS_NAME + " { static java.lang.String sMethodName; }",
        "-keep class " + CLASS_NAME + " { static java.lang.String sMethodName; }",
        "-keep,allowobfuscation class " + BOO + " { <methods>; }",
        "-dontshrink");
    CodeInspector inspector = compileWithR8(builder, pgConfigs).inspector();

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    FieldSubject field = clazz.field("java.lang.String", "sMethodName");
    assertTrue(field.isPresent());
    assertTrue(field.getStaticValue().isDexValueString());
    String defaultValue = field.getStaticValue().asDexValueString().getValue().toString();
    assertNotEquals(fooInBoo, defaultValue);
  }

  @Test
  public void invoke_singleUseOperand() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    builder.addStaticMethod(
        "void",
        "foo",
        ImmutableList.of("java.lang.String", "java.lang.String"),
        0,
        "return-void");
    MethodSignature foo = builder.addInitializer(ImmutableList.of(), 2,
        "invoke-direct {p0}, Ljava/lang/Object;-><init>()V",
        "const-string v0, \"" + BOO + "\"",
        "const-string v1, \"Mixed/form.Boo\"",
        "invoke-static {v0, v1}, LExample;->foo(Ljava/lang/String;Ljava/lang/String;)V",
        "return-void");

    CodeInspector inspector =
        compileWithR8(
                builder,
                testBuilder ->
                    testBuilder
                        .addKeepRules(
                            "-identifiernamestring class "
                                + CLASS_NAME
                                + " { static void foo(...); }",
                            "-keep class " + CLASS_NAME)
                        .allowDiagnosticWarningMessages())
            .assertAllWarningMessagesMatch(
                containsString("Cannot determine what 'Mixed/form.Boo' refers to"))
            .inspector();

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    DexEncodedMethod method = getMethod(inspector, foo);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    checkInstructions(
        code,
        ImmutableList.of(
            InvokeDirect.class,
            ConstString.class,
            ConstString.class,
            InvokeStatic.class,
            ReturnVoid.class));
    String s1 = ((ConstString) code.instructions[1]).getString().toString();
    String s2 = ((ConstString) code.instructions[2]).getString().toString();
    assertTrue(BOO.equals(s1) || BOO.equals(s2));
    assertTrue("Mixed/form.Boo".equals(s1) || "Mixed/form.Boo".equals(s2));
  }

  @Test
  public void invoke_sharedOperand() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    builder.addStaticMethod(
        "void",
        "foo",
        ImmutableList.of("java.lang.String"),
        0,
        "return-void");
    MethodSignature foo = builder.addInitializer(ImmutableList.of(), 3,
        "invoke-direct {p0}, Ljava/lang/Object;-><init>()V",
        "sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "const-string v1, \"" + BOO + "\"",
        "invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "invoke-static {v1}, LExample;->foo(Ljava/lang/String;)V",
        "return-void");

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class " + CLASS_NAME + " { static void foo(...); }",
        "-keep class " + CLASS_NAME);
    CodeInspector inspector = compileWithR8(builder, pgConfigs).inspector();

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    DexEncodedMethod method = getMethod(inspector, foo);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    checkInstructions(
        code,
        ImmutableList.of(
            InvokeDirect.class,
            SgetObject.class,
            ConstString.class,
            InvokeVirtual.class,
            ConstString.class,
            InvokeStatic.class,
            ReturnVoid.class));
    ConstString constString = (ConstString) code.instructions[2];
    assertEquals(BOO, constString.getString().toString());
    constString = (ConstString) code.instructions[4];
    assertEquals(BOO, constString.getString().toString());
  }

  @Test
  public void invoke_sharedOperand_renamed() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);
    builder.addStaticMethod(
        "void",
        "foo",
        ImmutableList.of("java.lang.String"),
        0,
        "return-void");
    MethodSignature foo = builder.addInitializer(ImmutableList.of(), 3,
        "invoke-direct {p0}, Ljava/lang/Object;-><init>()V",
        "sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "const-string v1, \"" + BOO + "\"",
        "invoke-virtual {v0, v1}, Ljava/io/PrintStream;->println(Ljava/lang/String;)V",
        "invoke-static {v1}, LExample;->foo(Ljava/lang/String;)V",
        "return-void");
    builder.addClass(BOO);

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class " + CLASS_NAME + " { static void foo(...); }",
        "-keep class " + CLASS_NAME,
        "-keep,allowobfuscation class " + BOO);
    CodeInspector inspector = compileWithR8(builder, pgConfigs).inspector();

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    DexEncodedMethod method = getMethod(inspector, foo);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    checkInstructions(
        code,
        ImmutableList.of(
            InvokeDirect.class,
            SgetObject.class,
            ConstString.class,
            InvokeVirtual.class,
            ConstString.class,
            InvokeStatic.class,
            ReturnVoid.class));
    ConstString constString = (ConstString) code.instructions[2];
    assertEquals(BOO, constString.getString().toString());
    constString = (ConstString) code.instructions[4];
    assertNotEquals(BOO, constString.getString().toString());
  }

  @Test
  public void reflective_field_singleUseOperand() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);

    MethodSignature foo = builder.addInitializer(ImmutableList.of(), 2,
        "invoke-direct {p0}, Ljava/lang/Object;-><init>()V",
        "const-class v0, LR;",
        "const-string v1, \"foo\"",
        "invoke-static {v0, v1}, "
            + "LR;->findField(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;",
        "return-void");

    builder.addClass("R");
    builder.addStaticField("foo", "Ljava/lang/String;");
    builder.addStaticMethod(
        "java.lang.reflect.Field",
        "findField",
        ImmutableList.of("java.lang.Class", "java.lang.String"),
        1,
        "invoke-virtual {p0, p1}, "
            + "Ljava/lang/Class;->getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;",
        "move-result-object v0",
        "return-object v0");

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class * {\n"
            + "  static java.lang.reflect.Field *(java.lang.Class,java.lang.String);\n"
            + "}",
        "-keep class " + CLASS_NAME,
        "-keep class R { *; }");
    CodeInspector inspector = compileWithR8(builder, pgConfigs).inspector();

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    DexEncodedMethod method = getMethod(inspector, foo);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    checkInstructions(
        code,
        ImmutableList.of(
            InvokeDirect.class,
            ConstClass.class,
            ConstString.class,
            InvokeStatic.class,
            ReturnVoid.class));
    ConstString constString = (ConstString) code.instructions[2];
    assertEquals("foo", constString.getString().toString());
  }

  @Test
  public void reflective_field_singleUseOperand_renamed() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);

    MethodSignature foo = builder.addInitializer(ImmutableList.of(), 2,
        "invoke-direct {p0}, Ljava/lang/Object;-><init>()V",
        "const-class v0, LR;",
        "const-string v1, \"foo\"",
        "invoke-static {v0, v1}, "
            + "LR;->findField(Ljava/lang/Class;Ljava/lang/String;)Ljava/lang/reflect/Field;",
        "return-void");

    builder.addClass("R");
    builder.addStaticField("foo", "Ljava/lang/String;");
    builder.addStaticMethod(
        "java.lang.reflect.Field",
        "findField",
        ImmutableList.of("java.lang.Class", "java.lang.String"),
        1,
        "invoke-virtual {p0, p1}, "
            + "Ljava/lang/Class;->getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;",
        "move-result-object v0",
        "return-object v0");

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class * {\n"
            + "  static java.lang.reflect.Field *(java.lang.Class,java.lang.String);\n"
            + "}",
        "-keep class " + CLASS_NAME,
        "-keep,allowobfuscation class R { *; }");
    CodeInspector inspector = compileWithR8(builder, pgConfigs).inspector();

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    DexEncodedMethod method = getMethod(inspector, foo);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    checkInstructions(
        code,
        ImmutableList.of(
            InvokeDirect.class,
            ConstClass.class,
            ConstString.class,
            InvokeStatic.class,
            ReturnVoid.class));
    ConstString constString = (ConstString) code.instructions[2];
    assertNotEquals("foo", constString.getString().toString());
  }

  @Test
  public void reflective_method_singleUseOperand() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);

    MethodSignature foo = builder.addInitializer(ImmutableList.of(), 3,
        "invoke-direct {p0}, Ljava/lang/Object;-><init>()V",
        "const-class v0, LR;",
        "const/4 v2, 0x1",
        "new-array v2, v2, [Ljava/lang/Class;",
        "const/4 v1, 0x0",
        "aput-object v0, v2, v1",
        "const-string v1, \"foo\"",
        "invoke-static {v0, v1, v2}, "
            + "LR;->findMethod(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)"
            + "Ljava/lang/reflect/Method;",
        "return-void");

    builder.addClass("R");
    builder.addStaticMethod("void", "foo", ImmutableList.of("R"), 0,"return-void");
    builder.addStaticMethod(
        "java.lang.reflect.Method",
        "findMethod",
        ImmutableList.of("java.lang.Class", "java.lang.String", "java.lang.Class[]"),
        1,
        "invoke-virtual {p0, p1, p2}, "
            + "Ljava/lang/Class;->getDeclaredMethod(Ljava/lang/String;[Ljava/lang/Class;)"
            + "Ljava/lang/reflect/Method;",
        "move-result-object v0",
        "return-object v0");

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class * {\n"
            + "  static java.lang.reflect.Method"
            + "    *(java.lang.Class,java.lang.String,java.lang.Class[]);\n"
            + "}",
        "-keep class " + CLASS_NAME,
        "-keep class R { *; }");
    CodeInspector inspector = compileWithR8(builder, pgConfigs).inspector();

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    DexEncodedMethod method = getMethod(inspector, foo);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    checkInstructions(
        code,
        ImmutableList.of(
            InvokeDirect.class,
            ConstClass.class,
            Const4.class,
            NewArray.class,
            Const4.class,
            AputObject.class,
            ConstString.class,
            InvokeStatic.class,
            ReturnVoid.class));
    ConstString constString = (ConstString) code.instructions[6];
    assertEquals("foo", constString.getString().toString());
  }

  @Test
  public void reflective_method_singleUseOperand_renamed() throws Exception {
    SmaliBuilder builder = new SmaliBuilder(CLASS_NAME);

    MethodSignature foo = builder.addInitializer(ImmutableList.of(), 3,
        "invoke-direct {p0}, Ljava/lang/Object;-><init>()V",
        "const-class v0, LR;",
        "const/4 v2, 0x1",
        "new-array v2, v2, [Ljava/lang/Class;",
        "const/4 v1, 0x0",
        "aput-object v0, v2, v1",
        "const-string v1, \"foo\"",
        "invoke-static {v0, v1, v2}, "
            + "LR;->findMethod(Ljava/lang/Class;Ljava/lang/String;[Ljava/lang/Class;)"
            + "Ljava/lang/reflect/Method;",
        "return-void");

    builder.addClass("R");
    builder.addStaticMethod("void", "foo", ImmutableList.of("R"), 0,"return-void");
    builder.addStaticMethod(
        "java.lang.reflect.Method",
        "findMethod",
        ImmutableList.of("java.lang.Class", "java.lang.String", "java.lang.Class[]"),
        1,
        "invoke-virtual {p0, p1, p2}, "
            + "Ljava/lang/Class;->getDeclaredMethod(Ljava/lang/String;[Ljava/lang/Class;)"
            + "Ljava/lang/reflect/Method;",
        "move-result-object v0",
        "return-object v0");

    List<String> pgConfigs = ImmutableList.of(
        "-identifiernamestring class * {\n"
            + "  static java.lang.reflect.Method"
            + "    *(java.lang.Class,java.lang.String,java.lang.Class[]);\n"
            + "}",
        "-keep class " + CLASS_NAME,
        "-keep,allowobfuscation class R { *; }");
    CodeInspector inspector = compileWithR8(builder, pgConfigs).inspector();

    ClassSubject clazz = inspector.clazz(CLASS_NAME);
    assertTrue(clazz.isPresent());
    DexEncodedMethod method = getMethod(inspector, foo);
    assertNotNull(method);

    DexCode code = method.getCode().asDexCode();
    checkInstructions(
        code,
        ImmutableList.of(
            InvokeDirect.class,
            ConstClass.class,
            Const4.class,
            NewArray.class,
            Const4.class,
            AputObject.class,
            ConstString.class,
            InvokeStatic.class,
            ReturnVoid.class));
    ConstString constString = (ConstString) code.instructions[6];
    assertNotEquals("foo", constString.getString().toString());
  }

  private R8TestCompileResult compileWithR8(
      SmaliBuilder builder, List<String> proguardConfigurations) throws Exception {
    return compileWithR8(builder, testBuilder -> testBuilder.addKeepRules(proguardConfigurations));
  }

  private R8TestCompileResult compileWithR8(
      SmaliBuilder builder, ThrowableConsumer<R8FullTestBuilder> configuration) throws Exception {
    return testForR8(Backend.DEX)
        .addProgramDexFileData(builder.compile())
        .apply(configuration)
        .debug()
        .compile();
  }
}
