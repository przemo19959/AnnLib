package application.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation creates controller class for every domain class. Annotation is inteded for Spring MVC controller creation. It works much the same way as {@link GenerateRepositories} annotation. It
 * has much the same attributes and behaves same way for them. Only controllerAnnotation attribute behaves different. If class of required name doesn't exist it is created from scratch. Otherwise some
 * parts are modified. Class contains only public modifier, otherwise is changed. If mapping annotation is not present, its added. If base url field is not present, its created. If field modifiers are
 * wrong, they are changed. If type of field ist not String, its changed. If field value is empty, its changed to default value. If field is not initialized, its changed.<br>
 * <br>
 * To sum it up this annotation:
 * <ul>
 * <li>creates controller class: <br>
 * $1<br>
 * &#64;RequestMapping($2.BASE_URL)<br>
 * public class $2+$3{<br>
 * &emsp;public static final String BASE_URL=$4;<br>
 * &emsp;//...<br>
 * }</li>
 * <li>where:
 * <ul>
 * <li>$1 is one of annotations from {@link GenerateControllers#controllerAnnotation()}</li>
 * <li>$2 is domain class name</li>
 * <li>$3 is suffix {@link GenerateControllers#controllerSuffix()}</li>
 * <li>$4 is default base URL for this controller. Default value is equal to "/$2.toLowerCase()"</li>
 * </ul>
 * </li>
 * </ul>
 * 
 * @author hex
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface GenerateControllers {
	/**
	 * Relative to source folder path, where processor will look for classes annotated with @Entity. If set path doesn't contain any such class, exception (build error) will be thrown. Exception
	 * contains info for user.
	 */
	String domainPackagePath();
	/**
	 * List of possible controller annotations. If class has any of annotation from this list, nothing is changed. If class doesn't have any, first one is added. List was introduced for user. Single
	 * value annotation attribute would force every controller to have certain controller annotation. By introducing list, user can change controller annotation for specific controllers.
	 */
	Class<?>[] controllerAnnotation();
	/**
	 * Relative to source folder path, where processor will create controller classes.
	 */
	String controllerPackagePath() default "controllers";
	/**
	 * Generated controller name is build by domain class and suffix. User can set own suffix. Can't be empty or contain spaces.
	 */
	String controllerSuffix() default "Controller";
}
