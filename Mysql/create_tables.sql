create table ai_chat_history
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    user_id     bigint                             not null comment '用户ID',
    session_id  varchar(64)                        not null comment '会话ID，用于关联多轮对话',
    session_name varchar(100)                       null comment '会话名称，由第一条消息自动生成',
    question    text                               not null comment '用户提问内容',
    answer      text                               not null comment 'AI回答内容',
    del_flag    tinyint  default 0                 null comment '逻辑删除标记：0=正常 1=已删除',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment 'AI对话历史记录表' collate = utf8mb4_unicode_ci;

create index idx_user_id
    on ai_chat_history (user_id);

create index idx_user_session
    on ai_chat_history (user_id, session_id);

create table ai_operate_record
(
    id              bigint auto_increment comment '主键ID'
        primary key,
    user_id         bigint                             not null comment '用户ID',
    operate_type    tinyint                            not null comment '操作类型：1=加仓 2=减仓 3=定投配置 4=发布帖子',
    operate_content text                               not null comment '操作方案详情',
    is_confirmed    tinyint                            not null comment '用户是否确认：0=未确认 1=已确认 2=已取消',
    is_executed     tinyint                            not null comment '是否执行完成：0=未执行 1=成功 2=失败',
    fail_reason     varchar(255)                       null comment '执行失败原因',
    create_time     datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time     datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment 'AI指令操作记录表' collate = utf8mb4_unicode_ci;

create index idx_is_confirmed
    on ai_operate_record (is_confirmed);

create index idx_user_id
    on ai_operate_record (user_id);

create table ai_alert_rule
(
    id              bigint auto_increment comment '主键ID'
        primary key,
    user_id         bigint                             not null comment '用户ID',
    fund_code       varchar(20)                        not null comment '基金代码',
    threshold       decimal(6, 2)                      not null comment '触发阈值(%)：如20代表20%',
    direction       tinyint                            not null comment '方向：1=止盈 2=止损',
    is_triggered    tinyint  default 0                 null comment '是否已触发：0=未触发 1=已触发',
    create_time     datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time     datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment 'AI止盈止损提醒规则表' collate = utf8mb4_unicode_ci;

create index idx_user_id
    on ai_alert_rule (user_id);

create index idx_user_fund
    on ai_alert_rule (user_id, fund_code);

create table community_post
(
    id            bigint auto_increment comment '主键ID'
        primary key,
    user_id       bigint                             not null comment '发布用户ID',
    title         varchar(100)                       not null comment '帖子标题',
    content       text                               not null comment '帖子正文内容',
    image_urls    text                               null comment '帖子图片URL，多个逗号分隔',
    like_count    int      default 0                 null comment '点赞数',
    comment_count int      default 0                 null comment '评论数',
    version       int      default 0                 null comment '乐观锁版本号',
    del_flag      tinyint  default 0                 null comment '逻辑删除标记：0=正常 1=已删除',
    create_time   datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time   datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '论坛帖子表' collate = utf8mb4_unicode_ci;

create index idx_create_time
    on community_post (create_time);

create index idx_user_id
    on community_post (user_id);

create table community_post_comment
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    post_id     bigint                             not null comment '所属帖子ID',
    user_id     bigint                             not null comment '评论用户ID',
    content     varchar(500)                       not null comment '评论内容',
    del_flag    tinyint  default 0                 null comment '逻辑删除标记：0=正常 1=已删除',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '帖子评论表' collate = utf8mb4_unicode_ci;

create index idx_post_id
    on community_post_comment (post_id);

create index idx_user_id
    on community_post_comment (user_id);

create table community_post_like
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    post_id     bigint                             not null comment '帖子ID',
    user_id     bigint                             not null comment '点赞用户ID',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    constraint uk_post_user
        unique (post_id, user_id)
)
    comment '帖子点赞记录表' collate = utf8mb4_unicode_ci;

create index idx_post_id
    on community_post_like (post_id);

create table fund_base_info
(
    id                 bigint auto_increment comment '主键ID'
        primary key,
    fund_code          varchar(20)                        not null comment '基金代码',
    fund_name          varchar(100)                       not null comment '基金全称',
    fund_short_name    varchar(50)                        null comment '基金简称',
    fund_type          varchar(30)                        null comment '基金类型：股票型/指数型/混合型/债券型',
    fund_plate         varchar(50)                        null comment '所属板块：光伏/半导体/CPO/纳斯达克',
    risk_level         tinyint                            null comment '风险等级：1=低 2=中低 3=中 4=中高 5=高',
    fund_manager       varchar(50)                        null comment '基金经理',
    fund_company       varchar(100)                       null comment '基金公司',
    establish_date     date                               null comment '成立日期',
    latest_net_value     decimal(10, 4)                     null comment '最新单位净值',
    latest_change_rate   decimal(6, 4)                      null comment '最新涨跌幅(%)',
    stock_position_ratio decimal(5, 2)                      null comment '股票仓位比例(%)',
    create_time        datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time        datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_fund_code
        unique (fund_code)
)
    comment '基金基础信息表' collate = utf8mb4_unicode_ci;

create index idx_fund_name
    on fund_base_info (fund_name);

create table fund_invest_exec_record
(
    id             bigint auto_increment comment '主键ID'
        primary key,
    plan_id        bigint                             not null comment '定投计划ID',
    user_id        bigint                             not null comment '用户ID',
    deduct_date    date                               not null comment '扣款日期',
    deduct_amount  decimal(15, 4)                     not null comment '扣款金额',
    charge_fee     decimal(15, 4)                     not null comment '手续费',
    confirm_shares decimal(15, 4)                     not null comment '确认份额',
    exec_status    tinyint                            not null comment '执行状态：0=成功 1=失败',
    create_time    datetime default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '定投执行记录表' collate = utf8mb4_unicode_ci;

create index idx_deduct_date
    on fund_invest_exec_record (deduct_date);

create index idx_plan_id
    on fund_invest_exec_record (plan_id);

create index idx_user_id
    on fund_invest_exec_record (user_id);

create table fund_invest_plan
(
    id                  bigint auto_increment comment '主键ID'
        primary key,
    user_id             bigint                                   not null comment '用户ID',
    fund_code           varchar(20)                              not null comment '基金代码',
    group_id            bigint                                   not null comment '所属分组ID',
    invest_amount       decimal(15, 4)                           not null comment '每期定投金额',
    charge_rate         decimal(6, 4)  default 0.0000            null comment '申购费率(%)',
    invest_cycle        tinyint                                  not null comment '定投周期：1=每日 2=每周 3=每月',
    cycle_day           tinyint                                  null comment '定投日期：每周1-7 / 每月1-28',
    next_deduct_date    date                                     not null comment '下次扣款日期',
    total_invest_period int            default 0                 null comment '累计定投期数',
    total_invest_amount decimal(15, 4) default 0.0000            null comment '累计定投总金额',
    plan_status         tinyint                                  not null comment '计划状态：0=进行中 1=已暂停 2=已终止',
    create_time         datetime       default CURRENT_TIMESTAMP null comment '创建时间',
    update_time         datetime       default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '定投计划表' collate = utf8mb4_unicode_ci;

create index idx_next_deduct_date
    on fund_invest_plan (next_deduct_date);

create index idx_plan_status
    on fund_invest_plan (plan_status);

create index idx_user_id
    on fund_invest_plan (user_id);

create table fund_net_value_history
(
    id                   bigint auto_increment comment '主键ID'
        primary key,
    fund_code            varchar(20)                        not null comment '基金代码',
    net_value_date       date                               not null comment '净值日期',
    unit_net_value       decimal(10, 4)                     not null comment '单位净值',
    cumulative_net_value decimal(10, 4)                     null comment '累计净值',
    daily_change_rate    decimal(6, 4)                      null comment '日涨跌幅(%)',
    create_time          datetime default CURRENT_TIMESTAMP null comment '创建时间',
    constraint uk_fund_date
        unique (fund_code, net_value_date)
)
    comment '基金历史净值表' collate = utf8mb4_unicode_ci;

create index idx_fund_code
    on fund_net_value_history (fund_code);

create table fund_stock_hold_detail
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    fund_code   varchar(20)                        not null comment '基金代码',
    stock_code  varchar(20)                        not null comment '股票代码',
    stock_name  varchar(50)                        not null comment '股票名称',
    hold_ratio  decimal(5, 2)                      not null comment '持仓占比(%)',
    report_date date                               not null comment '报告披露日期',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_fund_stock_report
        unique (fund_code, stock_code, report_date)
)
    comment '基金前十重仓股表' collate = utf8mb4_unicode_ci;

create index idx_fund_code_report
    on fund_stock_hold_detail (fund_code, report_date);

create table fund_trade_record
(
    id              bigint auto_increment comment '主键ID'
        primary key,
    user_id         bigint                                  not null comment '用户ID',
    fund_code       varchar(20)                             not null comment '基金代码',
    group_id        bigint                                  not null comment '所属分组ID',
    trade_type      tinyint                                 not null comment '交易类型：1=加仓 2=减仓 3=现金分红 4=红利再投资',
    trade_amount    decimal(15, 4)                          not null comment '交易金额',
    trade_shares    decimal(15, 4)                          not null comment '交易份额',
    charge_rate     decimal(6, 4) default 0.0000            null comment '交易费率(%)',
    charge_fee      decimal(15, 4)                          not null comment '手续费',
    trade_date      date                                    not null comment '交易日期',
    trade_time_flag tinyint                                 not null comment '交易时点：1=15点前 2=15点后/非交易日',
    dividend_type   tinyint                                 null comment '分红方式：1=现金 2=再投资',
    import_type     tinyint       default 0                 null comment '导入方式：0=手动 1=图片识别',
    create_time     datetime      default CURRENT_TIMESTAMP null comment '创建时间'
)
    comment '基金交易记录表' collate = utf8mb4_unicode_ci;

create index idx_trade_date
    on fund_trade_record (trade_date);

create index idx_user_fund
    on fund_trade_record (user_id, fund_code);

create index idx_user_id
    on fund_trade_record (user_id);

create table fund_user_group
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    user_id     bigint                             not null comment '用户ID',
    group_name  varchar(50)                        not null comment '分组名称',
    group_type  tinyint                            not null comment '分组类型：1=持仓分组 2=自选分组',
    sort        int      default 0                 null comment '排序序号',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_user_group_type_name
        unique (user_id, group_type, group_name)
)
    comment '用户基金分组表' collate = utf8mb4_unicode_ci;

create index idx_user_id_type
    on fund_user_group (user_id, group_type);

create table market_index_daily
(
    id                bigint auto_increment comment '主键ID'
        primary key,
    index_code        varchar(20)                        not null comment '指数代码',
    trade_date        date                               not null comment '交易日',
    open_point        decimal(10, 4)                     not null comment '开盘点位',
    close_point       decimal(10, 4)                     not null comment '收盘点位',
    highest_point     decimal(10, 4)                     not null comment '最高点位',
    lowest_point      decimal(10, 4)                     not null comment '最低点位',
    daily_change_rate decimal(6, 4)                      null comment '日涨跌幅(%)',
    create_time       datetime default CURRENT_TIMESTAMP null comment '创建时间',
    constraint uk_index_date
        unique (index_code, trade_date)
)
    comment '指数每日行情表' collate = utf8mb4_unicode_ci;

create index idx_index_code
    on market_index_daily (index_code);

create table market_index_info
(
    id                 bigint auto_increment comment '主键ID'
        primary key,
    index_code         varchar(20)                        not null comment '指数代码',
    index_name         varchar(50)                        not null comment '指数名称',
    market_type        tinyint                            not null comment '市场类型：1=A股 2=港股 3=美股',
    latest_point       decimal(10, 4)                     null comment '最新点位',
    latest_change_rate decimal(6, 4)                      null comment '最新涨跌幅(%)',
    create_time        datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time        datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_index_code
        unique (index_code)
)
    comment '大盘指数基础信息表' collate = utf8mb4_unicode_ci;

create index idx_market_type
    on market_index_info (market_type);

create table market_sector_info
(
    id                bigint auto_increment comment '主键ID'
        primary key,
    sector_code       varchar(20)                        not null comment '板块代码',
    sector_name       varchar(50)                        not null comment '板块名称',
    daily_change_rate decimal(6, 4)                      null comment '当日涨跌幅(%)',
    capital_flow      decimal(15, 2)                     null comment '资金净流入/流出(亿元)：正=流入 负=流出',
    create_time       datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time       datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_sector_code
        unique (sector_code)
)
    comment '行业板块资金&行情表' collate = utf8mb4_unicode_ci;

create table user_daily_profit
(
    id                 bigint auto_increment comment '主键ID'
        primary key,
    user_id            bigint                             not null comment '用户ID',
    profit_date        date                               not null comment '收益日期',
    daily_total_profit decimal(15, 4)                     not null comment '当日总收益',
    daily_profit_rate  decimal(6, 4)                      not null comment '当日收益率(%)',
    total_asset        decimal(15, 4)                     null comment '当日收盘总资产',
    create_time        datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time        datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_user_date
        unique (user_id, profit_date)
)
    comment '用户每日收益表' collate = utf8mb4_unicode_ci;

create index idx_profit_date
    on user_daily_profit (profit_date);

create index idx_user_id
    on user_daily_profit (user_id);

create table user_feedback
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    user_id     bigint                             not null comment '提交用户ID',
    content     text                               not null comment '建议内容',
    contact     varchar(50)                        null comment '联系方式（选填）',
    status      tinyint  default 0                 null comment '处理状态：0=待处理 1=已处理',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '用户功能建议表' collate = utf8mb4_unicode_ci;

create index idx_user_id
    on user_feedback (user_id);

create table user_follow_relation
(
    id               bigint auto_increment comment '主键ID'
        primary key,
    user_id          bigint                             not null comment '关注者用户ID',
    followed_user_id bigint                             not null comment '被关注者用户ID',
    create_time      datetime default CURRENT_TIMESTAMP null comment '创建时间',
    constraint uk_user_followed
        unique (user_id, followed_user_id)
)
    comment '用户关注&粉丝关联表' collate = utf8mb4_unicode_ci;

create index idx_followed_user_id
    on user_follow_relation (followed_user_id);

create index idx_user_id
    on user_follow_relation (user_id);

create table user_fund_daily_profit
(
    id                bigint auto_increment comment '主键ID'
        primary key,
    user_id           bigint                             not null comment '用户ID',
    fund_code         varchar(20)                        not null comment '基金代码',
    profit_date       date                               not null comment '收益日期',
    daily_profit      decimal(15, 4)                     not null comment '当日收益',
    daily_profit_rate decimal(6, 4)                      not null comment '当日收益率(%)',
    hold_market_value decimal(15, 4)                     null comment '当日持仓市值',
    create_time       datetime default CURRENT_TIMESTAMP null comment '创建时间',
    constraint uk_user_fund_date
        unique (user_id, fund_code, profit_date)
)
    comment '单只基金每日收益明细表' collate = utf8mb4_unicode_ci;

create index idx_user_date
    on user_fund_daily_profit (user_id, profit_date);

create table user_fund_favorite
(
    id          bigint auto_increment comment '主键ID'
        primary key,
    user_id     bigint                             not null comment '用户ID',
    fund_code   varchar(20)                        not null comment '基金代码',
    group_id    bigint                             not null comment '所属自选分组ID',
    create_time datetime default CURRENT_TIMESTAMP null comment '创建时间',
    constraint uk_user_fund_group
        unique (user_id, fund_code, group_id)
)
    comment '用户自选基金表' collate = utf8mb4_unicode_ci;

create index idx_group_id
    on user_fund_favorite (group_id);

create index idx_user_id
    on user_fund_favorite (user_id);

create table user_fund_hold
(
    id                bigint auto_increment comment '主键ID'
        primary key,
    user_id           bigint                             not null comment '用户ID',
    fund_code         varchar(20)                        not null comment '基金代码',
    group_id          bigint                             not null comment '所属分组ID',
    hold_shares       decimal(15, 4)                     not null comment '持有份额',
    cost_price        decimal(10, 4)                     not null comment '持仓成本单价',
    total_cost_amount decimal(15, 4)                     not null comment '持仓总成本',
    frozen_shares     decimal(15, 4) default 0.0000        null comment '冻结份额（卖出待确认期间冻结）',
    create_time       datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time       datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_user_fund_group
        unique (user_id, fund_code, group_id)
)
    comment '用户持仓表' collate = utf8mb4_unicode_ci;

create index idx_group_id
    on user_fund_hold (group_id);

create index idx_user_id
    on user_fund_hold (user_id);

create table user_info
(
    id                bigint auto_increment comment '主键ID'
        primary key,
    username          varchar(50)                                    not null comment '登录账号',
    password          varchar(255)                                   not null comment '加密密码',
    uid               varchar(30)                                    not null comment '用户唯一展示ID',
    nickname          varchar(50)                                    null comment '用户昵称',
    avatar            varchar(255)                                   null comment '头像URL',
    signature         varchar(100) default '这家伙太懒了，什么都没写' null comment '个性签名',
    security_question varchar(100)                                   not null comment '密保问题',
    security_answer   varchar(100)                                   not null comment '密保答案',
    follow_count      int          default 0                         null comment '关注数',
    fans_count        int          default 0                         null comment '粉丝数',
    like_total        int          default 0                         null comment '获赞总数',
    hold_privacy      tinyint      default 0                         null comment '持仓隐私开关：0=私密 1=仅粉丝可见 2=完全公开',
    operate_privacy   tinyint      default 0                         null comment '操作隐私开关：0=私密 1=仅粉丝可见 2=完全公开',
    del_flag          tinyint      default 0                         null comment '逻辑删除标记：0=正常 1=已删除',
    create_time       datetime     default CURRENT_TIMESTAMP         null comment '创建时间',
    update_time       datetime     default CURRENT_TIMESTAMP         null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_uid
        unique (uid),
    constraint uk_username
        unique (username)
)
    comment '用户主表' collate = utf8mb4_unicode_ci;

create table fund_pending_trade
(
    id              bigint auto_increment comment '主键ID'
        primary key,
    user_id         bigint                             not null comment '用户ID',
    fund_code       varchar(20)                        not null comment '基金代码',
    trade_type      tinyint                            not null comment '交易类型：1=买入 2=定投 3=卖出',
    trade_amount    decimal(15, 4)                     null comment '交易金额',
    trade_shares    decimal(15, 4)                     null comment '交易份额',
    charge_fee      decimal(15, 4)                     null comment '手续费',
    trade_time      datetime                           null comment '交易时间',
    trade_date      date                               not null comment '交易日期(T日)',
    confirm_date    date                               not null comment '确认日期',
    fund_type       tinyint                            null comment '基金类型：1=普通 2=QDII',
    related_plan_id bigint                             null comment '关联定投计划ID',
    status          tinyint  default 0                 not null comment '状态：0=待确认 1=已确认 2=已取消',
    create_time     datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time     datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间'
)
    comment '待确认交易表（T+1规则核心）' collate = utf8mb4_unicode_ci;

create index idx_user_confirm_date
    on fund_pending_trade (user_id, confirm_date);

create index idx_confirm_date_status
    on fund_pending_trade (confirm_date, status);

create table fund_realtime_valuation
(
    id                   bigint auto_increment comment '主键ID'
        primary key,
    fund_code            varchar(20)                        not null comment '基金代码',
    valuation_time       datetime                           not null comment '估值时间',
    pre_close_net_value  decimal(10, 4)                     null comment '昨日单位净值',
    estimate_net_value   decimal(10, 4)                     null comment '估算净值',
    estimate_change_rate decimal(6, 4)                      null comment '估算涨跌幅(%)',
    stock_position_ratio decimal(5, 2)                      null comment '股票仓位比例(%)',
    valuation_status     tinyint                            null comment '估值状态：1=正常 2=无重仓数据 3=非交易时间',
    create_time          datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time          datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_fund_valuation_time
        unique (fund_code, valuation_time)
)
    comment '基金实时估值快照表' collate = utf8mb4_unicode_ci;

create index idx_fund_code
    on fund_realtime_valuation (fund_code);

create table stock_base_info
(
    id           bigint auto_increment comment '主键ID'
        primary key,
    stock_code   varchar(20)                        not null comment '股票代码',
    stock_name   varchar(50)                        not null comment '股票名称',
    stock_market varchar(10)                        null comment '所属市场：SH=上交所 SZ=深交所 HK=港股 US=美股',
    industry     varchar(50)                        null comment '所属行业',
    listing_date date                               null comment '上市日期',
    create_time  datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time  datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_stock_code
        unique (stock_code)
)
    comment '股票基础信息表' collate = utf8mb4_unicode_ci;

create table stock_realtime_quote
(
    id              bigint auto_increment comment '主键ID'
        primary key,
    stock_code      varchar(20)                        not null comment '股票代码',
    latest_price    decimal(10, 4)                     null comment '最新价格',
    pre_close_price decimal(10, 4)                     null comment '昨日收盘价',
    change_amount   decimal(10, 4)                     null comment '涨跌额',
    change_rate     decimal(6, 4)                      null comment '涨跌幅(%)',
    high_price      decimal(10, 4)                     null comment '今日最高价',
    low_price       decimal(10, 4)                     null comment '今日最低价',
    trade_volume    bigint                             null comment '成交量(手)',
    trade_amount    decimal(15, 4)                     null comment '成交额(元)',
    quote_time      datetime                           null comment '行情更新时间',
    is_trading      tinyint                            null comment '是否交易中：1=是 0=停牌/休市',
    create_time     datetime default CURRENT_TIMESTAMP null comment '创建时间',
    update_time     datetime default CURRENT_TIMESTAMP null on update CURRENT_TIMESTAMP comment '更新时间',
    constraint uk_stock_code_quote
        unique (stock_code)
)
    comment '股票实时行情表' collate = utf8mb4_unicode_ci;
