package com.example.dataprocess.infrastructure.service;

/**
 * exportA 导出接口预留。
 *
 * <p>当前项目一期只做到 DSL 生成，不实现数据加工与导出。
 * 这个接口仅作为后续接入 exportA 的稳定边界保留，不参与当前主流程。</p>
 */
public interface ResultExportService {

    /**
     * 后续接入 exportA 时的调用入口。
     */
    void exportA(String taskId, Object exportPayload);
}
