package club.hsspace.sqlplugin;

import club.hsspace.whypps.framework.plugin.Access;
import com.github.yitter.contract.IdGeneratorOptions;
import com.github.yitter.idgen.YitIdHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @ClassName: IdWorker
 * @CreateTime: 2022/7/16
 * @Comment: 雪花飘移ID生成器
 * @Author: Qing_ning
 * @Mail: 1750359613@qq.com
 */
@Access
public class IdWorker {

    private static final Logger logger = LoggerFactory.getLogger(IdWorker.class);

    public IdWorker(short code) {
        IdGeneratorOptions options = new IdGeneratorOptions(code);
        YitIdHelper.setIdGenerator(options);
    }

    public long generateId() {
        return YitIdHelper.nextId();
    }

    public static void main(String[] args) {
        IdWorker idWorker = new IdWorker((short) 1);
        System.out.println(idWorker.generateId());
    }

}
