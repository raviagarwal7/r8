// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.utils.InternalOptions.ASM_VERSION;
import static com.google.common.collect.Lists.cartesianProduct;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.DataResourceProvider.Visitor;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper.ArtCommandBuilder;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppServices;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.SmaliWriter;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.EnqueuerFactory;
import com.android.tools.r8.shaking.ProguardClassFilter;
import com.android.tools.r8.shaking.ProguardClassNameList;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardKeepRule;
import com.android.tools.r8.shaking.ProguardKeepRule.Builder;
import com.android.tools.r8.shaking.ProguardKeepRuleType;
import com.android.tools.r8.shaking.ProguardMemberRule;
import com.android.tools.r8.shaking.ProguardMemberType;
import com.android.tools.r8.shaking.ProguardTypeMatcher;
import com.android.tools.r8.shaking.RootSetBuilder;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import com.android.tools.r8.shaking.serviceloader.ServiceLoaderMultipleTest.Greeter;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.PreloadedClassFileProvider;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.TestDescriptionWatcher;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;

public class TestBase {

  public enum Backend {
    CF,
    DEX
  }

  public static R8FullTestBuilder testForR8(TemporaryFolder temp, Backend backend) {
    return R8FullTestBuilder.create(new TestState(temp), backend);
  }

  public static R8CompatTestBuilder testForR8Compat(
      TemporaryFolder temp, Backend backend, boolean forceProguardCompatibility) {
    return R8CompatTestBuilder.create(new TestState(temp), backend, forceProguardCompatibility);
  }

  public static ExternalR8TestBuilder testForExternalR8(
      TemporaryFolder temp, Backend backend, TestRuntime runtime) {
    return ExternalR8TestBuilder.create(new TestState(temp), backend, runtime);
  }

  public static D8TestBuilder testForD8(TemporaryFolder temp, Backend backend) {
    return D8TestBuilder.create(new TestState(temp), backend);
  }

  public static D8TestBuilder testForD8(TemporaryFolder temp) {
    return D8TestBuilder.create(new TestState(temp), Backend.DEX);
  }

  public static DXTestBuilder testForDX(TemporaryFolder temp) {
    return DXTestBuilder.create(new TestState(temp));
  }

  public static JvmTestBuilder testForJvm(TemporaryFolder temp) {
    return JvmTestBuilder.create(new TestState(temp));
  }

  public static ProguardTestBuilder testForProguard(TemporaryFolder temp) {
    return ProguardTestBuilder.create(new TestState(temp));
  }

  public static GenerateMainDexListTestBuilder testForMainDexListGenerator(TemporaryFolder temp) {
    return GenerateMainDexListTestBuilder.create(new TestState(temp));
  }

  public R8FullTestBuilder testForR8(Backend backend) {
    return testForR8(temp, backend);
  }

  public R8CompatTestBuilder testForR8Compat(Backend backend) {
    return testForR8Compat(backend, true);
  }

  public R8CompatTestBuilder testForR8Compat(Backend backend, boolean forceProguardCompatibility) {
    return testForR8Compat(temp, backend, forceProguardCompatibility);
  }

  public ExternalR8TestBuilder testForExternalR8(Backend backend, TestRuntime runtime) {
    return testForExternalR8(temp, backend, runtime);
  }

  public D8TestBuilder testForD8() {
    return testForD8(temp, Backend.DEX);
  }

  public D8TestBuilder testForD8(Backend backend) {
    return testForD8(temp, backend);
  }

  public DXTestBuilder testForDX() {
    return testForDX(temp);
  }

  public JvmTestBuilder testForJvm() {
    return testForJvm(temp);
  }

  public TestBuilder<? extends TestRunResult<?>, ?> testForRuntime(
      TestRuntime runtime, Consumer<D8TestBuilder> d8TestBuilderConsumer) {
    if (runtime.isCf()) {
      return testForJvm();
    } else {
      assert runtime.isDex();
      D8TestBuilder d8TestBuilder = testForD8();
      d8TestBuilderConsumer.accept(d8TestBuilder);
      return d8TestBuilder;
    }
  }

  public TestBuilder<? extends TestRunResult<?>, ?> testForRuntime(
      TestRuntime runtime, AndroidApiLevel apiLevel) {
    return testForRuntime(runtime, d8TestBuilder -> d8TestBuilder.setMinApi(apiLevel));
  }

  public TestBuilder<? extends TestRunResult<?>, ?> testForRuntime(TestParameters parameters) {
    return testForRuntime(parameters.getRuntime(), parameters.getApiLevel());
  }

  public ProguardTestBuilder testForProguard() {
    return testForProguard(temp);
  }

  public GenerateMainDexListTestBuilder testForMainDexListGenerator() {
    return testForMainDexListGenerator(temp);
  }

  public JavaCompilerTool javac(CfRuntime jdk) {
    return JavaCompilerTool.create(jdk, temp);
  }

  public static JavaCompilerTool javac(CfRuntime jdk, TemporaryFolder temp) {
    return JavaCompilerTool.create(jdk, temp);
  }

  public static KotlinCompilerTool kotlinc(
      CfRuntime jdk,
      TemporaryFolder temp,
      KotlinCompiler kotlinCompiler,
      KotlinTargetVersion kotlinTargetVersion) {
    return KotlinCompilerTool.create(jdk, temp, kotlinCompiler, kotlinTargetVersion);
  }

  public static KotlinCompilerTool kotlinc(
      KotlinCompiler kotlinCompiler, KotlinTargetVersion kotlinTargetVersion) {
    return kotlinc(TestRuntime.getCheckedInJdk9(), staticTemp, kotlinCompiler, kotlinTargetVersion);
  }

  public KotlinCompilerTool kotlinc(
      CfRuntime jdk, KotlinCompiler kotlinCompiler, KotlinTargetVersion kotlinTargetVersion) {
    return KotlinCompilerTool.create(jdk, temp, kotlinCompiler, kotlinTargetVersion);
  }

  public static ClassFileTransformer transformer(Class<?> clazz) throws IOException {
    return ClassFileTransformer.create(clazz);
  }

  public static ClassFileTransformer transformer(byte[] bytes, ClassReference classReference) {
    return ClassFileTransformer.create(bytes, classReference);
  }

  // Actually running Proguard should only be during development.
  private static final boolean RUN_PROGUARD = System.getProperty("run_proguard") != null;
  // Actually running r8.jar in a forked process.
  private static final boolean RUN_R8_JAR = System.getProperty("run_r8_jar") != null;

  @Rule
  public TemporaryFolder temp = ToolHelper.getTemporaryFolderForTest();

  @Rule
  public TestDescriptionWatcher watcher = new TestDescriptionWatcher();

  private static TemporaryFolder staticTemp = null;

  @BeforeClass
  public static void testBaseBeforeClassSetup() throws IOException {
    assert staticTemp == null;
    staticTemp = ToolHelper.getTemporaryFolderForTest();
    staticTemp.create();
  }

  @AfterClass
  public static void testBaseBeforeClassTearDown() {
    assert staticTemp != null;
    staticTemp.delete();
    staticTemp = null;
  }

  public static TemporaryFolder getStaticTemp() {
    return staticTemp;
  }

  public static TestParametersBuilder getTestParameters() {
    return TestParametersBuilder.builder();
  }

  protected static <S, T, E extends Throwable> Function<S, T> memoizeFunction(
      ThrowingFunction<S, T, E> fn) {
    return CacheBuilder.newBuilder()
        .build(
            CacheLoader.from(
                b -> {
                  try {
                    return fn.applyWithRuntimeException(b);
                  } catch (Throwable e) {
                    throw new RuntimeException(e);
                  }
                }));
  }

  protected static <S, T, U, E extends Throwable> BiFunction<S, T, U> memoizeBiFunction(
      ThrowingBiFunction<S, T, U, E> fn) {
    class Pair {
      final S first;
      final T second;

      public Pair(S first, T second) {
        this.first = first;
        this.second = second;
      }

      @Override
      public boolean equals(Object obj) {
        if (!(obj instanceof Pair)) {
          return false;
        }
        Pair other = (Pair) obj;
        return Objects.equals(first, other.first) && Objects.equals(second, other.second);
      }

      @Override
      public int hashCode() {
        return Objects.hash(first, second);
      }
    }
    final Function<Pair, U> memoizedFn = memoizeFunction(pair -> fn.apply(pair.first, pair.second));
    return (a, b) -> memoizedFn.apply(new Pair(a, b));
  }

  /**
   * Check if tests should also run Proguard when applicable.
   */
  protected boolean isRunProguard() {
    return RUN_PROGUARD;
  }

  /**
   * Check if tests should run R8 in a forked process when applicable.
   */
  protected boolean isRunR8Jar() {
    return RUN_R8_JAR;
  }

  /**
   * Write lines of text to a temporary file.
   *
   * The file will include a line separator after the last line.
   */
  protected Path writeTextToTempFile(String... lines) throws IOException {
    return writeTextToTempFile(System.lineSeparator(), Arrays.asList(lines));
  }

  protected void writeTextToTempFile(Path file, String... lines) throws IOException {
    writeTextToTempFile(file, System.lineSeparator(), Arrays.asList(lines));
  }

  /**
   * Write lines of text to a temporary file, along with the specified line separator.
   *
   * The file will include a line separator after the last line.
   */
  protected Path writeTextToTempFile(String lineSeparator, List<String> lines)
      throws IOException {
    return writeTextToTempFile(lineSeparator, lines, true);
  }

  protected void writeTextToTempFile(Path file, String lineSeparator, List<String> lines)
      throws IOException {
    writeTextToTempFile(file, lineSeparator, lines, true);
  }

  /**
   * Write lines of text to a temporary file, along with the specified line separator.
   *
   * The argument <code>includeTerminatingLineSeparator</code> control if the file will include
   * a line separator after the last line.
   */
  protected Path writeTextToTempFile(
      String lineSeparator, List<String> lines, boolean includeTerminatingLineSeparator)
      throws IOException {
    Path file = temp.newFile().toPath();
    writeTextToTempFile(file, lineSeparator, lines, includeTerminatingLineSeparator);
    return file;
  }

  protected void writeTextToTempFile(
      Path file,
      String lineSeparator,
      List<String> lines,
      boolean includeTerminatingLineSeparator)
      throws IOException {
    String contents = String.join(lineSeparator, lines);
    if (includeTerminatingLineSeparator) {
      contents += lineSeparator;
    }
    Files.write(file, contents.getBytes(StandardCharsets.UTF_8));
  }

  /** Build an AndroidApp with the specified test classes as byte array. */
  protected AndroidApp buildAndroidApp(byte[]... classes) {
    return buildAndroidApp(Arrays.asList(classes));
  }

  /** Build an AndroidApp with the specified test classes as byte array. */
  protected AndroidApp buildAndroidApp(List<byte[]> classes) {
    AndroidApp.Builder builder = AndroidApp.builder();
    for (byte[] clazz : classes) {
      builder.addClassProgramData(clazz, Origin.unknown());
    }
    builder.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.N.getLevel()));
    return builder.build();
  }

  /**
   * Build an AndroidApp with the specified jar.
   */
  protected AndroidApp readJar(Path jar) {
    return AndroidApp.builder()
        .addProgramResourceProvider(ArchiveProgramResourceProvider.fromArchive(jar))
        .build();
  }

  /**
   * Build an AndroidApp with the specified test classes.
   */
  protected static AndroidApp readClasses(Class... classes) throws IOException {
    return readClasses(Arrays.asList(classes));
  }

  /** Build an AndroidApp with the specified test classes. */
  protected static AndroidApp readClasses(List<Class<?>> classes) throws IOException {
    return readClasses(classes, Collections.emptyList());
  }

  /** Build an AndroidApp with the specified test classes. */
  protected static AndroidApp readClasses(
      List<Class<?>> programClasses, List<Class<?>> libraryClasses) throws IOException {
    return buildClasses(programClasses, libraryClasses).build();
  }

  protected static AndroidApp.Builder buildClasses(Class<?>... programClasses) throws IOException {
    return buildClasses(Arrays.asList(programClasses), Collections.emptyList());
  }

  protected static AndroidApp.Builder buildClasses(Collection<Class<?>> programClasses)
      throws IOException {
    return buildClasses(programClasses, Collections.emptyList());
  }

  protected static AndroidApp.Builder buildClasses(
      Collection<Class<?>> programClasses, Collection<Class<?>> libraryClasses) throws IOException {
    AndroidApp.Builder builder = AndroidApp.builder();
    for (Class<?> clazz : programClasses) {
      builder.addProgramFiles(ToolHelper.getClassFileForTestClass(clazz));
    }
    if (!libraryClasses.isEmpty()) {
      PreloadedClassFileProvider.Builder libraryBuilder = PreloadedClassFileProvider.builder();
      for (Class<?> clazz : libraryClasses) {
        Path file = ToolHelper.getClassFileForTestClass(clazz);
        libraryBuilder.addResource(DescriptorUtils.javaTypeToDescriptor(clazz.getTypeName()),
            Files.readAllBytes(file));
      }
      builder.addLibraryResourceProvider(libraryBuilder.build());
    }
    return builder;
  }

  protected static AndroidApp readClassesAndRuntimeJar(
      List<Class<?>> programClasses, Backend backend) throws IOException {
    AndroidApp.Builder builder = AndroidApp.builder();
    for (Class<?> clazz : programClasses) {
      builder.addProgramFiles(ToolHelper.getClassFileForTestClass(clazz));
    }
    if (backend == Backend.DEX) {
      AndroidApiLevel androidLibrary = ToolHelper.getMinApiLevelForDexVm();
      builder.addLibraryFiles(ToolHelper.getAndroidJar(androidLibrary));
    } else {
      assert backend == Backend.CF;
      builder.addLibraryFiles(ToolHelper.getJava8RuntimeJar());
    }
    return builder.build();
  }

  /** Build an AndroidApp from the specified program files. */
  protected AndroidApp readProgramFiles(Path... programFiles) throws IOException {
    return AndroidApp.builder().addProgramFiles(programFiles).build();
  }

  /**
   * Copy test classes to the specified directory.
   */
  protected void copyTestClasses(Path dest, Class... classes) throws IOException {
    for (Class<?> clazz : classes) {
      Path path = dest.resolve(clazz.getCanonicalName().replace('.', '/') + ".class");
      Files.createDirectories(path.getParent());
      Files.copy(ToolHelper.getClassFileForTestClass(clazz), path);
    }
  }

  /** Create a temporary JAR file containing the specified test classes. */
  protected Path jarTestClasses(Class<?>... classes) throws IOException {
    return jarTestClasses(Arrays.asList(classes), null);
  }

  /** Create a temporary JAR file containing the specified test classes. */
  protected Path jarTestClasses(Iterable<Class<?>> classes) throws IOException {
    return jarTestClasses(classes, null);
  }

  /** Create a temporary JAR file containing the specified test classes and data resources. */
  protected Path jarTestClasses(Iterable<Class<?>> classes, List<DataResource> dataResources)
      throws IOException {
    Path jar = File.createTempFile("junit", ".jar", temp.getRoot()).toPath();
    try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar.toFile()))) {
      addTestClassesToJar(out, classes);
      if (dataResources != null) {
        addDataResourcesToJar(out, dataResources);
      }
    }
    return jar;
  }

  /** Create a temporary JAR file containing the specified test classes. */
  protected void addTestClassesToJar(JarOutputStream out, Iterable<Class<?>> classes)
      throws IOException {
    for (Class<?> clazz : classes) {
      try (FileInputStream in =
          new FileInputStream(ToolHelper.getClassFileForTestClass(clazz).toFile())) {
        out.putNextEntry(new ZipEntry(ToolHelper.getJarEntryForTestClass(clazz)));
        ByteStreams.copy(in, out);
        out.closeEntry();
      }
    }
  }

  /** Create a temporary JAR file containing the specified data resources. */
  protected void addDataResourcesToJar(
      JarOutputStream out, List<? extends DataResource> dataResources) throws IOException {
    try {
      for (DataResource dataResource : dataResources) {
        String name = dataResource.getName();
        boolean isDirectory = dataResource instanceof DataDirectoryResource;
        if (isDirectory && !name.endsWith("/")) {
          // Directory entries must end with a slash. Otherwise they will be empty files.
          name += "/";
        }
        out.putNextEntry(new ZipEntry(name));
        if (!isDirectory) {
          ByteStreams.copy(((DataEntryResource) dataResource).getByteStream(), out);
        }
        out.closeEntry();
      }
    } catch (ResourceException e) {
      throw new IOException("Resource error", e);
    }
  }

  /**
   * Creates a new, temporary JAR that contains all the entries from the given JAR as well as the
   * specified data resources. The given JAR is left unchanged.
   */
  protected Path addDataResourcesToExistingJar(
      Path existingJar, List<? extends DataResource> dataResources) throws IOException {
    Path newJar = File.createTempFile("app", FileUtils.JAR_EXTENSION, temp.getRoot()).toPath();
    try (JarOutputStream out = new JarOutputStream(new FileOutputStream(newJar.toFile()))) {
      ArchiveProgramResourceProvider.fromArchive(existingJar)
          .readArchive(
              (entry, stream) -> {
                out.putNextEntry(new ZipEntry(entry.getEntryName()));
                ByteStreams.copy(stream, out);
                out.closeEntry();
              });
      addDataResourcesToJar(out, dataResources);
    }
    return newJar;
  }

  private static DexApplication readApplicationForDexOutput(AndroidApp app, InternalOptions options)
      throws Exception {
    assert options.programConsumer == null;
    options.programConsumer = DexIndexedConsumer.emptyConsumer();
    return new ApplicationReader(app, options, Timing.empty()).read();
  }

  protected static AppView<AppInfo> computeAppView(AndroidApp app) throws Exception {
    AppInfo appInfo = new AppInfo(readApplicationForDexOutput(app, new InternalOptions()));
    return AppView.createForD8(appInfo);
  }

  protected static AppInfoWithClassHierarchy computeAppInfoWithClassHierarchy(AndroidApp app)
      throws Exception {
    return new AppInfoWithClassHierarchy(readApplicationForDexOutput(app, new InternalOptions()));
  }

  protected static AppView<AppInfoWithClassHierarchy> computeAppViewWithSubtyping(AndroidApp app)
      throws Exception {
    return computeAppViewWithSubtyping(
        app,
        factory ->
            buildConfigForRules(
                factory,
                Collections.singletonList(ProguardKeepRule.defaultKeepAllRule(unused -> {}))));
  }

  private static AppView<AppInfoWithClassHierarchy> computeAppViewWithSubtyping(
      AndroidApp app, Function<DexItemFactory, ProguardConfiguration> keepConfig) throws Exception {
    DexItemFactory dexItemFactory = new DexItemFactory();
    InternalOptions options = new InternalOptions(keepConfig.apply(dexItemFactory), new Reporter());
    DexApplication dexApplication = readApplicationForDexOutput(app, options);
    AppView<AppInfoWithClassHierarchy> appView =
        AppView.createForR8(new AppInfoWithClassHierarchy(dexApplication.toDirect()));
    appView.setAppServices(AppServices.builder(appView).build());
    return appView;
  }

  protected static AppView<AppInfoWithLiveness> computeAppViewWithLiveness(AndroidApp app)
      throws Exception {
    return computeAppViewWithLiveness(
        app,
        factory ->
            buildConfigForRules(
                factory, ImmutableList.of(ProguardKeepRule.defaultKeepAllRule(unused -> {}))));
  }

  protected static AppView<AppInfoWithLiveness> computeAppViewWithLiveness(
      AndroidApp app, Class<?> mainClass) throws Exception {
    return computeAppViewWithLiveness(
        app,
        factory ->
            buildConfigForRules(factory, buildKeepRuleForClassAndMethods(mainClass, factory)));
  }

  protected static AppView<AppInfoWithLiveness> computeAppViewWithLiveness(
      AndroidApp app, Function<DexItemFactory, ProguardConfiguration> keepConfig) throws Exception {
    AppView<AppInfoWithClassHierarchy> appView = computeAppViewWithSubtyping(app, keepConfig);
    // Run the tree shaker to compute an instance of AppInfoWithLiveness.
    ExecutorService executor = Executors.newSingleThreadExecutor();
    DirectMappedDexApplication application = appView.appInfo().app().asDirect();
    SubtypingInfo subtypingInfo = new SubtypingInfo(application.allClasses(), application);
    RootSet rootSet =
        new RootSetBuilder(
                appView, subtypingInfo, application.options.getProguardConfiguration().getRules())
            .run(executor);
    appView.setRootSet(rootSet);
    AppInfoWithLiveness appInfoWithLiveness =
        EnqueuerFactory.createForInitialTreeShaking(appView, subtypingInfo)
            .traceApplication(rootSet, ProguardClassFilter.empty(), executor, application.timing);
    // We do not run the tree pruner to ensure that the hierarchy is as designed and not modified
    // due to liveness.
    return appView.setAppInfo(appInfoWithLiveness);
  }

  protected static DexType buildType(Class<?> clazz, DexItemFactory factory) {
    return buildType(Reference.classFromClass(clazz), factory);
  }

  protected static DexType buildType(TypeReference type, DexItemFactory factory) {
    return factory.createType(type.getDescriptor());
  }

  protected static DexField buildField(Field field, DexItemFactory factory) {
    return buildField(Reference.fieldFromField(field), factory);
  }

  protected static DexField buildField(FieldReference field, DexItemFactory factory) {
    return factory.createField(
        buildType(field.getHolderClass(), factory),
        buildType(field.getFieldType(), factory),
        field.getFieldName());
  }

  protected static DexMethod buildMethod(Method method, DexItemFactory factory) {
    return buildMethod(Reference.methodFromMethod(method), factory);
  }

  protected static DexMethod buildMethod(MethodReference method, DexItemFactory factory) {
    return factory.createMethod(
        buildType(method.getHolderClass(), factory),
        buildProto(method.getReturnType(), method.getFormalTypes(), factory),
        method.getMethodName());
  }

  protected static DexMethod buildNullaryVoidMethod(
      Class<?> clazz, String name, DexItemFactory factory) {
    return buildMethod(
        Reference.method(Reference.classFromClass(clazz), name, Collections.emptyList(), null),
        factory);
  }

  protected static DexProto buildProto(
      TypeReference returnType, List<TypeReference> formalTypes, DexItemFactory factory) {
    return factory.createProto(
        returnType == null ? factory.voidType : buildType(returnType, factory),
        ListUtils.map(formalTypes, type -> buildType(type, factory)));
  }

  protected static List<ProguardConfigurationRule> buildKeepRuleForClass(
      Class<?> clazz, DexItemFactory factory) {
    Builder keepRuleBuilder = ProguardKeepRule.builder();
    keepRuleBuilder.setSource("buildKeepRuleForClass " + clazz.getTypeName());
    keepRuleBuilder.setType(ProguardKeepRuleType.KEEP);
    keepRuleBuilder.setClassNames(
        ProguardClassNameList.singletonList(
            ProguardTypeMatcher.create(
                factory.createType(DescriptorUtils.javaTypeToDescriptor(clazz.getTypeName())))));
    return Collections.singletonList(keepRuleBuilder.build());
  }

  protected static List<ProguardConfigurationRule> buildKeepRuleForClassAndMethods(
      Class<?> clazz, DexItemFactory factory) {
    Builder keepRuleBuilder = ProguardKeepRule.builder();
    keepRuleBuilder.setSource("buildKeepRuleForClassAndMethods " + clazz.getTypeName());
    keepRuleBuilder.setType(ProguardKeepRuleType.KEEP);
    keepRuleBuilder.setClassNames(
        ProguardClassNameList.singletonList(
            ProguardTypeMatcher.create(
                factory.createType(DescriptorUtils.javaTypeToDescriptor(clazz.getTypeName())))));
    keepRuleBuilder.setMemberRules(
        Lists.newArrayList(
            ProguardMemberRule.builder().setRuleType(ProguardMemberType.ALL_METHODS).build()));
    return Collections.singletonList(keepRuleBuilder.build());
  }

  protected static ProguardConfiguration buildConfigForRules(
      DexItemFactory factory, Collection<ProguardConfigurationRule> rules) {
    return buildConfigForRules(factory, new Reporter(), rules);
  }

  protected static ProguardConfiguration buildConfigForRules(
      DexItemFactory factory, Reporter reporter, Collection<ProguardConfigurationRule> rules) {
    ProguardConfiguration.Builder builder = ProguardConfiguration.builder(factory, reporter);
    rules.forEach(builder::addRule);
    return builder.build();
  }

  /** Returns a list containing all the data resources in the given app. */
  public static List<DataEntryResource> getDataResources(AndroidApp app) throws ResourceException {
    List<DataEntryResource> dataResources = new ArrayList<>();
    for (ProgramResourceProvider programResourceProvider : app.getProgramResourceProviders()) {
      dataResources.addAll(getDataResources(programResourceProvider.getDataResourceProvider()));
    }
    return dataResources;
  }

  public static List<DataEntryResource> getDataResources(DataResourceProvider dataResourceProvider)
      throws ResourceException {
    List<DataEntryResource> dataResources = new ArrayList<>();
    if (dataResourceProvider != null) {
      dataResourceProvider.accept(
          new Visitor() {
            @Override
            public void visit(DataDirectoryResource directory) {}

            @Override
            public void visit(DataEntryResource file) {
              dataResources.add(file);
            }
          });
    }
    return dataResources;
  }

  protected static Path getFileInTest(String folder, String fileName) {
    return Paths.get(ToolHelper.TESTS_DIR, "java", folder, fileName);
  }

  /**
   * Create a temporary JAR file containing all test classes in a package.
   */
  protected Path jarTestClassesInPackage(Package pkg) throws IOException {
    Path jar = File.createTempFile("junit", ".jar", temp.getRoot()).toPath();
    String zipEntryPrefix = ToolHelper.getJarEntryForTestPackage(pkg) + "/";
    try (JarOutputStream out = new JarOutputStream(new FileOutputStream(jar.toFile()))) {
      for (Path file : ToolHelper.getClassFilesForTestPackage(pkg)) {
        try (FileInputStream in = new FileInputStream(file.toFile())) {
          out.putNextEntry(new ZipEntry(zipEntryPrefix + file.getFileName()));
          ByteStreams.copy(in, out);
          out.closeEntry();
        }
      }
    }
    return jar;
  }

  /** Create a temporary JAR file containing the specified test classes. */
  protected Path jarTestClasses(List<Class<?>> classes) throws IOException {
    return jarTestClasses(classes.toArray(new Class<?>[]{}));
  }

  protected static List<Object[]> buildParameters(Object... arraysOrIterables) {
    Function<Object, List<Object>> arrayOrIterableToList =
        arrayOrIterable -> {
          if (arrayOrIterable.getClass().isArray()) {
            Object[] array = (Object[]) arrayOrIterable;
            return Arrays.asList(array);
          } else {
            assert arrayOrIterable instanceof Iterable<?>;
            Iterable<?> iterable = (Iterable) arrayOrIterable;
            return ImmutableList.builder().addAll(iterable).build();
          }
        };
    List<List<Object>> lists =
        Arrays.stream(arraysOrIterables).map(arrayOrIterableToList).collect(Collectors.toList());
    return cartesianProduct(lists).stream().map(List::toArray).collect(Collectors.toList());
  }

  /** Compile an application with D8. */
  protected AndroidApp compileWithD8(AndroidApp app) throws CompilationFailedException {
    D8Command.Builder builder = ToolHelper.prepareD8CommandBuilder(app);
    AndroidAppConsumers appSink = new AndroidAppConsumers(builder);
    D8.run(builder.build());
    return appSink.build();
  }

  /** Compile an application with D8. */
  protected AndroidApp compileWithD8(AndroidApp app, Consumer<InternalOptions> optionsConsumer)
      throws CompilationFailedException {
    return ToolHelper.runD8(app, optionsConsumer);
  }

  /** Compile an application with R8. */
  protected AndroidApp compileWithR8(Class... classes)
      throws IOException, CompilationFailedException {
    return ToolHelper.runR8(readClasses(classes));
  }

  /** Compile an application with R8. */
  protected AndroidApp compileWithR8(List<Class<?>> classes)
      throws IOException, CompilationFailedException {
    R8Command command = ToolHelper.prepareR8CommandBuilder(readClasses(classes)).build();
    return ToolHelper.runR8(command);
  }

  /** Compile an application with R8. */
  protected AndroidApp compileWithR8(
      List<Class<?>> classes, Consumer<InternalOptions> optionsConsumer)
      throws IOException, CompilationFailedException {
    R8Command command = ToolHelper.prepareR8CommandBuilder(readClasses(classes)).build();
    return ToolHelper.runR8(command, optionsConsumer);
  }

  /** Compile an application with R8. */
  protected AndroidApp compileWithR8(AndroidApp app) throws CompilationFailedException {
    R8Command command = ToolHelper.prepareR8CommandBuilder(app).build();
    return ToolHelper.runR8(command);
  }

  /** Compile an application with R8. */
  protected AndroidApp compileWithR8(AndroidApp app, Consumer<InternalOptions> optionsConsumer)
      throws IOException, CompilationFailedException {
    R8Command command = ToolHelper.prepareR8CommandBuilder(app)
        .setDisableTreeShaking(true)
        .setDisableMinification(true)
        .addProguardConfiguration(ImmutableList.of("-keepattributes *"), Origin.unknown())
        .build();

    return ToolHelper.runR8(command, optionsConsumer);
  }

  /** Compile an application with R8 using the supplied proguard configuration. */
  protected AndroidApp compileWithR8(List<Class<?>> classes, String proguardConfig)
      throws IOException, CompilationFailedException {
    return compileWithR8(readClasses(classes), proguardConfig);
  }

  /** Compile an application with R8 using the supplied proguard configuration. */
  protected AndroidApp compileWithR8(
      List<Class<?>> classes, String proguardConfig, Consumer<InternalOptions> optionsConsumer)
      throws IOException, CompilationFailedException {
    return compileWithR8(readClasses(classes), proguardConfig, optionsConsumer);
  }

  /** Compile an application with R8 using the supplied proguard configuration. */
  protected AndroidApp compileWithR8(List<Class<?>> classes, Path proguardConfig)
      throws IOException, CompilationFailedException {
    return compileWithR8(readClasses(classes), proguardConfig);
  }

  /** Compile an application with R8 using the supplied proguard configuration. */
  protected AndroidApp compileWithR8(AndroidApp app, Path proguardConfig)
      throws IOException, CompilationFailedException {
    R8Command command =
        ToolHelper.prepareR8CommandBuilder(app)
            .addProguardConfigurationFiles(proguardConfig)
            .build();
    return ToolHelper.runR8(command);
  }

  /** Compile an application with R8 using the supplied proguard configuration. */
  protected AndroidApp compileWithR8(AndroidApp app, String proguardConfig)
      throws IOException, CompilationFailedException {
    return compileWithR8(app, proguardConfig, null, Backend.DEX);
  }

  /** Compile an application with R8 using the supplied proguard configuration. */
  protected AndroidApp compileWithR8(AndroidApp app, String proguardConfig, Backend backend)
      throws IOException, CompilationFailedException {
    return compileWithR8(app, proguardConfig, null, backend);
  }

  /** Compile an application with R8 using the supplied proguard configuration. */
  protected AndroidApp compileWithR8(
      AndroidApp app, String proguardConfig, Consumer<InternalOptions> optionsConsumer)
      throws IOException, CompilationFailedException {
    return compileWithR8(app, proguardConfig, optionsConsumer, Backend.DEX);
  }

  /** Compile an application with R8 using the supplied proguard configuration and backend. */
  protected AndroidApp compileWithR8(
      AndroidApp app,
      String proguardConfig,
      Consumer<InternalOptions> optionsConsumer,
      Backend backend)
      throws CompilationFailedException {
    R8Command command =
        ToolHelper.prepareR8CommandBuilder(app, emptyConsumer(backend))
            .addProguardConfiguration(ImmutableList.of(proguardConfig), Origin.unknown())
            .addLibraryFiles(runtimeJar(backend))
            .build();
    return ToolHelper.runR8(command, optionsConsumer);
  }

  /** Compile an application with R8 using the supplied proguard configuration. */
  protected AndroidApp compileWithR8(
      AndroidApp app, Path proguardConfig, Consumer<InternalOptions> optionsConsumer)
      throws CompilationFailedException {
    R8Command command =
        ToolHelper.prepareR8CommandBuilder(app)
            .addProguardConfigurationFiles(proguardConfig)
            .build();
    return ToolHelper.runR8(command, optionsConsumer);
  }

  /**
   * Generate a Proguard configuration for keeping the "static void main(String[])" method of the
   * specified class.
   */
  public static String keepMainProguardConfiguration(Class<?> clazz) {
    return keepMainProguardConfiguration(clazz.getTypeName());
  }

  /**
   * Generate a Proguard configuration for keeping the "static void main(String[])" method of the
   * specified class.
   */
  public static String keepMainProguardConfiguration(Class<?> clazz, List<String> additionalLines) {
    return keepMainProguardConfiguration(clazz.getTypeName()) + StringUtils.lines(additionalLines);
  }

  /**
   * Generate a Proguard configuration for keeping the "public static void main(String[])" method of
   * the specified class.
   *
   * The class is assumed to be public.
   */
  public static String keepMainProguardConfiguration(String clazz) {
    return StringUtils.lines(
        "-keep class " + clazz + " {", "  public static void main(java.lang.String[]);", "}");
  }

  public static String noShrinkingNoMinificationProguardConfiguration() {
    return StringUtils.lines("-dontshrink", "-dontobfuscate");
  }

  /**
   * Generate a Proguard configuration for keeping the "static void main(String[])" method of the
   * specified class and specify if -allowaccessmodification and -dontobfuscate are added as well.
   */
  public static String keepMainProguardConfiguration(
      Class<?> clazz, boolean allowaccessmodification, boolean obfuscate) {
    return keepMainProguardConfiguration(clazz)
        + (allowaccessmodification ? "-allowaccessmodification\n" : "")
        + (obfuscate ? "-printmapping\n" : "-dontobfuscate\n");
  }

  public static String keepMainProguardConfiguration(
      String clazz, boolean allowaccessmodification, boolean obfuscate) {
    return keepMainProguardConfiguration(clazz)
        + (allowaccessmodification ? "-allowaccessmodification\n" : "")
        + (obfuscate ? "-printmapping\n" : "-dontobfuscate\n");
  }

  /**
   * Generate a Proguard configuration for keeping the "static void main(String[])" method of the
   * specified class and add rules to inline methods with the inlining annotation.
   */
  public static String keepMainProguardConfigurationWithInliningAnnotation(Class<?> clazz) {
    return "-forceinline class * { @com.android.tools.r8.ForceInline *; }"
        + System.lineSeparator()
        + "-neverinline class * { @com.android.tools.r8.NeverInline *; }"
        + System.lineSeparator()
        + keepMainProguardConfiguration(clazz);
  }

  public static String neverMergeRule() {
    return "-nevermerge @com.android.tools.r8.NeverMerge class *";
  }

  /**
   * Run application on the specified version of Art with the specified main class.
   */
  protected ProcessResult runOnArtRaw(AndroidApp app, String mainClass,
      Consumer<ArtCommandBuilder> cmdBuilder, DexVm version) throws IOException {
    Path out = File.createTempFile("junit", ".zip", temp.getRoot()).toPath();
    app.writeToZip(out, OutputMode.DexIndexed);
    return ToolHelper.runArtRaw(
        ImmutableList.of(out.toString()), mainClass, cmdBuilder, version, false);
  }

  /**
   * Run application on the specified version of Art with the specified main class.
   */
  protected ProcessResult runOnArtRaw(AndroidApp app, String mainClass, DexVm version)
      throws IOException {
    return runOnArtRaw(app, mainClass, null, version);
  }

  /**
   * Run application on Art with the specified main class.
   */
  protected ProcessResult runOnArtRaw(AndroidApp app, String mainClass) throws IOException {
    return runOnArtRaw(app, mainClass, null);
  }

  /**
   * Run application on Art with the specified main class.
   */
  protected ProcessResult runOnArtRaw(AndroidApp app, Class mainClass) throws IOException {
    return runOnArtRaw(app, mainClass.getTypeName());
  }

  /**
   * Run application on Art with the specified main class and provided arguments.
   */
  protected String runOnArt(AndroidApp app, Class mainClass, String... args) throws IOException {
    return runOnArt(app, mainClass, Arrays.asList(args));
  }

  /**
   * Run application on Art with the specified main class and provided arguments.
   */
  protected String runOnArt(AndroidApp app, String mainClass, List<String> args)
      throws IOException {
    return runOnArt(app, mainClass, args, null);
  }

  /**
   * Run application on Art with the specified main class, provided arguments, and specified VM
   * version.
   */
  protected String runOnArt(AndroidApp app, String mainClass, List<String> args, DexVm dexVm)
      throws IOException {
    Path out = File.createTempFile("junit", ".zip", temp.getRoot()).toPath();
    app.writeToZip(out, OutputMode.DexIndexed);
    return ToolHelper.runArtNoVerificationErrors(
        ImmutableList.of(out.toString()), mainClass,
        builder -> {
          builder.appendArtOption("-ea");
          for (String arg : args) {
            builder.appendProgramArgument(arg);
          }
        },
        dexVm);
  }

  /**
   * Run application on Art with the specified main class and provided arguments.
   */
  protected String runOnArt(AndroidApp app, Class mainClass, List<String> args) throws IOException {
    return runOnArt(app, mainClass.getCanonicalName(), args);
  }

  /**
   * Run application on Art with the specified main class and provided arguments.
   */
  protected String runOnArt(AndroidApp app, String mainClass, String... args) throws IOException {
    return runOnArt(app, mainClass, Arrays.asList(args));
  }

  /**
   * Run a single class application on Java.
   */
  protected String runOnJava(Class mainClass) throws Exception {
    ProcessResult result = ToolHelper.runJava(mainClass);
    ToolHelper.failOnProcessFailure(result);
    ToolHelper.failOnVerificationErrors(result);
    return result.stdout;
  }

  /** Run application on Java with the specified main class and provided arguments. */
  protected String runOnJava(AndroidApp app, Class mainClass, String... args) throws IOException {
    return runOnJava(app, mainClass, Arrays.asList(args));
  }

  /** Run application on Java with the specified main class and provided arguments. */
  protected String runOnJava(AndroidApp app, Class mainClass, List<String> args)
      throws IOException {
    return runOnJava(app, mainClass.getCanonicalName(), args);
  }

  /** Run application on Java with the specified main class and provided arguments. */
  protected String runOnJava(AndroidApp app, String mainClass, String... args) throws IOException {
    return runOnJava(app, mainClass, Arrays.asList(args));
  }

  /** Run application on Java with the specified main class and provided arguments. */
  protected String runOnJava(AndroidApp app, String mainClass, List<String> args)
      throws IOException {
    ProcessResult result = runOnJavaRaw(app, mainClass, args);
    ToolHelper.failOnProcessFailure(result);
    ToolHelper.failOnVerificationErrors(result);
    return result.stdout;
  }

  protected ProcessResult runOnJavaRawNoVerify(String main, byte[]... classes) throws IOException {
    return runOnJavaRawNoVerify(main, Arrays.asList(classes), Collections.emptyList());
  }

  protected ProcessResult runOnJavaRawNoVerify(String main, List<byte[]> classes, List<String> args)
      throws IOException {
    return ToolHelper.runJavaNoVerify(Collections.singletonList(writeToJar(classes)), main, args);
  }

  protected ProcessResult runOnJavaRaw(String main, byte[]... classes) throws IOException {
    return runOnJavaRaw(main, Arrays.asList(classes), Collections.emptyList());
  }

  protected ProcessResult runOnJavaRaw(String main, List<byte[]> classes, List<String> args)
      throws IOException {
    List<String> mainAndArgs = new ArrayList<>();
    mainAndArgs.add(main);
    mainAndArgs.addAll(args);
    return ToolHelper.runJava(
        Collections.singletonList(writeToJar(classes)),
        mainAndArgs.toArray(StringUtils.EMPTY_ARRAY));
  }

  protected ProcessResult runOnJavaRaw(AndroidApp app, String mainClass, List<String> args)
      throws IOException {
    Path out = File.createTempFile("junit", ".zip", temp.getRoot()).toPath();
    app.writeToZip(out, OutputMode.ClassFile);
    List<String> mainAndArgs = new ArrayList<>();
    mainAndArgs.add(mainClass);
    mainAndArgs.addAll(args);
    return ToolHelper.runJava(out, mainAndArgs.toArray(StringUtils.EMPTY_ARRAY));
  }

  protected ProcessResult runOnJavaRawNoVerify(AndroidApp app, String mainClass, List<String> args)
      throws IOException {
    Path out = File.createTempFile("junit", ".zip", temp.getRoot()).toPath();
    app.writeToZip(out, OutputMode.ClassFile);
    return ToolHelper.runJavaNoVerify(out, mainClass, args.toArray(StringUtils.EMPTY_ARRAY));
  }

  /** Run application on Art or Java with the specified main class. */
  protected String runOnVM(AndroidApp app, Class mainClass, Backend backend) throws IOException {
    return runOnVM(app, mainClass.getName(), backend);
  }

  /** Run application on Art or Java with the specified main class. */
  protected String runOnVM(AndroidApp app, String mainClass, Backend backend) throws IOException {
    switch (backend) {
      case CF:
        return runOnJava(app, mainClass);
      case DEX:
        return runOnArt(app, mainClass);
      default:
        throw new Unreachable("Unexpected backend: " + backend);
    }
  }

  protected ProcessResult runOnVMRaw(AndroidApp app, Class<?> mainClass, Backend backend)
      throws IOException {
    return runOnVMRaw(app, mainClass.getTypeName(), backend);
  }

  protected ProcessResult runOnVMRaw(AndroidApp app, String mainClass, Backend backend)
      throws IOException {
    switch (backend) {
      case CF:
        return runOnJavaRaw(app, mainClass, ImmutableList.of());
      case DEX:
        return runOnArtRaw(app, mainClass);
      default:
        throw new Unreachable("Unexpected backend: " + backend);
    }
  }

  public static String extractClassName(byte[] ccc) {
    return DescriptorUtils.descriptorToJavaType(extractClassDescriptor(ccc));
  }

  public static String extractClassDescriptor(byte[] ccc) {
    return "L" + extractClassInternalType(ccc) + ";";
  }

  private static String extractClassInternalType(byte[] ccc) {
    class ClassNameExtractor extends ClassVisitor {
      private String className;

      private ClassNameExtractor() {
        super(ASM_VERSION);
      }

      @Override
      public void visit(
          int version,
          int access,
          String name,
          String signature,
          String superName,
          String[] interfaces) {
        className = name;
      }

      String getClassInternalType() {
        return className;
      }
    }

    ClassReader reader = new ClassReader(ccc);
    ClassNameExtractor extractor = new ClassNameExtractor();
    reader.accept(
        extractor, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    return extractor.getClassInternalType();
  }

  protected static void writeClassesToJar(Path output, Collection<Class<?>> classes)
      throws IOException {
    ClassFileConsumer consumer = new ArchiveConsumer(output);
    for (Class<?> clazz : classes) {
      consumer.accept(
          ByteDataView.of(Files.readAllBytes(ToolHelper.getClassFileForTestClass(clazz))),
          DescriptorUtils.javaTypeToDescriptor(clazz.getTypeName()),
          null);
    }
    consumer.finished(null);
  }

  protected static void writeClassesToJar(Path output, Class<?>... classes) throws IOException {
    writeClassesToJar(output, Arrays.asList(classes));
  }

  protected static void writeClassFileDataToJar(Path output, Collection<byte[]> classes) {
    ClassFileConsumer consumer = new ArchiveConsumer(output);
    for (byte[] clazz : classes) {
      consumer.accept(ByteDataView.of(clazz), extractClassDescriptor(clazz), null);
    }
    consumer.finished(null);
  }

  protected static void writeClassFilesToJar(Path output, Collection<Path> classes)
      throws IOException {
    List<byte[]> bytes = new LinkedList<>();
    for (Path classPath : classes) {
      byte[] classBytes = Files.readAllBytes(Paths.get(classPath.toString()));
      bytes.add(classBytes);
    }
    writeClassFileDataToJar(output, bytes);
  }

  protected Path writeToJar(List<byte[]> classes) throws IOException {
    Path result = File.createTempFile("junit", ".jar", temp.getRoot()).toPath();
    writeClassFileDataToJar(result, classes);
    return result;
  }

  protected Path writeToJar(JasminBuilder jasminBuilder) throws Exception {
    return writeToJar(jasminBuilder.buildClasses());
  }

  /**
   * Disassemble the content of an application. Only works for an application with only dex code.
   */
  protected void disassemble(AndroidApp app) throws Exception {
    InternalOptions options = new InternalOptions();
    System.out.println(SmaliWriter.smali(app, options));
  }

  protected MethodSubject getMethodSubject(
      CodeInspector inspector,
      String className,
      String returnType,
      String methodName,
      List<String> parameters) {
    ClassSubject clazz = inspector.clazz(className);
    assertTrue(clazz.isPresent());
    MethodSubject method = clazz.method(returnType, methodName, parameters);
    assertTrue(method.isPresent());
    return method;
  }

  protected MethodSubject getMethodSubject(
      AndroidApp application,
      String className,
      String returnType,
      String methodName,
      List<String> parameters) {
    try {
      CodeInspector inspector = new CodeInspector(application);
      return getMethodSubject(inspector, className, returnType, methodName, parameters);
    } catch (Exception e) {
      return null;
    }
  }

  protected DexEncodedMethod getMethod(
      AndroidApp application,
      String className,
      String returnType,
      String methodName,
      List<String> parameters) {
    return getMethodSubject(application, className, returnType, methodName, parameters).getMethod();
  }

  protected ProgramMethod getMethod(
      CodeInspector inspector,
      String className,
      String returnType,
      String methodName,
      List<String> parameters) {
    return getMethodSubject(inspector, className, returnType, methodName, parameters)
        .getProgramMethod();
  }

  protected static void checkInstructions(
      DexCode code, List<Class<? extends Instruction>> instructions) {
    assertEquals(instructions.size(), code.instructions.length);
    for (int i = 0; i < instructions.size(); ++i) {
      assertEquals("Unexpected instruction at index " + i,
          instructions.get(i), code.instructions[i].getClass());
    }
  }

  protected Stream<Instruction> filterInstructionKind(
      DexCode dexCode, Class<? extends Instruction> kind) {
    return Arrays.stream(dexCode.instructions)
        .filter(kind::isInstance)
        .map(kind::cast);
  }

  protected long countCall(MethodSubject method, String className, String methodName) {
    return method.streamInstructions().filter(instructionSubject -> {
      if (instructionSubject.isInvoke()) {
        DexMethod invokedMethod = instructionSubject.getMethod();
        return invokedMethod.holder.toString().contains(className)
            && invokedMethod.name.toString().contains(methodName);
      }
      return false;
    }).count();
  }

  protected long countCall(MethodSubject method, String methodName) {
    return method.streamInstructions().filter(instructionSubject -> {
      if (instructionSubject.isInvoke()) {
        DexMethod invokedMethod = instructionSubject.getMethod();
        return invokedMethod.name.toString().contains(methodName);
      }
      return false;
    }).count();
  }

  public enum MinifyMode {
    NONE,
    JAVA,
    AGGRESSIVE;

    public boolean isMinify() {
      return this != NONE;
    }

    public boolean isAggressive() {
      return this == AGGRESSIVE;
    }
  }

  public static ProgramConsumer emptyConsumer(Backend backend) {
    if (backend == Backend.DEX) {
      return DexIndexedConsumer.emptyConsumer();
    } else {
      assert backend == Backend.CF;
      return ClassFileConsumer.emptyConsumer();
    }
  }

  public static OutputMode outputMode(Backend backend) {
    if (backend == Backend.DEX) {
      return OutputMode.DexIndexed;
    } else {
      assert backend == Backend.CF;
      return OutputMode.ClassFile;
    }
  }

  @Deprecated
  public static Path runtimeJar(TestParameters parameters) {
    if (parameters.isDexRuntime()) {
      return ToolHelper.getAndroidJar(parameters.getRuntime().asDex().getMinApiLevel());
    } else {
      assert parameters.isCfRuntime();
      return ToolHelper.getJava8RuntimeJar();
    }
  }

  @Deprecated
  public static Path runtimeJar(Backend backend) {
    if (backend == Backend.DEX) {
      return ToolHelper.getDefaultAndroidJar();
    } else {
      assert backend == Backend.CF;
      return ToolHelper.getJava8RuntimeJar();
    }
  }

  public static class JarBuilder {
    final Path jar;
    final ZipOutputStream stream;
    final Set<Class<?>> servicesAdded = Sets.newIdentityHashSet();

    private JarBuilder(TemporaryFolder temp) throws IOException {
      jar = temp.newFolder().toPath().resolve("a.jar");
      stream = new ZipOutputStream(Files.newOutputStream(jar));
    }

    public static JarBuilder builder(TemporaryFolder temp) throws IOException {
      return new JarBuilder(temp);
    }

    public JarBuilder addClass(Class<?> clazz) throws IOException {
      stream.putNextEntry(new ZipEntry(DescriptorUtils.getPathFromJavaType(clazz)));
      stream.write(Files.readAllBytes(ToolHelper.getClassFileForTestClass(clazz)));
      stream.closeEntry();
      return this;
    }

    public JarBuilder addResource(String path, String content) throws IOException {
      stream.putNextEntry(new ZipEntry(path));
      stream.write(content.getBytes(StandardCharsets.UTF_8));
      stream.closeEntry();
      return this;
    }

    public JarBuilder addServiceWithImplementations(
        Class<?> service, List<Class<?>> implementations) throws IOException {
      boolean added = servicesAdded.add(service);
      assert added : "Currently each service can only be added once";
      addResource(
          "META-INF/services/" + Greeter.class.getTypeName(),
          StringUtils.lines(
              implementations.stream().map(Class::getTypeName).collect(Collectors.toList())));
      return this;
    }

    public Path build() throws IOException {
      stream.close();
      return jar;
    }
  }

  public JarBuilder jarBuilder() throws IOException {
    return JarBuilder.builder(temp);
  }

  public List<Path> buildOnDexRuntime(TestParameters parameters, List<Path> paths)
      throws CompilationFailedException, IOException {
    if (parameters.isCfRuntime()) {
      return paths;
    }
    return Collections.singletonList(
        testForD8()
            .addProgramFiles(paths)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip());
  }

  public List<Path> buildOnDexRuntime(TestParameters parameters, Path... paths)
      throws IOException, CompilationFailedException {
    return buildOnDexRuntime(parameters, Arrays.asList(paths));
  }

  public Path buildOnDexRuntime(TestParameters parameters, Class<?>... classes)
      throws IOException, CompilationFailedException {
    if (parameters.isDexRuntime()) {
      return testForD8()
          .addProgramClasses(classes)
          .setMinApi(parameters.getApiLevel())
          .compile()
          .writeToZip();
    }
    Path path = temp.newFolder().toPath().resolve("classes.jar");
    ArchiveConsumer consumer = new ArchiveConsumer(path);
    for (Class clazz : classes) {
      consumer.accept(
          ByteDataView.of(ToolHelper.getClassAsBytes(clazz)),
          DescriptorUtils.javaTypeToDescriptor(clazz.getTypeName()),
          null);
    }
    consumer.finished(null);
    return path;
  }

  public static String binaryName(Class<?> clazz) {
    return DescriptorUtils.getBinaryNameFromJavaType(typeName(clazz));
  }

  public static String descriptor(Class<?> clazz) {
    return DescriptorUtils.javaTypeToDescriptor(typeName(clazz));
  }

  public static String typeName(Class<?> clazz) {
    return clazz.getTypeName();
  }

  public static AndroidApiLevel apiLevelWithDefaultInterfaceMethodsSupport() {
    return AndroidApiLevel.N;
  }

  public static AndroidApiLevel apiLevelWithInvokeCustomSupport() {
    return AndroidApiLevel.O;
  }

  public Path compileToZip(
      TestParameters parameters, Collection<Class<?>> classPath, Class<?>... compilationUnit)
      throws Exception {
    return compileToZip(parameters, classPath, Arrays.asList(compilationUnit));
  }

  public Path compileToZip(
      TestParameters parameters,
      Collection<Class<?>> classpath,
      Collection<Class<?>> compilationUnit)
      throws Exception {
    if (parameters.isCfRuntime()) {
      Path out = temp.newFolder().toPath().resolve("out.jar");
      writeClassesToJar(out, compilationUnit);
      return out;
    }
    return testForD8()
        .setMinApi(parameters.getApiLevel())
        .addProgramClasses(compilationUnit)
        .addClasspathClasses(classpath)
        .compile()
        .writeToZip();
  }
}
