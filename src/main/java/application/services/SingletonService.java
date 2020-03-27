package application.services;

import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
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
import static application.processors.SingletonProcessor.FIELD_NAME_PATTERN;

public class SingletonService {
	private static final String NO_FIELD_IN_CLASS = "Field \"{0}\" doesn''t exist in class!";
	private static final String DI_SET_FIELD = "this.{0}={1};";
	private static final String GET_INSTANCE_TEMPLATE1 = "if({0} == null) '{'synchronized ({1}.class) '{'if({2} == null)return new {3}({4});'}}'";
	private static final String GET_INSTANCE_TEMPLATE2 = "if({0} == null) {0}=new {1}({2});";

	private boolean codeChanged;

	private Element annotationElement;

	//annotation fields
	private String name;
	private String methodName;
	private boolean threadSafe;
	private String[] initFields;
	
	//TODO - 27 mar 2020:uporz¹dkowaæ kod i dodaæ, jeszcze metod¹ tworz¹c¹ pojedyncz¹ instacje i pobieraj¹c¹, 
	//w przypadku, gdy initFields nie jest puste

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

		try {
			CompilationUnit cu = StaticJavaParser.parse(path);
			ClassOrInterfaceDeclaration cls = cu.getClassByName(className).orElse(null);
			if(cls != null) {
				addOrRenameSingletonInstanceField(cls, className);
				addOrPrivateDefaultConstuctor(cls, initFields);
				addOrModifyGetInstanceMethod(cls, className);
				if(codeChanged)
					return cu.toString();
			}
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		return "no code";
	}

	private void addOrRenameSingletonInstanceField(ClassOrInterfaceDeclaration cls, String className) {
		//create correct fieldDeclaration
		FieldDeclaration fd = new FieldDeclaration().setModifiers(Keyword.PRIVATE, Keyword.VOLATILE, Keyword.STATIC);
		VariableDeclarator vd = new VariableDeclarator().setType(className).setName(name);
		fd.addVariable(vd);

		//find field with same signature (excluding field name)
		FieldDeclaration f = cls.getFields().stream()//
			.filter(fd1 -> fd1.getModifiers().equals(fd.getModifiers()) && //
							fd1.getVariable(0).getType().equals(fd.getVariable(0).getType()))//
			.findFirst().orElse(null);

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

	private <T> T findWhere(List<T> list, Predicate<T> predicate) {
		return list.stream().filter(predicate).findFirst().orElse(null);
	}

	private void addOrPrivateDefaultConstuctor(ClassOrInterfaceDeclaration cls, String[] initFields) throws AnnotationException {
		ConstructorDeclaration cd = createAndInitSingletonConstructor(cls);//create correct constructorDeclaration
		//find method with same signature (excluding method name)
		ConstructorDeclaration c = findWhere(cls.getConstructors(), cd1 -> cd1.getName().equals(cd.getName()));

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
				c.setBody(cd.getBody());
				codeChanged = true;
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

	private MethodDeclaration createAndInitSingletonGetIstanceMethod(ClassOrInterfaceDeclaration cls,String className) throws AnnotationException {
		String paramList=Arrays.stream(initFields).collect(Collectors.joining(", "));
		String methodClause = (threadSafe) ? MessageFormat.format(GET_INSTANCE_TEMPLATE1, name, className, name, className,paramList) : MessageFormat.format(GET_INSTANCE_TEMPLATE2, name, className,paramList);
		MethodDeclaration md = new MethodDeclaration().setModifiers(Keyword.PUBLIC, Keyword.STATIC)//
			.setType(className).setName(methodName);
		for(String initField:initFields) {
			FieldDeclaration fd = cls.getFieldByName(initField).orElse(null);
			if(fd == null)
				throw new AnnotationException(MessageFormat.format(NO_FIELD_IN_CLASS, initField), annotationElement, Singleton.class);
			md.addParameter(fd.getVariable(0).getType(), initField);
		}
		BlockStmt body = new BlockStmt();
		body.addStatement(methodClause);
		body.addStatement(new ReturnStmt(name));
		md.setBody(body);
		return md;
	}

	private void addOrModifyGetInstanceMethod(ClassOrInterfaceDeclaration cls, String className) throws AnnotationException {
		MethodDeclaration md = createAndInitSingletonGetIstanceMethod(cls,className);//create correct methodDeclaration
		//find method with same signature (excluding method name)
		MethodDeclaration m = findWhere(cls.getMethods(), md1 -> md1.getModifiers().equals(md.getModifiers()) && //
							md1.getType().equals(md.getType()));

		if(m != null) { //if exists, change name and body to proper
			if(m.getName().equals(md.getName()) == false) { //if names are different
				m.setName(methodName);
				codeChanged = true;
			}
			
			if(initFields.length > 0 && m.getParameters().equals(md.getParameters()) == false) {
				m.setParameters(md.getParameters());
				codeChanged = true;
			}

			if(initFields.length == 0 && m.getParameters().size() > 0) {
				m.getParameters().clear();
				codeChanged = true;
			}
			
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
