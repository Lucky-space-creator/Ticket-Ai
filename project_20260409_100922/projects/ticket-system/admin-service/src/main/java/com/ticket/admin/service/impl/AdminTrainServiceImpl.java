package com.ticket.admin.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ticket.admin.mapper.TrainMapper;
import com.ticket.admin.service.AdminTrainService;
import com.ticket.entity.Train;
import org.springframework.stereotype.Service;

@Service
public class AdminTrainServiceImpl extends ServiceImpl<TrainMapper, Train> implements AdminTrainService {
}
