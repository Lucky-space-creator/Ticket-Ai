package com.ticket.admin.application.catalog;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ticket.entity.Train;

/**
 * 管理端车次目录应用服务（与 train-service 运行域分离时的本地读模型/写入口；当前与车次表同库）。
 */
public interface AdminTrainService extends IService<Train> {
}
