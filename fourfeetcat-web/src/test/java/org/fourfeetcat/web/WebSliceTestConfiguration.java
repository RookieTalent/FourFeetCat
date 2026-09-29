package org.fourfeetcat.web;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 切片测试的上下文锚点（第26节）。
 *
 * <p>@WebMvcTest 要求测试所在包路径上存在一个 {@code @SpringBootConfiguration} 才能起切片上下文，而 web 模块**没有也不需要**启动类——
 * 它由 boot 模块装配运行。这个空配置就是那个锚点：切片只装 MVC 层，扫描到的非 web 层 Bean 会被 @WebMvcTest 的类型过滤器收窄掉， 不会把整个底座拉起来。
 *
 * <p>放在 test 源码里、且只扫本模块包：boot 模块的真上下文测试找的是它自己的启动类（那个在 main 源码里），二者互不影响。
 */
@SpringBootApplication(scanBasePackages = "org.fourfeetcat.web")
class WebSliceTestConfiguration {}
