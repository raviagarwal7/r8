// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static com.android.tools.r8.utils.FileUtils.isDexFile;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.DeviceRunner.DeviceRunnerConfigurationException;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper.DexVm.Kind;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AssemblyWriter;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.shaking.FilteredClassPath;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationParser;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.ZipUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.CRC32;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.rules.TemporaryFolder;

public class ToolHelper {

  static final Path[] EMPTY_PATH = {};

  public static final String SOURCE_DIR = "src/main/java/";
  public static final String BUILD_DIR = "build/";
  public static final String GENERATED_TEST_BUILD_DIR = BUILD_DIR + "generated/test/";
  public static final String LIBS_DIR = BUILD_DIR + "libs/";
  public static final String THIRD_PARTY_DIR = "third_party/";
  public static final String TOOLS_DIR = "tools/";
  public static final String TESTS_DIR = "src/test/";
  public static final String EXAMPLES_DIR = TESTS_DIR + "examples/";
  public static final String EXAMPLES_ANDROID_N_DIR = TESTS_DIR + "examplesAndroidN/";
  public static final String EXAMPLES_ANDROID_O_DIR = TESTS_DIR + "examplesAndroidO/";
  public static final String EXAMPLES_ANDROID_P_DIR = TESTS_DIR + "examplesAndroidP/";
  public static final String EXAMPLES_KOTLIN_DIR = TESTS_DIR + "examplesKotlin/";
  public static final String TESTS_BUILD_DIR = BUILD_DIR + "test/";
  public static final String JDK_TESTS_BUILD_DIR = TESTS_BUILD_DIR + "jdk11Tests/";
  public static final String EXAMPLES_BUILD_DIR = TESTS_BUILD_DIR + "examples/";
  public static final String EXAMPLES_CF_DIR = EXAMPLES_BUILD_DIR + "classes/";
  public static final String EXAMPLES_KOTLIN_BUILD_DIR = TESTS_BUILD_DIR + "examplesKotlin/";
  public static final String EXAMPLES_KOTLIN_RESOURCE_DIR =
      TESTS_BUILD_DIR + "kotlinR8TestResources/";
  public static final String EXAMPLES_ANDROID_N_BUILD_DIR = TESTS_BUILD_DIR + "examplesAndroidN/";
  public static final String EXAMPLES_ANDROID_O_BUILD_DIR = TESTS_BUILD_DIR + "examplesAndroidO/";
  public static final String EXAMPLES_ANDROID_P_BUILD_DIR = TESTS_BUILD_DIR + "examplesAndroidP/";
  public static final String EXAMPLES_JAVA9_BUILD_DIR = TESTS_BUILD_DIR + "examplesJava9/";
  public static final String EXAMPLES_JAVA10_BUILD_DIR = TESTS_BUILD_DIR + "examplesJava10/";
  public static final String EXAMPLES_JAVA11_JAR_DIR = TESTS_BUILD_DIR + "examplesJava11/";
  public static final String EXAMPLES_JAVA11_BUILD_DIR = BUILD_DIR + "classes/java/examplesJava11/";
  public static final String EXAMPLES_PROTO_BUILD_DIR = TESTS_BUILD_DIR + "examplesProto/";
  public static final String GENERATED_PROTO_BUILD_DIR = GENERATED_TEST_BUILD_DIR + "proto/";
  public static final String SMALI_DIR = TESTS_DIR + "smali/";
  public static final String SMALI_BUILD_DIR = TESTS_BUILD_DIR + "smali/";
  public static final String JAVA_CLASSES_DIR = BUILD_DIR + "classes/java/";
  public static final String JDK_11_TESTS_CLASSES_DIR = JAVA_CLASSES_DIR + "jdk11Tests/";

  public static final Path API_SAMPLE_JAR = Paths.get("tests", "r8_api_usage_sample.jar");

  public static final String LINE_SEPARATOR = StringUtils.LINE_SEPARATOR;
  public static final String CLASSPATH_SEPARATOR = File.pathSeparator;

  public static final String DEFAULT_DEX_FILENAME = "classes.dex";
  public static final String DEFAULT_PROGUARD_MAP_FILE = "proguard.map";

  public static final String JAVA_8_RUNTIME = "third_party/openjdk/openjdk-rt-1.8/rt.jar";
  public static final String DESUGAR_JDK_LIBS = "third_party/openjdk/desugar_jdk_libs/libjava.jar";
  public static final String CORE_LAMBDA_STUBS =
      "third_party/core-lambda-stubs/core-lambda-stubs.jar";
  public static final String JSR223_RI_JAR = "third_party/jsr223-api-1.0/jsr223-api-1.0.jar";
  public static final String RHINO_ANDROID_JAR =
      "third_party/rhino-android-1.1.1/rhino-android-1.1.1.jar";
  public static final String RHINO_JAR = "third_party/rhino-1.7.10/rhino-1.7.10.jar";
  static final String KT_PRELOADER =
      "third_party/kotlin/kotlin-compiler-1.3.72/kotlinc/lib/kotlin-preloader.jar";
  public static final String KT_COMPILER =
      "third_party/kotlin/kotlin-compiler-1.3.72/kotlinc/lib/kotlin-compiler.jar";
  public static final String K2JVMCompiler = "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler";
  public static final String KT_STDLIB =
      "third_party/kotlin/kotlin-compiler-1.3.72/kotlinc/lib/kotlin-stdlib.jar";
  public static final String KT_REFLECT =
      "third_party/kotlin/kotlin-compiler-1.3.72/kotlinc/lib/kotlin-reflect.jar";
  public static final String KT_SCRIPT_RT =
      "third_party/kotlin/kotlin-compiler-1.3.72/kotlinc/lib/kotlin-script-runtime.jar";
  private static final String ANDROID_JAR_PATTERN = "third_party/android_jar/lib-v%d/android.jar";
  private static final AndroidApiLevel DEFAULT_MIN_SDK = AndroidApiLevel.I;

  public static final String JDK_11_TESTS_DIR = "third_party/openjdk/jdk-11-test/";

  private static final String PROGUARD5_2_1 = "third_party/proguard/proguard5.2.1/bin/proguard";
  private static final String PROGUARD6_0_1 = "third_party/proguard/proguard6.0.1/bin/proguard";
  private static final String PROGUARD = PROGUARD5_2_1;
  public static final String JACOCO_AGENT = "third_party/jacoco/org.jacoco.agent-0.8.2-runtime.jar";
  public static final String PROGUARD_SETTINGS_FOR_INTERNAL_APPS = "third_party/proguardsettings/";

  private static final String RETRACE6_0_1 = "third_party/proguard/proguard6.0.1/bin/retrace";
  private static final String RETRACE = RETRACE6_0_1;
  public static final Path RETRACE_MAPS_DIR = Paths.get(THIRD_PARTY_DIR, "r8mappings");

  public static final long BOT_MAX_HEAP_SIZE = 7908360192L;

  public static final Path D8_JAR = Paths.get(LIBS_DIR, "d8.jar");
  public static final Path R8_JAR = Paths.get(LIBS_DIR, "r8.jar");
  public static final Path R8_WITH_DEPS_JAR = Paths.get(LIBS_DIR, "r8_with_deps.jar");
  public static final Path R8_WITH_RELOCATED_DEPS_JAR =
      Paths.get(LIBS_DIR, "r8_with_relocated_deps.jar");
  public static final Path R8_WITH_DEPS_11_JAR = Paths.get(LIBS_DIR, "r8_with_deps_11.jar");
  public static final Path R8_WITH_RELOCATED_DEPS_11_JAR =
      Paths.get(LIBS_DIR, "r8_with_relocated_deps_11.jar");
  public static final Path R8LIB_JAR = Paths.get(LIBS_DIR, "r8lib.jar");
  public static final Path R8LIB_MAP = Paths.get(LIBS_DIR, "r8lib.jar.map");
  public static final Path R8LIB_EXCLUDE_DEPS_JAR = Paths.get(LIBS_DIR, "r8lib-exclude-deps.jar");
  public static final Path R8LIB_EXCLUDE_DEPS_MAP =
      Paths.get(LIBS_DIR, "r8lib-exclude-deps.jar.map");
  public static final Path DEPS = Paths.get(LIBS_DIR, "deps_all.jar");

  public static final Path DESUGAR_LIB_CONVERSIONS =
      Paths.get(LIBS_DIR, "library_desugar_conversions.zip");
  public static final Path DESUGAR_LIB_JSON_FOR_TESTING =
      Paths.get("src/library_desugar/desugar_jdk_libs.json");

  public static boolean isLocalDevelopment() {
    return System.getProperty("local_development", "0").equals("1");
  }

  public static boolean shouldRunSlowTests() {
    return System.getProperty("slow_tests", "0").equals("1");
  }

  public static boolean verifyValidOutputMode(Backend backend, OutputMode outputMode) {
    return (backend == Backend.CF && outputMode == OutputMode.ClassFile) || backend == Backend.DEX;
  }

  public static StringConsumer consumeString(Consumer<String> consumer) {
    return new StringConsumer() {

      private StringBuilder builder;

      @Override
      public void accept(String string, DiagnosticsHandler handler) {
        if (builder == null) {
          builder = new StringBuilder();
        }
        builder.append(string);
      }

      @Override
      public void finished(DiagnosticsHandler handler) {
        if (builder != null) {
          consumer.accept(builder.toString());
        }
      }
    };
  }

  public enum DexVm {
    ART_4_0_4_TARGET(Version.V4_0_4, Kind.TARGET),
    ART_4_0_4_HOST(Version.V4_0_4, Kind.HOST),
    ART_4_4_4_TARGET(Version.V4_4_4, Kind.TARGET),
    ART_4_4_4_HOST(Version.V4_4_4, Kind.HOST),
    ART_5_1_1_TARGET(Version.V5_1_1, Kind.TARGET),
    ART_5_1_1_HOST(Version.V5_1_1, Kind.HOST),
    ART_6_0_1_TARGET(Version.V6_0_1, Kind.TARGET),
    ART_6_0_1_HOST(Version.V6_0_1, Kind.HOST),
    ART_7_0_0_TARGET(Version.V7_0_0, Kind.TARGET),
    ART_7_0_0_HOST(Version.V7_0_0, Kind.HOST),
    ART_8_1_0_TARGET(Version.V8_1_0, Kind.TARGET),
    ART_8_1_0_HOST(Version.V8_1_0, Kind.HOST),
    ART_DEFAULT(Version.DEFAULT, Kind.HOST),
    ART_9_0_0_TARGET(Version.V9_0_0, Kind.TARGET),
    ART_9_0_0_HOST(Version.V9_0_0, Kind.HOST),
    ART_10_0_0_TARGET(Version.V10_0_0, Kind.TARGET),
    ART_10_0_0_HOST(Version.V10_0_0, Kind.HOST);

    private static final ImmutableMap<String, DexVm> SHORT_NAME_MAP =
        Arrays.stream(DexVm.values()).collect(ImmutableMap.toImmutableMap(
            DexVm::toString, Function.identity()));

    public enum Version {
      V4_0_4("4.0.4"),
      V4_4_4("4.4.4"),
      V5_1_1("5.1.1"),
      V6_0_1("6.0.1"),
      V7_0_0("7.0.0"),
      V8_1_0("8.1.0"),
      DEFAULT("default"),
      V9_0_0("9.0.0"),
      V10_0_0("10.0.0");

      Version(String shortName) {
        this.shortName = shortName;
      }

      public boolean isDalvik() {
        return isOlderThanOrEqual(Version.V4_4_4);
      }

      public boolean isLatest() {
        return this == V10_0_0;
      }

      public boolean isNewerThan(Version other) {
        return compareTo(other) > 0;
      }

      public boolean isAtLeast(Version other) {
        return compareTo(other) >= 0;
      }

      public boolean isOlderThanOrEqual(Version other) {
        return compareTo(other) <= 0;
      }

      public String toString() {
        return shortName;
      }

      private String shortName;

      public static Version first() {
        return V4_0_4;
      }

      public static Version last() {
        return V10_0_0;
      }

      static {
        // Ensure first is always first and last is always last.
        assert Arrays.stream(values()).allMatch(v -> v == first() || v.compareTo(first()) > 0);
        assert Arrays.stream(values()).allMatch(v -> v == last() || v.compareTo(last()) < 0);
      }
    }

    public enum Kind {
      HOST("host"),
      TARGET("target");

      Kind(String shortName) {
        this.shortName = shortName;
      }

      public String toString() {
        return shortName;
      }

      private String shortName;
    }

    public String toString() {
      return version.shortName + '_' + kind.shortName;
    }

    public static DexVm fromShortName(String shortName) {
      return SHORT_NAME_MAP.get(shortName);
    }

    public static DexVm fromVersion(Version version) {
      return SHORT_NAME_MAP.get(version.shortName + "_" + Kind.HOST.toString());
    }

    public boolean isNewerThan(DexVm other) {
      return version.isNewerThan(other.version);
    }

    public boolean isOlderThanOrEqual(DexVm other) {
      return version.isOlderThanOrEqual(other.version);
    }

    DexVm(Version version, Kind kind) {
      this.version = version;
      this.kind = kind;
    }

    public Version getVersion() {
      return version;
    }

    public Kind getKind() {
      return kind;
    }

    private final Version version;
    private final Kind kind;
  }


  public abstract static class CommandBuilder {

    protected List<String> options = new ArrayList<>();
    protected Map<String, String> systemProperties = new LinkedHashMap<>();
    protected List<String> classpaths = new ArrayList<>();
    protected String mainClass;
    protected List<String> programArguments = new ArrayList<>();
    protected List<String> bootClassPaths = new ArrayList<>();
    protected String executionDirectory;

    public CommandBuilder appendArtOption(String option) {
      options.add(option);
      return this;
    }

    public CommandBuilder appendArtSystemProperty(String key, String value) {
      systemProperties.put(key, value);
      return this;
    }

    public CommandBuilder appendClasspath(String classpath) {
      classpaths.add(classpath);
      return this;
    }

    public CommandBuilder setMainClass(String className) {
      this.mainClass = className;
      return this;
    }

    public CommandBuilder appendProgramArgument(String option) {
      programArguments.add(option);
      return this;
    }

    public CommandBuilder appendBootClassPath(String lib) {
      bootClassPaths.add(lib);
      return this;
    }

    private List<String> command() {
      List<String> result = new ArrayList<>();
      // The art script _must_ be run with bash, bots default to /bin/dash for /bin/sh, so
      // explicitly set it;
      if (shouldUseDocker()) {
        result.add("tools/docker/run.sh");
      } else if (isLinux()) {
        result.add("/bin/bash");
      }
      result.add(getExecutable());
      for (String option : options) {
        result.add(option);
      }
      for (Map.Entry<String, String> entry : systemProperties.entrySet()) {
        StringBuilder builder = new StringBuilder("-D");
        builder.append(entry.getKey());
        builder.append("=");
        builder.append(entry.getValue());
        result.add(builder.toString());
      }
      if (!classpaths.isEmpty()) {
        result.add("-cp");
        result.add(String.join(":", classpaths));
      }
      if (!bootClassPaths.isEmpty()) {
        result.add("-Xbootclasspath:" + String.join(":", bootClassPaths));
      }
      if (mainClass != null) {
        result.add(mainClass);
      }
      for (String argument : programArguments) {
        result.add(argument);
      }
      return result;
    }

    public ProcessBuilder asProcessBuilder() {
      ProcessBuilder processBuilder = new ProcessBuilder(command());
      if (executionDirectory != null) {
        processBuilder.directory(new File(executionDirectory));
      }
      return processBuilder;
    }

    public String build() {
      return String.join(" ", command());
    }

    protected abstract boolean shouldUseDocker();

    protected abstract String getExecutable();
  }

  public static class ArtCommandBuilder extends CommandBuilder {

    private DexVm version;
    private boolean withArtFrameworks;

    public ArtCommandBuilder() {
      this.version = getDexVm();
    }

    public ArtCommandBuilder(DexVm version) {
      if (version.getKind() == Kind.HOST) {
        assert ART_BINARY_VERSIONS.containsKey(version);
      }
      this.version = version;
    }

    @Override
    protected boolean shouldUseDocker() {
      return isMac();
    }

    @Override
    protected String getExecutable() {
      if (withArtFrameworks && version.isNewerThan(DexVm.ART_4_4_4_HOST)) {
        // Run directly Art in its repository, which has been patched by gradle to match expected
        // path for the frameworks.
        executionDirectory = getArtDir(version);
        return getRawArtBinary(version);
      }
      return version != null ? getArtBinary(version) : getArtBinary();
    }

    public boolean isForDevice() {
      return version.getKind() == Kind.TARGET;
    }

    public ArtCommandBuilder addToJavaLibraryPath(File file) {
      Assume.assumeTrue("JNI tests are not yet supported on devices", !isForDevice());
      appendArtSystemProperty("java.library.path", file.getAbsolutePath());
      return this;
    }

    public DeviceRunner asDeviceRunner() {
      return new DeviceRunner()
          .setVmOptions(options)
          .setSystemProperties(systemProperties)
          .setClasspath(toFileList(classpaths))
          .setBootClasspath(toFileList(bootClassPaths))
          .setMainClass(mainClass)
          .setProgramArguments(programArguments);
    }
  }

  private static List<File> toFileList(List<String> filePathList) {
    return filePathList.stream().map(path -> new File(path)).collect(Collectors.toList());
  }

  public static class DXCommandBuilder extends CommandBuilder {

    public DXCommandBuilder() {
      appendProgramArgument("--dex");
    }

    @Override
    protected boolean shouldUseDocker() {
      return false;
    }

    @Override
    protected String getExecutable() {
      return DX.toAbsolutePath().toString();
    }
  }

  private static class StreamReader implements Runnable {

    private InputStream stream;
    private String result;

    public StreamReader(InputStream stream) {
      this.stream = stream;
    }

    public String getResult() {
      return result;
    }

    @Override
    public void run() {
      try {
        result = CharStreams.toString(new InputStreamReader(stream, StandardCharsets.UTF_8));
        stream.close();
      } catch (IOException e) {
        result = "Failed reading result for stream " + stream;
      }
    }
  }

  private static final String TOOLS = "tools";

  private static final Map<DexVm, String> ART_DIRS =
      ImmutableMap.<DexVm, String>builder()
          .put(DexVm.ART_DEFAULT, "art")
          .put(DexVm.ART_10_0_0_HOST, "art-10.0.0")
          .put(DexVm.ART_9_0_0_HOST, "art-9.0.0")
          .put(DexVm.ART_8_1_0_HOST, "art-8.1.0")
          .put(DexVm.ART_7_0_0_HOST, "art-7.0.0")
          .put(DexVm.ART_6_0_1_HOST, "art-6.0.1")
          .put(DexVm.ART_5_1_1_HOST, "art-5.1.1")
          .put(DexVm.ART_4_4_4_HOST, "dalvik")
          .put(DexVm.ART_4_0_4_HOST, "dalvik-4.0.4").build();
  private static final Map<DexVm, String> ART_BINARY_VERSIONS =
      ImmutableMap.<DexVm, String>builder()
          .put(DexVm.ART_DEFAULT, "bin/art")
          .put(DexVm.ART_10_0_0_HOST, "bin/art")
          .put(DexVm.ART_9_0_0_HOST, "bin/art")
          .put(DexVm.ART_8_1_0_HOST, "bin/art")
          .put(DexVm.ART_7_0_0_HOST, "bin/art")
          .put(DexVm.ART_6_0_1_HOST, "bin/art")
          .put(DexVm.ART_5_1_1_HOST, "bin/art")
          .put(DexVm.ART_4_4_4_HOST, "bin/dalvik")
          .put(DexVm.ART_4_0_4_HOST, "bin/dalvik").build();

  private static final Map<DexVm, String> ART_BINARY_VERSIONS_X64 =
      ImmutableMap.<DexVm, String>builder()
          .put(DexVm.ART_DEFAULT, "bin/art")
          .put(DexVm.ART_10_0_0_HOST, "bin/art")
          .put(DexVm.ART_9_0_0_HOST, "bin/art")
          .put(DexVm.ART_8_1_0_HOST, "bin/art")
          .put(DexVm.ART_7_0_0_HOST, "bin/art")
          .put(DexVm.ART_6_0_1_HOST, "bin/art").build();

  private static final List<String> DALVIK_BOOT_LIBS =
      ImmutableList.of(
          "core-libart-hostdex.jar",
          "core-hostdex.jar",
          "apache-xml-hostdex.jar");

  private static final List<String> ART_BOOT_LIBS =
      ImmutableList.of(
          "core-libart-hostdex.jar",
          "core-oj-hostdex.jar",
          "apache-xml-hostdex.jar");

  private static final Map<DexVm, List<String>> BOOT_LIBS;

  static {
    ImmutableMap.Builder<DexVm, List<String>> builder = ImmutableMap.builder();
    builder
        .put(DexVm.ART_DEFAULT, ART_BOOT_LIBS)
        .put(DexVm.ART_10_0_0_HOST, ART_BOOT_LIBS)
        .put(DexVm.ART_9_0_0_HOST, ART_BOOT_LIBS)
        .put(DexVm.ART_8_1_0_HOST, ART_BOOT_LIBS)
        .put(DexVm.ART_7_0_0_HOST, ART_BOOT_LIBS)
        .put(DexVm.ART_6_0_1_HOST, ART_BOOT_LIBS)
        .put(DexVm.ART_5_1_1_HOST, ART_BOOT_LIBS)
        .put(DexVm.ART_4_4_4_HOST, DALVIK_BOOT_LIBS)
        .put(DexVm.ART_4_0_4_HOST, DALVIK_BOOT_LIBS);
    BOOT_LIBS = builder.build();
  }

  private static final Map<DexVm, String> PRODUCT;

  static {
    ImmutableMap.Builder<DexVm, String> builder = ImmutableMap.builder();
    builder
        .put(DexVm.ART_DEFAULT, "angler")
        .put(DexVm.ART_10_0_0_HOST, "coral")
        .put(DexVm.ART_9_0_0_HOST, "marlin")
        .put(DexVm.ART_8_1_0_HOST, "marlin")
        .put(DexVm.ART_7_0_0_HOST, "angler")
        .put(DexVm.ART_6_0_1_HOST, "angler")
        .put(DexVm.ART_5_1_1_HOST, "mako")
        .put(DexVm.ART_4_4_4_HOST, "<missing>")
        .put(DexVm.ART_4_0_4_HOST, "<missing>");
    PRODUCT = builder.build();
  }

  private static final Path DX = getDxExecutablePath();

  private static Path getDexVmPath(DexVm vm) {
    return Paths.get(
        TOOLS,
        "linux",
        vm.getVersion() == DexVm.Version.DEFAULT
            ? "art"
            : "art-" + vm.getVersion());
  }

  private static Path getDexVmLibPath(DexVm vm) {
    return getDexVmPath(vm).resolve("lib");
  }

  private static Path getDex2OatPath(DexVm vm) {
    return getDexVmPath(vm).resolve("bin").resolve("dex2oat");
  }

  private static Path getProductPath(DexVm vm) {
    return getDexVmPath(vm).resolve("product").resolve(PRODUCT.get(vm));
  }

  private static String getArchString(DexVm vm) {
    return vm.isOlderThanOrEqual(DexVm.ART_5_1_1_HOST) ? "arm" : "arm64";
  }

  private static Path getProductBootImagePath(DexVm vm) {
    return getProductPath(vm).resolve("system").resolve("framework").resolve("boot.art");
  }

  public static byte[] getClassAsBytes(Class clazz) throws IOException {
    return Files.readAllBytes(getClassFileForTestClass(clazz));
  }

  public static long getClassByteCrc(Class clazz) {
    byte[] bytes = null;
    try {
      bytes = getClassAsBytes(clazz);
    } catch (IOException ioe) {
      Assert.fail(ioe.toString());
    }
    CRC32 crc = new CRC32();
    crc.update(bytes, 0, bytes.length);
    return crc.getValue();
  }

  public static String getArtDir(DexVm version) {
    String dir = ART_DIRS.get(version);
    if (dir == null) {
      throw new IllegalStateException("Does not support dex vm: " + version);
    }
    if (isLinux() || isMac()) {
      // The Linux version is used on Mac, where it is run in a Docker container.
      return TOOLS + "/linux/" + dir;
    }
    fail("Unsupported platform, we currently only support mac and linux: " + getPlatform());
    return ""; //never here
  }

  public static String toolsDir() {
    String osName = System.getProperty("os.name");
    if (osName.equals("Mac OS X")) {
      return "mac";
    } else if (osName.contains("Windows")) {
      return "windows";
    } else {
      return "linux";
    }
  }

  public static String getProguardScript() {
    if (isWindows()) {
      return PROGUARD + ".bat";
    }
    return PROGUARD + ".sh";
  }

  public static String getProguard6Script() {
    if (isWindows()) {
      return PROGUARD6_0_1 + ".bat";
    }
    return PROGUARD6_0_1 + ".sh";
  }

  public static Backend[] getBackends() {
    if (getDexVm() == DexVm.ART_DEFAULT) {
      return Backend.values();
    }
    return new Backend[]{Backend.DEX};
  }

  private static String getRetraceScript() {
    if (isWindows()) {
      return RETRACE + ".bat";
    }
    return RETRACE + ".sh";
  }

  private static Path getDxExecutablePath() {
    String toolsDir = toolsDir();
    String executableName = toolsDir.equals("windows") ? "dx.bat" : "dx";
    return Paths.get(TOOLS, toolsDir(), "dx", "bin", executableName);
  }

  public static String getArtBinary(DexVm version) {
    return getArtDir(version) + "/" + getRawArtBinary(version);
  }

  public static String getRawArtBinary(DexVm version) {
    String binary = ART_BINARY_VERSIONS.get(version);
    if (binary == null) {
      throw new IllegalStateException("Does not support running with dex vm: " + version);
    }
    return binary;
  }

  public static Path getJava8RuntimeJar() {
    return Paths.get(JAVA_8_RUNTIME);
  }

  public static Path getDesugarJDKLibs() {
    return Paths.get(DESUGAR_JDK_LIBS);
  }

  public static Path getCoreLambdaStubs() {
    return Paths.get(CORE_LAMBDA_STUBS);
  }

  @Deprecated
  // Use getFirstSupportedAndroidJar(AndroidApiLevel) to specify a specific Android jar.
  public static Path getDefaultAndroidJar() {
    return getAndroidJar(AndroidApiLevel.getDefault());
  }

  public static Path getFirstSupportedAndroidJar(AndroidApiLevel apiLevel) {
    // Fast path.
    if (hasAndroidJar(apiLevel)) {
      return getAndroidJar(apiLevel.getLevel());
    }
    // Search for an android jar.
    for (AndroidApiLevel level : AndroidApiLevel.getAndroidApiLevelsSorted()) {
      if (level.getLevel() >= apiLevel.getLevel() && hasAndroidJar(level)) {
        return getAndroidJar(level.getLevel());
      }
    }
    return getAndroidJar(AndroidApiLevel.LATEST);
  }

  public static Path getAndroidJar(int apiLevel) {
    return getAndroidJar(AndroidApiLevel.getAndroidApiLevel(apiLevel));
  }

  private static Path getAndroidJarPath(AndroidApiLevel apiLevel) {
    String jar = String.format(
        ANDROID_JAR_PATTERN,
        (apiLevel == AndroidApiLevel.getDefault() ? DEFAULT_MIN_SDK : apiLevel).getLevel());
    return Paths.get(jar);
  }

  public static boolean hasAndroidJar(AndroidApiLevel apiLevel) {
    Path path = getAndroidJarPath(apiLevel);
    return Files.exists(path);
  }

  public static Path getAndroidJar(AndroidApiLevel apiLevel) {
    Path path = getAndroidJarPath(apiLevel);
    assert Files.exists(path)
        : "Expected android jar to exist for API level " + apiLevel;
    return path;
  }

  public static Path getMostRecentAndroidJar() {
    List<AndroidApiLevel> apiLevels = AndroidApiLevel.getAndroidApiLevelsSorted();
    ListIterator<AndroidApiLevel> iterator = apiLevels.listIterator(apiLevels.size());
    while (iterator.hasPrevious()) {
      AndroidApiLevel apiLevel = iterator.previous();
      if (hasAndroidJar(apiLevel)) {
        return getAndroidJar(apiLevel);
      }
    }
    throw new Unreachable("Unable to find a most recent android.jar");
  }

  public static Path getKotlinStdlibJar() {
    Path path = Paths.get(KT_STDLIB);
    assert Files.exists(path) : "Expected kotlin stdlib jar";
    return path;
  }

  public static Path getKotlinReflectJar() {
    Path path = Paths.get(KT_REFLECT);
    assert Files.exists(path) : "Expected kotlin reflect jar";
    return path;
  }

  public static Path getJdwpTestsCfJarPath(AndroidApiLevel minSdk) {
    if (minSdk.getLevel() >= AndroidApiLevel.N.getLevel()) {
      return Paths.get("third_party", "jdwp-tests", "apache-harmony-jdwp-tests-host.jar");
    } else {
      return Paths.get(ToolHelper.BUILD_DIR, "libs", "jdwp-tests-preN.jar");
    }
  }

  public static Path getJdwpTestsDexJarPath(AndroidApiLevel minSdk) {
    if (minSdk.getLevel() >= AndroidApiLevel.N.getLevel()) {
      return Paths.get("third_party", "jdwp-tests", "apache-harmony-jdwp-tests-hostdex.jar");
    } else {
      return Paths.get(ToolHelper.BUILD_DIR, "libs", "jdwp-tests-preN-dex.jar");
    }
  }

  /**
   * Get the junit jar bundled with the framework.
   */
  public static Path getFrameworkJunitJarPath(DexVm version) {
    return Paths.get(getArtDir(version), "framework", "junit.jar");
  }

  static class RetainedTemporaryFolder extends TemporaryFolder {

    RetainedTemporaryFolder(java.io.File parentFolder) {
      super(parentFolder);
    }

    protected void after() {
    } // instead of remove, do nothing
  }

  // For non-Linux platforms create the temporary directory in the repository root to simplify
  // running Art in a docker container
  public static TemporaryFolder getTemporaryFolderForTest() {
    String tmpDir = System.getProperty("test_dir");
    if (tmpDir == null) {
      return new TemporaryFolder(ToolHelper.isLinux() ? null : Paths.get("build", "tmp").toFile());
    } else {
      return new RetainedTemporaryFolder(new java.io.File(tmpDir));
    }
  }

  public static String getArtBinary() {
    return getArtBinary(getDexVm());
  }

  public static Set<DexVm> getArtVersions() {
    String artVersion = System.getProperty("dex_vm");
    if (artVersion != null) {
      DexVm artVersionEnum = getDexVm();
      if (artVersionEnum.getKind() == Kind.HOST
          && !ART_BINARY_VERSIONS.containsKey(artVersionEnum)) {
        throw new RuntimeException("Unsupported Art version " + artVersion);
      }
      return ImmutableSet.of(artVersionEnum);
    } else {
      if (isWindows()) {
        return Collections.emptySet();
      } else if (isLinux()) {
        return ART_BINARY_VERSIONS.keySet();
      } else {
        return ART_BINARY_VERSIONS_X64.keySet();
      }
    }
  }

  public static List<String> getBootLibs(DexVm dexVm) {
    String prefix = getArtDir(dexVm) + "/";
    List<String> result = new ArrayList<>();
    BOOT_LIBS.get(dexVm).stream().forEach(x -> result.add(prefix + "framework/" + x));
    return result;
  }

  // Returns if the passed in vm to use is the default.
  public static boolean isDefaultDexVm(DexVm dexVm) {
    return dexVm == DexVm.ART_DEFAULT;
  }

  @Deprecated
  public static DexVm getDexVm() {
    String artVersion = System.getProperty("dex_vm");
    if (artVersion == null) {
      return DexVm.ART_DEFAULT;
    } else {
      DexVm artVersionEnum = DexVm.fromShortName(artVersion);
      if (artVersionEnum == null
          && !artVersion.endsWith(Kind.HOST.toString())
          && !artVersion.endsWith(Kind.TARGET.toString())) {
        // Default to host Art/Dalvik when not specified.
        artVersionEnum = DexVm.fromShortName(artVersion + '_' + Kind.HOST.toString());
      }
      if (artVersionEnum == null) {
        throw new RuntimeException("Unsupported Art version " + artVersion);
      } else {
        return artVersionEnum;
      }
    }
  }

  public static AndroidApiLevel getMinApiLevelForDexVmNoHigherThan(AndroidApiLevel threshold) {
    AndroidApiLevel minApiLevelForDexVm = getMinApiLevelForDexVm();
    return minApiLevelForDexVm.getLevel() < threshold.getLevel() ? minApiLevelForDexVm : threshold;
  }

  public static AndroidApiLevel getMinApiLevelForDexVm() {
    return getMinApiLevelForDexVm(ToolHelper.getDexVm());
  }

  public static AndroidApiLevel getMinApiLevelForDexVm(DexVm dexVm) {
    switch (dexVm.version) {
      case DEFAULT:
        return AndroidApiLevel.O;
      case V10_0_0:
        return AndroidApiLevel.Q;
      case V9_0_0:
        return AndroidApiLevel.P;
      case V8_1_0:
        return AndroidApiLevel.O_MR1;
      case V7_0_0:
        return AndroidApiLevel.N;
      case V6_0_1:
        return AndroidApiLevel.M;
      case V5_1_1:
        return AndroidApiLevel.L_MR1;
      case V4_4_4:
        return AndroidApiLevel.K;
      case V4_0_4:
        return AndroidApiLevel.I_MR1;
      default:
        throw new Unreachable("Missing min api level for dex vm " + dexVm);
    }
  }

  private static String getPlatform() {
    return System.getProperty("os.name");
  }

  public static boolean isLinux() {
    return getPlatform().startsWith("Linux");
  }

  public static boolean isMac() {
    return getPlatform().startsWith("Mac");
  }

  public static boolean isWindows() {
    return getPlatform().startsWith("Windows");
  }

  public static boolean isJava8Runtime() {
    return System.getProperty("java.specification.version").equals("8");
  }

  public static boolean isJava9Runtime() {
    return System.getProperty("java.specification.version").equals("9");
  }

  public static boolean isTestingR8Lib() {
    return System.getProperty("java.class.path").contains("r8lib.jar");
  }

  public static boolean artSupported() {
    if (!isLinux() && !isMac() && !isWindows()) {
      System.err.println("Testing on your platform is not fully supported. " +
          "Art does not work on on your platform.");
      return false;
    }
    if (isWindows() && getDexVm().getKind() == Kind.HOST) {
      System.err.println("Testing on host is not supported on Windows.");
      return false;
    }
    return true;
  }

  public static boolean isDex2OatSupported() {
    return !isWindows();
  }

  public static Path getClassPathForTests() {
    return Paths.get(BUILD_DIR, "classes", "java", "test");
  }

  private static List<String> getNamePartsForTestPackage(Package pkg) {
    return Lists.newArrayList(pkg.getName().split("\\."));
  }

  public static Path getPackageDirectoryForTestPackage(Package pkg) {
    List<String> parts = getNamePartsForTestPackage(pkg);
    return getClassPathForTests().resolve(Paths.get("", parts.toArray(StringUtils.EMPTY_ARRAY)));
  }

  public static String getJarEntryForTestPackage(Package pkg) {
    List<String> parts = getNamePartsForTestPackage(pkg);
    return String.join("/", parts);
  }

  private static List<String> getNamePartsForTestClass(Class clazz) {
    List<String> parts = Lists.newArrayList(clazz.getCanonicalName().split("\\."));
    Class enclosing = clazz;
    while (enclosing.getEnclosingClass() != null) {
      parts.set(parts.size() - 2, parts.get(parts.size() - 2) + "$" + parts.get(parts.size() - 1));
      parts.remove(parts.size() - 1);
      enclosing = enclosing.getEnclosingClass();
    }
    parts.set(parts.size() - 1, parts.get(parts.size() - 1) + ".class");
    return parts;
  }

  public static List<Path> getClassFilesForTestPackage(Package pkg) throws IOException {
    return getClassFilesForTestDirectory(ToolHelper.getPackageDirectoryForTestPackage(pkg));
  }

  public static List<Path> getClassFilesForTestDirectory(Path directory) throws IOException {
    return getClassFilesForTestDirectory(directory, null);
  }

  public static List<Path> getClassFilesForTestDirectory(
      Path directory, Predicate<Path> filter) throws IOException {
    return Files.walk(directory)
        .filter(path -> path.toString().endsWith(".class") && (filter == null || filter.test(path)))
        .collect(Collectors.toList());
  }

  public static Path getClassFileForTestClass(Class clazz) {
    List<String> parts = getNamePartsForTestClass(clazz);
    return getClassPathForTests().resolve(Paths.get("", parts.toArray(StringUtils.EMPTY_ARRAY)));
  }

  public static Collection<Path> getClassFilesForInnerClasses(Path path) throws IOException {
    Set<Path> paths = new HashSet<>();
    String prefix = path.toString().replace(CLASS_EXTENSION, "$");
    paths.addAll(
        ToolHelper.getClassFilesForTestDirectory(
            path.getParent(), p -> p.toString().startsWith(prefix)));
    return paths;
  }

  public static Collection<Path> getClassFilesForInnerClasses(Collection<Class<?>> classes)
      throws IOException {
    Set<Path> paths = new HashSet<>();
    for (Class clazz : classes) {
      Path path = ToolHelper.getClassFileForTestClass(clazz);
      String prefix = path.toString().replace(CLASS_EXTENSION, "$");
      paths.addAll(
          ToolHelper.getClassFilesForTestDirectory(
              path.getParent(), p -> p.toString().startsWith(prefix)));
    }
    return paths;
  }

  public static Collection<Path> getClassFilesForInnerClasses(Class<?>... classes)
      throws IOException {
    return getClassFilesForInnerClasses(Arrays.asList(classes));
  }

  public static Path getFileNameForTestClass(Class clazz) {
    List<String> parts = getNamePartsForTestClass(clazz);
    return Paths.get("", parts.toArray(StringUtils.EMPTY_ARRAY));
  }

  public static String getJarEntryForTestClass(Class clazz) {
    List<String> parts = getNamePartsForTestClass(clazz);
    return String.join("/", parts);
  }

  public static DirectMappedDexApplication buildApplication(List<String> fileNames)
      throws IOException, ExecutionException {
    return buildApplicationWithAndroidJar(fileNames, getDefaultAndroidJar());
  }

  public static DirectMappedDexApplication buildApplicationWithAndroidJar(
      List<String> fileNames, Path androidJar) throws IOException, ExecutionException {
    AndroidApp input =
        AndroidApp.builder()
            .addProgramFiles(ListUtils.map(fileNames, Paths::get))
            .addLibraryFiles(androidJar)
            .build();
    return new ApplicationReader(input, new InternalOptions(), Timing.empty()).read().toDirect();
  }

  public static ProguardConfiguration loadProguardConfiguration(
      DexItemFactory factory, List<Path> configPaths) {
    Reporter reporter = new Reporter();
    if (configPaths.isEmpty()) {
      return ProguardConfiguration.builder(factory, reporter)
          .disableShrinking()
          .disableObfuscation()
          .disableOptimization()
          .addKeepAttributePatterns(ImmutableList.of("*"))
          .build();
    }
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(factory, reporter);
    for (Path configPath : configPaths) {
      parser.parse(configPath);
    }
    return parser.getConfig();
  }

  public static D8Command.Builder prepareD8CommandBuilder(AndroidApp app) {
    return D8Command.builder(app);
  }

  public static R8Command.Builder prepareR8CommandBuilder(AndroidApp app) {
    return prepareR8CommandBuilder(app, DexIndexedConsumer.emptyConsumer());
  }

  public static R8Command.Builder prepareR8CommandBuilder(
      AndroidApp app, ProgramConsumer programConsumer) {
    return R8Command.builder(app)
        .setProgramConsumer(programConsumer)
        .setProguardMapConsumer(StringConsumer.emptyConsumer());
  }

  public static R8Command.Builder prepareR8CommandBuilder(
      AndroidApp app, ProgramConsumer programConsumer, DiagnosticsHandler diagnosticsHandler) {
    return R8Command.builder(app, diagnosticsHandler)
        .setProgramConsumer(programConsumer)
        .setProguardMapConsumer(StringConsumer.emptyConsumer());
  }

  public static AndroidApp runR8(AndroidApp app) throws CompilationFailedException {
    return runR8WithProgramConsumer(app, DexIndexedConsumer.emptyConsumer());
  }

  public static AndroidApp runR8WithProgramConsumer(AndroidApp app, ProgramConsumer programConsumer)
      throws CompilationFailedException {
    return runR8(prepareR8CommandBuilder(app, programConsumer).build());
  }

  public static AndroidApp runR8(AndroidApp app, Consumer<InternalOptions> optionsConsumer)
      throws CompilationFailedException {
    R8Command command = prepareR8CommandBuilder(app)
        .setDisableTreeShaking(true)
        .setDisableMinification(true)
        .build();
    return runR8(command, optionsConsumer);
  }

  public static AndroidApp runR8(R8Command command) throws CompilationFailedException {
    return runR8(command, null);
  }

  public static AndroidApp runR8(R8Command command, Consumer<InternalOptions> optionsConsumer)
      throws CompilationFailedException {
    return runR8WithFullResult(command, optionsConsumer);
  }

  public static void runR8WithoutResult(
      R8Command command, Consumer<InternalOptions> optionsConsumer)
      throws CompilationFailedException {
    InternalOptions internalOptions = command.getInternalOptions();
    optionsConsumer.accept(internalOptions);
    R8.runForTesting(command.getInputApp(), internalOptions);
  }

  public static AndroidApp runR8WithFullResult(
      R8Command command, Consumer<InternalOptions> optionsConsumer)
      throws CompilationFailedException {
    // TODO(zerny): Should we really be adding the android library in ToolHelper?
    AndroidApp app = command.getInputApp();
    if (app.getLibraryResourceProviders().isEmpty()) {
      // Add the android library matching the minsdk. We filter out junit and testing classes
      // from the android jar to avoid duplicate classes in art and jctf tests.
      AndroidApp.Builder builder = AndroidApp.builder(app);
      addFilteredAndroidJar(builder, AndroidApiLevel.getAndroidApiLevel(command.getMinApiLevel()));
      app = builder.build();
    }
    InternalOptions options = command.getInternalOptions();
    if (optionsConsumer != null) {
      optionsConsumer.accept(options);
    }
    AndroidAppConsumers compatSink = new AndroidAppConsumers(options);
    R8.runForTesting(app, options);
    return compatSink.build();
  }

  public static void runL8(L8Command command) throws CompilationFailedException {
    runL8(command, options -> {});
  }

  public static void runL8(L8Command command, Consumer<InternalOptions> optionsModifier)
      throws CompilationFailedException {
    InternalOptions internalOptions = command.getInternalOptions();
    optionsModifier.accept(internalOptions);
    L8.runForTesting(
        command.getInputApp(),
        internalOptions,
        command.isShrinking(),
        command.getD8Command(),
        command.getR8Command());
  }

  public static void addFilteredAndroidJar(BaseCommand.Builder builder, AndroidApiLevel apiLevel) {
    addFilteredAndroidJar(getAppBuilder(builder), apiLevel);
  }

  public static void addFilteredAndroidJar(AndroidApp.Builder builder, AndroidApiLevel apiLevel) {
    builder.addFilteredLibraryArchives(
        Collections.singletonList(
            new FilteredClassPath(
                getAndroidJar(apiLevel),
                ImmutableList.of("!junit/**", "!android/test/**"),
                Origin.unknown(),
                Position.UNKNOWN)));
  }

  public static AndroidApp runD8(AndroidApp app) throws CompilationFailedException {
    return runD8(app, null);
  }

  public static AndroidApp runD8(AndroidApp app, Consumer<InternalOptions> optionsConsumer)
      throws CompilationFailedException {
    return runD8(D8Command.builder(app), optionsConsumer);
  }

  public static AndroidApp runD8(D8Command.Builder builder) throws CompilationFailedException {
    return runD8(builder, null);
  }

  public static AndroidApp runD8(
      D8Command.Builder builder, Consumer<InternalOptions> optionsConsumer)
      throws CompilationFailedException {
    AndroidAppConsumers compatSink = new AndroidAppConsumers(builder);
    D8Command command = builder.build();
    InternalOptions options = command.getInternalOptions();
    if (optionsConsumer != null) {
      optionsConsumer.accept(options);
    }
    D8.runForTesting(command.getInputApp(), options);
    return compatSink.build();
  }

  public static void runD8WithoutResult(
      D8Command command, Consumer<InternalOptions> optionsConsumer)
      throws CompilationFailedException {
    InternalOptions internalOptions = command.getInternalOptions();
    optionsConsumer.accept(internalOptions);
    D8.runForTesting(command.getInputApp(), internalOptions);
  }

  public static AndroidApp runDexer(String fileName, String outDir, String... extraArgs)
      throws IOException {
    List<String> args = new ArrayList<>();
    Collections.addAll(args, extraArgs);
    Collections.addAll(args, "--output=" + outDir + "/classes.dex", fileName);
    int result = runDX(args.toArray(StringUtils.EMPTY_ARRAY)).exitCode;
    return result != 0 ? null : builderFromProgramDirectory(Paths.get(outDir)).build();
  }

  public static ProcessResult runDX(String... args) throws IOException {
    return runDX(null, args);
  }

  public static ProcessResult runDX(Path workingDirectory, String... args) throws IOException {
    return runProcess(createProcessBuilderForRunningDx(workingDirectory, args));
  }

  public static ProcessBuilder createProcessBuilderForRunningDx(String... args) {
    return createProcessBuilderForRunningDx(null, args);
  }

  public static ProcessBuilder createProcessBuilderForRunningDx(
      Path workingDirectory, String... args) {
    Assume.assumeTrue(ToolHelper.artSupported());
    DXCommandBuilder builder = new DXCommandBuilder();
    for (String arg : args) {
      builder.appendProgramArgument(arg);
    }
    ProcessBuilder pb = builder.asProcessBuilder();
    if (workingDirectory != null) {
      pb.directory(workingDirectory.toFile());
    }
    return pb;
  }

  public static ProcessResult runJava(Class clazz) throws Exception {
    String main = clazz.getTypeName();
    Path path = getClassPathForTests();
    return runJava(path, main);
  }

  public static ProcessResult runJava(Path classpath, String... args) throws IOException {
    return runJava(ImmutableList.of(classpath), args);
  }

  public static ProcessResult runJava(List<Path> classpath, String... args) throws IOException {
    return runJava(ImmutableList.of(), classpath, args);
  }

  public static ProcessResult runJava(List<String> vmArgs, List<Path> classpath, String... args)
      throws IOException {
    return runJava(TestRuntime.getSystemRuntime().asCf(), vmArgs, classpath, args);
  }

  public static ProcessResult runJava(CfRuntime runtime, List<Path> classpath, String... args)
      throws IOException {
    return runJava(runtime, ImmutableList.of(), classpath, args);
  }

  @Deprecated
  public static ProcessResult runKotlinc(
      List<Path> classPaths,
      Path directoryToCompileInto,
      List<String> extraOptions,
      Path... filesToCompile)
      throws IOException {
    List<String> cmdline = new ArrayList<>(Arrays.asList(getJavaExecutable()));
    cmdline.add("-jar");
    cmdline.add(KT_PRELOADER);
    cmdline.add("org.jetbrains.kotlin.preloading.Preloader");
    cmdline.add("-cp");
    cmdline.add(KT_COMPILER);
    cmdline.add(K2JVMCompiler);
    String[] strings = Arrays.stream(filesToCompile).map(Path::toString).toArray(String[]::new);
    Collections.addAll(cmdline, strings);
    cmdline.add("-d");
    cmdline.add(directoryToCompileInto.toString());
    List<String> cp = classPaths == null ? null
        : classPaths.stream().map(Path::toString).collect(Collectors.toList());
    if (cp != null) {
      cmdline.add("-cp");
      if (isWindows()) {
        cmdline.add(String.join(";", cp));
      } else {
        cmdline.add(String.join(":", cp));
      }
    }
    if (extraOptions != null) {
      cmdline.addAll(extraOptions);
    }
    ProcessBuilder builder = new ProcessBuilder(cmdline);
    return ToolHelper.runProcess(builder);
  }

  public static ProcessResult runJava(
      CfRuntime runtime, List<String> vmArgs, List<Path> classpath, String... args)
      throws IOException {
    String cp =
        classpath.stream().map(Path::toString).collect(Collectors.joining(CLASSPATH_SEPARATOR));
    List<String> cmdline = new ArrayList<>(Arrays.asList(runtime.getJavaExecutable().toString()));
    cmdline.addAll(vmArgs);
    cmdline.add("-cp");
    cmdline.add(cp);
    cmdline.addAll(Arrays.asList(args));
    ProcessBuilder builder = new ProcessBuilder(cmdline);
    return runProcess(builder);
  }

  public static ProcessResult runJavaNoVerify(
      Path classpath, String mainClass, String... args) throws IOException {
    return runJavaNoVerify(
        Collections.singletonList(classpath), mainClass, Lists.newArrayList(args));
  }

  public static ProcessResult runJavaNoVerify(
      List<Path> classpath, String mainClass, String... args) throws IOException {
    return runJavaNoVerify(classpath, mainClass, Lists.newArrayList(args));
  }

  public static ProcessResult runJavaNoVerify(
      List<Path> classpath, String mainClass, List<String> args) throws IOException {
    String cp =
        classpath.stream().map(Path::toString).collect(Collectors.joining(CLASSPATH_SEPARATOR));
    ArrayList<String> cmdline = Lists.newArrayList(
        getJavaExecutable(), "-cp", cp, "-noverify", mainClass);
    cmdline.addAll(args);
    ProcessBuilder builder = new ProcessBuilder(cmdline);
    return runProcess(builder);
  }

  public static ProcessResult forkD8(Path dir, String... args)
      throws IOException, InterruptedException {
    return forkJava(dir, D8.class, args);
  }

  public static ProcessResult forkR8(Path dir, String... args)
      throws IOException, InterruptedException {
    return forkJava(dir, R8.class, args);
  }

  public static ProcessResult forkR8WithJavaOptions(
      Path dir, List<String> javaOptions, String... args) throws IOException {
    String r8Jar = R8_JAR.toAbsolutePath().toString();
    return forkJavaWithJarAndJavaOptions(dir, r8Jar, Arrays.asList(args), javaOptions);
  }

  public static ProcessResult forkR8Jar(Path dir, String... args)
      throws IOException, InterruptedException {
    String r8Jar = R8_JAR.toAbsolutePath().toString();
    return forkJavaWithJar(dir, r8Jar, Arrays.asList(args));
  }

  public static ProcessResult forkGenerateMainDexList(Path dir, List<String> args1, String... args2)
      throws IOException, InterruptedException {
    List<String> args = new ArrayList<>();
    args.addAll(args1);
    args.addAll(Arrays.asList(args2));
    return forkJava(dir, GenerateMainDexList.class, args);
  }

  public static ProcessResult forkGenerateMainDexList(Path dir, String... args)
      throws IOException, InterruptedException {
    return forkJava(dir, GenerateMainDexList.class, args);
  }

  private static ProcessResult forkJava(Path dir, Class clazz, String... args)
      throws IOException, InterruptedException {
    return forkJava(dir, clazz, Arrays.asList(args));
  }

  private static ProcessResult forkJavaWithJar(Path dir, String jarPath, List<String> args)
      throws IOException {
    return forkJavaWithJarAndJavaOptions(dir, jarPath, args, ImmutableList.of());
  }

  private static ProcessResult forkJavaWithJarAndJavaOptions(
      Path dir, String jarPath, List<String> args, List<String> javaOptions) throws IOException {
    List<String> command =
        new ImmutableList.Builder<String>()
            .add(getJavaExecutable())
            .addAll(javaOptions)
            .add("-jar")
            .add(jarPath)
            .addAll(args)
            .build();
    return runProcess(new ProcessBuilder(command).directory(dir.toFile()));
  }


  private static ProcessResult forkJava(Path dir, Class clazz, List<String> args)
      throws IOException {
    List<String> command = new ImmutableList.Builder<String>()
        .add(getJavaExecutable())
        .add("-cp").add(System.getProperty("java.class.path"))
        .add(clazz.getCanonicalName())
        .addAll(args)
        .build();
    return runProcess(new ProcessBuilder(command).directory(dir.toFile()));
  }

  @Deprecated
  // Use CfRuntime.getJavaExecutable() for a specific JDK or getSystemJavaExecutable
  public static String getJavaExecutable() {
    return getSystemJavaExecutable();
  }

  public static String getSystemJavaExecutable() {
    return TestRuntime.getSystemRuntime().asCf().getJavaExecutable().toString();
  }

  public static ProcessResult runArtRaw(ArtCommandBuilder builder) throws IOException {
    return runArtProcessRaw(builder);
  }

  public static ProcessResult runArtRaw(String file, String mainClass)
      throws IOException {
    return runArtRaw(Collections.singletonList(file), mainClass, null);
  }

  public static ProcessResult runArtRaw(
      String file, String mainClass, Consumer<ArtCommandBuilder> extras) throws IOException {
    return runArtRaw(Collections.singletonList(file), mainClass, extras);
  }

  public static ProcessResult runArtRaw(List<String> files, String mainClass,
      Consumer<ArtCommandBuilder> extras)
      throws IOException {
    return runArtRaw(files, mainClass, extras, null, false);
  }

  // Index used to name directory aimed at storing dex files and process result
  // for one invokation of runArtRaw() in order to avoid conflicts in case of
  // multiple calls within the same test.
  private static int testOutputPathIndex = 0;

  public static ProcessResult runArtRaw(
      List<String> files,
      String mainClass,
      Consumer<ArtCommandBuilder> extras,
      DexVm version,
      boolean withArtFrameworks,
      String... args)
      throws IOException {
    ArtCommandBuilder builder =
        version != null ? new ArtCommandBuilder(version) : new ArtCommandBuilder();
    builder.withArtFrameworks = withArtFrameworks;
    files.forEach(builder::appendClasspath);
    builder.setMainClass(mainClass);
    if (extras != null) {
      extras.accept(builder);
    }
    for (String arg : args) {
      builder.appendProgramArgument(arg);
    }
    ProcessResult processResult = null;

    // Whenever we start a new test method we reset the index count.
    String reset_output_index = System.getProperty("reset_output_index");
    if (reset_output_index != null) {
      System.clearProperty("reset_output_index");
      testOutputPathIndex = 0;
    } else {
      assert testOutputPathIndex >= 0;
      testOutputPathIndex++;
    }

    String goldenFilesDirInProp = System.getProperty("use_golden_files_in");
    if (goldenFilesDirInProp != null) {
      File goldenFileDir = new File(goldenFilesDirInProp);
      assert goldenFileDir.isDirectory();
      processResult =
          compareAgainstGoldenFiles(
              files.stream().map(f -> new File(f)).collect(Collectors.toList()), goldenFileDir);
      if (processResult.exitCode == 0) {
        processResult = readProcessResult(goldenFileDir);
      }
    } else {
      processResult = runArtProcessRaw(builder);
    }

    String goldenFilesDirToProp = System.getProperty("generate_golden_files_to");
    if (goldenFilesDirToProp != null) {
      File goldenFileDir = new File(goldenFilesDirToProp);
      assert goldenFileDir.isDirectory();
      storeAsGoldenFiles(
          files.stream().map(f -> new File(f)).collect(Collectors.toList()), goldenFileDir);
      storeProcessResult(processResult, goldenFileDir);
    }

    return processResult;
  }

  private static Path findNonConflictingDestinationFilePath(Path testOutputPath) {
    int index = 0;
    Path destFilePath;
    do {
      destFilePath = Paths.get(testOutputPath.toString(),
          "classes-" + String.format("%03d", index) + FileUtils.DEX_EXTENSION);
      index++;
    } while (destFilePath.toFile().exists());

    return destFilePath;
  }

  private static Path getTestOutputPath(File destDir) throws IOException {
    assert destDir.exists();
    assert destDir.isDirectory();

    String testClassName = System.getProperty("test_class_name");
    String testName = System.getProperty("test_name");
    String headSha1 = System.getProperty("test_git_HEAD_sha1");

    assert testClassName != null;
    assert testName != null;
    assert headSha1 != null;

    return Files.createDirectories(
        Paths.get(
            destDir.getAbsolutePath(),
            headSha1,
            testClassName,
            testName + "-" + String.format("%03d", testOutputPathIndex)));
  }

  private static List<File> unzipDexFilesArchive(File zipFile) throws IOException {
    File tmpDir = Files.createTempDirectory("r8-test-").toFile();
    tmpDir.deleteOnExit();
    return ZipUtils.unzip(zipFile.getAbsolutePath(), tmpDir);
  }

  private static void storeAsGoldenFiles(List<File> files, File destDir) throws IOException {
    Path testOutputPath = getTestOutputPath(destDir);

    for (File f : files) {
      Path filePath = f.toPath();
      // TODO(jmhenaff): Check it's been produced by D8/R8?
      List<File> testFiles = Collections.singletonList(f);
      if (FileUtils.isArchive(filePath)) {
        testFiles = unzipDexFilesArchive(f);
      }
      for (File testFile : testFiles) {
        Path testFilePath = testFile.toPath();
        if (FileUtils.isDexFile(testFilePath)) {
          Path destFile = findNonConflictingDestinationFilePath(testOutputPath);
          FileUtils.writeToFile(destFile, null, Files.readAllBytes(testFilePath));
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private static void storeProcessResult(ProcessResult processResult, File dest)
      throws IOException {
    Gson gson = new Gson();
    Path testOutputPath = getTestOutputPath(dest);
    try (FileWriter fw = new FileWriter(new File(testOutputPath.toFile(), "processResult.json"))) {
      gson.toJson(processResult, ProcessResult.class, fw);
    }
  }

  private static ProcessResult readProcessResult(File dest) throws IOException {
    File processResultFile = new File(getTestOutputPath(dest).toFile(), "processResult.json");
    Gson gson = new Gson();
    try (FileReader fr = new FileReader(processResultFile)) {
      return gson.fromJson(fr, ProcessResult.class);
    }
  }

  private static ProcessResult compareAgainstGoldenFiles(List<File> files, File destDir)
      throws IOException {
    Path testOutputPath = getTestOutputPath(destDir);

    int index = 0;
    String stdErr = "";
    boolean passed = true;
    for (File f : files) {
      Path filePath = f.toPath();

      List<File> testFiles = Collections.singletonList(f);
      if (FileUtils.isArchive(filePath)) {
        testFiles = unzipDexFilesArchive(f);
      }

      for (File testFile : testFiles) {
        Path testFilePath = testFile.toPath();
        // TODO(jmhenaff): Check it's been produced by D8/R8?
        if (FileUtils.isDexFile(testFilePath)) {
          File goldenFile = Paths.get(testOutputPath.toString(),
              "classes-" + String.format("%03d", index) + FileUtils.DEX_EXTENSION).toFile();
          if (!goldenFile.exists()) {
            String fileDesc = "'" + testFile.getAbsolutePath() + "'";
            if (FileUtils.isZipFile(filePath)) {
              fileDesc += " (extracted from '" + f.getAbsolutePath() + "')";
            }
            stdErr += "Cannot find golden file '" + goldenFile.getAbsolutePath()
                + "' to compare against test file " + fileDesc + "\n";
            passed = false;
          } else if (!com.google.common.io.Files.equal(testFile, goldenFile)) {
            String fileDesc = "'" + testFile.getAbsolutePath() + "'";
            if (FileUtils.isZipFile(filePath)) {
              fileDesc += " (extracted from '" + f.getAbsolutePath() + "')";
            }
            stdErr +=
                "File " + fileDesc + " differs from golden file '" + goldenFile.getAbsolutePath()
                    + "'\n";
            passed = false;
          }
          index++;
        }
      }
    }
    // Ensure we processed as many files as there are golden files
    File goldenFile = Paths.get(testOutputPath.toString(),
        "classes-" + String.format("%03d", index) + FileUtils.DEX_EXTENSION).toFile();
    if (goldenFile.exists()) {
      stdErr += "Less dex files have been produced: there is at least one more golden file ('"
          + goldenFile.getAbsolutePath() + "'\n";
      passed = false;
    }
    return new ProcessResult(passed ? 0 : -1, "", stdErr);
  }

  public static boolean dealsWithGoldenFiles() {
    return compareAgaintsGoldenFiles() || generateGoldenFiles();
  }

  public static boolean compareAgaintsGoldenFiles() {
    return System.getProperty("use_golden_files_in") != null;
  }

  public static boolean generateGoldenFiles() {
    return System.getProperty("generate_golden_files_to") != null;
  }

  public static ProcessResult runArtNoVerificationErrorsRaw(String file, String mainClass)
      throws IOException {
    return runArtNoVerificationErrorsRaw(Collections.singletonList(file), mainClass, null);
  }

  public static ProcessResult runArtNoVerificationErrorsRaw(List<String> files, String mainClass,
      Consumer<ArtCommandBuilder> extras)
      throws IOException {
    return runArtNoVerificationErrorsRaw(files, mainClass, extras, null);
  }

  public static ProcessResult runArtNoVerificationErrorsRaw(List<String> files, String mainClass,
      Consumer<ArtCommandBuilder> extras,
      DexVm version)
      throws IOException {
    ProcessResult result = runArtRaw(files, mainClass, extras, version, false);
    failOnProcessFailure(result);
    failOnVerificationErrors(result);
    return result;
  }

  public static String runArtNoVerificationErrors(String file, String mainClass)
      throws IOException {
    return runArtNoVerificationErrorsRaw(file, mainClass).stdout;
  }

  public static String runArtNoVerificationErrors(List<String> files, String mainClass,
      Consumer<ArtCommandBuilder> extras)
      throws IOException {
    return runArtNoVerificationErrors(files, mainClass, extras, null);
  }

  public static String runArtNoVerificationErrors(List<String> files, String mainClass,
      Consumer<ArtCommandBuilder> extras,
      DexVm version)
      throws IOException {
    return runArtNoVerificationErrorsRaw(files, mainClass, extras, version).stdout;
  }

  protected static void failOnProcessFailure(ProcessResult result) {
    if (result.exitCode != 0) {
      fail("Unexpected failure: '" + result.stderr + "'\n" + result.stdout);
    }
  }

  protected static void failOnVerificationErrors(ProcessResult result) {
    if (result.stderr.contains("Verification error")) {
      fail("Verification error: \n" + result.stderr);
    }
  }

  private static ProcessResult runArtProcessRaw(ArtCommandBuilder builder) throws IOException {
    Assume.assumeTrue(artSupported() || dealsWithGoldenFiles());
    ProcessResult result;
    if (builder.isForDevice()) {
      try {
        result = builder.asDeviceRunner().run();
      } catch (DeviceRunnerConfigurationException e) {
        throw new RuntimeException(e);
      }
    } else {
      result = runProcess(builder.asProcessBuilder());
    }
    return result;
  }

  public static String runArt(ArtCommandBuilder builder) throws IOException {
    ProcessResult result = runArtProcessRaw(builder);
    failOnProcessFailure(result);
    return result.stdout;
  }

  public static String checkArtOutputIdentical(String file1, String file2, String mainClass,
      DexVm version)
      throws IOException {
    return checkArtOutputIdentical(Collections.singletonList(file1),
        Collections.singletonList(file2), mainClass, null, version);
  }

  public static String checkArtOutputIdentical(List<String> files1, List<String> files2,
      String mainClass,
      Consumer<ArtCommandBuilder> extras,
      DexVm version)
      throws IOException {
    return checkArtOutputIdentical(
        version,
        mainClass,
        extras,
        ImmutableList.of(ListUtils.map(files1, Paths::get), ListUtils.map(files2, Paths::get)));
  }

  public static String checkArtOutputIdentical(
      DexVm version,
      String mainClass,
      Consumer<ArtCommandBuilder> extras,
      Collection<Collection<Path>> programs)
      throws IOException {
    for (Collection<Path> program : programs) {
      for (Path path : program) {
        assertTrue("File " + path + " must exist", Files.exists(path));
      }
    }
    String output = null;
    for (Collection<Path> program : programs) {
      String result =
          ToolHelper.runArtNoVerificationErrors(
              ListUtils.map(program, Path::toString), mainClass, extras, version);
      if (output != null) {
        assertEquals(output, result);
      } else {
        output = result;
      }
    }
    return output;
  }

  public static void runDex2Oat(Path file, Path outFile) throws IOException {
    runDex2Oat(file, outFile, getDexVm());
  }

  public static void runDex2Oat(Path file, Path outFile, DexVm vm) throws IOException {
    ProcessResult result = runDex2OatRaw(file, outFile, vm);
    if (result.exitCode != 0) {
      fail("dex2oat failed, exit code " + result.exitCode + ", stderr:\n" + result.stderr);
    }
    if (result.stderr != null && result.stderr.contains("Verification error")) {
      fail("Verification error: \n" + result.stderr);
    }
  }

  public static ProcessResult runDex2OatRaw(Path file, Path outFile, DexVm vm) throws IOException {
    // TODO(jmhenaff): find a way to run this on windows (push dex and run on device/emulator?)
    Assume.assumeTrue(ToolHelper.isDex2OatSupported());
    Assume.assumeFalse("b/144975341", vm.version == DexVm.Version.V10_0_0);
    if (vm.isOlderThanOrEqual(DexVm.ART_4_4_4_HOST)) {
      // Run default dex2oat for tests on dalvik runtimes.
      vm = DexVm.ART_DEFAULT;
    }
    assert Files.exists(file);
    assert ByteStreams.toByteArray(Files.newInputStream(file)).length > 0;
    List<String> command = new ArrayList<>();
    command.add(getDex2OatPath(vm).toString());
    command.add("--android-root=" + getProductPath(vm) + "/system");
    command.add("--runtime-arg");
    command.add("-Xnorelocate");
    command.add("--dex-file=" + file.toAbsolutePath());
    command.add("--oat-file=" + outFile.toAbsolutePath());
    // TODO(zerny): Create a proper interface for invoking dex2oat. Hardcoding arch here is a hack!
    command.add("--instruction-set=" + getArchString(vm));
    ProcessBuilder builder = new ProcessBuilder(command);
    builder.environment().put("LD_LIBRARY_PATH", getDexVmLibPath(vm).toString());
    return runProcess(builder);
  }

  public static ProcessResult runProguardRaw(
      String proguardScript, Path inJar, Path outJar, List<Path> configs, Path map)
      throws IOException {
    return runProguardRaw(
        proguardScript, inJar, outJar, ToolHelper.getDefaultAndroidJar(), configs, map);
  }

  public static ProcessResult runProguardRaw(
      String proguardScript, Path inJar, Path outJar, Path lib, List<Path> configs, Path map)
      throws IOException {
    List<String> command = new ArrayList<>();
    command.add(proguardScript);
    command.add("-forceprocessing");  // Proguard just checks the creation time on the in/out jars.
    command.add("-injars");
    command.add(inJar.toString());
    command.add("-libraryjars");
    command.add(lib.toString());
    configs.forEach(config -> command.add("@" + config));
    command.add("-outjar");
    command.add(outJar.toString());
    command.add("-printmapping");
    if (map != null) {
      command.add(map.toString());
    }
    ProcessBuilder builder = new ProcessBuilder(command);
    return ToolHelper.runProcess(builder);
  }

  public static String runProguard(
      String proguardScript, Path inJar, Path outJar, List<Path> configs, Path map)
      throws IOException {
    ToolHelper.ProcessResult result = runProguardRaw(proguardScript, inJar, outJar, configs, map);
    if (result.exitCode != 0) {
      fail("Proguard failed, exit code " + result.exitCode + ", stderr:\n" + result.stderr);
    }
    return result.stdout;
  }

  public static ProcessResult runProguardRaw(
      Path inJar, Path outJar, Path lib, Path config, Path map) throws IOException {
    return runProguardRaw(getProguardScript(), inJar, outJar, lib, ImmutableList.of(config), map);
  }

  public static ProcessResult runProguardRaw(Path inJar, Path outJar, List<Path> config, Path map)
      throws IOException {
    return runProguardRaw(getProguardScript(), inJar, outJar, config, map);
  }

  public static ProcessResult runProguardRaw(Path inJar, Path outJar, Path config, Path map)
      throws IOException {
    return runProguardRaw(getProguardScript(), inJar, outJar, ImmutableList.of(config), map);
  }

  public static String runProguard(Path inJar, Path outJar, Path config, Path map)
      throws IOException {
    return runProguard(inJar, outJar, ImmutableList.of(config), map);
  }

  public static String runProguard(Path inJar, Path outJar, List<Path> config, Path map)
      throws IOException {
    return runProguard(getProguardScript(), inJar, outJar, config, map);
  }

  public static ProcessResult runProguard6Raw(Path inJar, Path outJar, Path config, Path map)
      throws IOException {
    return runProguardRaw(getProguard6Script(), inJar, outJar, ImmutableList.of(config), map);
  }

  public static ProcessResult runProguard6Raw(
      Path inJar, Path outJar, List<Path> config, Path map) throws IOException {
    return runProguardRaw(getProguard6Script(), inJar, outJar, config, map);
  }

  public static ProcessResult runProguard6Raw(
      Path inJar, Path outJar, Path lib, Path config, Path map) throws IOException {
    return runProguardRaw(getProguard6Script(), inJar, outJar, lib, ImmutableList.of(config), map);
  }

  public static String runProguard6(Path inJar, Path outJar, Path config, Path map)
      throws IOException {
    return runProguard6(inJar, outJar, ImmutableList.of(config), map);
  }

  public static String runProguard6(Path inJar, Path outJar, List<Path> configs, Path map)
      throws IOException {
    return runProguard(getProguard6Script(), inJar, outJar, configs, map);
  }

  public static ProcessResult runRetraceRaw(Path map, Path stackTrace) throws IOException {
    List<String> command = new ArrayList<>();
    command.add(getRetraceScript());
    command.add(map.toString());
    command.add(stackTrace.toString());
    ProcessBuilder builder = new ProcessBuilder(command);
    return ToolHelper.runProcess(builder);
  }

  public static String runRetrace(Path map, Path stackTrace) throws IOException {
    ProcessResult result = runRetraceRaw(map, stackTrace);
    if (result.exitCode != 0) {
      fail("Retrace failed, exit code " + result.exitCode + ", stderr:\n" + result.stderr);
    }
    return result.stdout;
  }

  public static class ProcessResult {

    public final int exitCode;
    public final String stdout;
    public final String stderr;
    public final String command;

    public ProcessResult(int exitCode, String stdout, String stderr, String command) {
      this.exitCode = exitCode;
      this.stdout = stdout;
      this.stderr = stderr;
      this.command = command;
    }

    ProcessResult(int exitCode, String stdout, String stderr) {
      this(exitCode, stdout, stderr, null);
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("EXIT CODE: ");
      builder.append(exitCode);
      builder.append("\n");
      builder.append("STDOUT: ");
      builder.append("\n");
      builder.append(stdout);
      builder.append("\n");
      builder.append("STDERR: ");
      builder.append("\n");
      builder.append(stderr);
      builder.append("\n");
      return builder.toString();
    }
  }

  // Process.pid() is added in Java 9. Until we use Java 9 this can be used on Linux and Mac OS.
  // https://docs.oracle.com/javase/9/docs/api/java/lang/Process.html#pid--
  private static synchronized long getPidOfProcess(Process p) {
    long pid = -1;
    try {
      if (p.getClass().getName().equals("java.lang.UNIXProcess")) {
        Field f = p.getClass().getDeclaredField("pid");
        f.setAccessible(true);
        pid = f.getLong(p);
        f.setAccessible(false);
      }
    } catch (Exception e) {
      pid = -1;
    }
    return pid;
  }

  public static ProcessResult runProcess(ProcessBuilder builder) throws IOException {
    return runProcess(builder, System.out);
  }

  public static ProcessResult runProcess(ProcessBuilder builder, PrintStream out)
      throws IOException {
    String command = String.join(" ", builder.command());
    out.println(command);
    return drainProcessOutputStreams(builder.start(), command);
  }

  public static ProcessResult drainProcessOutputStreams(Process process, String command) {
    // Drain stdout and stderr so that the process does not block. Read stdout and stderr
    // in parallel to make sure that neither buffer can get filled up which will cause the
    // C program to block in a call to write.
    StreamReader stdoutReader = new StreamReader(process.getInputStream());
    StreamReader stderrReader = new StreamReader(process.getErrorStream());
    Thread stdoutThread = new Thread(stdoutReader);
    Thread stderrThread = new Thread(stderrReader);
    stdoutThread.start();
    stderrThread.start();
    try {
      process.waitFor();
      stdoutThread.join();
      stderrThread.join();
    } catch (InterruptedException e) {
      throw new RuntimeException("Execution interrupted", e);
    }
    return new ProcessResult(
        process.exitValue(), stdoutReader.getResult(), stderrReader.getResult(), command);
  }

  public static R8Command.Builder addProguardConfigurationConsumer(
      R8Command.Builder builder, Consumer<ProguardConfiguration.Builder> consumer) {
    builder.addProguardConfigurationConsumerForTesting(consumer);
    return builder;
  }

  public static R8Command.Builder addSyntheticProguardRulesConsumerForTesting(
      R8Command.Builder builder, Consumer<List<ProguardConfigurationRule>> consumer) {
    builder.addSyntheticProguardRulesConsumerForTesting(consumer);
    return builder;
  }

  public static R8Command.Builder allowPartiallyImplementedProguardOptions(
      R8Command.Builder builder) {
    builder.allowPartiallyImplementedProguardOptions();
    return builder;
  }

  public static R8Command.Builder allowTestProguardOptions(R8Command.Builder builder) {
    builder.allowTestProguardOptions();
    return builder;
  }

  public static AndroidApp getApp(BaseCommand command) {
    return command.getInputApp();
  }

  public static AndroidApp.Builder getAppBuilder(BaseCommand.Builder builder) {
    return builder.getAppBuilder();
  }

  public static AndroidApp.Builder builderFromProgramDirectory(Path directory) throws IOException {
    AndroidApp.Builder builder = AndroidApp.builder();
    Files.walkFileTree(
        directory,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            if (isDexFile(file)) {
              builder.addProgramFile(file);
            }
            return FileVisitResult.CONTINUE;
          }
        });
    return builder;
  }

  public static void writeApplication(DexApplication application, InternalOptions options)
      throws ExecutionException {
    R8.writeApplication(
        Executors.newSingleThreadExecutor(),
        application,
        null,
        GraphLense.getIdentityLense(),
        InitClassLens.getDefault(),
        NamingLens.getIdentityLens(),
        options,
        null);
  }

  public enum KotlinTargetVersion {
    JAVA_6("JAVA_6"),
    JAVA_8("JAVA_8");

    private final String folderName;

    KotlinTargetVersion(String folderName) {
      this.folderName = folderName;
    }

    public String getFolderName() {
      return folderName;
    }

    public String getJvmTargetString() {
      switch (this) {
        case JAVA_6:
          return "1.6";
        case JAVA_8:
          return "1.8";
        default:
          throw new Unimplemented("JvmTarget not specified for " + this);
      }
    }
  }

  public static void disassemble(AndroidApp app, PrintStream ps)
      throws IOException, ExecutionException {
    DexApplication application =
        new ApplicationReader(app, new InternalOptions(), Timing.empty()).read().toDirect();
    new AssemblyWriter(application, new InternalOptions(), true, false).write(ps);
  }

  public static Path getTestFolderForClass(Class<?> clazz) {
    return Paths.get(ToolHelper.TESTS_DIR)
        .resolve("java")
        .resolve(ToolHelper.getFileNameForTestClass(clazz))
        .getParent();
  }

  public static Collection<Path> getFilesInTestFolderRelativeToClass(
      Class<?> clazz, String folderName, String endsWith) throws IOException {
    Path subFolder = getTestFolderForClass(clazz).resolve(folderName);
    assert Files.isDirectory(subFolder);
    try (Stream<Path> walker = Files.walk(subFolder)) {
      return walker.filter(path -> path.toString().endsWith(endsWith)).collect(Collectors.toList());
    }
  }
}
