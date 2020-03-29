package application.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation during compilation process automatically generates code for singleton pattern.<br>
 * Responsibilites:
 * <ul>
 * <li>generating singleton instance field "private volatile static MyClass INSTANCE". Name of that field is controlled by <b>name</b> attribute. If class has already field with same modifiers and
 * type ,only name is changed (if not equal)</li>
 * <li>generating singleton constructor "private MyClass(params...){}". List of params is controlled through <b>initFields</b> attribute. If class has already constructor, then first one is modified
 * for this pattern. Constructor contains list of expressions "this.initField=initField" for every field in <b>initFields</b> attribute. Other parameters are automatically removed.</li>
 * <li>generating singleton getInstance method "public static MyClass getInstance(){...}". This method creates singleton instance using private constructor and returns initialized INSTANCE field. If
 * <b>initFields</b> attribute is not empty, then this method instead of creating singleton instance, throws {@link IllegalArgumentException}. In that case for creating instance is responsible another
 * method. If instance wasn't initialized createSingletonInstance method, then exception is thrown.</li>
 * <li>generating singleton createSingletonInstance method "public static void createSingletonInstance(params...){}". Params list is same as in private generated constructor. This method appears onlu,
 * when <b>initFields</b> is not empty. In that case, this method only creates singleton instance and getInstance method is used to get that intance.</li>
 * </ul>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface Singleton {
	/**
	 * Name of singleton instance field in annotated class.
	 */
	String name() default "INSTANCE";
	/**
	 * Name of public static method, which initialises and returns singleton instance (when initFields is empty). When initFields is not empty, then this method only returns instance. If instance is
	 * null (not initialized), then exception is thrown.
	 */
	String methodName() default "getInstance";
	/**
	 * wether getInstance method is thread safe (using Double-Checked Locking).
	 */
	boolean threadSafe() default true;
	/**
	 * List of field names from annotatedClass, which will be used to initialize singleton instance. If empty createSingletonInstance method won't be created and getInstance method creates and returns
	 * singleton instance.
	 */
	String[] initFields() default {};
}
