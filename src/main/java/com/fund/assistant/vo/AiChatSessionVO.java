package com.fund.assistant.vo;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Getter
@Setter
@Accessors(chain = true)
public class AiChatSessionVO {

    private String sessionId;

    private String sessionName;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Integer messageCount;
}
