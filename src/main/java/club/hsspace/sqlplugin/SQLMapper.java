package club.hsspace.sqlplugin;

import club.hsspace.whypps.framework.plugin.Access;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @ClassName: SQLMapper
 * @CreateTime: 2022/7/16
 * @Comment: 注解Mapper接口
 * @Author: Qing_ning
 * @Mail: 1750359613@qq.com
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Access
public @interface SQLMapper {

}
