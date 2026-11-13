package edu.bjfu.eldercare;
import java.util.*;
import java.math.BigDecimal;
import java.time.*;
final class Inputs {
    static String text(Map<String,Object> m,String k){Object v=m.get(k);if(v==null||v.toString().isBlank())throw new Rule("缺少字段: "+k);return v.toString().strip();}
    static String optional(Map<String,Object> m,String k,String fallback){return m.get(k)==null||m.get(k).toString().isBlank()?fallback:m.get(k).toString().strip();}
    static long number(Map<String,Object> m,String k){try{return Long.parseLong(text(m,k));}catch(NumberFormatException e){throw new Rule("编号或整数无效: "+k);}}
    static BigDecimal decimal(Map<String,Object> m,String k){try{return new BigDecimal(text(m,k));}catch(NumberFormatException e){throw new Rule("数字无效: "+k);}}
    static boolean flag(Map<String,Object> m,String k){String s=text(m,k);if(!Set.of("true","false").contains(s))throw new Rule("布尔值无效: "+k);return Boolean.parseBoolean(s);}
    static LocalDateTime time(Map<String,Object> m,String k){try{return LocalDateTime.parse(text(m,k));}catch(Exception e){throw new Rule("日期时间无效: "+k);}}
    @SuppressWarnings("unchecked") static List<Map<String,Object>> objects(Map<String,Object> m,String k){Object v=m.get(k);if(!(v instanceof List<?> l)||l.isEmpty()||l.size()>500)throw new Rule(k+" 应为 1 至 500 个对象的数组");for(Object x:l)if(!(x instanceof Map<?,?>))throw new Rule(k+" 数组元素必须为对象");return (List<Map<String,Object>>)v;}
    static List<Long> ids(Map<String,Object> m,String k){Object v=m.get(k);if(!(v instanceof List<?> l)||l.isEmpty()||l.size()>500)throw new Rule(k+" 应为编号数组");try{return l.stream().map(x->Long.parseLong(x.toString())).distinct().toList();}catch(Exception e){throw new Rule(k+" 编号无效");}}
    static String choice(Map<String,Object> m,String k,String... values){String v=text(m,k);if(!Arrays.asList(values).contains(v))throw new Rule(k+" 取值无效");return v;}
}
