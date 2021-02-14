// ASM: a very small and fast Java bytecode manipulation framework
// Copyright (c) 2000-2011 INRIA, France Telecom
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
// 1. Redistributions of source code must retain the above copyright
//    notice, this list of conditions and the following disclaimer.
// 2. Redistributions in binary form must reproduce the above copyright
//    notice, this list of conditions and the following disclaimer in the
//    documentation and/or other materials provided with the distribution.
// 3. Neither the name of the copyright holders nor the names of its
//    contributors may be used to endorse or promote products derived from
//    this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF
// THE POSSIBILITY OF SUCH DAMAGE.
package nebula.tinyasm.util;

import static org.objectweb.asm.Opcodes.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.TypePath;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.util.ASMifiable;
import org.objectweb.asm.util.Printer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nebula.tinyasm.util.TinyLocalsStack.Var;

/**
 * A {@link Printer} that prints the ASM code to generate the classes if visits.
 *
 * @author Eric Bruneton
 */
public class TinyASMifier extends Printer {
	static Logger logger = LoggerFactory.getLogger(TinyASMifier.class);

	/** A pseudo access flag used to distinguish class access flags. */
	private static final int ACCESS_CLASS = 0x40000;

	/** A pseudo access flag used to distinguish field access flags. */
	private static final int ACCESS_FIELD = 0x80000;

	/** A pseudo access flag used to distinguish inner class flags. */
	private static final int ACCESS_INNER = 0x100000;

	/** A pseudo access flag used to distinguish module requires / exports flags. */
	private static final int ACCESS_MODULE = 0x200000;

	private static final String ANNOTATION_VISITOR = "annotationVisitor";
	private static final String ANNOTATION_VISITOR0 = "annotationVisitor0 = ";
//	private static final String NEW_OBJECT_ARRAY = ", new Object[] {";
	private static final String END_ARRAY = " });\n";
	private static final String END_PARAMETERS = ");\n\n";
	private static final String VISIT_END = ".visitEnd();\n";
//	private static final String VISIT_END = ".visitEnd();\n";

	private static final Map<Integer, String> CLASS_VERSIONS;

	static {
		HashMap<Integer, String> classVersions = new HashMap<Integer, String>();
		classVersions.put(Opcodes.V1_1, "V1_1");
		classVersions.put(Opcodes.V1_2, "V1_2");
		classVersions.put(Opcodes.V1_3, "V1_3");
		classVersions.put(Opcodes.V1_4, "V1_4");
		classVersions.put(Opcodes.V1_5, "V1_5");
		classVersions.put(Opcodes.V1_6, "V1_6");
		classVersions.put(Opcodes.V1_7, "V1_7");
		classVersions.put(Opcodes.V1_8, "V1_8");
		classVersions.put(Opcodes.V9, "V9");
		classVersions.put(Opcodes.V10, "V10");
		classVersions.put(Opcodes.V11, "V11");
		CLASS_VERSIONS = Collections.unmodifiableMap(classVersions);
	}

	/** The name of the visitor variable in the produced code. */
	protected final String visitname;

	/** The identifier of the annotation visitor variable in the produced code. */
	protected final int id;

	/** The name of the Label variables in the produced code. */
	protected Map<Label, String> labelNames;

	/**
	 * Constructs a new {@link TinyASMifier}. <i>Subclasses must not use this
	 * constructor</i>. Instead, they must use the
	 * {@link #TinyASMifier(int, String, int)} version.
	 *
	 * @throws IllegalStateException If a subclass calls this constructor.
	 */
	public TinyASMifier() {
		this(Opcodes.ASM6, "classWriter", 0);
		if (getClass() != TinyASMifier.class) {
			throw new IllegalStateException();
		}
	}

	/**
	 * Constructs a new {@link TinyASMifier}.
	 *
	 * @param api                 the ASM API version implemented by this class.
	 *                            Must be one of {@link Opcodes#ASM4},
	 *                            {@link Opcodes#ASM5}, {@link Opcodes#ASM6} or
	 *                            {@link Opcodes#ASM7_EXPERIMENTAL}.
	 * @param visitorVariableName the name of the visitor variable in the produced
	 *                            code.
	 * @param annotationVisitorId identifier of the annotation visitor variable in
	 *                            the produced code.
	 */
	protected TinyASMifier(final int api, final String visitorVariableName, final int annotationVisitorId) {
		super(api);
		this.visitname = "\t" + visitorVariableName;
		this.id = annotationVisitorId;
	}

//  /**
//   * Prints the ASM source code to generate the given class to the standard output.
//   *
//   * <p>Usage: ASMifier [-debug] &lt;binary class name or class file name&gt;
//   *
//   * @param args the command line arguments.
//   * @throws IOException if the class cannot be found, or if an IOException occurs.
//   */
//  public static void main(final String[] args) throws IOException {
//    String usage =
//        "Prints the ASM code to generate the given class.\n"
//            + "Usage: ASMifier [-debug] <fully qualified class name or class file name>";
////    main(usage, new TinyASMifier(), args);
//  }

	// -----------------------------------------------------------------------------------------------
	// Classes
	// -----------------------------------------------------------------------------------------------

	@Override
	public void visit(final int version, final int access, final String name, final String signature, final String superName, final String[] interfaces) {
		String simpleName;
		if (name == null) {
			simpleName = "module-info";
		} else {
			int lastSlashIndex = name.lastIndexOf('/');
			if (lastSlashIndex == -1) {
				simpleName = name;
			} else {
				text.add("package " + name.substring(0, lastSlashIndex).replace('/', '.') + ";\n");
				simpleName = name.substring(lastSlashIndex + 1).replace('-', '_');
			}

		}

//		  SignatureWriter sw = new SignatureWriter();
//	        SignatureVisitor sa = new AddGernicMVisiter(sw);
//	        SignatureReader sr = new SignatureReader(s1);
//	        sr.acceptType(sa);
//	        return sw.toString();
//    text.add("import org.objectweb.asm.AnnotationVisitor;\n");
//    text.add("import org.objectweb.asm.Attribute;\n");
//    text.add("import org.objectweb.asm.ClassReader;\n");
//    text.add("import org.objectweb.asm.ClassWriter;\n");
//    text.add("import org.objectweb.asm.ConstantDynamic;\n");
//    text.add("import org.objectweb.asm.FieldVisitor;\n");
//    text.add("import org.objectweb.asm.Handle;\n");
		text.add("import org.objectweb.asm.Label;\n");
//    text.add("import org.objectweb.asm.MethodVisitor;\n");
//    text.add("import org.objectweb.asm.Opcodes;\n");
//    text.add("import org.objectweb.asm.Type;\n");
//    text.add("import org.objectweb.asm.TypePath;\n");

		text.add("import nebula.tinyasm.ClassBody;\n");
		text.add("import nebula.tinyasm.ClassBuilder;\n");
		text.add("import nebula.tinyasm.MethodCode;\n");
		text.add("import org.objectweb.asm.Type;\n");

		text.add("import static org.objectweb.asm.Opcodes.*;\n");

		text.add("import nebula.tinyasm.Clazz;\n");
		text.add("@SuppressWarnings(\"unused\")\n");

		text.add("public class " + simpleName + "TinyAsmDump {\n\n");
		text.add("public static byte[] dump () throws Exception {\n\n");
//    text.add("ClassWriter classWriter = new ClassWriter(0);\n");
//    text.add("FieldVisitor fieldVisitor;\n");
//    text.add("MethodVisitor methodVisitor;\n");
//    text.add("AnnotationVisitor annotationVisitor0;\n\n");

		// ClassBody classWriter =
		// ClassBuilder.make("nebula.tinyasm.util.SimpleSample").body();
//		ClassSignature = ( visitFormalTypeParameter visitClassBound? visitInterfaceBound* )* (visitSuperclass visitInterface* )
//				MethodSignature = ( visitFormalTypeParameter visitClassBound? visitInterfaceBound* )* (visitParameterType* visitReturnType visitExceptionType* )
//				TypeSignature = visitBaseType | visitTypeVariable | visitArrayType | ( visitClassType visitTypeArgument* ( visitInnerClassType visitTypeArgument* )* visitEnd ) )

		stringBuilder.setLength(0);
		/// stringBuilder.append("classWriter.visit(");
		if (signature == null) {
			stringBuilder.append("ClassBody classWriter = ClassBuilder.make(");
			appendConstant(name.replace('/', '.'));
			if (!Object.class.getName().equals(superName.replace('/', '.'))) {
				stringBuilder.append(", ");
				stringBuilder.append(superName.replace('/', '.') + ".class");
			}
//			stringBuilder.append(", ");
//			if (interfaces != null && interfaces.length > 0) {
//				stringBuilder.append("new String[] {");
//				for (int i = 0; i < interfaces.length; ++i) {
//					stringBuilder.append(i == 0 ? " " : ", ");
//					appendConstant(interfaces[i]);
//				}
//				stringBuilder.append(" }");
//			} else {
//				stringBuilder.append("null");
//			}
			stringBuilder.append(")");
		} else {
			stringBuilder.append("ClassBody classWriter = ClassBuilder.make(");

			appendConstant(name.replace('/', '.'));
			stringBuilder.append(", ");
			SignatureReader sr = new SignatureReader(signature);
			ClassSignature signatureVistor = new ClassSignature(Opcodes.ASM5);
			sr.accept(signatureVistor);
			stringBuilder.append(signatureVistor.superClass.toString());
			for (StringBuilder string : signatureVistor.interfacesClass) {
				stringBuilder.append(",");
				stringBuilder.append(string);
			}
//			stringBuilder.append(signatureVistor.interfacesClass.toString());
			stringBuilder.append(")");
			for (StringBuilder string : signatureVistor.typeParameterClass) {
				stringBuilder.append(".formalTypeParameter(");
				stringBuilder.append(string);
				stringBuilder.append(")");
			}
		}

		if (access != ACC_PUBLIC) {
			stringBuilder.append(".access(");
			appendAccessFlags(access | ACCESS_CLASS);
			stringBuilder.append(")");
		}
		stringBuilder.append(".body();\n\n");
		text.add(stringBuilder.toString());
	}

	class ClazzBuilder {
		ClazzBuilder parent;
		String classType;
		List<ClazzBuilder> typeArguments;

		public ClazzBuilder(String classType) {
			this.classType = classType;
		}

		public ClazzBuilder argument(String classType) {
			ClazzBuilder b = new ClazzBuilder(classType);
			b.parent = this;
			return b;
		}
	}

	@Override
	public void visitSource(final String file, final String debug) {
//    stringBuilder.setLength(0);
//    stringBuilder.append("classWriter.visitSource(");
//    appendConstant(file);
//    stringBuilder.append(", ");
//    appendConstant(debug);
//    stringBuilder.append(END_PARAMETERS);
//    text.add(stringBuilder.toString());
	}

	@Override
	public Printer visitModule(final String name, final int flags, final String version) {
		stringBuilder.setLength(0);
		stringBuilder.append("ModuleVisitor moduleVisitor = classWriter.visitModule(");
		appendConstant(name);
		stringBuilder.append(", ");
		appendAccessFlags(flags | ACCESS_MODULE);
		stringBuilder.append(", ");
		appendConstant(version);
		stringBuilder.append(END_PARAMETERS);
		text.add(stringBuilder.toString());
		TinyASMifier asmifier = createASMifier("moduleVisitor", 0);
		text.add(asmifier.getText());
		text.add("}\n");
		return asmifier;
	}

	@Override
	public void visitNestHostExperimental(final String nestHost) {
		stringBuilder.setLength(0);
		stringBuilder.append("classWriter.visitNestHostExperimental(");
		appendConstant(nestHost);
		stringBuilder.append(");\n\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitOuterClass(final String owner, final String name, final String descriptor) {
		stringBuilder.setLength(0);
		stringBuilder.append("classWriter.visitOuterClass(");
		appendConstant(owner);
		stringBuilder.append(", ");
		appendConstant(name);
		stringBuilder.append(", ");
		appendConstant(descriptor);
		stringBuilder.append(END_PARAMETERS);
		text.add(stringBuilder.toString());
	}

	@Override
	public TinyASMifier visitClassAnnotation(final String descriptor, final boolean visible) {
		return visitAnnotation(descriptor, visible);
	}

	@Override
	public TinyASMifier visitClassTypeAnnotation(final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
		return visitTypeAnnotation(typeRef, typePath, descriptor, visible);
	}

	@Override
	public void visitClassAttribute(final Attribute attribute) {
		visitAttribute(attribute);
	}

	@Override
	public void visitNestMemberExperimental(final String nestMember) {
		stringBuilder.setLength(0);
		stringBuilder.append("classWriter.visitNestMemberExperimental(");
		appendConstant(nestMember);
		stringBuilder.append(");\n\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitInnerClass(final String name, final String outerName, final String innerName, final int access) {
		stringBuilder.setLength(0);
		stringBuilder.append("classWriter.visitInnerClass(");
		appendConstant(name);
		stringBuilder.append(", ");
		appendConstant(outerName);
		stringBuilder.append(", ");
		appendConstant(innerName);
		stringBuilder.append(", ");
		appendAccessFlags(access | ACCESS_INNER);
		stringBuilder.append(END_PARAMETERS);
		text.add(stringBuilder.toString());
	}

//	private String clazzOf(String description) {
//		logger.trace("clazzOf({})", description);
//		switch (description) {
//		case "I":
//			return "int.class";
//
//		default:
//			break;
//		}
//		return null;
//	}

	static Map<String, String> typeMaps = new HashMap<>();
	static {
		typeMaps.put("Z", "boolean.class");
		typeMaps.put("B", "byte.class");
		typeMaps.put("C", "char.class");
		typeMaps.put("S", "short.class");
		typeMaps.put("I", "int.class");
		typeMaps.put("J", "long.class");
		typeMaps.put("F", "float.class");
		typeMaps.put("D", "double.class");
		typeMaps.put("[Z", "boolean[].class");
		typeMaps.put("[B", "byte[].class");
		typeMaps.put("[C", "char[].class");
		typeMaps.put("[S", "short[].class");
		typeMaps.put("[I", "int[].class");
		typeMaps.put("[J", "long[].class");
		typeMaps.put("[F", "float[].class");
		typeMaps.put("[D", "double[].class");
	}

	private String clazzOf(Type type) {
		logger.trace("clazzOf({})", type);
		if (typeMaps.containsKey(type.getInternalName())) {
			return typeMaps.get(type.getInternalName());
		} else if (type.getSort() == Type.ARRAY && type.getElementType().getSort() == Type.OBJECT) {
			logger.debug("{} Array", type.getElementType());
			return type.getElementType().getClassName() + "[].class";
		} else if (type.getSort() == Type.OBJECT) {
			return type.getClassName() + ".class";
		}

//		Class<?> c = char[].class.isar;
		return "unknown.class";
//		switch (description) {
//		case "I":
//			return "int.class";
//
//		default:
//			break;
//		}
//		return null;
	}

	@Override
	public TinyASMifier visitField(final int access, final String name, final String descriptor, final String signature, final Object value) {
//		classWriter.field("i", int.class);
		stringBuilder.setLength(0);
		if ((access & ACC_STATIC) > 0) {
			stringBuilder.append("classWriter.staticField(");
		} else {

			stringBuilder.append("classWriter.field(");
		}
//		if (!((access & ACC_PRIVATE) > 0)) {
////			appendAccessFlags(access | ACCESS_FIELD);
//			appendAccessFlags(access);
//			stringBuilder.append(", ");
//
////			
////			stringBuilder.append("{\n");
////			stringBuilder.append("fieldVisitor = classWriter.visitField(");
//
////			stringBuilder.append(", ");
////			appendConstant(name);
////			stringBuilder.append(", ");
////			appendConstant(descriptor);
////			stringBuilder.append(", ");
////			appendConstant(signature);
////			stringBuilder.append(", ");
////			appendConstant(value);
////			stringBuilder.append(");\n");
//			appendConstant(name);
//			stringBuilder.append(", Clazz.of(");
//			stringBuilder.append(clazzOf(Type.getType(descriptor)));
//			stringBuilder.append(")");
//		} else {
		appendAccessFlags(access);
		stringBuilder.append(", ");

//			
//			stringBuilder.append("{\n");
//			stringBuilder.append("fieldVisitor = classWriter.visitField(");

//			stringBuilder.append(", ");

		appendConstant(name);

		if (signature == null) {
			stringBuilder.append(", Clazz.of(");
			stringBuilder.append(clazzOf(Type.getType(descriptor)));
			stringBuilder.append(")");
		} else {
			stringBuilder.append(",");
			SignatureReader sr = new SignatureReader(signature);
			ClassSignature signatureVistor = new ClassSignature(Opcodes.ASM5);
			sr.accept(signatureVistor);
			stringBuilder.append(signatureVistor.toString());
		}
//			stringBuilder.append(", ");
//			appendConstant(value);
//			stringBuilder.append(");\n");
//		}
		stringBuilder.append(");\n");

		text.add(stringBuilder.toString());
		TinyASMifier asmifier = createASMifier("fieldVisitor", 0);
//		text.add(asmifier.getText());
//		text.add("}\n");
		return asmifier;
	}

	TinyLocalsStack methodLocals = new TinyLocalsStack();;

	boolean methodIsStatic = false;

////	mhLocals.push(field.name, new LocalsVariable(field, labelCurrent));
// LocalsStack mhLocals = new LocalsStack();
//	Stack<Type> stack = new Stack<>();

	Type[] methodParamTypes;
	List<StringBuilder> methodParamClazzes;

	@Override
	public TinyASMifier visitMethod(final int access, final String name, final String descriptor, final String signature, final String[] exceptions) {
		methodLocals = new TinyLocalsStack();

		logger.debug("visitMethod(final int access, final String {}, final String {}, final String {}, final String[] exceptions)", name, descriptor,
				signature);
		if ((access & ACC_STATIC) > 0) {
			methodIsStatic = true;
			methodVisitParameter = 0;
		} else {
			methodLocals.push("this", Type.getType(Object.class));
			methodIsStatic = false;
			methodVisitParameter = 1;
		}

		stringBuilder.setLength(0);
//		stringBuilder.append("{\n");
		if (!methodIsStatic) {
			stringBuilder.append("classWriter.method(");
			appendAccessFlags(access);
			stringBuilder.append(", ");
		} else {
			stringBuilder.append("classWriter.staticMethod(");
			appendAccessFlags(access);
			stringBuilder.append(", ");
		}
		Type returnType = Type.getReturnType(descriptor);
		methodParamTypes = Type.getArgumentTypes(descriptor);
		methodParamClazzes = null;
		if (signature == null) {
			if (returnType != Type.VOID_TYPE) {
				stringBuilder.append(clazzOf(returnType));
				stringBuilder.append(", ");
			}
			appendConstant(name);
		} else {
			SignatureReader sr = new SignatureReader(signature);
			ClassSignature signatureVistor = new ClassSignature(Opcodes.ASM5);
			sr.accept(signatureVistor);
			stringBuilder.append(signatureVistor.returnClass.toString());
			stringBuilder.append(", ");
			appendConstant(name);
			methodParamClazzes = signatureVistor.paramsClass;
//			stringBuilder.append(", ");
//			stringBuilder.append(signatureVistor.paramsClass.toString());
		}

//		stringBuilder.append(", ");
//		if (exceptions != null && exceptions.length > 0) {
//			stringBuilder.append("new String[] {");
//			for (int i = 0; i < exceptions.length; ++i) {
//				stringBuilder.append(i == 0 ? " " : ", ");
//				appendConstant(exceptions[i]);
//			}
//			stringBuilder.append(" }");
//		} else {
//			stringBuilder.append("null");
//		}
		stringBuilder.append(")");
		text.add(stringBuilder.toString());
		stringBuilder.setLength(0);

		if (methodParamTypes.length > 0) {
			for (int i = 0; i < methodParamTypes.length; i++) {
				methodLocals.push("", methodParamTypes[i]);
			}
		}

		TinyASMifier asmifier = createASMifier("code", 0);
		asmifier.methodLocals = methodLocals;
		asmifier.methodVisitParameter = this.methodVisitParameter;
		asmifier.methodParamTypes = this.methodParamTypes;
		asmifier.methodParamClazzes = this.methodParamClazzes;
		asmifier.methodIsStatic = this.methodIsStatic;
		text.add(asmifier.getText());
		text.add("});\n");
		return asmifier;
	}

	@Override
	public void visitClassEnd() {
///		text.add("classWriter.visitEnd();\n\n");
		text.add("return classWriter.end().toByteArray();\n");
		text.add("}\n");
		text.add("}\n");
	}

	// -----------------------------------------------------------------------------------------------
	// Modules
	// -----------------------------------------------------------------------------------------------

	@Override
	public void visitMainClass(final String mainClass) {
		stringBuilder.setLength(0);
		stringBuilder.append("moduleVisitor.visitMainClass(");
		appendConstant(mainClass);
		stringBuilder.append(");\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitPackage(final String packaze) {
		stringBuilder.setLength(0);
		stringBuilder.append("moduleVisitor.visitPackage(");
		appendConstant(packaze);
		stringBuilder.append(");\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitRequire(final String module, final int access, final String version) {
		stringBuilder.setLength(0);
		stringBuilder.append("moduleVisitor.visitRequire(");
		appendConstant(module);
		stringBuilder.append(", ");
		appendAccessFlags(access | ACCESS_MODULE);
		stringBuilder.append(", ");
		appendConstant(version);
		stringBuilder.append(");\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitExport(final String packaze, final int access, final String... modules) {
		stringBuilder.setLength(0);
		stringBuilder.append("moduleVisitor.visitExport(");
		appendConstant(packaze);
		stringBuilder.append(", ");
		appendAccessFlags(access | ACCESS_MODULE);
		if (modules != null && modules.length > 0) {
			stringBuilder.append(", new String[] {");
			for (int i = 0; i < modules.length; ++i) {
				stringBuilder.append(i == 0 ? " " : ", ");
				appendConstant(modules[i]);
			}
			stringBuilder.append(" }");
		}
		stringBuilder.append(");\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitOpen(final String packaze, final int access, final String... modules) {
		stringBuilder.setLength(0);
		stringBuilder.append("moduleVisitor.visitOpen(");
		appendConstant(packaze);
		stringBuilder.append(", ");
		appendAccessFlags(access | ACCESS_MODULE);
		if (modules != null && modules.length > 0) {
			stringBuilder.append(", new String[] {");
			for (int i = 0; i < modules.length; ++i) {
				stringBuilder.append(i == 0 ? " " : ", ");
				appendConstant(modules[i]);
			}
			stringBuilder.append(" }");
		}
		stringBuilder.append(");\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitUse(final String service) {
		stringBuilder.setLength(0);
		stringBuilder.append("moduleVisitor.visitUse(");
		appendConstant(service);
		stringBuilder.append(");\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitProvide(final String service, final String... providers) {
		stringBuilder.setLength(0);
		stringBuilder.append("moduleVisitor.visitProvide(");
		appendConstant(service);
		stringBuilder.append(",  new String[] {");
		for (int i = 0; i < providers.length; ++i) {
			stringBuilder.append(i == 0 ? " " : ", ");
			appendConstant(providers[i]);
		}
		stringBuilder.append(END_ARRAY);
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitModuleEnd() {
//		text.add("moduleVisitor.visitEnd();\n");
	}

	// -----------------------------------------------------------------------------------------------
	// Annotations
	// -----------------------------------------------------------------------------------------------

	@Override
	public void visit(final String name, final Object value) {
		stringBuilder.setLength(0);
		stringBuilder.append(ANNOTATION_VISITOR).append(id).append(".visit(");
		appendConstant(name);
		stringBuilder.append(", ");
		appendConstant(value);
		stringBuilder.append(");\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitEnum(final String name, final String descriptor, final String value) {
		stringBuilder.setLength(0);
		stringBuilder.append(ANNOTATION_VISITOR).append(id).append(".visitEnum(");
		appendConstant(name);
		stringBuilder.append(", ");
		appendConstant(descriptor);
		stringBuilder.append(", ");
		appendConstant(value);
		stringBuilder.append(");\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public TinyASMifier visitAnnotation(final String name, final String descriptor) {
		stringBuilder.setLength(0);
		stringBuilder.append("{\n").append("AnnotationVisitor annotationVisitor").append(id + 1).append(" = annotationVisitor");
		stringBuilder.append(id).append(".visitAnnotation(");
		appendConstant(name);
		stringBuilder.append(", ");
		appendConstant(descriptor);
		stringBuilder.append(");\n");
		text.add(stringBuilder.toString());
		TinyASMifier asmifier = createASMifier(ANNOTATION_VISITOR, id + 1);
		text.add(asmifier.getText());
		text.add("}\n");
		return asmifier;
	}

	@Override
	public TinyASMifier visitArray(final String name) {
		stringBuilder.setLength(0);
		stringBuilder.append("{\n");
		stringBuilder.append("AnnotationVisitor annotationVisitor").append(id + 1).append(" = annotationVisitor");
		stringBuilder.append(id).append(".visitArray(");
		appendConstant(name);
		stringBuilder.append(");\n");
		text.add(stringBuilder.toString());
		TinyASMifier asmifier = createASMifier(ANNOTATION_VISITOR, id + 1);
		text.add(asmifier.getText());
		text.add("}\n");
		return asmifier;
	}

	@Override
	public void visitAnnotationEnd() {
		stringBuilder.setLength(0);
		stringBuilder.append(ANNOTATION_VISITOR).append(id).append(VISIT_END);
		text.add(stringBuilder.toString());
	}

	// -----------------------------------------------------------------------------------------------
	// Fields
	// -----------------------------------------------------------------------------------------------

	@Override
	public TinyASMifier visitFieldAnnotation(final String descriptor, final boolean visible) {
		return visitAnnotation(descriptor, visible);
	}

	@Override
	public TinyASMifier visitFieldTypeAnnotation(final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
		return visitTypeAnnotation(typeRef, typePath, descriptor, visible);
	}

	@Override
	public void visitFieldAttribute(final Attribute attribute) {
		visitAttribute(attribute);
	}

	@Override
	public void visitFieldEnd() {
		stringBuilder.setLength(0);
		stringBuilder.append(visitname).append(VISIT_END);
		text.add(stringBuilder.toString());
	}

	// -----------------------------------------------------------------------------------------------
	// Methods
	// -----------------------------------------------------------------------------------------------

	int methodVisitParameter = 0;

	@Override
	public void visitParameter(final String parameterName, final int access) {
		Var var = methodLocals.stack.get(methodVisitParameter);
		var.name = parameterName;
		var.access = access;
		methodVisitParameter++;
		// TODO
//		stringBuilder.setLength(0);
//		stringBuilder.append(visitname).append(".visitParameter(");
//		appendString(stringBuilder, parameterName);
//		stringBuilder.append(", ");
//		appendAccessFlags(access);
//		text.add(stringBuilder.append(");\n").toString());
	}

	@Override
	public TinyASMifier visitAnnotationDefault() {
		stringBuilder.setLength(0);
		stringBuilder.append("{\n").append(ANNOTATION_VISITOR0).append(visitname).append(".visitAnnotationDefault();\n");
		text.add(stringBuilder.toString());
		TinyASMifier asmifier = createASMifier(ANNOTATION_VISITOR, 0);
		text.add(asmifier.getText());
		text.add("}\n");
		return asmifier;
	}

	@Override
	public TinyASMifier visitMethodAnnotation(final String descriptor, final boolean visible) {
		return visitAnnotation(descriptor, visible);
	}

	@Override
	public TinyASMifier visitMethodTypeAnnotation(final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
		return visitTypeAnnotation(typeRef, typePath, descriptor, visible);
	}

	@Override
	public TinyASMifier visitAnnotableParameterCount(final int parameterCount, final boolean visible) {
		stringBuilder.setLength(0);
		stringBuilder.append(visitname).append(".visitAnnotableParameterCount(").append(parameterCount).append(", ").append(visible).append(");\n");
		text.add(stringBuilder.toString());
		return this;
	}

	@Override
	public TinyASMifier visitParameterAnnotation(final int parameter, final String descriptor, final boolean visible) {
		stringBuilder.setLength(0);
		stringBuilder.append("{\n").append(ANNOTATION_VISITOR0).append(visitname).append(".visitParameterAnnotation(").append(parameter).append(", ");
		appendConstant(descriptor);
		stringBuilder.append(", ").append(visible).append(");\n");
		text.add(stringBuilder.toString());
		TinyASMifier asmifier = createASMifier(ANNOTATION_VISITOR, 0);
		text.add(asmifier.getText());
		text.add("}\n");
		return asmifier;
	}

	@Override
	public void visitMethodAttribute(final Attribute attribute) {
		visitAttribute(attribute);
	}

	@Override
	public void visitCode() {
		makeParameters();
	}

	boolean hasMakeParameters = false;

	private void makeParameters() {
		stringBuilder.setLength(0);
		hasMakeParameters = true;

		if (methodParamTypes.length > 0) {
			if (methodParamClazzes == null) {
				int offset = methodIsStatic ? 0 : 1;
				for (int i = 0; i < methodParamTypes.length; i++) {
					stringBuilder.setLength(0);
					Var var = methodLocals.stack.get(i + offset);
					stringBuilder.append("\n\t.parameter(");

					if (var.access != 0) {
						appendAccessFlags(var.access);
						stringBuilder.append(",");
					}

					stringBuilder.append("\"");
					text.add(stringBuilder.toString());
					stringBuilder.setLength(0);
					text.add(var);
					stringBuilder.append("\",");
					stringBuilder.append(clazzOf(methodParamTypes[i]));
					stringBuilder.append(")");
					text.add(stringBuilder.toString());
				}
			} else {
				int offset = methodIsStatic ? 0 : 1;
				for (int i = 0; i < methodParamClazzes.size(); i++) {
					stringBuilder.setLength(0);
					Var var = methodLocals.stack.get(i + offset);
					stringBuilder.append("\n\t.parameter(");

					if (var.access != 0) {
						appendAccessFlags(var.access);
						stringBuilder.append(",");
					}

					stringBuilder.append("\"");
					text.add(stringBuilder.toString());
					stringBuilder.setLength(0);
					text.add(var);
					stringBuilder.append("\",");
					stringBuilder.append(methodParamClazzes.get(i));
					stringBuilder.append(")");
					text.add(stringBuilder.toString());
				}
			}
		}

		stringBuilder.setLength(0);
		stringBuilder.append(".code(code -> {\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitFrame(final int type, final int nLocal, final Object[] local, final int nStack, final Object[] stack) {
//		stringBuilder.setLength(0);
//		switch (type) {
//		case Opcodes.F_NEW:
//		case Opcodes.F_FULL:
//			declareFrameTypes(nLocal, local);
//			declareFrameTypes(nStack, stack);
//			if (type == Opcodes.F_NEW) {
//				stringBuilder.append(visitname).append(".visitFrame(Opcodes.F_NEW, ");
//			} else {
//				stringBuilder.append(visitname).append(".visitFrame(Opcodes.F_FULL, ");
//			}
//			stringBuilder.append(nLocal).append(NEW_OBJECT_ARRAY);
//			appendFrameTypes(nLocal, local);
//			stringBuilder.append("}, ").append(nStack).append(NEW_OBJECT_ARRAY);
//			appendFrameTypes(nStack, stack);
//			stringBuilder.append('}');
//			break;
//		case Opcodes.F_APPEND:
//			declareFrameTypes(nLocal, local);
//			stringBuilder.append(visitname).append(".visitFrame(Opcodes.F_APPEND,").append(nLocal).append(NEW_OBJECT_ARRAY);
//			appendFrameTypes(nLocal, local);
//			stringBuilder.append("}, 0, null");
//			break;
//		case Opcodes.F_CHOP:
//			stringBuilder.append(visitname).append(".visitFrame(Opcodes.F_CHOP,").append(nLocal).append(", null, 0, null");
//			break;
//		case Opcodes.F_SAME:
//			stringBuilder.append(visitname).append(".visitFrame(Opcodes.F_SAME, 0, null, 0, null");
//			break;
//		case Opcodes.F_SAME1:
//			declareFrameTypes(1, stack);
//			stringBuilder.append(visitname).append(".visitFrame(Opcodes.F_SAME1, 0, null, 1, new Object[] {");
//			appendFrameTypes(1, stack);
//			stringBuilder.append('}');
//			break;
//		default:
//			throw new IllegalArgumentException();
//		}
//		stringBuilder.append(");\n");
//		text.add(stringBuilder.toString());
	}

	@Override
	public void visitInsn(final int opcode) {
		stringBuilder.setLength(0);
		switch (opcode) {

		case NOP: // 0; // visitInsn
			stringBuilder.append(visitname).append(".NOP();\n");
			break;
		case ACONST_NULL: // 1; // -
			stringBuilder.append(visitname).append(".LOADConstNULL();\n");
			break;
		case ICONST_M1: // 2; // -
			stringBuilder.append(visitname).append(".LOADConst(-1);\n");
			break;

		case ICONST_0: // 3; // -
			stringBuilder.append(visitname).append(".LOADConst(0);\n");
			break;
		case ICONST_1: // 4; // -
			stringBuilder.append(visitname).append(".LOADConst(1);\n");
			break;
		case ICONST_2: // 5; // -
			stringBuilder.append(visitname).append(".LOADConst(2);\n");
			break;
		case ICONST_3: // 6; // -
			stringBuilder.append(visitname).append(".LOADConst(3);\n");
			break;
		case ICONST_4: // 7; // -
			stringBuilder.append(visitname).append(".LOADConst(4);\n");
			break;
		case ICONST_5: // 8; // -
			stringBuilder.append(visitname).append(".LOADConst(5);\n");
			break;
		case LCONST_0: // 9; // -
			stringBuilder.append(visitname).append(".LOADConst(0L);\n");
			break;
		case LCONST_1: // 10; // -
			stringBuilder.append(visitname).append(".LOADConst(1L);\n");
			break;
		case FCONST_0: // 11; // -
			stringBuilder.append(visitname).append(".LOADConst(0F);\n");
			break;
		case FCONST_1: // 12; // -
			stringBuilder.append(visitname).append(".LOADConst(1F);\n");
			break;
		case FCONST_2: // 13; // -
			stringBuilder.append(visitname).append(".LOADConst(2F);\n");
			break;
		case DCONST_0: // 14; // -
			stringBuilder.append(visitname).append(".LOADConst(0D);\n");
			break;
		case DCONST_1: // 15; // -
			stringBuilder.append(visitname).append(".LOADConst(1D);\n");
			break;
		case LDC: // 18; // visitLdcInsn

		case IALOAD: // 46; // visitInsn
		case LALOAD: // 47; // -
		case FALOAD: // 48; // -
		case DALOAD: // 49; // -
		case AALOAD: // 50; // -
		case BALOAD: // 51; // -
		case CALOAD: // 52; // -
		case SALOAD: // 53; // -
			stringBuilder.append(visitname).append(".ARRAYLOAD();\n");
//			stringBuilder.append(visitname).append(".visitInsn(").append(OPCODES[opcode]).append(");\n");
			break;

		case IASTORE: // 79; // visitInsn
		case LASTORE: // 80; // -
		case FASTORE: // 81; // -
		case DASTORE: // 82; // -
		case AASTORE: // 83; // -
		case BASTORE: // 84; // -
		case CASTORE: // 85; // -
		case SASTORE: // 86; // -
			stringBuilder.append(visitname).append(".ARRAYSTORE();\n");
//			stringBuilder.append(visitname).append(".visitInsn(").append(OPCODES[opcode]).append(");\n");
			break;
		case POP: // 87; // -
		case POP2: // 88; // -
			stringBuilder.append(visitname).append(".POP();\n");
			break;
		case DUP: // 89; // -
		case DUP_X1: // 90; // -
		case DUP_X2: // 91; // -
		case DUP2: // 92; // -
		case DUP2_X1: // 93; // -
		case DUP2_X2: // 94; // -
			stringBuilder.append(visitname).append(".DUP();\n");
			break;
		case SWAP: // 95; // -
			stringBuilder.append(visitname).append(".SWAP();\n");
			break;

		case IADD: // 96; // -
		case LADD: // 97; // -
		case FADD: // 98; // -
		case DADD: // 99; // -
			stringBuilder.append(visitname).append(".ADD();\n");
			break;
		case ISUB: // 100; // -
		case LSUB: // 101; // -
		case FSUB: // 102; // -
		case DSUB: // 103; // -
			stringBuilder.append(visitname).append(".SUB();\n");
			break;
		case IMUL: // 104; // -
		case LMUL: // 105; // -
		case FMUL: // 106; // -
		case DMUL: // 107; // -
			stringBuilder.append(visitname).append(".MUL();\n");
			break;
		case IDIV: // 108; // -
		case LDIV: // 109; // -
		case FDIV: // 110; // -
		case DDIV: // 111; // -
			stringBuilder.append(visitname).append(".DIV();\n");
			break;
		case IREM: // 112; // -
		case LREM: // 113; // -
		case FREM: // 114; // -
		case DREM: // 115; // -
			stringBuilder.append(visitname).append(".REM();\n");
			break;
		case INEG: // 116; // -
		case LNEG: // 117; // -
		case FNEG: // 118; // -
		case DNEG: // 119; // -
			stringBuilder.append(visitname).append(".NEG();\n");
			break;
		case ISHL: // 120; // -
		case LSHL: // 121; // -
			stringBuilder.append(visitname).append(".SHL();\n");
			break;
		case ISHR: // 122; // -
		case LSHR: // 123; // -
		case IUSHR: // 124; // -
		case LUSHR: // 125; // -
			stringBuilder.append(visitname).append(".SHR();\n");
			break;
		case IAND: // 126; // -
		case LAND: // 127; // -
			stringBuilder.append(visitname).append(".AND();\n");
			break;
		case IOR: // 128; // -
		case LOR: // 129; // -
			stringBuilder.append(visitname).append(".OR();\n");
			break;
		case IXOR: // 130; // -
		case LXOR: // 131; // -
			stringBuilder.append(visitname).append(".XOR();\n");
			break;
		case I2L: // 133; // visitInsn
			stringBuilder.append(visitname).append(".CONVERTTO(long.class);\n");
			break;
		case I2F: // 134; // -
			stringBuilder.append(visitname).append(".CONVERTTO(float.class);\n");
			break;
		case I2D: // 135; // -
			stringBuilder.append(visitname).append(".CONVERTTO(double.class);\n");
			break;
		case L2I: // 136; // -
			stringBuilder.append(visitname).append(".CONVERTTO(int.class);\n");
			break;
		case L2F: // 137; // -
			stringBuilder.append(visitname).append(".CONVERTTO(float.class);\n");
			break;
		case L2D: // 138; // -
			stringBuilder.append(visitname).append(".CONVERTTO(double.class);\n");
			break;
		case F2I: // 139; // -
			stringBuilder.append(visitname).append(".CONVERTTO(int.class);\n");
			break;
		case F2L: // 140; // -
			stringBuilder.append(visitname).append(".CONVERTTO(long.class);\n");
			break;
		case F2D: // 141; // -
			stringBuilder.append(visitname).append(".CONVERTTO(double.class);\n");
			break;
		case D2I: // 142; // -
			stringBuilder.append(visitname).append(".CONVERTTO(int.class);\n");
			break;
		case D2L: // 143; // -
			stringBuilder.append(visitname).append(".CONVERTTO(long.class);\n");
			break;
		case D2F: // 144; // -
			stringBuilder.append(visitname).append(".CONVERTTO(float.class);\n");
			break;
		case I2B: // 145; // -
			stringBuilder.append(visitname).append(".CONVERTTO(byte.class);\n");
			break;
		case I2C: // 146; // -
			stringBuilder.append(visitname).append(".CONVERTTO(char.class);\n");
			break;
		case I2S: // 147; // -
			stringBuilder.append(visitname).append(".CONVERTTO(short.class);\n");
			break;

		case LCMP: // 148; // -
			stringBuilder.append(visitname).append(".LCMP();\n");
			break;
		case FCMPL: // 149; // -
			stringBuilder.append(visitname).append(".CMPL();\n");
			break;
		case FCMPG: // 150; // -
			stringBuilder.append(visitname).append(".CMPG();\n");
			break;
		case DCMPL: // 151; // -
			stringBuilder.append(visitname).append(".CMPL();\n");
			break;
		case DCMPG: // 152; // -
			stringBuilder.append(visitname).append(".CMPG();\n");
			break;

		case IRETURN: // 172; // visitInsn
		case LRETURN: // 173; // -
		case FRETURN: // 174; // -
		case DRETURN: // 175; // -
		case ARETURN: // 176; // -
			stringBuilder.append(visitname).append(".RETURNTop();\n");
			break;
		case RETURN: // 177; // -
			stringBuilder.append(visitname).append(".RETURN();\n");
			break;

		case ARRAYLENGTH: // 190; // visitInsn
			stringBuilder.append(visitname).append(".ARRAYLENGTH();\n");
			break;
		case ATHROW: // 191; // -
		case MONITORENTER: // 194; // visitInsn
		case MONITOREXIT: // 195; // -
			stringBuilder.append(visitname).append(".visitInsn(").append(OPCODES[opcode]).append(");\n");
			break;

		default:
			stringBuilder.append(visitname).append(".visitInsn(").append(OPCODES[opcode]).append(");\n");
			break;
		}
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitIntInsn(final int opcode, final int operand) {
		stringBuilder.setLength(0);

		switch (opcode) {
		case BIPUSH: // 16; // visitIntInsn
			stringBuilder.append(visitname).append(".LOADConst(").append(operand).append(");\n");
			break;
		case SIPUSH: // 17; // -
			stringBuilder.append(visitname).append(".LOADConst(").append(operand).append(");\n");
			break;

		case NEWARRAY: // 188; // visitIntInsn
			switch (operand) {
			case T_BOOLEAN:// 4;
				stringBuilder.append(visitname).append(".NEWARRAY(").append("boolean.class").append(");\n");
				break;
			case T_CHAR:// 5;
				stringBuilder.append(visitname).append(".NEWARRAY(").append("char.class").append(");\n");
				break;
			case T_FLOAT:// 6;
				stringBuilder.append(visitname).append(".NEWARRAY(").append("float.class").append(");\n");
				break;
			case T_DOUBLE:// 7;
				stringBuilder.append(visitname).append(".NEWARRAY(").append("double.class").append(");\n");
				break;
			case T_BYTE:// 8;
				stringBuilder.append(visitname).append(".NEWARRAY(").append("byte.class").append(");\n");
				break;
			case T_SHORT:// 9;
				stringBuilder.append(visitname).append(".NEWARRAY(").append("short.class").append(");\n");
				break;
			case T_INT:// 10;
				stringBuilder.append(visitname).append(".NEWARRAY(").append("int.class").append(");\n");
				break;
			case T_LONG:// 11;
				stringBuilder.append(visitname).append(".NEWARRAY(").append("long.class").append(");\n");
				break;

			default:
				stringBuilder.append(visitname).append(".NEWARRAY(").append(TYPES[operand]).append(");\n");
				break;
			}
			break;
		default:
			stringBuilder.append(visitname).append(".visitIntInsn(").append(OPCODES[opcode]).append(", ")
					.append(opcode == Opcodes.NEWARRAY ? TYPES[operand] : Integer.toString(operand)).append(");\n");
		}
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitVarInsn(final int opcode, final int var) {
		stringBuilder.setLength(0);
//		stringBuilder.append(name).append(".visitVarInsn(").append(OPCODES[opcode]).append(", ").append(var)
//				.append(");\n");
		Var localVar = null;
		if (ILOAD <= opcode && opcode <= ALOAD) {
			switch (opcode) {
			case ILOAD: // 21; // visitVarInsn
				localVar = methodLocals.accessLoad(var, 1);
				break;
			case LLOAD: // 22; // -
				localVar = methodLocals.accessLoad(var, 2);
				break;
			case FLOAD: // 23; // -
				localVar = methodLocals.accessLoad(var, 2);
				break;
			case DLOAD: // 24; // -
				localVar = methodLocals.accessLoad(var, 2);
				break;
			case ALOAD: // 25; // -
				localVar = methodLocals.accessLoad(var, 1);
				break;
			}
//			if (localVar.count == 1) {
//				text.add(stringBuilder.toString());
//				text.add(new DefineVar(localVar));
//				stringBuilder.setLength(0);
//			}

			stringBuilder.append(visitname).append(".LOAD(\"");
			text.add(stringBuilder.toString());
			text.add(localVar);
			stringBuilder.setLength(0);

			stringBuilder.append("\");\n");
			text.add(stringBuilder.toString());
		} else if (ISTORE <= opcode && opcode <= ASTORE) {
			switch (opcode) {
			case ISTORE: // 54; // visitVarInsn
				localVar = methodLocals.accessStore(var, 1);
				break;
			case LSTORE: // 55; // -
				localVar = methodLocals.accessStore(var, 2);
				break;
			case FSTORE: // 56; // -
				localVar = methodLocals.accessStore(var, 2);
				break;
			case DSTORE: // 57; // -
				localVar = methodLocals.accessStore(var, 2);
				break;
			case ASTORE: // 58; // -
				localVar = methodLocals.accessStore(var, 1);
				break;
			}
//			if (localVar.count == 1) {
//				text.add(stringBuilder.toString());
//				text.add(new DefineVar(localVar));
//				stringBuilder.setLength(0);
//			}
			stringBuilder.append(visitname).append(".STORE(\"");

			text.add(stringBuilder.toString());
			text.add(localVar);
			stringBuilder.setLength(0);

			stringBuilder.append("\"");
			if (localVar.count == 1) {
				text.add(stringBuilder.toString());
				text.add(new VarType(localVar));
				stringBuilder.setLength(0);
				stringBuilder.append("");
			}
			stringBuilder.append(");\n");
			text.add(stringBuilder.toString());
		} else if (opcode == RET) {// 169; // visitVarInsn

			stringBuilder.append(visitname).append(".visitVarInsn(").append(OPCODES[opcode]).append(", ").append(var).append(");\n");

		}

	}

//	class DefineVar {
//		Var var;
//
//		public DefineVar(Var var) {
//			this.var = var;
//		}
//
//		@Override
//		public String toString() {
//			return visitname + ".define(\"" + var.name + "\"," + var.type.getClassName() + ".class);\n";
//		}
//	}
	class VarType {
		Var var;

		public VarType(Var var) {
			this.var = var;
		}

		@Override
		public String toString() {
			return var.type != null ? "," + (var.getS() == null ? clazzOf(var.type) : var.getS()) : "";
		}
	}

	@Override
	public void visitTypeInsn(final int opcode, final String type) {
		switch (opcode) {

		case NEW: // 187; // visitTypeInsn
			stringBuilder.setLength(0);
			stringBuilder.append(visitname).append(".NEW(");
			stringBuilder.append(clazzOf(Type.getObjectType(type)));
			stringBuilder.append(");\n");
			text.add(stringBuilder.toString());
			break;
		case ANEWARRAY: // 189; // visitTypeInsn
			stringBuilder.setLength(0);
			stringBuilder.append(visitname).append(".NEWARRAY(");
			stringBuilder.append(clazzOf(Type.getObjectType(type)));
			stringBuilder.append(");\n");
			text.add(stringBuilder.toString());
			break;
		case CHECKCAST: // 192; // visitTypeInsn
			stringBuilder.setLength(0);
			stringBuilder.append(visitname).append(".CHECKCAST(");
			stringBuilder.append(clazzOf(Type.getObjectType(type)));
			stringBuilder.append(");\n");
			text.add(stringBuilder.toString());
			break;
		case INSTANCEOF: // 193; // -
			stringBuilder.setLength(0);
			stringBuilder.append(visitname).append(".INSTANCEOF(");
			stringBuilder.append(clazzOf(Type.getObjectType(type)));
			stringBuilder.append(");\n");
			text.add(stringBuilder.toString());
			break;

		default:
			stringBuilder.setLength(0);
			stringBuilder.append(visitname).append(".visitTypeInsn(").append(OPCODES[opcode]).append(", ");
			appendConstant(type);
			stringBuilder.append(");\n");
			text.add(stringBuilder.toString());
			break;
		}
	}

	@Override
	public void visitFieldInsn(final int opcode, final String owner, final String name, final String descriptor) {

		stringBuilder.setLength(0);
		switch (opcode) {

		case GETSTATIC: // 178; // visitFieldInsn

//			code.GETSTATIC(System.class,"out",PrintStream.class);
			stringBuilder.setLength(0);
			stringBuilder.append(this.visitname).append(".GETSTATIC(");
			stringBuilder.append(clazzOf(Type.getObjectType(owner)));
			stringBuilder.append(", ");
			appendConstant(name);
			stringBuilder.append(", ");
			stringBuilder.append(clazzOf(Type.getType(descriptor)));
			stringBuilder.append(");\n");
			text.add(stringBuilder.toString());
			break;
		case PUTSTATIC: // 179; // -

//			code.GETSTATIC(System.class,"out",PrintStream.class);
			
			stringBuilder.setLength(0);
			stringBuilder.append(this.visitname).append(".PUTSTATIC(");
			stringBuilder.append(clazzOf(Type.getObjectType(owner)));
			stringBuilder.append(", ");
			appendConstant(name);
			stringBuilder.append(", ");
			stringBuilder.append(clazzOf(Type.getType(descriptor)));
			stringBuilder.append(");\n");
			text.add(stringBuilder.toString());
			break;

		case GETFIELD: // 180; // -
//			stringBuilder.setLength(0);
//			stringBuilder.append("//");
//			stringBuilder.append(this.visitname).append(".visitFieldInsn(").append(OPCODES[opcode]).append(", ");
////			appendConstant(owner);
////			stringBuilder.append(", ");
//			appendConstant(name);
//			stringBuilder.append(", ");
//			appendConstant(descriptor);
//			stringBuilder.append(");\n");
//			text.add(stringBuilder.toString());

			stringBuilder.setLength(0);
			stringBuilder.append(this.visitname).append(".GETFIELD(");
			appendConstant(name);
			stringBuilder.append(", ");
			stringBuilder.append(clazzOf(Type.getType(descriptor)));
			stringBuilder.append(");\n");
			text.add(stringBuilder.toString());
			break;
		case PUTFIELD: // 181; // -
//			stringBuilder.setLength(0);
//			stringBuilder.append("//");
//			stringBuilder.append(this.visitname).append(".visitFieldInsn(").append(OPCODES[opcode]).append(", ");
////			appendConstant(owner);
////			stringBuilder.append(", ");
//			appendConstant(name);
//			stringBuilder.append(", ");
//			appendConstant(descriptor);
//			stringBuilder.append(");\n");
//			text.add(stringBuilder.toString());

			stringBuilder.setLength(0);
			stringBuilder.append(this.visitname).append(".PUTFIELD(");
			appendConstant(name);
			stringBuilder.append(", ");
			stringBuilder.append(clazzOf(Type.getType(descriptor)));
			stringBuilder.append(");\n");
			text.add(stringBuilder.toString());
			break;

		default:

			stringBuilder.setLength(0);
			stringBuilder.append(this.visitname).append(".visitFieldInsn(").append(OPCODES[opcode]).append(", ");
//			appendConstant(owner);
//			stringBuilder.append(", ");
			appendConstant(name);
			stringBuilder.append(", ");
			appendConstant(descriptor);
			stringBuilder.append(");\n");
			text.add(stringBuilder.toString());
			break;
		}
	}

	/** @deprecated */
	@Deprecated
	@Override
	public void visitMethodInsn(final int opcode, final String owner, final String name, final String descriptor) {
		if (api >= Opcodes.ASM5) {
			super.visitMethodInsn(opcode, owner, name, descriptor);
			return;
		}
		doVisitMethodInsn(opcode, owner, name, descriptor, opcode == Opcodes.INVOKEINTERFACE);
	}

	@Override
	public void visitMethodInsn(final int opcode, final String owner, final String name, final String descriptor, final boolean isInterface) {
		if (api < Opcodes.ASM5) {
			super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
			return;
		}
		doVisitMethodInsn(opcode, owner, name, descriptor, isInterface);
	}

	private void doVisitMethodInsn(final int opcode, final String owner, final String name, final String descriptor, final boolean isInterface) {
//		stringBuilder.setLength(0);
//		stringBuilder.append(this.name).append(".visitMethodInsn(").append(OPCODES[opcode]).append(", ");
//		appendConstant(owner);
//		stringBuilder.append(", ");
//		appendConstant(name);
//		stringBuilder.append(", ");
//		appendConstant(descriptor);
//		stringBuilder.append(", ");
//		stringBuilder.append(isInterface ? "true" : "false");
//		stringBuilder.append(");\n");
//		text.add(stringBuilder.toString());

		stringBuilder.setLength(0);
		switch (opcode) {
		case INVOKEVIRTUAL: // 182; // visitMethodInsn
			stringBuilder.append(this.visitname).append(".VIRTUAL(");
			break;
		case INVOKESPECIAL: // 183; // -
			stringBuilder.append(this.visitname).append(".SPECIAL(");
			break;
		case INVOKESTATIC: // 184; // -
			stringBuilder.append(this.visitname).append(".STATIC(");
			break;
		case INVOKEINTERFACE: // 185; // -
			stringBuilder.append(this.visitname).append(".INTERFACE(");
			break;
		}

//		stringBuilder.append(this.name).append(".visitMethodInsn(").append(OPCODES[opcode]).append(", ");
		stringBuilder.append(owner.replace('/', '.') + ".class");
		stringBuilder.append(", ");
		appendConstant(name);
		stringBuilder.append(")");
		Type returnType = Type.getReturnType(descriptor);
		if (returnType != Type.VOID_TYPE) {
			stringBuilder.append("\n\t\t.reTurn(");
			stringBuilder.append(clazzOf(returnType));
			stringBuilder.append(")");
		}

		Type[] argumentTypes = Type.getArgumentTypes(descriptor);
		for (int i = 0; i < argumentTypes.length; i++) {
			stringBuilder.append("\n\t\t.parameter(");
			stringBuilder.append(clazzOf(argumentTypes[i]));
			stringBuilder.append(")");
		}
//		appendConstant(descriptor);
//		stringBuilder.append(", ");
//		stringBuilder.append(isInterface ? "true" : "false");
		stringBuilder.append(".INVOKE();\n");
		text.add(stringBuilder.toString());

//		code.SPECIAL(java.lang.Object.class, "<init>").INVOKE();
	}

	@Override
	public void visitInvokeDynamicInsn(final String name, final String descriptor, final Handle bootstrapMethodHandle,
			final Object... bootstrapMethodArguments) {
		// case INVOKEDYNAMIC: // 186; // visitInvokeDynamicInsn
		stringBuilder.setLength(0);
		stringBuilder.append(this.visitname).append(".visitInvokeDynamicInsn(");
		appendConstant(name);
		stringBuilder.append(", ");
		appendConstant(descriptor);
		stringBuilder.append(", ");
		appendConstant(bootstrapMethodHandle);
		stringBuilder.append(", new Object[]{");
		for (int i = 0; i < bootstrapMethodArguments.length; ++i) {
			appendConstant(bootstrapMethodArguments[i]);
			if (i != bootstrapMethodArguments.length - 1) {
				stringBuilder.append(", ");
			}
		}
		stringBuilder.append("});\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitJumpInsn(final int opcode, final Label label) {
		stringBuilder.setLength(0);
		declareLabel(label, OPCODES[opcode]);
		switch (opcode) {
		case IFEQ: // 153; // visitJumpInsn
			stringBuilder.append(visitname).append(".IFEQ(");
			break;
		case IFNE: // 154; // -
			stringBuilder.append(visitname).append(".IFNE(");
			break;
		case IFLT: // 155; // -
			stringBuilder.append(visitname).append(".IFLT(");
			break;
		case IFGE: // 156; // -
			stringBuilder.append(visitname).append(".IFGE(");
			break;
		case IFGT: // 157; // -
			stringBuilder.append(visitname).append(".IFGT(");
			break;
		case IFLE: // 158; // -
			stringBuilder.append(visitname).append(".IFLE(");
			break;
		case IF_ICMPEQ: // 159; // -
			stringBuilder.append(visitname).append(".IF_ICMPEQ(");
			break;
		case IF_ICMPNE: // 160; // -
			stringBuilder.append(visitname).append(".IF_ICMPNE(");
			break;
		case IF_ICMPLT: // 161; // -
			stringBuilder.append(visitname).append(".IF_ICMPLT(");
			break;
		case IF_ICMPGE: // 162; // -
			stringBuilder.append(visitname).append(".IF_ICMPGE(");
			break;
		case IF_ICMPGT: // 163; // -
			stringBuilder.append(visitname).append(".IF_ICMPGT(");
			break;
		case IF_ICMPLE: // 164; // -
			stringBuilder.append(visitname).append(".IF_ICMPLE(");
			break;
		case IF_ACMPEQ: // 165; // -
			stringBuilder.append(visitname).append(".IF_ACMPEQ(");
			break;
		case IF_ACMPNE: // 166; // -
			stringBuilder.append(visitname).append(".IF_ACMPNE(");
			break;
		case GOTO: // 167; // -
			stringBuilder.append(visitname).append(".GOTO(");
			break;
		case JSR: // 168; // -
			stringBuilder.append(visitname).append(".JSR(");
			break;
		case IFNULL: // 198; // visitJumpInsn
			stringBuilder.append(visitname).append(".IFNULL(");
			break;
		case IFNONNULL: // 199; // -
			stringBuilder.append(visitname).append(".IFNONNULL(");
			break;

		default:
			stringBuilder.append(visitname).append(".visitJumpInsn(");
			break;
		}
		appendLabel(label);
		stringBuilder.append(");\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitLabel(final Label label) {
		if (labelNames != null && labelNames.containsKey(label)) {
			stringBuilder.setLength(0);
//			declareLabel(label);
			stringBuilder.append("\n");
			stringBuilder.append(visitname).append(".visitLabel(");
			appendLabel(label);
			stringBuilder.append(");\n");
			text.add(stringBuilder.toString());
		}
	}

	@Override
	public void visitLdcInsn(final Object value) {
		stringBuilder.setLength(0);
		stringBuilder.append(visitname).append(".LOADConst(");
		appendConstant(value);
		stringBuilder.append(");\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitIincInsn(final int var, final int increment) {
		Var localVar = null;
		localVar = methodLocals.accessLoad(var, 1);

		stringBuilder.setLength(0);
		stringBuilder.append(visitname).append(".IINC(\"");
		text.add(stringBuilder.toString());
		text.add(localVar);
		stringBuilder.setLength(0);
		stringBuilder.append("\", ").append(increment).append(");\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitTableSwitchInsn(final int min, final int max, final Label dflt, final Label... labels) {
//	case TABLESWITCH: // 170; // visiTableSwitchInsn
		stringBuilder.setLength(0);
		for (int i = 0; i < labels.length; ++i) {
			declareLabel(labels[i]);
		}
		declareLabel(dflt);

		stringBuilder.append(visitname).append(".visitTableSwitchInsn(").append(min).append(", ").append(max).append(", ");
		appendLabel(dflt);
		stringBuilder.append(", new Label[] {");
		for (int i = 0; i < labels.length; ++i) {
			stringBuilder.append(i == 0 ? " " : ", ");
			appendLabel(labels[i]);
		}
		stringBuilder.append(END_ARRAY);
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitLookupSwitchInsn(final Label dflt, final int[] keys, final Label[] labels) {
		// case LOOKUPSWITCH: // 171; // visitLookupSwitch
		stringBuilder.setLength(0);
		for (int i = 0; i < labels.length; ++i) {
			declareLabel(labels[i]);
		}
		declareLabel(dflt);

		stringBuilder.append(visitname).append(".visitLookupSwitchInsn(");
		appendLabel(dflt);
		stringBuilder.append(", new int[] {");
		for (int i = 0; i < keys.length; ++i) {
			stringBuilder.append(i == 0 ? " " : ", ").append(keys[i]);
		}
		stringBuilder.append(" }, new Label[] {");
		for (int i = 0; i < labels.length; ++i) {
			stringBuilder.append(i == 0 ? " " : ", ");
			appendLabel(labels[i]);
		}
		stringBuilder.append(END_ARRAY);
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitMultiANewArrayInsn(final String descriptor, final int numDimensions) {
		// case MULTIANEWARRAY: //197; // visitMultiANewArrayInsn
		stringBuilder.setLength(0);
		stringBuilder.append(visitname).append(".visitMultiANewArrayInsn(");
		appendConstant(descriptor);
		stringBuilder.append(", ").append(numDimensions).append(");\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public TinyASMifier visitInsnAnnotation(final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
		return visitTypeAnnotation("visitInsnAnnotation", typeRef, typePath, descriptor, visible);
	}

	@Override
	public void visitTryCatchBlock(final Label start, final Label end, final Label handler, final String type) {
		stringBuilder.setLength(0);
		declareLabel(start);
		declareLabel(end);
		declareLabel(handler);
		stringBuilder.append(visitname).append(".visitTryCatchBlock(");
		appendLabel(start);
		stringBuilder.append(", ");
		appendLabel(end);
		stringBuilder.append(", ");
		appendLabel(handler);
		stringBuilder.append(", ");
		appendConstant(type);
		stringBuilder.append(");\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public TinyASMifier visitTryCatchAnnotation(final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
		return visitTypeAnnotation("visitTryCatchAnnotation", typeRef, typePath, descriptor, visible);
	}

	@Override
	public void visitLocalVariable(final String name, final String descriptor, final String signature, final Label start, final Label end, final int index) {
		if (index < methodLocals.size()) {
			TinyLocalsStack.Var var = methodLocals.getByLocal(index);
			var.name = name;
			if (signature == null) {
				var.type = Type.getType(descriptor);
			} else {
				SignatureReader sr = new SignatureReader(signature);
				ClassSignature signatureVistor = new ClassSignature(Opcodes.ASM5);
				sr.accept(signatureVistor);
				logger.debug("localvariable({} {}", name, signatureVistor.superClass);
				var.setS(signatureVistor.superClass.toString());
			}
		}

//		System.out.println(name + " " + index);
//		stringBuilder.setLength(0);
//		stringBuilder.append(this.name).append(".visitLocalVariable(");
//		appendConstant(name);
//		stringBuilder.append(", ");
//		appendConstant(descriptor);
//		stringBuilder.append(", ");
//		appendConstant(signature);
//		stringBuilder.append(", ");
//		appendLabel(start);
//		stringBuilder.append(", ");
//		appendLabel(end);
//		stringBuilder.append(", ").append(index).append(");\n");
//		text.add(stringBuilder.toString());
	}

	@Override
	public Printer visitLocalVariableAnnotation(final int typeRef, final TypePath typePath, final Label[] start, final Label[] end, final int[] index,
			final String descriptor, final boolean visible) {
		stringBuilder.setLength(0);
		stringBuilder.append("{\n").append(ANNOTATION_VISITOR0).append(visitname).append(".visitLocalVariableAnnotation(").append(typeRef);
		if (typePath == null) {
			stringBuilder.append(", null, ");
		} else {
			stringBuilder.append(", TypePath.fromString(\"").append(typePath).append("\"), ");
		}
		stringBuilder.append("new Label[] {");
		for (int i = 0; i < start.length; ++i) {
			stringBuilder.append(i == 0 ? " " : ", ");
			appendLabel(start[i]);
		}
		stringBuilder.append(" }, new Label[] {");
		for (int i = 0; i < end.length; ++i) {
			stringBuilder.append(i == 0 ? " " : ", ");
			appendLabel(end[i]);
		}
		stringBuilder.append(" }, new int[] {");
		for (int i = 0; i < index.length; ++i) {
			stringBuilder.append(i == 0 ? " " : ", ").append(index[i]);
		}
		stringBuilder.append(" }, ");
		appendConstant(descriptor);
		stringBuilder.append(", ").append(visible).append(");\n");
		text.add(stringBuilder.toString());
		TinyASMifier asmifier = createASMifier(ANNOTATION_VISITOR, 0);
		text.add(asmifier.getText());
		text.add("}\n");
		return asmifier;
	}

	@Override
	public void visitLineNumber(final int line, final Label start) {
		stringBuilder.setLength(0);
		stringBuilder.append("\n");
		stringBuilder.append(visitname).append(".LINE(").append(line);
		stringBuilder.append(");\n");
		text.add(stringBuilder.toString());
	}

	@Override
	public void visitMaxs(final int maxStack, final int maxLocals) {
//		stringBuilder.setLength(0);
//		stringBuilder.append(name).append(".visitMaxs(").append(maxStack).append(", ").append(maxLocals).append(");\n");
//		text.add(stringBuilder.toString());
	}

	@Override
	public void visitMethodEnd() {
		if (!hasMakeParameters) {
			this.makeParameters();
		}
//		stringBuilder.setLength(0);
////		stringBuilder.append(visitname).append(VISIT_END);
//		text.add(stringBuilder.toString());
	}

	// -----------------------------------------------------------------------------------------------
	// Common methods
	// -----------------------------------------------------------------------------------------------

	/**
	 * Visits a class, field or method annotation.
	 *
	 * @param descriptor the class descriptor of the annotation class.
	 * @param visible    <tt>true</tt> if the annotation is visible at runtime.
	 * @return a new {@link TinyASMifier} to visit the annotation values.
	 */
	public TinyASMifier visitAnnotation(final String descriptor, final boolean visible) {
		stringBuilder.setLength(0);
		stringBuilder.append("{\n").append(ANNOTATION_VISITOR0).append(visitname).append(".visitAnnotation(");
		appendConstant(descriptor);
		stringBuilder.append(", ").append(visible).append(");\n");
		text.add(stringBuilder.toString());
		TinyASMifier asmifier = createASMifier(ANNOTATION_VISITOR, 0);
		text.add(asmifier.getText());
		text.add("}\n");
		return asmifier;
	}

	/**
	 * Visits a class, field or method type annotation.
	 *
	 * @param typeRef    a reference to the annotated type. The sort of this type
	 *                   reference must be
	 *                   {@link org.objectweb.asm.TypeReference#FIELD}. See
	 *                   {@link org.objectweb.asm.TypeReference}.
	 * @param typePath   the path to the annotated type argument, wildcard bound,
	 *                   array element type, or static inner type within 'typeRef'.
	 *                   May be <tt>null</tt> if the annotation targets 'typeRef' as
	 *                   a whole.
	 * @param descriptor the class descriptor of the annotation class.
	 * @param visible    <tt>true</tt> if the annotation is visible at runtime.
	 * @return a new {@link TinyASMifier} to visit the annotation values.
	 */
	public TinyASMifier visitTypeAnnotation(final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
		return visitTypeAnnotation("visitTypeAnnotation", typeRef, typePath, descriptor, visible);
	}

	/**
	 * Visits a class, field, method, instruction or try catch block type
	 * annotation.
	 *
	 * @param method     the name of the visit method for this type of annotation.
	 * @param typeRef    a reference to the annotated type. The sort of this type
	 *                   reference must be
	 *                   {@link org.objectweb.asm.TypeReference#FIELD}. See
	 *                   {@link org.objectweb.asm.TypeReference}.
	 * @param typePath   the path to the annotated type argument, wildcard bound,
	 *                   array element type, or static inner type within 'typeRef'.
	 *                   May be <tt>null</tt> if the annotation targets 'typeRef' as
	 *                   a whole.
	 * @param descriptor the class descriptor of the annotation class.
	 * @param visible    <tt>true</tt> if the annotation is visible at runtime.
	 * @return a new {@link TinyASMifier} to visit the annotation values.
	 */
	public TinyASMifier visitTypeAnnotation(final String method, final int typeRef, final TypePath typePath, final String descriptor, final boolean visible) {
		stringBuilder.setLength(0);
		stringBuilder.append("{\n").append(ANNOTATION_VISITOR0).append(visitname).append(".").append(method).append("(").append(typeRef);
		if (typePath == null) {
			stringBuilder.append(", null, ");
		} else {
			stringBuilder.append(", TypePath.fromString(\"").append(typePath).append("\"), ");
		}
		appendConstant(descriptor);
		stringBuilder.append(", ").append(visible).append(");\n");
		text.add(stringBuilder.toString());
		TinyASMifier asmifier = createASMifier(ANNOTATION_VISITOR, 0);
		text.add(asmifier.getText());
		text.add("}\n");
		return asmifier;
	}

	/**
	 * Visit a class, field or method attribute.
	 *
	 * @param attribute an attribute.
	 */
	public void visitAttribute(final Attribute attribute) {
		stringBuilder.setLength(0);
		stringBuilder.append("// ATTRIBUTE ").append(attribute.type).append('\n');
		if (attribute instanceof ASMifiable) {
			if (labelNames == null) {
				labelNames = new HashMap<Label, String>();
			}
			stringBuilder.append("{\n");
			StringBuffer stringBuffer = new StringBuffer();
			((ASMifiable) attribute).asmify(stringBuffer, "attribute", labelNames);
			stringBuilder.append(stringBuffer.toString());
			stringBuilder.append(visitname).append(".visitAttribute(attribute);\n");
			stringBuilder.append("}\n");
		}
		text.add(stringBuilder.toString());
	}

	// -----------------------------------------------------------------------------------------------
	// Utility methods
	// -----------------------------------------------------------------------------------------------

	/**
	 * Constructs a new {@link TinyASMifier}.
	 *
	 * @param visitorVariableName the name of the visitor variable in the produced
	 *                            code.
	 * @param annotationVisitorId identifier of the annotation visitor variable in
	 *                            the produced code.
	 * @return a new {@link TinyASMifier}.
	 */
	protected TinyASMifier createASMifier(final String visitorVariableName, final int annotationVisitorId) {
		return new TinyASMifier(Opcodes.ASM6, visitorVariableName, annotationVisitorId);
	}

	/**
	 * Appends a string representation of the given access flags to
	 * {@link #stringBuilder}.
	 *
	 * @param accessFlags some access flags.
	 */
	private void appendAccessFlags(final int accessFlags) {
		boolean isEmpty = true;
		if ((accessFlags & Opcodes.ACC_PUBLIC) != 0) {
			stringBuilder.append("ACC_PUBLIC");
			isEmpty = false;
		}
		if ((accessFlags & Opcodes.ACC_PRIVATE) != 0) {
			stringBuilder.append("ACC_PRIVATE");
			isEmpty = false;
		}
		if ((accessFlags & Opcodes.ACC_PROTECTED) != 0) {
			stringBuilder.append("ACC_PROTECTED");
			isEmpty = false;
		}
		if ((accessFlags & Opcodes.ACC_FINAL) != 0) {
			if (!isEmpty) {
				stringBuilder.append(" | ");
			}
			if ((accessFlags & ACCESS_MODULE) == 0) {
				stringBuilder.append("ACC_FINAL");
			} else {
				stringBuilder.append("ACC_TRANSITIVE");
			}
			isEmpty = false;
		}
		if ((accessFlags & Opcodes.ACC_STATIC) != 0) {
			if (!isEmpty) {
				stringBuilder.append(" | ");
			}
			stringBuilder.append("ACC_STATIC");
			isEmpty = false;
		}
		if ((accessFlags & (Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_SUPER | Opcodes.ACC_TRANSITIVE)) != 0) {
			if (!isEmpty) {
				stringBuilder.append(" | ");
			}
			if ((accessFlags & ACCESS_CLASS) == 0) {
				if ((accessFlags & ACCESS_MODULE) == 0) {
					stringBuilder.append("ACC_SYNCHRONIZED");
				} else {
					stringBuilder.append("ACC_TRANSITIVE");
				}
			} else {
				stringBuilder.append("ACC_SUPER");
			}
			isEmpty = false;
		}
		if ((accessFlags & (Opcodes.ACC_VOLATILE | Opcodes.ACC_BRIDGE | Opcodes.ACC_STATIC_PHASE)) != 0) {
			if (!isEmpty) {
				stringBuilder.append(" | ");
			}
			if ((accessFlags & ACCESS_FIELD) == 0) {
				if ((accessFlags & ACCESS_MODULE) == 0) {
					stringBuilder.append("ACC_BRIDGE");
				} else {
					stringBuilder.append("ACC_STATIC_PHASE");
				}
			} else {
				stringBuilder.append("ACC_VOLATILE");
			}
			isEmpty = false;
		}
		if ((accessFlags & Opcodes.ACC_VARARGS) != 0 && (accessFlags & (ACCESS_CLASS | ACCESS_FIELD)) == 0) {
			if (!isEmpty) {
				stringBuilder.append(" | ");
			}
			stringBuilder.append("ACC_VARARGS");
			isEmpty = false;
		}
		if ((accessFlags & Opcodes.ACC_TRANSIENT) != 0 && (accessFlags & ACCESS_FIELD) != 0) {
			if (!isEmpty) {
				stringBuilder.append(" | ");
			}
			stringBuilder.append("ACC_TRANSIENT");
			isEmpty = false;
		}
		if ((accessFlags & Opcodes.ACC_NATIVE) != 0 && (accessFlags & (ACCESS_CLASS | ACCESS_FIELD)) == 0) {
			if (!isEmpty) {
				stringBuilder.append(" | ");
			}
			stringBuilder.append("ACC_NATIVE");
			isEmpty = false;
		}
		if ((accessFlags & Opcodes.ACC_ENUM) != 0 /* TODO&& (accessFlags & (ACCESS_CLASS | ACCESS_FIELD | ACCESS_INNER)) != 0 */) {
			if (!isEmpty) {
				stringBuilder.append(" | ");
			}
			stringBuilder.append("ACC_ENUM");
			isEmpty = false;
		}
		if ((accessFlags & Opcodes.ACC_ANNOTATION) != 0 && (accessFlags & (ACCESS_CLASS | ACCESS_INNER)) != 0) {
			if (!isEmpty) {
				stringBuilder.append(" | ");
			}
			stringBuilder.append("ACC_ANNOTATION");
			isEmpty = false;
		}
		if ((accessFlags & Opcodes.ACC_ABSTRACT) != 0) {
			if (!isEmpty) {
				stringBuilder.append(" | ");
			}
			stringBuilder.append("ACC_ABSTRACT");
			isEmpty = false;
		}
		if ((accessFlags & Opcodes.ACC_INTERFACE) != 0) {
			if (!isEmpty) {
				stringBuilder.append(" | ");
			}
			stringBuilder.append("ACC_INTERFACE");
			isEmpty = false;
		}
		if ((accessFlags & Opcodes.ACC_STRICT) != 0) {
			if (!isEmpty) {
				stringBuilder.append(" | ");
			}
			stringBuilder.append("ACC_STRICT");
			isEmpty = false;
		}
		if ((accessFlags & Opcodes.ACC_SYNTHETIC) != 0) {
			if (!isEmpty) {
				stringBuilder.append(" | ");
			}
			stringBuilder.append("ACC_SYNTHETIC");
			isEmpty = false;
		}
		if ((accessFlags & Opcodes.ACC_DEPRECATED) != 0) {
			if (!isEmpty) {
				stringBuilder.append(" | ");
			}
			stringBuilder.append("ACC_DEPRECATED");
			isEmpty = false;
		}
		if ((accessFlags & (Opcodes.ACC_MANDATED | Opcodes.ACC_MODULE)) != 0) {
			if (!isEmpty) {
				stringBuilder.append(" | ");
			}
			if ((accessFlags & ACCESS_CLASS) == 0) {
				stringBuilder.append("ACC_MANDATED");
			} else {
				stringBuilder.append("ACC_MODULE");
			}
			isEmpty = false;
		}
		if (isEmpty) {
			stringBuilder.append('0');
		}
	}

	/**
	 * Appends a string representation of the given constant to
	 * {@link #stringBuilder}.
	 *
	 * @param value a {@link String}, {@link Type}, {@link Handle}, {@link Byte},
	 *              {@link Short}, {@link Character}, {@link Integer},
	 *              {@link Float}, {@link Long} or {@link Double} object, or an
	 *              array of primitive values. May be <tt>null</tt>.
	 */
	@SuppressWarnings("deprecation")
	protected void appendConstant(final Object value) {
		if (value == null) {
			stringBuilder.append("null");
		} else if (value instanceof String) {
			appendString(stringBuilder, (String) value);
		} else if (value instanceof Type) {
			stringBuilder.append("Type.getType(\"");
			stringBuilder.append(((Type) value).getDescriptor());
			stringBuilder.append("\")");
		} else if (value instanceof Handle) {
			stringBuilder.append("new Handle(");
			Handle handle = (Handle) value;
			stringBuilder.append("Opcodes.").append(HANDLE_TAG[handle.getTag()]).append(", \"");
			stringBuilder.append(handle.getOwner()).append("\", \"");
			stringBuilder.append(handle.getName()).append("\", \"");
			stringBuilder.append(handle.getDesc()).append("\", ");
			stringBuilder.append(handle.isInterface()).append(")");
		} else if (value instanceof ConstantDynamic) {
			stringBuilder.append("new ConstantDynamic(\"");
			ConstantDynamic constantDynamic = (ConstantDynamic) value;
			stringBuilder.append(constantDynamic.getName()).append("\", \"");
			stringBuilder.append(constantDynamic.getDescriptor()).append("\", ");
			appendConstant(constantDynamic.getBootstrapMethod());
			stringBuilder.append(", new Object[] {");
			Object[] bootstrapMethodArguments = constantDynamic.getBootstrapMethodArguments();
			for (int i = 0; i < bootstrapMethodArguments.length; ++i) {
				appendConstant(bootstrapMethodArguments[i]);
				if (i != bootstrapMethodArguments.length - 1) {
					stringBuilder.append(", ");
				}
			}
			stringBuilder.append("})");
		} else if (value instanceof Byte) {
			stringBuilder.append("Byte.valueOf((byte)").append(value).append(')');
		} else if (value instanceof Boolean) {
			stringBuilder.append(((Boolean) value).booleanValue() ? "Boolean.TRUE" : "Boolean.FALSE");
		} else if (value instanceof Short) {
			stringBuilder.append("Short.valueOf((short)").append(value).append(')');
		} else if (value instanceof Character) {
			stringBuilder.append("Character.valueOf((char)").append((int) ((Character) value).charValue()).append(')');
		} else if (value instanceof Integer) {
			stringBuilder.append("Integer.valueOf(").append(value).append(')');
		} else if (value instanceof Float) {
			stringBuilder.append("Float.valueOf(\"").append(value).append("\")");
		} else if (value instanceof Long) {
			stringBuilder.append("Long.valueOf(").append(value).append("L)");
		} else if (value instanceof Double) {
			stringBuilder.append("Double.valueOf(\"").append(value).append("\")");
		} else if (value instanceof byte[]) {
			byte[] byteArray = (byte[]) value;
			stringBuilder.append("new byte[] {");
			for (int i = 0; i < byteArray.length; i++) {
				stringBuilder.append(i == 0 ? "" : ",").append(byteArray[i]);
			}
			stringBuilder.append('}');
		} else if (value instanceof boolean[]) {
			boolean[] booleanArray = (boolean[]) value;
			stringBuilder.append("new boolean[] {");
			for (int i = 0; i < booleanArray.length; i++) {
				stringBuilder.append(i == 0 ? "" : ",").append(booleanArray[i]);
			}
			stringBuilder.append('}');
		} else if (value instanceof short[]) {
			short[] shortArray = (short[]) value;
			stringBuilder.append("new short[] {");
			for (int i = 0; i < shortArray.length; i++) {
				stringBuilder.append(i == 0 ? "" : ",").append("(short)").append(shortArray[i]);
			}
			stringBuilder.append('}');
		} else if (value instanceof char[]) {
			char[] charArray = (char[]) value;
			stringBuilder.append("new char[] {");
			for (int i = 0; i < charArray.length; i++) {
				stringBuilder.append(i == 0 ? "" : ",").append("(char)").append((int) charArray[i]);
			}
			stringBuilder.append('}');
		} else if (value instanceof int[]) {
			int[] intArray = (int[]) value;
			stringBuilder.append("new int[] {");
			for (int i = 0; i < intArray.length; i++) {
				stringBuilder.append(i == 0 ? "" : ",").append(intArray[i]);
			}
			stringBuilder.append('}');
		} else if (value instanceof long[]) {
			long[] longArray = (long[]) value;
			stringBuilder.append("new long[] {");
			for (int i = 0; i < longArray.length; i++) {
				stringBuilder.append(i == 0 ? "" : ",").append(longArray[i]).append('L');
			}
			stringBuilder.append('}');
		} else if (value instanceof float[]) {
			float[] floatArray = (float[]) value;
			stringBuilder.append("new float[] {");
			for (int i = 0; i < floatArray.length; i++) {
				stringBuilder.append(i == 0 ? "" : ",").append(floatArray[i]).append('f');
			}
			stringBuilder.append('}');
		} else if (value instanceof double[]) {
			double[] doubleArray = (double[]) value;
			stringBuilder.append("new double[] {");
			for (int i = 0; i < doubleArray.length; i++) {
				stringBuilder.append(i == 0 ? "" : ",").append(doubleArray[i]).append('d');
			}
			stringBuilder.append('}');
		}
	}

	/**
	 * Appends a declaration of the given label to {@link #stringBuilder}. This
	 * declaration is of the form "Label labelXXX = new Label();". Does nothing if
	 * the given label has already been declared.
	 *
	 * @param label a label.
	 */
	protected void declareLabel(final Label label) {
		if (labelNames == null) {
			labelNames = new HashMap<Label, String>();
		}
		String labelName = labelNames.get(label);
		if (labelName == null) {
			labelName = "label" + labelNames.size();
			labelNames.put(label, labelName);
			stringBuilder.append("\tLabel ").append(labelName).append(" = new Label();\n");
		}
	}

	protected void declareLabel(final Label label, String name) {
		if (labelNames == null) {
			labelNames = new HashMap<Label, String>();
		}
		String labelName = labelNames.get(label);
		if (labelName == null) {
			labelName = "label" + labelNames.size() + "Of" + name;
			labelNames.put(label, labelName);
			stringBuilder.append("\tLabel ").append(labelName).append(" = new Label();\n");
		}
	}

	/**
	 * Appends the name of the given label to {@link #stringBuilder}. The given
	 * label <i>must</i> already have a name. One way to ensure this is to always
	 * call {@link #declareLabel} before calling this method.
	 *
	 * @param label a label.
	 */
	protected void appendLabel(final Label label) {
		stringBuilder.append(labelNames.get(label));
	}
}
