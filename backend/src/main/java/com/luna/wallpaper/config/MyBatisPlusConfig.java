package com.luna.wallpaper.config;

import java.time.LocalDateTime;

import org.apache.ibatis.reflection.MetaObject;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusPropertiesCustomizer;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;

@Configuration
@MapperScan("com.luna.wallpaper")
class MyBatisPlusConfig {

	@Bean
	MybatisPlusPropertiesCustomizer mybatisPlusPropertiesCustomizer() {
		return properties -> properties.getGlobalConfig()
				.setBanner(false)
				.setEnableSqlRunner(false);
	}

	@Bean
	MybatisPlusInterceptor mybatisPlusInterceptor() {
		MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
		interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
		interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
		return interceptor;
	}

	@Bean
	MetaObjectHandler auditTimeMetaObjectHandler() {
		return new MetaObjectHandler() {
			@Override
			public void insertFill(MetaObject metaObject) {
				LocalDateTime now = LocalDateTime.now();
				strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
				strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
			}

			@Override
			public void updateFill(MetaObject metaObject) {
				strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
			}
		};
	}
}
