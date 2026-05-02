package com.fund.assistant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.VelocityTemplateEngine;

import java.util.Collections;

/**
 * <p>
 * 基金估值助手 代码生成器
 * </p>
 *
 * @author jhShen
 * @since 2026-05-02
 */
public class CodeGenerator {
    public static void main(String[] args) {
        FastAutoGenerator.create(
                        // 【关键修改】加上了 allowPublicKeyRetrieval=true，解决 MySQL 8.0 连接报错
                        "jdbc:mysql://localhost:3306/fund_valuation_assistant?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true",
                        "root",
                        "8023")
                // 全局配置
                .globalConfig(builder -> {
                    builder.author("jhShen")
                            .outputDir(System.getProperty("user.dir") + "/src/main/java");
                })
                // 包配置
                .packageConfig(builder -> {
                    builder.parent("com.fund.assistant")
                            .entity("entity")
                            .mapper("mapper")
                            .service("service")
                            .serviceImpl("service.impl")
                            .controller("controller")
                            .pathInfo(Collections.singletonMap(
                                    OutputFile.xml,
                                    System.getProperty("user.dir") + "/src/main/resources/mapper"
                            ));
                })
                // 策略配置
                .strategyConfig(builder -> {
                    // 你的数据库所有表
                    builder.addInclude(
                            "ai_chat_history",
                            "ai_operate_record",
                            "community_post",
                            "community_post_comment",
                            "community_post_like",
                            "fund_base_info",
                            "fund_invest_exec_record",
                            "fund_invest_plan",
                            "fund_net_value_history",
                            "fund_stock_hold_detail",
                            "fund_trade_record",
                            "fund_user_group",
                            "market_index_daily",
                            "market_index_info",
                            "market_sector_info",
                            "user_daily_profit",
                            "user_feedback",
                            "user_follow_relation",
                            "user_fund_daily_profit",
                            "user_fund_favorite",
                            "user_fund_hold",
                            "user_info"
                    );

                    // 实体类配置
                    builder.entityBuilder()
                            .enableLombok()
                            .enableChainModel()
                            .enableTableFieldAnnotation()
                            .idType(IdType.AUTO);

                    // Controller配置
                    builder.controllerBuilder()
                            .enableRestStyle();

                    // Service配置
                    builder.serviceBuilder()
                            .formatServiceFileName("%sService")
                            .formatServiceImplFileName("%sServiceImpl");
                })
                // 模板引擎
                .templateEngine(new VelocityTemplateEngine())
                // 执行生成
                .execute();
    }
}