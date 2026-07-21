package com.voyage.common;

/**
 * 员工号上下文 —— 基于 ThreadLocal，天然支持虚拟线程。
 * 由 EmpContextConfig 中的 HandlerInterceptor 在请求进入时设置、请求结束时清理。
 */
public final class EmpContext {

    private static final ThreadLocal<String> EMP_NO_HOLDER = new ThreadLocal<>();
    private static final String DEFAULT_EMP_NO = "0000000";

    private EmpContext() {
    }

    /**
     * 获取当前请求操作者的员工号，未设置时返回 "0000000"。
     */
    public static String getEmpNo() {
        String empNo = EMP_NO_HOLDER.get();
        return empNo != null ? empNo : DEFAULT_EMP_NO;
    }

    /**
     * 设置当前请求的员工号（由拦截器调用）。
     */
    public static void setEmpNo(String empNo) {
        EMP_NO_HOLDER.set(empNo);
    }

    /**
     * 清除 ThreadLocal（由拦截器在 afterCompletion 中调用，防止内存泄漏）。
     */
    public static void clear() {
        EMP_NO_HOLDER.remove();
    }
}
