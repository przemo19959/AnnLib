package application.services;

import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import javax.lang.model.element.Element;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier.Keyword;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;

import application.annotations.Singleton;
import application.processors.AnnotationException;
import static application.processors.AnnLibProcessor.FIELD_NAME_PATTERN;

public class SingletonService {
	private static final String CREATE_SINGLETON_INSTANCE = "createSingletonInstance";
	private static final String NO_FIELD_IN_CLASS = "Field \"{0}\" doesn''t exist in class!";
	private static final String DI_SET_FIELD = "this.{0}={1};";
	private static final String GET_INSTANCE_TEMPLATE1 = "if({0} == null) '{'synchronized ({1}.class) '{'if({0} == null)return new {1}();'}}'";
	private static final String GET_INSTANCE_TEMPLATE2 = "if({0} == null) {0}=new {1}();";
	private static final String GET_INSTANCE_TEMPLATE1_THROWS = "if({0} == null) '{'synchronized ({1}.class) '{'if({0} == null) throw new IllegalArgumentException(\"Use createSingletonInstance method before this one!\");'}}'";
	private static final String GET_INSTANCE_TEMPLATE2_THROWS = "if({0} == null) throw new IllegalArgumentException(\"Use createSingletonInstance method before this one!\");";
	private static final String GET_INSTANCE_TEMPLATE1_INIT_FIELDS = "if({0} == null) '{'synchronized ({1}.class) '{'if({0} == null) {0}=new {1}({2});'}}'";
	private static final String GET_INSTANCE_TEMPLATE2_INIT_FIELDS = "if({0} == null) {0}=new {1}({2});";

	private boolean codeChanged;
	private Element annotationElement;

	//annotation fields
	private String name;
	private String methodName;
	private boolean threadSafe;
	private String[] initFields;

	public String processAnnotation(Element annotationElement, Path path, String className) throws AnnotationException {
		this.annotationElement = annotationElement;
		codeChanged = false;
		Singleton annotation = annotationElement.getAnnotation(Singleton.class);
		name = annotation.name();
		if(FIELD_NAME_PATTERN.matcher(name).matches() == false)
			throw new AnnotationException("Field name: \"" + name + "\" violates Java naming rules!", annotationElement, Singleton.class);

		methodName = annotation.methodName();
		if(FIELD_NAME_PATTERN.matcher(methodName).matches() == false)
			throw new AnnotationException("Method name:	\"" + methodName + "\" violates Java naming rules!", annotationElement, Singleton.class);
		
		threadSafe = annotation.threadSafe();
		initFields = annotation.initFields();
		for(String initField:initFields) {
			if(FIELD_NAME_PATTERN.matcher(initField).matches() == false)
				throw new AnnotationException("Field name: \"" + initField + "\" violates Java naming rules!", annotationElement, Singleton.class);
		}
		
		if(methodName.equals(CREATE_SINGLETON_INSTANCE))
			throw new AnnotationException("Method name:	\"" + methodName + "\" is assigned for another method!", annotationElement, Singleton.class);

		try {
			CompilationUnit cu = StaticJavaParser.parse(path);
			ClassOrInterfaceDeclaration cls = cu.getClassByName(className).orElse(null);
			if(cls != null) {
				addOrRenameSingletonInstanceField(cls, className);
				addOrPrivateDefaultConstuctor(cls);
				addOrModifySingletonMethod(cls, createAndInitSingletonGetInstanceMethod(className)); //for getInstance
				if(initFields.length>0) {
					addOrModifySingletonMethod(cls, createAndInitSingletonCreateInstanceMethod(cls, className));
				}else {
					if(cls.getMethodsByName(CREATE_SINGLETON_INSTANCE).size()>0) {
						cls.remove(cls.getMethodsByName(CREATE_SINGLETON_INSTANCE).get(0));
					}
				}
				if(codeChanged)
					return cu.toString();
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		return "no code";
	}

	private FieldDeclaration getCorrectSingletonInstanceField(String className) {
		FieldDeclaration fd = new FieldDeclaration().setModifiers(Keyword.PRIVATE, Keyword.VOLATILE, Keyword.STATIC);
		VariableDeclarator vd = new VariableDeclarator().setType(className).setName(name);
		fd.addVariable(vd);
		return fd;
	}

	private void addOrRenameSingletonInstanceField(ClassOrInterfaceDeclaration cls, String className) {
		FieldDeclaration fd = getCorrectSingletonInstanceField(className);
		FieldDeclaration f = Utils.findWhere(cls.getFields(), fd1 -> fd1.getModifiers().equals(fd.getModifiers()) && //
																fd1.getVariable(0).getType().equals(fd.getVariable(0).getType()));
		if(f != null) { //if exists, change name to proper
			if(f.getVariable(0).getName().equals(fd.getVariable(0).getName()) == false) { //if names are different
				f.getVariable(0).setName(name);
				codeChanged = true;
			}
		} else { //if doesn't exist, add field to class
			cls.addMember(fd);
			codeChanged = true;
		}
	}

	private ConstructorDeclaration createAndInitSingletonConstructor(ClassOrInterfaceDeclaration cls) throws AnnotationException {
		ConstructorDeclaration cd = new ConstructorDeclaration().setModifiers(Keyword.PRIVATE).setName(cls.getName());
		for(String initField:initFields) {
			FieldDeclaration fd = cls.getFieldByName(initField).orElse(null);
			if(fd == null)
				throw new AnnotationException(MessageFormat.format(NO_FIELD_IN_CLASS, initField), annotationElement, Singleton.class);
			cd.addParameter(fd.getVariable(0).getType(), initField);
			cd.getBody().addStatement(MessageFormat.format(DI_SET_FIELD, initField, initField));
		}
		return cd;
	}

	private void addOrPrivateDefaultConstuctor(ClassOrInterfaceDeclaration cls) throws AnnotationException {
		ConstructorDeclaration cd = createAndInitSingletonConstructor(cls);
		ConstructorDeclaration c = Utils.findWhere(cls.getConstructors(), cd1 -> cd1.getName().equals(cd.getName()));

		if(c != null) { //if exists, 
			if(c.getModifiers().equals(cd.getModifiers()) == false) {
				c.setModifiers(cd.getModifiers());
				codeChanged = true;
			}

			List<Parameter> oldParams = c.getParameters().stream()//
				.filter(p -> cd.getParameters().contains(p) == false)//
				.collect(Collectors.toList());

			if(initFields.length > 0 && c.getParameters().equals(cd.getParameters()) == false) {
				c.setParameters(cd.getParameters());
				codeChanged = true;
			}

			if(initFields.length == 0 && c.getParameters().size() > 0) {
				c.getParameters().clear();
				codeChanged = true;
			}

			BlockStmt cBody = c.getBody();
			//if body doesn't exist, add
			if(cBody.isEmpty()) {
				if(cd.getBody().isEmpty()==false) {
					c.setBody(cd.getBody());
					codeChanged = true;
				}
			} else {
				for(Statement st:cd.getBody().getStatements()) {
					if(cBody.getStatements().contains(st) == false) {
						cBody.addStatement(st);
						codeChanged = true;
					}
				}

				NodeList<Statement> toDelete = new NodeList<>();
				for(Parameter p:oldParams) {
					for(Statement st:cBody.getStatements()) {
						if(st.toString().startsWith("this." + p.getNameAsString()) && //
							st.toString().endsWith(p.getNameAsString() + ";")) {
							toDelete.add(st);
						}
					}
				}
				if(toDelete.size() > 0) {
					toDelete.forEach(td -> cBody.remove(td));
					codeChanged = true;
				}
			}
		} else { //if doesn't exist, add method to class
			cls.addMember(cd);
			codeChanged = true;
		}
	}

	private MethodDeclaration createAndInitSingletonGetInstanceMethod(String className) throws AnnotationException {
		String methodClause = "";
		if(threadSafe) {
			if(initFields.length == 0) {
				methodClause = MessageFormat.format(GET_INSTANCE_TEMPLATE1, name, className);
			} else {
				methodClause = MessageFormat.format(GET_INSTANCE_TEMPLATE1_THROWS, name, className);
			}
		} else {
			if(initFields.length == 0) {
				methodClause = MessageFormat.format(GET_INSTANCE_TEMPLATE2, name, className);
			} else {
				methodClause = MessageFormat.format(GET_INSTANCE_TEMPLATE2_THROWS, name);
			}
		}
		MethodDeclaration md = new MethodDeclaration().setModifiers(Keyword.PUBLIC, Keyword.STATIC)//
			.setType(className).setName(methodName);

		BlockStmt body = new BlockStmt();
		body.addStatement(methodClause);
		body.addStatement(new ReturnStmt(name));
		md.setBody(body);
		return md;
	}

	private MethodDeclaration createAndInitSingletonCreateInstanceMethod(ClassOrInterfaceDeclaration cls, String className) throws AnnotationException {
		String paramList = Arrays.stream(initFields).collect(Collectors.joining(", "));
		String methodClause = "";
		if(threadSafe) {
			methodClause = MessageFormat.format(GET_INSTANCE_TEMPLATE1_INIT_FIELDS, name, className, paramList);
		} else {
			methodClause = MessageFormat.format(GET_INSTANCE_TEMPLATE2_INIT_FIELDS, name, className, paramList);
		}
		MethodDeclaration md = new MethodDeclaration().setModifiers(Keyword.PUBLIC, Keyword.STATIC)//
			.setType("void").setName(CREATE_SINGLETON_INSTANCE);

		for(String initField:initFields) {
			FieldDeclaration fd = cls.getFieldByName(initField).orElse(null);
			if(fd == null)
				throw new AnnotationException(MessageFormat.format(NO_FIELD_IN_CLASS, initField), annotationElement, Singleton.class);
			md.addParameter(fd.getVariable(0).getType(), initField);
		}

		BlockStmt body = new BlockStmt();
		body.addStatement(methodClause);
		md.setBody(body);
		return md;
	}

	private void addOrModifySingletonMethod(ClassOrInterfaceDeclaration cls, MethodDeclaration md) throws AnnotationException {
		MethodDeclaration m = null;
		if(md.getNameAsString().equals(CREATE_SINGLETON_INSTANCE) == false) {
			m = Utils.findWhere(cls.getMethods(), md1 -> md1.getModifiers().equals(md.getModifiers()) && //
													md1.getType().equals(md.getType()));
		} else {
			m = Utils.findWhere(cls.getMethods(), md1 -> md1.getModifiers().equals(md.getModifiers()) && //
													md1.getType().equals(md.getType()) && md1.getName().equals(md.getName()));
		}

		if(m != null) { //if exists, change name and body to proper
			if(md.getNameAsString().equals(CREATE_SINGLETON_INSTANCE) == false) {
				if(m.getName().equals(md.getName()) == false) { //if names are different
					m.setName(methodName);
					codeChanged = true;
				}
			}

			if(initFields.length > 0 && m.getParameters().equals(md.getParameters()) == false) {
				m.setParameters(md.getParameters());
				codeChanged = true;
			}

			//			if(initFields.length == 0 && m.getParameters().size() > 0) {
			//				m.getParameters().clear();
			//				codeChanged = true;
			//			}

			BlockStmt body1 = m.getBody().orElse(null);
			//if body doesn't exist, add
			if(body1 == null) {
				m.setBody(md.getBody().get());
				codeChanged = true;
			} else {
				if(m.getBody().get().equals(md.getBody().get()) == false) {
					m.setBody(md.getBody().get());
					codeChanged = true;
				}
			}
		} else { //if doesn't exist, add method to class
			cls.addMember(md);
			codeChanged = true;
		}
	}
}
