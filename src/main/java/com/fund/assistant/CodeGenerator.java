package com.fund.assistant;

import com.baomidou.mybatisplus.generator.FastAutoGenerator;
import com.baomidou.mybatisplus.generator.config.OutputFile;
import com.baomidou.mybatisplus.generator.engine.VelocityTemplateEngine;

import java.util.Collections;

public class CodeGenerator {
    public static void main(String[] args) {
        FastAutoGenerator.create(
                        "jdbc:mysql://localhost:3306/fund_valuation_assistant?useSSL=false&useUnicode=true&characterEncoding=utf-8&serverTimezone=GMT%2B8",
                        "root",
                        "8023"
                )

                .globalConfig(builder -> {
                    builder.author("jhshen")
                            .outputDir(System.getProperty("user.dir") + "/src/main/java");
                })

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

                .strategyConfig(builder -> {
                    builder.addInclude("stock_base_info", "stock_realtime_quote", "fund_realtime_valuation")
                            .entityBuilder()
                            .enableLombok()
                            .controllerBuilder()
                            .enableRestStyle()
                            .serviceBuilder()
                            .formatServiceFileName("%sService")
                            .formatServiceImplFileName("%sServiceImpl");
                })

                .templateEngine(new VelocityTemplateEngine())
                .execute();
    }
}