package application.services;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
import application.services.general.ServiceTemplate;
import application.services.general.Utils;

import static application.processors.AnnLibProcessor.FIELD_NAME_PATTERN;

public class SingletonService extends ServiceTemplate<Singleton> {
	private static final String METHOD_NAME_USED_ERROR = "Method name: \"{0}\" is assigned for another method!";
	private static final String METHOD_NAME_ERROR = "Method name: \"{0}\" violates Java naming rules!";
	private static final String FIELD_NAME_ERROR = "Field name: \"{0}\" violates Java naming rules!";
	private static final String CREATE_SINGLETON_INSTANCE = "createSingletonInstance";
	private static final String NO_FIELD_IN_CLASS = "Field \"{0}\" doesn''t exist in class!";
	private static final String DI_SET_FIELD = "this.{0}={1};";
	private static final String GET_INSTANCE_TEMPLATE1 = "if({0} == null) '{'synchronized ({1}.class) '{'if({0} == null)return new {1}();'}}'";
	private static final String GET_INSTANCE_TEMPLATE2 = "if({0} == null) {0}=new {1}();";
	private static final String GET_INSTANCE_TEMPLATE1_THROWS = "if({0} == null) '{'synchronized ({1}.class) '{'if({0} == null) throw new IllegalArgumentException(\"Use createSingletonInstance method before this one!\");'}}'";
	private static final String GET_INSTANCE_TEMPLATE2_THROWS = "if({0} == null) throw new IllegalArgumentException(\"Use createSingletonInstance method before this one!\");";
	private static final String GET_INSTANCE_TEMPLATE1_INIT_FIELDS = "if({0} == null) '{'synchronized ({1}.class) '{'if({0} == null) {0}=new {1}({2});'}}'";
	private static final String GET_INSTANCE_TEMPLATE2_INIT_FIELDS = "if({0} == null) {0}=new {1}({2});";

	//annotation fields
	private String name;
	private String methodName;
	private boolean threadSafe;
	private String[] initFields;

	@Override
	public void step1_injectAnnotation(Singleton annotation){
		name = annotation.name();
		checkArgument(FIELD_NAME_PATTERN.matcher(name).matches(), MessageFormat.format(FIELD_NAME_ERROR, name));

		methodName = annotation.methodName();
		checkArgument(FIELD_NAME_PATTERN.matcher(methodName).matches(), MessageFormat.format(METHOD_NAME_ERROR, methodName));

		threadSafe = annotation.threadSafe();
		initFields = annotation.initFields();
		for(String initField:initFields) {
			checkArgument(FIELD_NAME_PATTERN.matcher(initField).matches(), MessageFormat.format(FIELD_NAME_ERROR, initField));
		}
		checkArgument(methodName.equals(CREATE_SINGLETON_INSTANCE)==false, MessageFormat.format(METHOD_NAME_USED_ERROR, methodName));
	}

	@Override
	public void step2_processing(ClassOrInterfaceDeclaration cls, String className){
		addOrRenameSingletonInstanceField(cls, className);
		addOrPrivateDefaultConstuctor(cls);
		addOrModifySingletonMethod(cls, createAndInitSingletonGetInstanceMethod(className)); //for getInstance
		if(initFields.length > 0) {
			addOrModifySingletonMethod(cls, createAndInitSingletonCreateInstanceMethod(cls, className));
		} else {
			if(cls.getMethodsByName(CREATE_SINGLETON_INSTANCE).size() > 0) {
				cls.remove(cls.getMethodsByName(CREATE_SINGLETON_INSTANCE).get(0));
			}
		}
	}

	private FieldDeclaration getCorrectSingletonInstanceField(String className) {
		FieldDeclaration fd = new FieldDeclaration().setModifiers(Keyword.PRIVATE, Keyword.VOLATILE, Keyword.STATIC);
		VariableDeclarator vd = new VariableDeclarator().setType(className).setName(name);
		fd.addVariable(vd);
		return fd;
	}

	private void addOrRenameSingletonInstanceField(ClassOrInterfaceDeclaration cls, String className) {
		FieldDeclaration fd = getCorrectSingletonInstanceField(className);
		FieldDeclaration f = Utils.findWhere(cls.getFields(), fd1 -> Utils.modifiersEqual(fd1, fd) && Utils.typeEqual(0, fd1, fd));
		if(f != null) { //if exists, change name to proper
			if(Utils.nameEqual(0, f, fd) == false) { //if names are different
				f.getVariable(0).setName(name);
				codeChanged = true;
			}
		} else { //if doesn't exist, add field to class
			cls.addMember(fd);
			codeChanged = true;
		}
	}

	private ConstructorDeclaration createAndInitSingletonConstructor(ClassOrInterfaceDeclaration cls){
		ConstructorDeclaration cd = new ConstructorDeclaration()//
			.setModifiers(Keyword.PRIVATE)//
			.setName(cls.getName());
		for(String initField:initFields) {
			FieldDeclaration fd = cls.getFieldByName(initField).orElse(null);
			checkArgument(fd != null, MessageFormat.format(NO_FIELD_IN_CLASS, initField));
			cd.addParameter(fd.getVariable(0).getType(), initField);
			cd.getBody().addStatement(MessageFormat.format(DI_SET_FIELD, initField, initField));
		}
		return cd;
	}

	private void addOrPrivateDefaultConstuctor(ClassOrInterfaceDeclaration cls){
		ConstructorDeclaration cd = createAndInitSingletonConstructor(cls);
		ConstructorDeclaration c = Utils.findWhere(cls.getConstructors(), cd1 -> Utils.nameEqual(cd1, cd));

		if(c != null) { //if exists, 
			if(Utils.modifiersEqual(c, cd) == false) {
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
				if(cd.getBody().isEmpty() == false) {
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

	private MethodDeclaration createAndInitSingletonGetInstanceMethod(String className){
		String methodClause = "";
		if(threadSafe) {
			methodClause = MessageFormat.format((initFields.length == 0) ? GET_INSTANCE_TEMPLATE1 : GET_INSTANCE_TEMPLATE1_THROWS, name, className);
		} else {
			methodClause = (initFields.length == 0) ? MessageFormat.format(GET_INSTANCE_TEMPLATE2, name, className) : MessageFormat.format(GET_INSTANCE_TEMPLATE2_THROWS, name);
		}

		MethodDeclaration md = new MethodDeclaration()//
			.setModifiers(Keyword.PUBLIC, Keyword.STATIC)//
			.setType(className)//
			.setName(methodName);

		md.setBody(new BlockStmt().addStatement(methodClause).addStatement(new ReturnStmt(name)));
		return md;
	}

	private MethodDeclaration createAndInitSingletonCreateInstanceMethod(ClassOrInterfaceDeclaration cls, String className){
		String paramList = Arrays.stream(initFields).collect(Collectors.joining(", "));
		String methodClause = MessageFormat.format(threadSafe ? GET_INSTANCE_TEMPLATE1_INIT_FIELDS : GET_INSTANCE_TEMPLATE2_INIT_FIELDS, name, className, paramList);

		MethodDeclaration md = new MethodDeclaration()//
			.setModifiers(Keyword.PUBLIC, Keyword.STATIC)//
			.setType("void")//
			.setName(CREATE_SINGLETON_INSTANCE);

		for(String initField:initFields) {
			FieldDeclaration fd = cls.getFieldByName(initField).orElse(null);
			checkArgument(fd!=null, MessageFormat.format(NO_FIELD_IN_CLASS, initField));
			md.addParameter(fd.getVariable(0).getType(), initField);
		}

		md.setBody(new BlockStmt().addStatement(methodClause));
		return md;
	}

	private void addOrModifySingletonMethod(ClassOrInterfaceDeclaration cls, MethodDeclaration md){
		MethodDeclaration m = null;
		if(md.getNameAsString().equals(CREATE_SINGLETON_INSTANCE) == false) {
			m = Utils.findWhere(cls.getMethods(), md1 -> Utils.modifiersEqual(md1, md) && Utils.typeEqual(md1, md));
		} else {
			m = Utils.findWhere(cls.getMethods(), md1 -> Utils.modifiersEqual(md1, md) && Utils.typeEqual(md1, md) && Utils.nameEqual(md1, md));
		}

		if(m != null) { //if exists, change name and body to proper
			if(md.getNameAsString().equals(CREATE_SINGLETON_INSTANCE) == false) {
				if(Utils.nameEqual(m, md) == false) { //if names are different
					m.setName(methodName);
					codeChanged = true;
				}
			}

			if(initFields.length > 0 && m.getParameters().equals(md.getParameters()) == false) {
				m.setParameters(md.getParameters());
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
