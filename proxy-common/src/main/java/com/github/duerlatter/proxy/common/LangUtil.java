package com.github.duerlatter.proxy.common;

import lombok.extern.slf4j.Slf4j;

/**
 * 类型转换工具类
 *
 * @author fsren
 * @date 2025-06-25
 */
@Slf4j
public class LangUtil {

    /**
     * Boolean解析方法，可传入Boolean或String值
     *
     * @param value Boolean或String值
     * @return Boolean 返回类型
     */
    public static Boolean parseBoolean(Object value) {
        if (value != null) {
            if (value instanceof Boolean) {
                return (Boolean) value;
            } else if (value instanceof String) {
                return Boolean.valueOf((String) value);
            }
        }
        return null;
    }

    /**
     * 解析Boolean值，支持Boolean或String类型。
     *
     * @param value        Boolean或String值
     * @param defaultValue 默认值，当解析失败时返回此值
     * @return boolean 返回类型
     */
    public static boolean parseBoolean(Object value, boolean defaultValue) {
        if (value != null) {
            if (value instanceof Boolean) {
                return (Boolean) value;
            } else if (value instanceof String) {
                try {
                    return Boolean.parseBoolean((String) value);
                } catch (Exception e) {
                    log.warn("parse boolean value({}) failed.", value);
                }
            }
        }
        return defaultValue;
    }

    /**
     * 解析Int值，支持Integer或String类型。
     *
     * @param value Integer或String值
     * @return Integer 返回类型
     */
    public static Integer parseInt(Object value) {
        if (value != null) {
            if (value instanceof Integer) {
                return (Integer) value;
            } else if (value instanceof String) {
                return Integer.valueOf((String) value);
            }
        }
        return null;
    }

    /**
     * 解析Int值，支持Integer或String类型。
     *
     * @param value        Integer或String值
     * @param defaultValue 默认值，当解析失败时返回此值
     * @return Integer 返回类型
     */
    public static Integer parseInt(Object value, Integer defaultValue) {
        if (value != null) {
            if (value instanceof Integer) {
                return (Integer) value;
            } else if (value instanceof String) {
                try {
                    return Integer.valueOf((String) value);
                } catch (Exception e) {
                    log.warn("parse Integer value({}) failed.", value);
                }
            }
        }
        return defaultValue;
    }

    /**
     * 解析Long值，支持Long或String类型。
     *
     * @param value Long或String值
     * @return Long 返回类型
     */
    public static Long parseLong(Object value) {
        if (value != null) {
            if (value instanceof Long) {
                return (Long) value;
            } else if (value instanceof String) {
                return Long.parseLong((String) value);
            }
        }
        return null;
    }

    /**
     * 解析Long值，支持Long或String类型。
     *
     * @param value        Long或String值
     * @param defaultValue 默认值，当解析失败时返回此值
     * @return Long 返回类型
     */
    public static Long parseLong(Object value, Long defaultValue) {
        if (value != null) {
            if (value instanceof Long) {
                return (Long) value;
            } else if (value instanceof String) {
                try {
                    return Long.parseLong((String) value);
                } catch (NumberFormatException e) {
                    log.warn("parse Long value({}) failed.", value);
                }
            }
        }
        return defaultValue;
    }

    /**
     * 解析Double值，支持Double或String类型。
     *
     * @param value Double或String值
     * @return Double 返回类型
     */
    public static Double parseDouble(Object value) {
        if (value != null) {
            if (value instanceof Double) {
                return (Double) value;
            } else if (value instanceof String) {
                return Double.parseDouble((String) value);
            }
        }
        return null;
    }

    /**
     * 解析Double值，支持Double或String类型。
     *
     * @param value        Double或String值
     * @param defaultValue 默认值，当解析失败时返回此值
     * @return Double 返回类型
     */
    public static Double parseDouble(Object value, Double defaultValue) {
        if (value != null) {
            if (value instanceof Double) {
                return (Double) value;
            } else if (value instanceof String) {
                try {
                    return Double.valueOf((String) value);
                } catch (NumberFormatException e) {
                    log.warn("parse Double value({}) failed.", value);
                }
            }
        }
        return defaultValue;
    }
}
