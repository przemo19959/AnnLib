package application.services;

import java.util.List;
import java.util.function.Predicate;

public class Utils {
	public static <T> T findWhere(List<T> list, Predicate<T> predicate) {
		return list.stream().filter(predicate).findFirst().orElse(null);
	}
}
