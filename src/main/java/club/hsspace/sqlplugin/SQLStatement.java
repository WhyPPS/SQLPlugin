package club.hsspace.sqlplugin;

import club.hsspace.whypps.framework.plugin.Access;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @ClassName: SQLStatement
 * @CreateTime: 2022/7/16
 * @Comment: SQL语句注解
 * @Author: Qing_ning
 * @Mail: 1750359613@qq.com
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Access
public @interface SQLStatement {

    /*
     * 命名空间
     */
    String namespace();

    /*
     * 参数为null则取消执行SQL
     */
    boolean nullCancel() default true;

}
