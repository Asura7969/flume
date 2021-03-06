import com.google.common.base.Preconditions;
import org.apache.commons.lang.StringUtils;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.interceptor.Interceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by gongwenzhou on 2017/11/24.
 *
 * flume自定义拦截器
 * 把写好的类(MySearchAndReplaceInterceptor)打成jar,放置到flume-1.7.0的lib目录下
 *
 * 切换到flume-1.7.0目录下,重新拷贝一份conf文件夹:  cp -r conf conf_MySearchAndReplaceInterceptor
 * 1、修改里面的 log4j.properties ,方便管理查看日志
 *
 *      #flume.root.logger=DEBUG,console
        flume.root.logger=INFO,LOGFILE
        flume.log.dir=./logs
        flume.log.file=flume_MySearchAndReplaceInterceptor.log
 *
 * 拷贝一份 flume-conf.properties.template,并且重新命名 :   cp flume-conf.properties.template flume-conf.properties
 * 修改 flume-conf.properties文件:  vim flume-conf.properties
 * 主要在里面添加的拦截器配置如下:
 *      #---------拦截器相关配置------------------
        #定义拦截器
        agent1.sources.r1.interceptors = i1 i2
        # 设置拦截器类型
        agent1.sources.r1.interceptors.i1.type = MySearchAndReplaceInterceptor
        agent1.sources.r1.interceptors.i1.searchReplace = "gift_record:giftRecord,video_info:videoInfo,user_info:userInfo"

        # 设置拦截器类型
        agent1.sources.r1.interceptors.i2.type = regex_extractor
        # 设置正则表达式，匹配指定的数据，这样设置会在数据的header中增加log_type="某个值"
        agent1.sources.r1.interceptors.i2.regex = "type":"(\\w+)"
        agent1.sources.r1.interceptors.i2.serializers = s1
        agent1.sources.r1.interceptors.i2.serializers.s1.name = log_type
 *
 * 意思就是，即把gift_record 换成giftRecord
 　　　　　　　 video_info转换成videoInfo
 　　　　　　　 user_info转换成userInfo
 * 注意: agent1.sources.r1.interceptors.i1.type=包名.类名(或者继续追加$Builder)
 *
 *
 * 启动agent服务:
 *  bin/flume-ng agent --conf conf_MySearchAndReplaceInterceptor/  --conf-file conf_MySearchAndReplaceInterceptor/flume-conf.properties --name agent1  -Dflume.root.logger=INFO,console
 *
 * 启动报错等见博客:
 *  http://www.cnblogs.com/zlslch/p/7253943.html
 *  http://www.cnblogs.com/zlslch/p/7253983.html
 *  http://www.cnblogs.com/zlslch/p/7255373.html
 *
 */
public class MySearchAndReplaceInterceptor implements Interceptor {

    private static final Logger logger = LoggerFactory
            .getLogger(MySearchAndReplaceInterceptor.class);

    /**
     * 需要替换的字符串信息
     * 格式："key:value,key:value"
     */
    private final String search_replace;
    private String[] splits;
    private String[] key_value;
    private String key;
    private String value;
    private HashMap<String, String> hashMap = new HashMap<String, String>();
    private Pattern compile = Pattern.compile("\"type\":\"(\\w+)\"");
    private Matcher matcher;
    private String group;

    private MySearchAndReplaceInterceptor(String search_replace) {
        this.search_replace = search_replace;
    }

    /**
     * 初始化放在，最开始执行一次
     * 把配置的数据初始化到map中，方便后面调用
     */
    public void initialize() {
        try{
            if(StringUtils.isNotBlank(search_replace)){
                splits = search_replace.split(",");
                for (String key_value_pair:splits) {
                    key_value = key_value_pair.split(":");
                    key = key_value[0];
                    value = key_value[1];
                    hashMap.put(key,value);
                }
            }
        }catch (Exception e){
            logger.error("数据格式错误，初始化失败。"+search_replace,e.getCause());
        }
    }

    /**
     * 具体的处理逻辑
     * @param event
     * @return
     */
    public Event intercept(Event event) {
        try{
            String origBody = new String(event.getBody());
            matcher = compile.matcher(origBody);
            if(matcher.find()){
                group = matcher.group(1);
                if(StringUtils.isNotBlank(group)){
                    String newBody = origBody.replaceAll("\"type\":\""+group+"\"", "\"type\":\""+hashMap.get(group)+"\"");
                    event.setBody(newBody.getBytes());
                }
            }
        }catch (Exception e){
            logger.error("拦截器处理失败！",e.getCause());
        }
        return event;
    }

    public List<Event> intercept(List<Event> events) {
        for (Event event : events) {
            intercept(event);
        }
        return events;
    }

    public void close() {

    }


    public static class Builder implements Interceptor.Builder {
        private static final String SEARCH_REPLACE_KEY = "searchReplace";

        private String searchReplace;

        public void configure(Context context) {
            searchReplace = context.getString(SEARCH_REPLACE_KEY);
            Preconditions.checkArgument(!StringUtils.isEmpty(searchReplace),
                    "Must supply a valid search pattern " + SEARCH_REPLACE_KEY +
                            " (may not be empty)");
        }

        public Interceptor build() {
            Preconditions.checkNotNull(searchReplace,
                    "Regular expression searchReplace required");
            return new MySearchAndReplaceInterceptor(searchReplace);
        }

    }
}
