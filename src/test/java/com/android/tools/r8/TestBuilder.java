// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.utils.ListUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

public abstract class TestBuilder<RR extends TestRunResult, T extends TestBuilder<RR, T>> {

  private final TestState state;

  public TestBuilder(TestState state) {
    this.state = state;
  }

  public TestState getState() {
    return state;
  }

  abstract T self();

  public <S, E extends Throwable> S map(ThrowingFunction<T, S, E> fn) {
    return fn.applyWithRuntimeException(self());
  }

  public T apply(ThrowableConsumer<T> fn) {
    if (fn != null) {
      fn.acceptWithRuntimeException(self());
    }
    return self();
  }

  public T applyIf(boolean value, ThrowableConsumer<T> consumer) {
    T self = self();
    if (value) {
      consumer.acceptWithRuntimeException(self);
    }
    return self;
  }

  public T applyIf(
      boolean value, ThrowableConsumer<T> trueConsumer, ThrowableConsumer<T> falseConsumer) {
    T self = self();
    if (value) {
      trueConsumer.acceptWithRuntimeException(self);
    } else {
      falseConsumer.acceptWithRuntimeException(self);
    }
    return self;
  }

  @Deprecated
  public abstract RR run(String mainClass)
      throws CompilationFailedException, ExecutionException, IOException;

  public abstract RR run(TestRuntime runtime, String mainClass, String... args)
      throws CompilationFailedException, ExecutionException, IOException;

  @Deprecated
  public RR run(Class<?> mainClass)
      throws CompilationFailedException, ExecutionException, IOException {
    return run(mainClass.getTypeName());
  }

  public RR run(TestRuntime runtime, Class<?> mainClass, String... args)
      throws CompilationFailedException, ExecutionException, IOException {
    return run(runtime, mainClass.getTypeName(), args);
  }

  public abstract DebugTestConfig debugConfig();

  public abstract T addProgramFiles(Collection<Path> files);

  public abstract T addProgramClassFileData(Collection<byte[]> classes);

  public T addProgramClassFileData(byte[]... classes) {
    return addProgramClassFileData(Arrays.asList(classes));
  }

  public abstract T addProgramDexFileData(Collection<byte[]> data);

  public T addProgramDexFileData(byte[]... data) {
    return addProgramDexFileData(Arrays.asList(data));
  }

  public T addProgramClasses(Class<?>... classes) {
    return addProgramClasses(Arrays.asList(classes));
  }

  public T addProgramClasses(Collection<Class<?>> classes) {
    return addProgramFiles(getFilesForClasses(classes));
  }

  public T addProgramFiles(Path... files) {
    return addProgramFiles(Arrays.asList(files));
  }

  public T addProgramClassesAndInnerClasses(Class<?>... classes) throws IOException {
    return addProgramClassesAndInnerClasses(Arrays.asList(classes));
  }

  public T addProgramClassesAndInnerClasses(Collection<Class<?>> classes) throws IOException {
    return addProgramClasses(classes).addInnerClasses(classes);
  }

  public T addInnerClasses(Class<?>... classes) throws IOException {
    return addInnerClasses(Arrays.asList(classes));
  }

  public T addInnerClasses(Collection<Class<?>> classes) throws IOException {
    return addProgramFiles(getFilesForInnerClasses(classes));
  }

  public abstract T addLibraryFiles(Collection<Path> files);

  public T addLibraryClasses(Class<?>... classes) {
    return addLibraryClasses(Arrays.asList(classes));
  }

  public abstract T addLibraryClasses(Collection<Class<?>> classes);

  public T addLibraryFiles(Path... files) {
    return addLibraryFiles(Arrays.asList(files));
  }

  public T addClasspathClasses(Class<?>... classes) {
    return addClasspathClasses(Arrays.asList(classes));
  }

  public abstract T addClasspathClasses(Collection<Class<?>> classes);

  public T addClasspathFiles(Path... files) {
    return addClasspathFiles(Arrays.asList(files));
  }

  public abstract T addClasspathFiles(Collection<Path> files);

  public final T addTestingAnnotationsAsProgramClasses() {
    return addProgramClasses(getTestingAnnotations());
  }

  public final T addTestingAnnotationsAsLibraryClasses() {
    return addLibraryClasses(getTestingAnnotations());
  }

  private List<Class<?>> getTestingAnnotations() {
    return ImmutableList.of(
        AssumeMayHaveSideEffects.class,
        ForceInline.class,
        KeepConstantArguments.class,
        KeepUnusedArguments.class,
        NeverClassInline.class,
        NeverInline.class,
        NeverMerge.class,
        NeverPropagateValue.class);
  }

  static Collection<Path> getFilesForClasses(Collection<Class<?>> classes) {
    return ListUtils.map(classes, ToolHelper::getClassFileForTestClass);
  }

  static Collection<Path> getFilesForInnerClasses(Collection<Class<?>> classes) throws IOException {
    return ToolHelper.getClassFilesForInnerClasses(classes);
  }

  public abstract T addRunClasspathFiles(Collection<Path> files);

  public T addRunClasspathFiles(Path... files) {
    return addRunClasspathFiles(Arrays.asList(files));
  }
}
