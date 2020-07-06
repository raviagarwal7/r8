// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.transformers;

import static com.android.tools.r8.references.Reference.classFromTypeName;
import static com.android.tools.r8.utils.DescriptorUtils.getBinaryNameFromDescriptor;
import static com.android.tools.r8.utils.StringUtils.replaceAll;
import static org.objectweb.asm.Opcodes.ASM7;

import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.transformers.MethodTransformer.MethodContext;
import com.android.tools.r8.utils.DescriptorUtils;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class ClassFileTransformer {

  /**
   * Basic algorithm for transforming the content of a class file.
   *
   * <p>The provided transformers are nested in the order given: the first in the list will receive
   * is call back first, if it forwards to 'super' then the seconds call back will be called, etc,
   * until finally the writer will be called. If the writer is not called the effect is as if the
   * callback was never called and its content will not be in the result.
   */
  public static byte[] transform(
      byte[] bytes,
      List<ClassTransformer> classTransformers,
      List<MethodTransformer> methodTransformers) {
    ClassReader reader = new ClassReader(bytes);
    ClassWriter writer = new ClassWriter(reader, 0);
    ClassVisitor subvisitor = new InnerMostClassTransformer(writer, methodTransformers);
    for (int i = classTransformers.size() - 1; i >= 0; i--) {
      classTransformers.get(i).setSubVisitor(subvisitor);
      subvisitor = classTransformers.get(i);
    }
    reader.accept(subvisitor, 0);
    return writer.toByteArray();
  }

  // Inner-most bride from the class transformation to the method transformers.
  private static class InnerMostClassTransformer extends ClassVisitor {
    ClassReference classReference;
    final List<MethodTransformer> methodTransformers;

    InnerMostClassTransformer(ClassWriter writer, List<MethodTransformer> methodTransformers) {
      super(ASM7, writer);
      this.methodTransformers = methodTransformers;
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      super.visit(version, access, name, signature, superName, interfaces);
      classReference = Reference.classFromBinaryName(name);
    }

    @Override
    public MethodVisitor visitMethod(
        int access, String name, String descriptor, String signature, String[] exceptions) {
      MethodContext context = createMethodContext(access, name, descriptor);
      MethodVisitor subvisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
      for (int i = methodTransformers.size() - 1; i >= 0; i--) {
        MethodTransformer transformer = methodTransformers.get(i);
        transformer.setSubVisitor(subvisitor);
        transformer.setContext(context);
        subvisitor = transformer;
      }
      return subvisitor;
    }

    private MethodContext createMethodContext(int access, String name, String descriptor) {
      // Maybe clean up this parsing of info as it is not very nice.
      MethodSignature methodSignature = MethodSignature.fromSignature(name, descriptor);
      MethodReference methodReference =
          Reference.method(
              classReference,
              name,
              Arrays.stream(methodSignature.parameters)
                  .map(DescriptorUtils::javaTypeToDescriptor)
                  .map(Reference::typeFromDescriptor)
                  .collect(Collectors.toList()),
              methodSignature.type.equals("void")
                  ? null
                  : Reference.typeFromDescriptor(
                      DescriptorUtils.javaTypeToDescriptor(methodSignature.type)));
      return new MethodContext(methodReference, access);
    }
  }

  // Transformer utilities.

  private final byte[] bytes;
  private final ClassReference classReference;
  private final List<ClassTransformer> classTransformers = new ArrayList<>();
  private final List<MethodTransformer> methodTransformers = new ArrayList<>();

  private ClassFileTransformer(byte[] bytes, ClassReference classReference) {
    this.bytes = bytes;
    this.classReference = classReference;
  }

  public static ClassFileTransformer create(byte[] bytes, ClassReference classReference) {
    return new ClassFileTransformer(bytes, classReference);
  }

  public static ClassFileTransformer create(Class<?> clazz) throws IOException {
    return create(ToolHelper.getClassAsBytes(clazz), classFromTypeName(clazz.getTypeName()));
  }

  public byte[] transform() {
    return ClassFileTransformer.transform(bytes, classTransformers, methodTransformers);
  }

  /** Base addition of a transformer on the class. */
  public ClassFileTransformer addClassTransformer(ClassTransformer transformer) {
    classTransformers.add(transformer);
    return this;
  }

  /** Base addition of a transformer on methods. */
  public ClassFileTransformer addMethodTransformer(MethodTransformer transformer) {
    methodTransformers.add(transformer);
    return this;
  }

  public ClassReference getClassReference() {
    return classReference;
  }

  /** Unconditionally replace the implements clause of a class. */
  public ClassFileTransformer setImplements(Class<?>... interfaces) {
    return addClassTransformer(
        new ClassTransformer() {
          @Override
          public void visit(
              int version,
              int access,
              String name,
              String signature,
              String superName,
              String[] ignoredInterfaces) {
            super.visit(
                version,
                access,
                name,
                signature,
                superName,
                Arrays.stream(interfaces)
                    .map(clazz -> DescriptorUtils.getBinaryNameFromJavaType(clazz.getTypeName()))
                    .toArray(String[]::new));
          }
        });
  }

  /** Unconditionally replace the descriptor (ie, qualified name) of a class. */
  public ClassFileTransformer setClassDescriptor(String newDescriptor) {
    assert DescriptorUtils.isClassDescriptor(newDescriptor);
    String newBinaryName = getBinaryNameFromDescriptor(newDescriptor);
    return addClassTransformer(
            new ClassTransformer() {
              @Override
              public void visit(
                  int version,
                  int access,
                  String binaryName,
                  String signature,
                  String superName,
                  String[] interfaces) {
                super.visit(version, access, newBinaryName, signature, superName, interfaces);
              }

              @Override
              public FieldVisitor visitField(
                  int access, String name, String descriptor, String signature, Object object) {
                return super.visitField(
                    access,
                    name,
                    replaceAll(descriptor, getClassReference().getDescriptor(), newDescriptor),
                    signature,
                    object);
              }

              @Override
              public MethodVisitor visitMethod(
                  int access,
                  String name,
                  String descriptor,
                  String signature,
                  String[] exceptions) {
                return super.visitMethod(
                    access,
                    name,
                    replaceAll(descriptor, getClassReference().getDescriptor(), newDescriptor),
                    signature,
                    exceptions);
              }
            })
        .replaceClassDescriptorInMethodInstructions(
            getClassReference().getDescriptor(), newDescriptor);
  }

  public ClassFileTransformer setVersion(int newVersion) {
    return addClassTransformer(
        new ClassTransformer() {
          @Override
          public void visit(
              int version,
              int access,
              String name,
              String signature,
              String superName,
              String[] interfaces) {
            super.visit(newVersion, access, name, signature, superName, interfaces);
          }
        });
  }

  public ClassFileTransformer setMinVersion(CfVm jdk) {
    return setMinVersion(jdk.getClassfileVersion());
  }

  public ClassFileTransformer setMinVersion(int minVersion) {
    return addClassTransformer(
        new ClassTransformer() {
          @Override
          public void visit(
              int version,
              int access,
              String name,
              String signature,
              String superName,
              String[] interfaces) {
            super.visit(
                Integer.max(version, minVersion), access, name, signature, superName, interfaces);
          }
        });
  }

  public ClassFileTransformer setAccessFlags(Consumer<ClassAccessFlags> fn) {
    return addClassTransformer(
        new ClassTransformer() {
          @Override
          public void visit(
              int version,
              int access,
              String name,
              String signature,
              String superName,
              String[] interfaces) {
            ClassAccessFlags flags = ClassAccessFlags.fromCfAccessFlags(access);
            fn.accept(flags);
            super.visit(
                version, flags.getAsCfAccessFlags(), name, signature, superName, interfaces);
          }
        });
  }

  public ClassFileTransformer setNest(Class<?> host, Class<?>... members) {
    assert !Arrays.asList(members).contains(host);
    return setMinVersion(CfVm.JDK11)
        .addClassTransformer(
            new ClassTransformer() {

              final String hostName = DescriptorUtils.getBinaryNameFromJavaType(host.getTypeName());

              final List<String> memberNames =
                  Arrays.stream(members)
                      .map(m -> DescriptorUtils.getBinaryNameFromJavaType(m.getTypeName()))
                      .collect(Collectors.toList());

              String className;

              @Override
              public void visit(
                  int version,
                  int access,
                  String name,
                  String signature,
                  String superName,
                  String[] interfaces) {
                super.visit(version, access, name, signature, superName, interfaces);
                className = name;
              }

              @Override
              public void visitNestHost(String nestHost) {
                // Ignore/remove existing nest information.
              }

              @Override
              public void visitNestMember(String nestMember) {
                // Ignore/remove existing nest information.
              }

              @Override
              public void visitEnd() {
                if (className.equals(hostName)) {
                  for (String memberName : memberNames) {
                    super.visitNestMember(memberName);
                  }
                } else {
                  assert memberNames.contains(className);
                  super.visitNestHost(hostName);
                }
                super.visitEnd();
              }
            });
  }

  public ClassFileTransformer unsetAbstract() {
    return setAccessFlags(ClassAccessFlags::unsetAbstract);
  }

  public ClassFileTransformer setAnnotation() {
    return setAccessFlags(
        accessFlags -> {
          assert accessFlags.isAbstract();
          assert accessFlags.isInterface();
          accessFlags.setAnnotation();
        });
  }

  public ClassFileTransformer setBridge(Method method) {
    return setAccessFlags(method, MethodAccessFlags::setBridge);
  }

  public ClassFileTransformer setPublic(Method method) {
    return setAccessFlags(
        method,
        accessFlags -> {
          accessFlags.unsetPrivate();
          accessFlags.unsetProtected();
          accessFlags.setPublic();
        });
  }

  public ClassFileTransformer setPrivate(Method method) {
    return setAccessFlags(
        method,
        accessFlags -> {
          accessFlags.unsetPublic();
          accessFlags.unsetProtected();
          accessFlags.setPrivate();
        });
  }

  public ClassFileTransformer setSynthetic(Method method) {
    return setAccessFlags(method, AccessFlags::setSynthetic);
  }

  public ClassFileTransformer setAccessFlags(
      Constructor<?> constructor, Consumer<MethodAccessFlags> setter) {
    return setAccessFlags(Reference.methodFromMethod(constructor), setter);
  }

  public ClassFileTransformer setAccessFlags(Method method, Consumer<MethodAccessFlags> setter) {
    return setAccessFlags(Reference.methodFromMethod(method), setter);
  }

  private ClassFileTransformer setAccessFlags(
      MethodReference methodReference, Consumer<MethodAccessFlags> setter) {
    return addClassTransformer(
        new ClassTransformer() {

          @Override
          public MethodVisitor visitMethod(
              int access, String name, String descriptor, String signature, String[] exceptions) {
            boolean isConstructor =
                name.equals(Constants.INSTANCE_INITIALIZER_NAME)
                    || name.equals(Constants.CLASS_INITIALIZER_NAME);
            MethodAccessFlags accessFlags =
                MethodAccessFlags.fromCfAccessFlags(access, isConstructor);
            if (name.equals(methodReference.getMethodName())
                && descriptor.equals(methodReference.getMethodDescriptor())) {
              setter.accept(accessFlags);
            }
            return super.visitMethod(
                accessFlags.getAsCfAccessFlags(), name, descriptor, signature, exceptions);
          }
        });
  }

  @FunctionalInterface
  public interface MethodPredicate {
    boolean test(int access, String name, String descriptor, String signature, String[] exceptions);
  }

  public ClassFileTransformer removeInnerClasses() {
    return addClassTransformer(
        new ClassTransformer() {
          @Override
          public void visitInnerClass(String name, String outerName, String innerName, int access) {
            // Intentionally empty.
          }
        });
  }

  public ClassFileTransformer removeMethods(MethodPredicate predicate) {
    return addClassTransformer(
        new ClassTransformer() {
          @Override
          public MethodVisitor visitMethod(
              int access, String name, String descriptor, String signature, String[] exceptions) {
            return predicate.test(access, name, descriptor, signature, exceptions)
                ? null
                : super.visitMethod(access, name, descriptor, signature, exceptions);
          }
        });
  }

  /** Abstraction of the MethodVisitor.visitMethodInsn method with its sub visitor. */
  @FunctionalInterface
  public interface MethodInsnTransform {
    void visitMethodInsn(
        int opcode,
        String owner,
        String name,
        String descriptor,
        boolean isInterface,
        MethodVisitor visitor);
  }

  /** Abstraction of the MethodVisitor.visitTypeInsn method with its sub visitor. */
  @FunctionalInterface
  public interface TypeInsnTransform {
    void visitTypeInsn(int opcode, String type, MethodVisitor visitor);
  }

  /** Abstraction of the MethodVisitor.visitLdcInsn method with its sub visitor. */
  @FunctionalInterface
  public interface LdcInsnTransform {
    void visitLdcInsn(Object value, MethodVisitor visitor);
  }

  /** Abstraction of the MethodVisitor.visitTryCatchBlock method with its sub visitor. */
  @FunctionalInterface
  public interface TryCatchBlockTransform {
    void visitTryCatchBlock(
        Label start, Label end, Label handler, String type, MethodVisitor visitor);
  }

  public ClassFileTransformer replaceAnnotationDescriptor(
      String oldDescriptor, String newDescriptor) {
    return addClassTransformer(
        new ClassTransformer() {
          @Override
          public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return super.visitAnnotation(
                descriptor.equals(oldDescriptor) ? newDescriptor : descriptor, visible);
          }
        });
  }

  public ClassFileTransformer replaceClassDescriptorInMethodInstructions(
      String oldDescriptor, String newDescriptor) {
    return addMethodTransformer(
        new MethodTransformer() {
          @Override
          public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            super.visitFieldInsn(
                opcode,
                rewriteASMInternalTypeName(owner),
                name,
                replaceAll(descriptor, oldDescriptor, newDescriptor));
          }

          @Override
          public void visitLdcInsn(Object value) {
            if (value instanceof Type) {
              Type type = (Type) value;
              super.visitLdcInsn(
                  Type.getType(replaceAll(type.getDescriptor(), oldDescriptor, newDescriptor)));
            } else {
              super.visitLdcInsn(value);
            }
          }

          @Override
          public void visitMethodInsn(
              int opcode, String owner, String name, String descriptor, boolean isInterface) {
            super.visitMethodInsn(
                opcode,
                rewriteASMInternalTypeName(owner),
                name,
                replaceAll(descriptor, oldDescriptor, newDescriptor),
                isInterface);
          }

          @Override
          public void visitTypeInsn(int opcode, String type) {
            super.visitTypeInsn(opcode, rewriteASMInternalTypeName(type));
          }

          private String rewriteASMInternalTypeName(String type) {
            return Type.getType(
                    replaceAll(
                        Type.getObjectType(type).getDescriptor(), oldDescriptor, newDescriptor))
                .getInternalName();
          }
        });
  }

  @FunctionalInterface
  private interface VisitMethodInsnCallback {
    void visitMethodInsn(
        int opcode, String owner, String name, String descriptor, boolean isInterface);
  }

  private MethodVisitor redirectVisitMethodInsn(
      MethodVisitor visitor, VisitMethodInsnCallback callback) {
    return new MethodVisitor(ASM7, visitor) {
      @Override
      public void visitMethodInsn(
          int opcode, String owner, String name, String descriptor, boolean isInterface) {
        callback.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
      }
    };
  }

  public ClassFileTransformer transformMethodInsnInMethod(
      String methodName, MethodInsnTransform transform) {
    return addMethodTransformer(
        new MethodTransformer() {
          @Override
          public void visitMethodInsn(
              int opcode, String owner, String name, String descriptor, boolean isInterface) {
            if (getContext().method.getMethodName().equals(methodName)) {
              transform.visitMethodInsn(
                  opcode,
                  owner,
                  name,
                  descriptor,
                  isInterface,
                  redirectVisitMethodInsn(this, super::visitMethodInsn));
            } else {
              super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
          }
        });
  }

  @FunctionalInterface
  private interface VisitTypeInsnCallback {
    void visitTypeInsn(int opcode, String type);
  }

  private MethodVisitor redirectVisitTypeInsn(
      MethodVisitor visitor, VisitTypeInsnCallback callback) {
    return new MethodVisitor(ASM7, visitor) {
      @Override
      public void visitTypeInsn(int opcode, String type) {
        callback.visitTypeInsn(opcode, type);
      }
    };
  }

  public ClassFileTransformer transformTypeInsnInMethod(
      String methodName, TypeInsnTransform transform) {
    return addMethodTransformer(
        new MethodTransformer() {
          @Override
          public void visitTypeInsn(int opcode, String type) {
            if (getContext().method.getMethodName().equals(methodName)) {
              transform.visitTypeInsn(
                  opcode, type, redirectVisitTypeInsn(this, super::visitTypeInsn));
            } else {
              super.visitTypeInsn(opcode, type);
            }
          }
        });
  }

  @FunctionalInterface
  private interface VisitTryCatchBlockCallback {
    void visitTryCatchBlock(Label start, Label end, Label handler, String type);
  }

  private MethodVisitor redirectVistiTryCatchBlock(
      MethodVisitor visitor, VisitTryCatchBlockCallback callback) {
    return new MethodVisitor(ASM7, visitor) {
      @Override
      public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
        callback.visitTryCatchBlock(start, end, handler, type);
      }
    };
  }

  public ClassFileTransformer transformTryCatchBlock(
      String methodName, TryCatchBlockTransform transform) {
    return addMethodTransformer(
        new MethodTransformer() {
          @Override
          public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            if (getContext().method.getMethodName().equals(methodName)) {
              transform.visitTryCatchBlock(
                  start,
                  end,
                  handler,
                  type,
                  redirectVistiTryCatchBlock(this, super::visitTryCatchBlock));
            } else {
              super.visitTryCatchBlock(start, end, handler, type);
            }
          }
        });
  }

  @FunctionalInterface
  private interface VisitLdcInsnCallback {
    void visitLdcInsn(Object value);
  }

  private MethodVisitor redirectVisitLdcInsn(MethodVisitor visitor, VisitLdcInsnCallback callback) {
    return new MethodVisitor(ASM7, visitor) {
      @Override
      public void visitLdcInsn(Object value) {
        callback.visitLdcInsn(value);
      }
    };
  }

  public ClassFileTransformer transformLdcInsnInMethod(
      String methodName, LdcInsnTransform transform) {
    return addMethodTransformer(
        new MethodTransformer() {
          @Override
          public void visitLdcInsn(Object value) {
            if (getContext().method.getMethodName().equals(methodName)) {
              transform.visitLdcInsn(value, redirectVisitLdcInsn(this, super::visitLdcInsn));
            } else {
              super.visitLdcInsn(value);
            }
          }
        });
  }
}
