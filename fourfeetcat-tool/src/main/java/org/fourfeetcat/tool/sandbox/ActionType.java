package org.fourfeetcat.tool.sandbox;

/**
 * 待校验动作的类型（第20节前向接口）。
 *
 * <p>文件读与文件写**分开**取值，是为了将来能按读/写分权限——现在同路由到一处校验也不影响，但类型先立住，扩展时不用改调用方。
 */
public enum ActionType {
  FILE_READ,
  FILE_WRITE,
  SHELL_COMMAND,
  HTTP_REQUEST
}
