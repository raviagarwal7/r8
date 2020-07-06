// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.whyareyoukeeping;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.shaking.WhyAreYouKeepingConsumer;
import com.android.tools.r8.utils.StringUtils;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

class A {

  public void foo() {
    bar();
  }

  @NeverInline
  public void bar() {
    baz();
  }

  @NeverInline
  public void baz() {
    System.out.println("called baz");
  }
}

@RunWith(Parameterized.class)
public class WhyAreYouKeepingTest extends TestBase {

  public static final String expected =
      StringUtils.lines(
          "com.android.tools.r8.shaking.whyareyoukeeping.A",
          "|- is referenced in keep rule:",
          "|  -keep class com.android.tools.r8.shaking.whyareyoukeeping.A { void foo(); }");

  public static final String expectedPathToBaz =
      StringUtils.lines(
          "void com.android.tools.r8.shaking.whyareyoukeeping.A.baz()",
          "|- is invoked from:",
          "|  void com.android.tools.r8.shaking.whyareyoukeeping.A.bar()",
          "|- is invoked from:",
          "|  void com.android.tools.r8.shaking.whyareyoukeeping.A.foo()",
          "|- is referenced in keep rule:",
          "|  -keep class com.android.tools.r8.shaking.whyareyoukeeping.A { void foo(); }");

  @Parameters(name = "{0}")
  public static Backend[] parameters() {
    return ToolHelper.getBackends();
  }

  public final Backend backend;

  public WhyAreYouKeepingTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void testWhyAreYouKeepingViaProguardConfig() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    testForR8(backend)
        .addProgramClasses(A.class)
        .addKeepMethodRules(Reference.methodFromMethod(A.class.getMethod("foo")))
        .addKeepRules("-whyareyoukeeping class " + A.class.getTypeName())
        // Redirect the compilers stdout to intercept the '-whyareyoukeeping' output
        .collectStdout()
        .compile()
        .assertStdoutThatMatches(equalTo(expected));
  }

  @Test
  public void testWhyAreYouKeepingViaConsumer() throws Exception {
    WhyAreYouKeepingConsumer graphConsumer = new WhyAreYouKeepingConsumer(null);
    testForR8(backend)
        .addProgramClasses(A.class)
        .addKeepMethodRules(Reference.methodFromMethod(A.class.getMethod("foo")))
        .setKeptGraphConsumer(graphConsumer)
        .compile();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    graphConsumer.printWhyAreYouKeeping(Reference.classFromClass(A.class), new PrintStream(baos));
    String output = new String(baos.toByteArray(), StandardCharsets.UTF_8);
    assertEquals(expected, output);
  }

  @Test
  public void testWhyAreYouKeepingPathViaProguardConfig()
      throws NoSuchMethodException, CompilationFailedException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    testForR8(backend)
        .addProgramClasses(A.class)
        .enableInliningAnnotations()
        .addKeepRules("-whyareyoukeeping class " + A.class.getTypeName() + " { baz(); }")
        .addKeepMethodRules(Reference.methodFromMethod(A.class.getMethod("foo")))
        // Redirect the compilers stdout to intercept the '-whyareyoukeeping' output
        .collectStdout()
        .compile()
        .assertStdoutThatMatches(equalTo(expected + expectedPathToBaz));
  }

  @Test
  public void testWhyAreYouKeepingPathViaConsumer()
      throws NoSuchMethodException, CompilationFailedException {
    WhyAreYouKeepingConsumer graphConsumer = new WhyAreYouKeepingConsumer(null);
    testForR8(backend)
        .addProgramClasses(A.class)
        .setKeptGraphConsumer(graphConsumer)
        .enableInliningAnnotations()
        .addKeepMethodRules(Reference.methodFromMethod(A.class.getMethod("foo")))
        .compile();
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    graphConsumer.printWhyAreYouKeeping(
        Reference.methodFromMethod(A.class.getMethod("baz")), new PrintStream(baos));
    String output = new String(baos.toByteArray(), StandardCharsets.UTF_8);
    assertEquals(expectedPathToBaz, output);
  }

  @Test
  public void testNonExistentClassWhyAreYouKeepingViaProguardConfig() throws Exception {
    testForR8(backend)
        .addProgramClasses(A.class)
        .addKeepMethodRules(Reference.methodFromMethod(A.class.getMethod("foo")))
        .addKeepRules("-whyareyoukeeping class NonExistentClass")
        .allowUnusedProguardConfigurationRules()
        // Redirect the compilers stdout to intercept the '-whyareyoukeeping' output
        .collectStdout()
        .compile()
        .assertNoStdout();
  }

  @Test
  public void testNonExistentMethodWhyAreYouKeepingViaProguardConfig() throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    String aName = A.class.getTypeName();
    testForR8(backend)
        .addProgramClasses(A.class)
        .addKeepMethodRules(Reference.methodFromMethod(A.class.getMethod("foo")))
        .addKeepRules("-whyareyoukeeping class " + aName + " { nonExistentMethod(); }")
        // Redirect the compilers stdout to intercept the '-whyareyoukeeping' output
        .collectStdout()
        .compile()
        .assertStdoutThatMatches(not(equalTo("")));
  }
}
