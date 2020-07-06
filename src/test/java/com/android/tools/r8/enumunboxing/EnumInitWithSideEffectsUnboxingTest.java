package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumInitWithSideEffectsUnboxingTest extends EnumUnboxingTestBase {

  private final KeepRule enumKeepRule;
  private final TestParameters parameters;

  @Parameters(name = "{1}, enum keep rule: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        getAllEnumKeepRules(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public EnumInitWithSideEffectsUnboxingTest(KeepRule enumKeepRule, TestParameters parameters) {
    this.enumKeepRule = enumKeepRule;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(EnumInitWithSideEffectsUnboxingTest.class)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(enumKeepRule.getKeepRule())
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  static class TestClass {

    static MyEnum f = MyEnum.A;

    public static void main(String[] args) {}
  }

  enum MyEnum {
    A;

    MyEnum() {
      System.out.println("Hello world!");
    }
  }
}
